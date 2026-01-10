package com.tsAdmin.common.algorithm.multiobjective;  // 包声明：多目标优化算法包
import java.util.List;  // 导入列表接口
import java.util.Random;  // 导入随机数生成器

import com.tsAdmin.common.algorithm.multiobjective.MultiObjectiveEvaluator.ObjectiveVector;  // 导入多目标向量类
import com.tsAdmin.common.algorithm.multiobjective.NonDominatedSet.AddAnalysis;

/**
 * 概率接受机制
 * 实现基于归一化△E的概率接受函数
 * 根据新解与非支配集的关系决定是否接受：
 * 1. 如果新解支配某些旧解 → 总是接受，并更新前沿
 * 2. 如果新解被某些旧解支配 → 以概率接受（退火机制）
 * 3. 如果新解与所有旧解互不支配 → 总是接受，并加入前沿
 */ 
public class ProbabilityAcceptance
{
    private final Random random;  // 随机数生成器：用于概率接受判断
    private final DynamicNormalizer normalizer;  // 动态归一化器：用于归一化能量差计算

    /**
     * 默认构造函数：创建概率接受机制，使用默认的随机数生成器和归一化器
     */
    public ProbabilityAcceptance()
    {
        this.random = new Random();  // 创建默认的随机数生成器
        this.normalizer = new DynamicNormalizer();  // 创建默认的动态归一化器
    }

    /**
     * 带归一化器的构造函数：创建概率接受机制，使用指定的归一化器
     * @param normalizer 动态归一化器
     */
    public ProbabilityAcceptance(DynamicNormalizer normalizer)
    {
        this.random = new Random();  // 创建默认的随机数生成器
        this.normalizer = normalizer;  // 使用指定的归一化器
    }

    /**
     * 完整构造函数：创建概率接受机制，使用指定的随机数生成器和归一化器
     * @param random 随机数生成器
     * @param normalizer 动态归一化器
     */
    public ProbabilityAcceptance(Random random, DynamicNormalizer normalizer)
    {
        this.random = random;  // 使用指定的随机数生成器
        this.normalizer = normalizer;  // 使用指定的归一化器
    }

    /**
     * 计算接受概率（标准模拟退火公式）
     * P(accept) = exp(-△E / T)
     * 
     * @param deltaE 能量差（△E）：新解与旧解的差异，deltaE < 0表示新解更优
     * @param temperature 当前温度：温度越高，接受劣解的概率越大
     * @return 接受概率（0.0-1.0）
     */
    public double calculateAcceptanceProbability(double deltaE, double temperature)
    {
        // 如果温度过低（<=0），只接受更优解（deltaE < 0）
        if (temperature <= 0)
        {
            // 温度过低，只接受更优解
            // 如果新解更优（deltaE < 0），接受概率为1.0；否则为0.0
            return deltaE < 0 ? 1.0 : 0.0;
        }

        // 如果新解更优（deltaE < 0），总是接受
        if (deltaE < 0)
        {
            // 新解更优，总是接受
            return 1.0;
        }

        // 使用标准模拟退火接受概率公式：P = exp(-△E / T)
        // 当deltaE > 0时（新解更差），根据温度决定接受概率
        // 温度越高，接受概率越大；能量差越大，接受概率越小
        return Math.exp(-deltaE / temperature);  // 标准退火公式
    }


    /**
     * 计算多目标情况下的接受概率
     * 根据新解与非支配集的关系决定是否接受：
     * 1. 如果新解支配某些旧解 → 总是接受，并更新前沿
     * 2. 如果新解被某些旧解支配 → 以概率接受（退火机制）
     * 3. 如果新解与所有旧解互不支配 → 总是接受，并加入前沿
     * 
     * @param newVector 新解的目标向量
     * @param nonDominatedSet 非支配集
     * @param temperature 当前温度
     * @return 接受结果
     */
    public AcceptanceResult calculateMultiObjectiveAcceptance(
            ObjectiveVector newVector,
            NonDominatedSet nonDominatedSet,
            double temperature,
             AddAnalysis analysis)
    {
         if (analysis == null) {
        analysis = nonDominatedSet.analyzeAdd(newVector); // 如果未传入，则自己分析
    }
        

        // ========== 情况 1：新解支配某些旧解 ========== 
        if (!analysis.getDominated().isEmpty())
        {
            // 新解支配某些旧解 → 总是接受
            // 返回接受结果，概率为1，原因为支配其他解
            return new AcceptanceResult(true, 1.0, AcceptanceReason.DOMINATES_OTHERS);
        }
        // ========== 情况 2：新解被某些旧解支配 ========== 
        if (analysis.isDominated())
        {
            // 新解被支配 → 以概率接受（退火机制）
            // 找到一个参考点（如最接近的被支配解）
            ObjectiveVector referenceVector = findClosestReference(newVector, nonDominatedSet);
            // 计算归一化后的能量差（使用欧几里得距离）
            double normalizedDeltaE = normalizer.calculateNormalizedEnergyDifference(referenceVector, newVector);
            // 使用归一化能量差计算接受概率
            double probability = calculateAcceptanceProbability(normalizedDeltaE, temperature);

            // 返回概率接受结果
            return new AcceptanceResult(
                random.nextDouble() < probability,  // 是否接受：随机数 < 接受概率
                probability,  // 接受概率
                AcceptanceReason.DOMINATED  // 原因为被支配
            );
        }
        // ========== 情况 3：新解与所有旧解互不支配 ========== 
        // （即既不支配任何旧解，也不被任何旧解支配）
      
        // 新解互不支配 → 总是接受
        return new AcceptanceResult(true, 1.0, AcceptanceReason.NON_DOMINATED);
    }

    /**
     * 找到非支配集中最接近新解的解（作为参考）
     * 使用归一化后的欧几里得距离
     * 
     * @param newVector 新解的目标向量
     * @param nonDominatedSet 非支配集
     * @return 最接近的目标向量
     */
    private ObjectiveVector findClosestReference(ObjectiveVector newVector, NonDominatedSet nonDominatedSet)
    {
        List<ObjectiveVector> vectors = nonDominatedSet.getObjectiveVectors();  // 获取当前前沿
        if (vectors.isEmpty())  // 若暂无参考，只能返回自身
        {
            return newVector; // 如果非支配集为空，返回新解本身
        }

        ObjectiveVector closest = vectors.get(0);  // 用第一条记录初始化
        double minDistance = Double.MAX_VALUE;  // 当前最小距离

        for (ObjectiveVector vector : vectors)  // 遍历所有候选参考
        {
            double distance = normalizer.calculateNormalizedEnergyDifference(newVector, vector);  // 归一化空间距离
            if (distance < minDistance)  // 找到更近的参考
            {
                minDistance = distance;
                closest = vector;
            }
        }

        return closest;
    }

    /**
     * 接受结果类
     */
    public static class AcceptanceResult
    {
        private final boolean accepted;      // 是否接受
        private final double probability;   // 接受概率
        private final AcceptanceReason reason; // 接受原因

        public AcceptanceResult(boolean accepted, double probability, AcceptanceReason reason)
        {
            this.accepted = accepted;
            this.probability = probability;
            this.reason = reason;
        }

        public boolean isAccepted() { return accepted; }
        public double getProbability() { return probability; }
        public AcceptanceReason getReason() { return reason; }

        @Override
        public String toString()
        {
            return String.format("接受结果: %s, 概率: %.4f, 原因: %s",
                accepted ? "接受" : "拒绝", probability, reason);
        }
    }

    /**
     * 接受原因枚举
     */
    public enum AcceptanceReason
    {
        DOMINATES_OTHERS,  // 新解支配某些旧解 → 总是接受
        DOMINATED,         // 新解被支配 → 以概率接受（退火机制）
        NON_DOMINATED      // 新解互不支配 → 总是接受
    }

    /**
     * 获取归一化器
     */
    public DynamicNormalizer getNormalizer()
    {
        return normalizer;
    }
}