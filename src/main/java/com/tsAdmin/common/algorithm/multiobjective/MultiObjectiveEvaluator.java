package com.tsAdmin.common.algorithm.multiobjective;  // 包声明：多目标优化算法包
import java.util.EnumMap;  // 记录多目标取值
import java.util.List;  // 导入列表接口

import com.tsAdmin.common.Coordinate;  // 导入坐标类
import com.tsAdmin.common.PathNode;  // 导入路径节点类
import com.tsAdmin.model.Assignment;  // 导入分配方案模型类
import com.tsAdmin.model.Car;  // 导入车辆模型类

/**
 * 多目标评估器
 * 专门评估老师要求的5个优化目标：
 * 1. 全局车辆总体等待时间
 * 2. 全局车辆空载里程  
 * 3. 车辆利用率（减少载重浪费）
 * 4. 全局车辆总运输达成吨位
 * 5. 全局车辆总碳排放量
 */
public class MultiObjectiveEvaluator
{
    /**
     * 目标类型枚举：为每个目标标记"是否以最小化为目标"
     * 若 isMinimize=false，则表示这是一个最大化指标（例如运量），在比较/归一化时会自动取相反数
     */
    public enum ObjectiveType {
        WAITING_TIME(true),      // 等待时间：越短越好
        EMPTY_DISTANCE(true),    // 空驶里程：越少越好
        LOAD_WASTE(true),        // 载重浪费：越少越好
        DELIVERED_TONNAGE(false),// 运量：越大越好（最大化目标）
        CARBON_EMISSION(true);   // 碳排放：越少越好

        private final boolean minimize;

        ObjectiveType(boolean minimize) {
            this.minimize = minimize;
        }

        public boolean isMinimize() {
            return minimize;
        }
    }

    /**
     * 多目标向量：内部使用 EnumMap 存储全部指标，便于扩展
     * 对外提供原始值（getValue）和用于比较/归一化的值（getComparableValue）
     */
    public static class ObjectiveVector {
        //存储各目标类型的值的映射表
        private final EnumMap<ObjectiveType, Double> values;

        public ObjectiveVector(EnumMap<ObjectiveType, Double> values) {
            // 创建一个新的枚举映射，用于存储目标值
            this.values = new EnumMap<>(ObjectiveType.class);
            // 遍历所有目标类型，确保每个目标都有对应的值
            for (ObjectiveType type : ObjectiveType.values()) {
                // 将目标值放入映射中，如果不存在则使用默认值0.0
                this.values.put(type, values.getOrDefault(type, 0.0));
            }
        }

        public double getValue(ObjectiveType type) {
            return values.getOrDefault(type, 0.0);
        }

        /**
         * 获取用于比较/归一化的值：最大化目标会取相反数，统一转成"越小越好"的形式
         */
        public double getComparableValue(ObjectiveType type) {
            double raw = getValue(type);
            return type.isMinimize() ? raw : -raw;
        }

        public EnumMap<ObjectiveType, Double> toMap() {
            return new EnumMap<>(values);
        }

        // 提供5个目标的便捷 getter（返回原始值）
        public double getWaitingTime() { return getValue(ObjectiveType.WAITING_TIME); }
        public double getEmptyDistance() { return getValue(ObjectiveType.EMPTY_DISTANCE); }
        public double getLoadWaste() { return getValue(ObjectiveType.LOAD_WASTE); }
        public double getDeliveredTonnage() { return getValue(ObjectiveType.DELIVERED_TONNAGE); }
        public double getCarbonEmission() { return getValue(ObjectiveType.CARBON_EMISSION); }

        @Override
        public String toString() {
            return String.format(
                "(等待=%.2f, 空驶=%.2f, 载重浪费=%.2f, 运量=%.2f, 碳排=%.2f)",
                getWaitingTime(), getEmptyDistance(), getLoadWaste(),
                getDeliveredTonnage(), getCarbonEmission());
        }
    }

    // ========== 评估器参数 ==========
    
    /**
     * 单位装卸耗时（默认 0.01，含义：每单位货物装卸 0.01 时间单位）
     * 作为车辆等待时间的估计值
     */
    private double handlingTimePerUnit = 0.01;

    /**
     * 碳排放因子（吨·公里 → 碳排放量），默认 0.0002，可按业务需求调整
     */
    private double carbonEmissionFactor = 0.0002;
    
    /**
     * 平均速度（单位：距离单位/时间单位）
     * 用于计算行驶时间，进而计算空驶里程和碳排放
     */
    private double averageSpeed = 60.0;

    /**
     * 默认构造函数：创建多目标评估器，使用默认参数
     */
    public MultiObjectiveEvaluator() {
        // 使用默认参数，无需额外初始化
    }

    // ========== 参数设置方法 ==========

    public void setHandlingTimePerUnit(double handlingTimePerUnit) {
        this.handlingTimePerUnit = handlingTimePerUnit;
    }

    public void setCarbonEmissionFactor(double carbonEmissionFactor) {
        this.carbonEmissionFactor = carbonEmissionFactor;
    }

    public void setAverageSpeed(double averageSpeed) {
        this.averageSpeed = averageSpeed;
    }

    /**
     * 路径模拟：统一计算5个优化目标
     * 这样所有指标都来自同一次遍历，保证一致性
     */
    private RouteMetrics computeRouteMetrics(Car car, List<PathNode> nodeList) {
        RouteMetrics metrics = new RouteMetrics();  // 初始化累加器

        if (car == null || nodeList == null || nodeList.isEmpty()) {
            return metrics;
        }

        Coordinate carPosition = new Coordinate(car.getPosition());  // 车辆当前位置
        double maxLoad = Math.max(1.0, car.getMaxLoad());  // 记录最大载重，至少为1避免除0
        double currentLoad = Math.max(0.0, maxLoad - car.getRemainingLoad());  // 当前已装载量

        for (PathNode pathNode : nodeList) {
            Coordinate target = pathNode.isOrigin()
                ? pathNode.getDemand().getOrigin()
                : pathNode.getDemand().getDestination();

            double distance = Coordinate.distance(carPosition, target);  // 当前位置到目标点距离
            
            // 计算空驶里程（当前无载货时的行驶距离）
            if (currentLoad <= 1e-6) {
                metrics.emptyDistance += distance;
            }

            // 载重浪费 = 剩余可用载重 × 行驶距离
            double unusedCapacity = Math.max(0.0, maxLoad - currentLoad);
            metrics.loadWaste += unusedCapacity * distance;
            
            // 碳排放 = (当前载重吨位 × 行驶距离) × 排放因子
            metrics.carbonEmission += currentLoad * distance * carbonEmissionFactor;

            // 装卸耗时 = 货物量 × 单位耗时
            double handlingTime = pathNode.getDemand().getQuantity() * handlingTimePerUnit;
            metrics.waitingTime += handlingTime;  // 累加等待时间

            carPosition.set(target);  // 更新车辆位置

            double quantity = pathNode.getDemand().getQuantity();
            if (pathNode.isOrigin()) {
                // 装货：载重上升
                currentLoad = Math.min(maxLoad, currentLoad + quantity);
            }
            else {
                // 卸货：先计入运量，再释放载重
                metrics.totalTonnage += quantity;
                currentLoad = Math.max(0.0, currentLoad - quantity);
            }
        }

        return metrics;
    }

    /**
     * 简单的统计结构体，集中存储一次模拟的5个优化指标
     */
    private static class RouteMetrics {
        double waitingTime = 0.0;     // 累计等待时间（装卸耗时）
        double emptyDistance = 0.0;   // 空车运行距离
        double loadWaste = 0.0;       // 载重浪费（未利用载重 × 距离）
        double totalTonnage = 0.0;    // 已完成运量
        double carbonEmission = 0.0;  // 碳排放（吨公里 × 因子）
    }

    /**
     * 根据目标类型计算对应的目标函数值
     */
    public double evaluate(Car car, List<PathNode> nodeList, ObjectiveType objectiveType) {
        RouteMetrics metrics = computeRouteMetrics(car, nodeList);

        switch (objectiveType) {
            case WAITING_TIME:
                return metrics.waitingTime;
            case EMPTY_DISTANCE:
                return metrics.emptyDistance;
            case LOAD_WASTE:
                return metrics.loadWaste;
            case DELIVERED_TONNAGE:
                return metrics.totalTonnage;
            case CARBON_EMISSION:
                return metrics.carbonEmission;
            default:
                throw new IllegalArgumentException("未知的目标类型: " + objectiveType);
        }
    }

    // ========== 多目标向量计算 ==========

    /**
     * 计算多目标向量（同时计算5个目标函数的值）
     */
    public ObjectiveVector evaluateAll(Car car, List<PathNode> nodeList) {
        RouteMetrics metrics = computeRouteMetrics(car, nodeList);

        EnumMap<ObjectiveType, Double> values = new EnumMap<>(ObjectiveType.class);
        values.put(ObjectiveType.WAITING_TIME, metrics.waitingTime);
        values.put(ObjectiveType.EMPTY_DISTANCE, metrics.emptyDistance);
        values.put(ObjectiveType.LOAD_WASTE, metrics.loadWaste);
        values.put(ObjectiveType.DELIVERED_TONNAGE, metrics.totalTonnage);
        values.put(ObjectiveType.CARBON_EMISSION, metrics.carbonEmission);

        return new ObjectiveVector(values);
    }

    /**
     * 计算Assignment的多目标向量
     * 便捷方法，直接从Assignment对象中提取车辆和路径节点信息
     */
    public ObjectiveVector evaluateAll(Assignment assignment) {
        return evaluateAll(assignment.getCar(), assignment.getNodeList());
    }

    // ========== 批量计算（用于多个车辆/分配方案） ==========

    /**
     * 计算多个分配方案的多目标向量
     */
    public List<ObjectiveVector> evaluateAll(List<Assignment> assignments) {
        List<ObjectiveVector> vectors = new java.util.ArrayList<>();
        for (Assignment assignment : assignments) {
            vectors.add(evaluateAll(assignment));
        }
        return vectors;
    }

    /**
     * 计算所有车辆的总目标值（用于某些优化目标）
     */
    public double sumObjective(List<Assignment> assignments, ObjectiveType objectiveType) {
        double sum = 0.0;
        for (Assignment assignment : assignments) {
            sum += evaluate(assignment.getCar(), assignment.getNodeList(), objectiveType);
        }
        return sum;
    }

    // ========== Getter方法 ==========

    public double getHandlingTimePerUnit() { return handlingTimePerUnit; }
    public double getCarbonEmissionFactor() { return carbonEmissionFactor; }
    public double getAverageSpeed() { return averageSpeed; }
}