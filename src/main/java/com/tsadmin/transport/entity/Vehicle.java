package com.tsadmin.transport.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import com.tsadmin.transport.domain.share.Coordinate;
import com.tsadmin.transport.domain.share.PathNode;
import com.tsadmin.transport.domain.share.Timer;
import com.tsadmin.transport.util.RandomUtil;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 车辆 */
@Entity
@Table(name = "vehicles")
public class Vehicle
{
    /** 车辆状态 */
    public enum VehState
    {
        /** 空闲 */
        AVAILABLE,
        /** 接单行驶 */
        ORDER_TAKEN,
        /** 装货 */
        LOADING,
        /** 运货行驶 */
        TRANSPORTING,
        /** 卸货 */
        UNLOADING,
        /** 停车等待 */
        FREEZE
    }

    private static Map<VehState, Double> freezeChance = Map.of(
        VehState.AVAILABLE, 0.00,
        VehState.ORDER_TAKEN, 0.04,
        VehState.LOADING, 0.02,
        VehState.TRANSPORTING, 0.04,
        VehState.UNLOADING, 0.02
    );

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(columnDefinition = "UUID")
    private UUID uuid;
    @Column(name = "max_load", nullable = false)
    private int maxLoad;
    @Column(name = "max_volume", nullable = false)
    private int maxVolume;
    @Column(name = "current_load")
    private double load;
    @Column(name = "current_volume")
    private double volume;
    @Embedded
    private Coordinate position;
    @Enumerated(EnumType.STRING)
    @Column(name = "current_state")
    private VehState currState;
    @Enumerated(EnumType.STRING)
    @Column(name = "previous_state")
    private VehState prevState;

    private List<PathNode> nodeList = new ArrayList<>();
    private Timer stateTimer;                           // 状态计时器
    private Demand currDemand;                          // 车辆当前执行订单
    private CarStatistics statistics;                   // 车辆统计参数

    /** 车辆构造函数 */
    protected Vehicle() {}
    public Vehicle(UUID uuid, int maxLoad, int maxVolume, Coordinate position)
    {
        this.uuid = uuid;
        this.maxLoad = maxLoad;
        this.maxVolume = maxVolume;
        this.position = position;
        this.stateTimer = new Timer();
        this.statistics = new CarStatistics();
    }
    /**
     * 车辆的拷贝构造方法
     * <p><i>拷贝得到的车辆不带有计时器以及统计数据等数据</i>
     * @param others 被拷贝的车辆
     */
    public Vehicle(Vehicle others)
    {
        this(others.uuid, others.maxLoad, others.maxVolume, others.position);
        this.load = others.load;
        this.volume = others.volume;
    }

    // Setter
    public void setLoad(int load) { this.load = load; }
    public void setVolume(int volume) { this.volume = volume; }
    public void setPosition(Coordinate position) { this.position = position; }
    public void setNodeList(List<PathNode> nodeList) { this.nodeList = nodeList; }
    public void setCurrDemand(Demand demand) { currDemand = demand; }
    public void setState(VehState newState)
    {
        prevState = currState;
        currState = newState;
    }

    // Getter
    public UUID getUUID() { return uuid; }
    public int getMaxLoad() { return maxLoad; }
    public int getMaxVolume() { return maxVolume; }
    public double getLoad() { return load; }
    public double getVolume() { return volume; }
    public Coordinate getPosition() { return position; }
    public List<PathNode> getNodeList() { return nodeList; }
    public VehState getState() { return currState; }
    public VehState getPrevState() { return prevState; }
    public Timer getStateTimer() { return stateTimer; }
    public Demand getCurrDemand() { return currDemand; }
    public CarStatistics getStatistics() { return statistics; }
    public double getRemainingLoad() { return maxLoad - load; }
    public double getRemainingVolume() { return maxVolume - volume; }

    public void addPathNode(PathNode node) { nodeList.add(node); }

    /** 获取并移除路径点列表中的第一个点 */
    public PathNode fetchFirstNode()
    {
        PathNode ret = nodeList.getFirst();
        nodeList.removeFirst();
        return ret;
    }

    /** 计时器滴答一次，即向前进一周期 并记录时间 */
    public void tick(VehState currState)
    { 
        stateTimer.tick();
    }

    /**
     * 状态转换函数，根据当前状态和随机数决定车辆的下一个状态，并处理装卸货、冻结等逻辑
     */
    public void changeState()
    {
        double randNum = RandomUtil.nextDouble();
        VehState nextState = currState;

        // 非冻结状态有一定几率变为冻结状态，模拟小概率事故的发生，此时当前状态的一切操作被冻结（延后）
        if (currState != VehState.FREEZE && randNum < freezeChance.get(currState))
        {
            nextState = VehState.FREEZE;
        }
        else
        {
            // 当前状态结束，对车辆属性参数进行对应修改并根据当前状态获取下一状态
            switch (currState)
            {
                case ORDER_TAKEN:
                    position = currDemand.getOrigin();
                    nextState = VehState.LOADING;
                    break;

                case LOADING:
                    load += currDemand.getQuantity();
                    volume += currDemand.getVolume();

                    nextState = nodeList.getFirst().isOrigin() ? VehState.ORDER_TAKEN : VehState.TRANSPORTING;
                    currDemand = nodeList.getFirst().getDemand();
                    break;

                case TRANSPORTING:
                    position = currDemand.getDestination();
                    nextState = VehState.UNLOADING;
                    break;

                case UNLOADING:
                    load -= currDemand.getQuantity();
                    volume -= currDemand.getVolume();
                    statistics.incrementCompletedOrders();
                    statistics.calculateAverageOrderCycle();
                    currDemand.onCompleted();

                    if (!nodeList.isEmpty())
                    {
                        nextState = nodeList.getFirst().isOrigin() ? VehState.ORDER_TAKEN : VehState.TRANSPORTING;
                        currDemand = nodeList.getFirst().getDemand();
                    }
                    else
                    {
                        nextState = VehState.AVAILABLE;
                        currDemand = null;
                        statistics.resetCompleteOrderCycle();
                    }
                    break;

                case FREEZE:
                    // 当前状态为冻结状态，在转换状态前需要回退状态，根据上一状态进行状态转换
                    setState(prevState);
                    changeState();
                    return;

                case AVAILABLE:
                    nextState = nodeList.isEmpty() ? VehState.AVAILABLE : VehState.ORDER_TAKEN;
                    if(nextState==VehState.ORDER_TAKEN)
                    { 
                        currDemand= nodeList.getFirst().getDemand();
                    }
                default:
                    break;
            }
        }

        setState(nextState);
        resetTimer();
    }

    /** 重置当前状态计时器*/
    private void resetTimer()
    {
        int time = switch (currState)
        {
            case LOADING, UNLOADING -> (int)(0.01 * currDemand.getQuantity());
            case FREEZE -> 30;
            default -> 0;
        };
        stateTimer.setTime(time);
    }
}
