package com.tsadmin.transport.service.simulation;

import java.util.Map;

import com.tsadmin.transport.common.enums.VehicleStatus;
import com.tsadmin.transport.common.util.RandomUtil;
import com.tsadmin.transport.domain.entity.Vehicle;

public class VehStateMachine
{
    private static Map<VehicleStatus, Double> freezeChance = Map.of(
        VehicleStatus.AVAILABLE, 0.00,
        VehicleStatus.ORDER_TAKEN, 0.04,
        VehicleStatus.LOADING, 0.02,
        VehicleStatus.TRANSPORTING, 0.04,
        VehicleStatus.UNLOADING, 0.02
    );

    /**
     * 状态转换函数，根据当前状态和随机数决定车辆的下一个状态，并处理装卸货、冻结等逻辑
     */
    public void changeState(Vehicle veh)
    {
        double randNum = RandomUtil.nextDouble();
        VehicleStatus currState = veh.getState();
        VehicleStatus nextState = currState;

        // 非冻结状态有一定几率变为冻结状态，模拟小概率事故的发生，此时当前状态的一切操作被冻结（延后）
        if (currState != VehicleStatus.FREEZE && randNum < freezeChance.get(currState))
        {
            nextState = VehicleStatus.FREEZE;
        }
        else
        {
            // 当前状态结束，对车辆属性参数进行对应修改并根据当前状态获取下一状态
            switch (currState)
            {
                case ORDER_TAKEN:
                    position = currDemand.getOrigin();
                    nextState = VehicleStatus.LOADING;
                    break;

                case LOADING:
                    load += currDemand.getQuantity();
                    volume += currDemand.getVolume();

                    nextState = nodeList.getFirst().isOrigin() ? VehicleStatus.ORDER_TAKEN : VehicleStatus.TRANSPORTING;
                    currDemand = nodeList.getFirst().getDemand();
                    break;

                case TRANSPORTING:
                    position = currDemand.getDestination();
                    nextState = VehicleStatus.UNLOADING;
                    break;

                case UNLOADING:
                    load -= currDemand.getQuantity();
                    volume -= currDemand.getVolume();
                    statistics.incrementCompletedOrders();
                    statistics.calculateAverageOrderCycle();
                    // currDemand.onCompleted();

                    if (!nodeList.isEmpty())
                    {
                        nextState = nodeList.getFirst().isOrigin() ? VehicleStatus.ORDER_TAKEN : VehicleStatus.TRANSPORTING;
                        currDemand = nodeList.getFirst().getDemand();
                    }
                    else
                    {
                        nextState = VehicleStatus.AVAILABLE;
                        currDemand = null;
                        statistics.resetCompleteOrderCycle();
                    }
                    break;

                case FREEZE:
                    // 当前状态为冻结状态，在转换状态前需要回退状态，根据上一状态进行状态转换
                    veh.setState(veh.getPrevState());
                    changeState(veh);
                    return;

                case AVAILABLE:
                    nextState = nodeList.isEmpty() ? VehicleStatus.AVAILABLE : VehicleStatus.ORDER_TAKEN;
                    if(nextState==VehicleStatus.ORDER_TAKEN)
                    { 
                        currDemand = nodeList.getFirst().getDemand();
                    }
                default:
                    break;
            }
        }

        veh.setState(nextState);
        veh.resetTimer();
    }
}
