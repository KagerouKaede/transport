package com.tsAdmin.model;

/**
 * 车辆统计参数类，用于存储每辆车的运营统计数据。
 */
public class CarStatistics
{
    private double waitingTime;
    private double emptyDistance;
    private double wastedLoad;
    private double totalWeight;
    private double carbonEmission;

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

    // ===== getter 保持不变 =====
    public double getWaitingTime() { return waitingTime; }
    public double getEmptyDistance() { return emptyDistance; }
    public double getWastedLoad() { return wastedLoad; }
    public double getTotalWeight() { return totalWeight; }
    public double getCarbonEmission() { return carbonEmission; }
} 