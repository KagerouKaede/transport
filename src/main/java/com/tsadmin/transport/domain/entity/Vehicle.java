package com.tsadmin.transport.domain.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import com.tsadmin.transport.common.enums.VehicleStatus;
import com.tsadmin.transport.common.share.Coordinate;
import com.tsadmin.transport.common.share.PathNode;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

/** 车辆 */
@Entity
@Table
public class Vehicle
{
    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(columnDefinition = "UUID")
    private UUID uuid;
    @Column(nullable = false)
    private int maxLoad;
    @Column(nullable = false)
    private int maxVolume;
    @Embedded
    private Coordinate position;
    private double load = 0;
    private double volume = 0;
    @Enumerated(EnumType.STRING)
    private VehicleStatus currState = VehicleStatus.AVAILABLE;
    @Enumerated(EnumType.STRING)
    private VehicleStatus prevState = VehicleStatus.AVAILABLE;

    @Transient
    private List<PathNode> nodeList = new ArrayList<>();
    @Transient
    private Demand currDemand;                          // 车辆当前执行订单
    @Transient
    private CarStatistics statistics;                   // 车辆统计参数

    /** 车辆构造函数 */
    public Vehicle() {}
    public Vehicle(int maxLoad, int maxVolume, Coordinate position)
    {
        this.maxLoad = maxLoad;
        this.maxVolume = maxVolume;
        this.position = position;
        this.statistics = new CarStatistics();
    }
    /**
     * 车辆的拷贝构造方法
     * <p><i>拷贝得到的车辆不带有计时器以及统计数据等数据</i>
     * @param others 被拷贝的车辆
     */
    public Vehicle(Vehicle others)
    {
        this(others.maxLoad, others.maxVolume, others.position);
        this.load = others.load;
        this.volume = others.volume;
    }

    public void addPathNode(PathNode node) { nodeList.add(node); }

    public boolean canLoad(double load, double volume)
    {
        return this.load + load <= maxLoad && this.volume + volume <= maxVolume;
    }

    public void load(double load, double volume)
    {
        if (!canLoad(load, volume)) throw new IllegalArgumentException("Overloaded! Please check canLoad(...) first!");

        this.load += load;
        this.volume += volume;
    }

    // /** 获取并移除路径点列表中的第一个点 */
    // public PathNode fetchFirstNode()
    // {
    //     PathNode ret = nodeList.getFirst();
    //     nodeList.removeFirst();
    //     return ret;
    // }

    // /** 重置当前状态计时器*/
    // public void resetTimer()
    // {
    //     int time = switch (currState)
    //     {
    //         case LOADING, UNLOADING -> (int)(0.01 * currDemand.getQuantity());
    //         case FREEZE -> 30;
    //         default -> 0;
    //     };
    //     // stateTimer.setTime(time);
    // }

    // Setter
    public void setPosition(Coordinate position) { this.position = position; }
    public void setNodeList(List<PathNode> nodeList) { this.nodeList = nodeList; }
    public void setCurrDemand(Demand demand) { currDemand = demand; }
    public void setState(VehicleStatus newState)
    {
        prevState = currState;
        currState = newState;
    }
    // 使用 load() & unload() 修改载重和体积
    // public void setLoad(int load) { this.load = load; }
    // public void setVolume(int volume) { this.volume = volume; }

    // Getter
    public UUID getUUID() { return uuid; }
    public int getMaxLoad() { return maxLoad; }
    public int getMaxVolume() { return maxVolume; }
    public double getLoad() { return load; }
    public double getVolume() { return volume; }
    public Coordinate getPosition() { return position; }
    public List<PathNode> getNodeList() { return nodeList; }
    public VehicleStatus getState() { return currState; }
    public VehicleStatus getPrevState() { return prevState; }
    public Demand getCurrDemand() { return currDemand; }
    public CarStatistics getStatistics() { return statistics; }
    public double getRemainingLoad() { return maxLoad - load; }
    public double getRemainingVolume() { return maxVolume - volume; }
}
