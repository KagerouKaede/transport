package com.tsAdmin.model;

/**
 * 车辆统计参数类，用于存储每辆车的运营统计数据。
 */
public class CarStatistics
{
    private static final double carbonEmissionFactor = 0.0002; // MultiObjectiveEvaluator类里设定的碳排放因子

    //基础指标
    private double waitingTime;// 车辆总体等待时间
    private double emptyDistance;// 车辆总空载里程
    private double wastedLoad;// 车辆总载重浪费
    private double totalWeight;// 车辆总运量
    private double carbonEmission;// 车辆总碳排放量
    private double totalDistance;// 车辆总行驶里程
    private int completedOrders = 0; // 完成订单数
    private int completeOrderCycle = 0; // 完成订单周期数
    private double averageOrderCycle = 0.0; // 平均完成订单周期数

    //基础计算指标（单纯使用基础指标四则运算得到的指标）
    //虽然基础计算指标没有存储必要性，但方便看代码时一眼明白有哪些指标，不许删，谁删我给谁妈杀了。
    private double mileage_utilization_rate;// 里程利用率 = （总里程 - 总空载里程）/ 总里程
    private double carbon_emission_per_unit;// 单位运量碳排放 = 总碳排放 / 使用运力（理论上这么算，但由于碳排放是使用运力乘系数得到的，这里单位运量碳排放就是碳排放因子）
    //拓展指标（需要用到使用Car属性运算得到的指标）
    private double load_utilization_rate;// 实时装载率（载重利用率） = 载重 / 核载
    private double capacity_utilization_rate;// 运力利用率 = 使用运力(碳排放/碳排放因子) / 总共运力

    // ===== 新增 setter =====
    public void setWaitingTime(double waitingTime) {
        this.waitingTime = waitingTime;
    }

    public void setEmptyDistance(double emptyDistance) {
        this.emptyDistance = emptyDistance;
    }

    public void setWastedLoad(double wastedLoad) {
        this.wastedLoad = wastedLoad;
    }

    public void setTotalWeight(double totalWeight) {
        this.totalWeight = totalWeight;
    }

    public void setCarbonEmission(double carbonEmission) {
        this.carbonEmission = carbonEmission;
    }

    public void setTotalDistance(double totalDistance) {
        this.totalDistance = totalDistance;
    }

    public void calculateLoad_utilization_rate(Car car) {
        int load = car.getLoad();
        int maxload = car.getMaxLoad();
        if (maxload == 0) {
            this.load_utilization_rate = 0.0;
        } else {
            this.load_utilization_rate = (double)load / maxload;
        }
    }
    public void calculateCapacity_utilization_rate(Car car) {
        double usedCapacity = this.carbonEmission / carbonEmissionFactor;
        double totalCapacity = car.getMaxLoad()*totalDistance;
        if (totalCapacity == 0) {
            this.capacity_utilization_rate = 0.0;
        } else {
            this.capacity_utilization_rate = usedCapacity / totalCapacity;
        }
    }
    public void incrementCompletedOrders() {
        this.completedOrders++;
    }
    public void incrementCompleteOrderCycle() {
        this.completeOrderCycle++;
    }
    public void resetCompleteOrderCycle() {
        this.completeOrderCycle = 0;
    }
    public void calculateAverageOrderCycle() {
        if (completedOrders == 0) {
            this.averageOrderCycle = 0;
        } else {
            this.averageOrderCycle = (averageOrderCycle*(completedOrders-1)+completeOrderCycle) / (double)completedOrders;
        }
    }

    // ===== getter 保持不变 =====
    public double getWaitingTime() { return waitingTime; }
    public double getEmptyDistance() { return emptyDistance; }
    public double getWastedLoad() { return wastedLoad; }
    public double getTotalWeight() { return totalWeight; }
    public double getCarbonEmission() { return carbonEmission; }
    public double getTotalDistance() { return totalDistance; }
    public double getMileageUtilizationRate() {
        if (totalDistance == 0) return 0.0;
        mileage_utilization_rate = (totalDistance - emptyDistance) / totalDistance;
        return mileage_utilization_rate;
    }
    public double getCarbonEmissionPerUnit() {
        if (totalWeight == 0) return 0.0;
        carbon_emission_per_unit = carbonEmissionFactor;
        return carbon_emission_per_unit;
    }
    public double getLoad_utilization_rate() { return load_utilization_rate; }
    public double getCapacity_utilization_rate() { return capacity_utilization_rate; }
    public int getCompletedOrders() { return completedOrders; }
    public double getAverageOrderCycle() { return averageOrderCycle; }
} 