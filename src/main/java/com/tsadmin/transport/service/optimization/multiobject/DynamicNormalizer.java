package com.tsadmin.transport.service.optimization.multiobject;  // 包声明：多目标优化算法包

import java.util.*;  // 导入Java工具类（List, ArrayList, Map, HashMap等）

import com.tsAdmin.common.algorithm.multiobjective.MultiObjectiveEvaluator.ObjectiveType;  // 导入目标类型枚举
import com.tsAdmin.common.algorithm.multiobjective.MultiObjectiveEvaluator.ObjectiveVector;  // 导入多目标向量类

/**
 * 动态归一化模块
 * 实现动态归一化功能：T_norm = (T - T_min) / (T_max - T_min)
 * 支持在算法运行过程中动态更新T_max和T_min
 * 处理三个目标维度的归一化
 */
public class DynamicNormalizer
{
    /**
     * 归一化范围类
     * 存储每个目标类型的最大值和最小值
     */
    /**
     * 归一化范围内部类
     * 存储每个目标类型的最大值和最小值，用于归一化计算
     */
    private static class NormalizationRange
    {
        private double min;  // 最小值：用于归一化计算的下界
        private double max;  // 最大值：用于归一化计算的上界
        private boolean initialized;  // 是否已初始化：标记范围是否已经设置过值

        /**
         * 构造函数：初始化归一化范围
         */
        public NormalizationRange()
        {
            this.min = Double.MAX_VALUE;      // 初始化为极大值，便于后续更新为实际最小值
            this.max = -Double.MAX_VALUE;     // 初始化为极小值，便于后续更新为实际最大值
            this.initialized = false;         // 标记为未初始化
        }

        /**
         * 更新归一化范围：根据新值更新最小值和最大值
         * @param value 新的目标值
         */
        public void update(double value)
        {
            // 如果范围未初始化，将第一个值同时设为最小值和最大值
            if (!initialized)
            {
                min = value;  // 第一个值作为最小值
                max = value;  // 第一个值作为最大值
                initialized = true;  // 标记为已初始化
            }
            else
            {
                // 如果新值更小，更新最小值
                if (value < min) min = value;
                // 如果新值更大，更新最大值
                if (value > max) max = value;
            }
        }

        /**
         * 手动设置归一化范围
         * @param min 最小值
         * @param max 最大值
         */
        public void setRange(double min, double max)
        {
            this.min = min;  // 设置最小值
            this.max = max;  // 设置最大值
            this.initialized = true;  // 标记为已初始化
        }

        public double getMin() { return min; }  // 获取最小值
        public double getMax() { return max; }  // 获取最大值
        public boolean isInitialized() { return initialized; }  // 检查是否已初始化
        public double getRange() { return max - min; }  // 获取范围大小（最大值-最小值）
    }

    private final Map<ObjectiveType, NormalizationRange> ranges;  // 各目标类型的归一化范围映射表

    /**
     * 构造函数：创建动态归一化器
     */
    public DynamicNormalizer()
    {
        this.ranges = new HashMap<>();  // 创建范围映射表
        // 初始化所有目标类型的范围
        // 遍历所有目标类型（DISTANCE, TIME, COST）
        for (ObjectiveType type : ObjectiveType.values())
        {
            // 为每个目标类型创建一个未初始化的归一化范围
            ranges.put(type, new NormalizationRange());
        }
    }

    /**
     * 更新归一化范围（从单个值）
     * 
     * @param objectiveType 目标类型
     * @param value 目标值
     */
    public void update(ObjectiveType objectiveType, double value)
    {
        double comparable = objectiveType.isMinimize() ? value : -value;  // 对最大化目标取反，统一为最小化
        ranges.get(objectiveType).update(comparable);  // 更新对应维度的[min,max]
    }

    /**
     * 更新归一化范围（从目标向量）
     * 
     * @param vector 目标向量
     */
    public void update(ObjectiveVector vector)
    {
        for (ObjectiveType type : ObjectiveType.values())  // 针对每个目标维度刷新范围
        {
            update(type, vector.getValue(type));  // 复用单值更新逻辑
        }
    }

    /**
     * 更新归一化范围（从多个目标向量）
     * 
     * @param vectors 目标向量列表
     */
    public void update(List<ObjectiveVector> vectors)
    {
        for (ObjectiveVector vector : vectors)  // 遍历所有向量逐个更新
        {
            update(vector);
        }
    }

    /**
     * 从非支配集更新归一化范围
     * 
     * @param nonDominatedSet 非支配集
     */
    public void updateFromNonDominatedSet(NonDominatedSet nonDominatedSet)
    {
        List<ObjectiveVector> vectors = nonDominatedSet.getObjectiveVectors();  // 拿到当前帕累托前沿
        update(vectors);  // 直接复用批量更新
    }

    /**
     * 设置归一化范围（手动设置）
     * 
     * @param objectiveType 目标类型
     * @param min 最小值
     * @param max 最大值
     */
    public void setRange(ObjectiveType objectiveType, double min, double max)
    {
        if (min > max)  // 防止手动配置非法区间
        {
            throw new IllegalArgumentException("最小值不能大于最大值");
        }
        ranges.get(objectiveType).setRange(min, max);  // 直接覆盖内部范围
    }

    /**
     * 归一化单个值
     * 公式：T_norm = (T - T_min) / (T_max - T_min)
     * 
     * @param objectiveType 目标类型（DISTANCE/TIME/COST）
     * @param value 原始值
     * @return 归一化后的值（0.0-1.0之间）
     */
    public double normalize(ObjectiveType objectiveType, double value)
    {
        double comparable = objectiveType.isMinimize() ? value : -value;  // 方向统一后再做归一化
        // 获取指定目标类型的归一化范围
        NormalizationRange range = ranges.get(objectiveType);  // 找到对应目标的范围
        
        // 如果范围未初始化，无法进行归一化，返回中间值0.5
        if (!range.isInitialized())
        {
            // 如果范围未初始化，返回0.5（中间值）
            return 0.5;
        }

        // 计算范围大小（最大值-最小值）
        double rangeSize = range.getRange();
        // 如果范围太小（接近0），避免除以0的错误，返回中间值0.5
        if (Math.abs(rangeSize) < 1e-9)
        {
            // 如果范围太小（接近0），返回0.5
            return 0.5;
        }

        // 应用归一化公式：T_norm = (T - T_min) / (T_max - T_min)
        double normalized = (comparable - range.getMin()) / rangeSize;  // 线性映射至[0,1]
        
        // 限制在[0, 1]范围内（处理超出范围的值）
        // 如果归一化值小于0，说明原始值小于最小值，限制为0
        if (normalized < 0.0) normalized = 0.0;
        // 如果归一化值大于1，说明原始值大于最大值，限制为1
        if (normalized > 1.0) normalized = 1.0;
        
        // 返回归一化后的值（在[0, 1]范围内）
        return normalized;
    }

    /**
     * 归一化目标向量
     * 
     * @param vector 原始目标向量
     * @return 归一化后的目标向量
     */
    public ObjectiveVector normalize(ObjectiveVector vector)
    {
        EnumMap<ObjectiveType, Double> normalizedValues = new EnumMap<>(ObjectiveType.class);  // 枚举映射保持键有序
        for (ObjectiveType type : ObjectiveType.values())  // 逐维度归一化
        {
            normalizedValues.put(type, normalize(type, vector.getValue(type)));
        }
        return new ObjectiveVector(normalizedValues);
    }

    /**
     * 归一化多个目标向量
     * 
     * @param vectors 原始目标向量列表
     * @return 归一化后的目标向量列表
     */
    public List<ObjectiveVector> normalize(List<ObjectiveVector> vectors)
    {
        List<ObjectiveVector> normalized = new ArrayList<>();  // 存储归一化结果
        for (ObjectiveVector vector : vectors)  // 依次处理每个输入向量
        {
            normalized.add(normalize(vector));
        }
        return normalized;
    }

    /**
     * 反归一化（将归一化值转换回原始值）
     * 
     * @param objectiveType 目标类型
     * @param normalizedValue 归一化后的值（0.0-1.0）
     * @return 原始值
     */
    public double denormalize(ObjectiveType objectiveType, double normalizedValue)
    {
        NormalizationRange range = ranges.get(objectiveType);
        
        if (!range.isInitialized())  // 尚未有样本，无法反归一化
        {
            throw new IllegalStateException("归一化范围未初始化，无法反归一化");
        }

        double rangeSize = range.getRange();  // 计算跨度
        double comparable = range.getMin() + normalizedValue * rangeSize;  // 把归一化值映射回可比较值
        return objectiveType.isMinimize() ? comparable : -comparable;  // 对最大化目标再次取反
    }

    /**
     * 计算归一化后的能量差（△E）
     * 用于概率接受机制
     * 
     * @param oldVector 旧解的目标向量
     * @param newVector 新解的目标向量
     * @return 归一化后的能量差（标量值，用于计算接受概率）
     */
    public double calculateNormalizedEnergyDifference(ObjectiveVector oldVector, ObjectiveVector newVector)
    {
        double sumSquares = 0.0;  // 用欧氏距离衡量整体差异
        for (ObjectiveType type : ObjectiveType.values())  // 所有目标逐一比较
        {
            double delta = normalize(type, newVector.getValue(type)) -
                           normalize(type, oldVector.getValue(type));
            sumSquares += delta * delta;
        }
        return Math.sqrt(sumSquares);  // 返回归一化空间距离
    }

 
    /**
     * 获取指定目标类型的归一化范围
     * 
     * @param objectiveType 目标类型
     * @return [最小值, 最大值]数组，如果未初始化则返回null
     */
    public double[] getRange(ObjectiveType objectiveType)
    {
        NormalizationRange range = ranges.get(objectiveType);  // 获取内部存储
        if (!range.isInitialized())  // 未初始化返回null，提示调用方
        {
            return null;
        }
        return new double[]{range.getMin(), range.getMax()};  // 以数组形式返回副本
    }

    /**
     * 获取所有目标类型的归一化范围
     * 
     * @return 目标类型到范围的映射
     */
    public Map<ObjectiveType, double[]> getAllRanges()
    {
        Map<ObjectiveType, double[]> allRanges = new HashMap<>();  // 生成快照避免暴露内部状态
        for (ObjectiveType type : ObjectiveType.values())  // 遍历所有目标类型
        {
            double[] range = getRange(type);
            if (range != null)
            {
                allRanges.put(type, range);
            }
        }
        return allRanges;
    }

    /**
     * 检查指定目标类型的范围是否已初始化
     * 
     * @param objectiveType 目标类型
     * @return true表示已初始化
     */
    public boolean isInitialized(ObjectiveType objectiveType)
    {
        return ranges.get(objectiveType).isInitialized();
    }

    /**
     * 检查所有目标类型的范围是否都已初始化
     * 
     * @return true表示都已初始化
     */
    public boolean isAllInitialized()
    {
        for (ObjectiveType type : ObjectiveType.values())  // 任一未初始化则返回false
        {
            if (!isInitialized(type))
            {
                return false;
            }
        }
        return true;
    }

    /**
     * 重置归一化范围
     */
    public void reset()
    {
        for (ObjectiveType type : ObjectiveType.values())  // 所有目标重新生成空范围
        {
            ranges.put(type, new NormalizationRange());
        }
    }

    /**
     * 重置指定目标类型的归一化范围
     */
    public void reset(ObjectiveType objectiveType)
    {
        ranges.put(objectiveType, new NormalizationRange());  // 指定目标维度恢复初始状态
    }
}

