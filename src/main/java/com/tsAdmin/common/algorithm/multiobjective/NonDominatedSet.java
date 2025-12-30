package com.tsAdmin.common.algorithm.multiobjective;

import java.util.*;
import com.tsAdmin.model.Assignment;
import com.tsAdmin.common.algorithm.multiobjective.MultiObjectiveEvaluator.ObjectiveType;
import com.tsAdmin.common.algorithm.multiobjective.MultiObjectiveEvaluator.ObjectiveVector;

/**
 * 非支配集管理类（Pareto 前沿存储器）
 * 
 * 职责：
 * - 存储所有非支配解（即 Pareto 最优解）
 * - 提供分析新解与当前前沿关系的能力
 * - 支持强制更新前沿（由外部算法决策后调用）
 * 
 * 设计原则：
 * - 本类不做“是否接受新解”的决策（那是 ProbabilityAcceptance 的职责）
 * - 只提供“分析”和“更新”两个原子操作，保持职责单一
 */
public class NonDominatedSet {

    /**
     * 非支配解条目
     * 封装一个解的目标向量和对应的分配方案
     */
    public static class NonDominatedSolution {
        private final ObjectiveVector objectiveVector;  // 多目标向量（5个指标值）
        private final List<Assignment> assignments;     // 对应的车辆分配方案

        public NonDominatedSolution(ObjectiveVector objectiveVector, List<Assignment> assignments) {
            this.objectiveVector = objectiveVector;
            // 防御性拷贝，防止外部修改影响内部状态
            this.assignments = new ArrayList<>(assignments);
        }

        public ObjectiveVector getObjectiveVector() { 
            return objectiveVector; 
        }
        
        public List<Assignment> getAssignments() { 
            // 返回副本，保证封装性
            return new ArrayList<>(assignments); 
        }
    }

    // 内部存储：所有非支配解
    private final List<NonDominatedSolution> solutions = new ArrayList<>();
    // 支配关系比较器（用于判断解之间的帕累托支配关系）
    private final DominanceComparator comparator = new DominanceComparator();

    /**
     * 分析新解与当前非支配集的关系（不修改集合）
     * 
     * 该方法回答三个关键问题：
     * 1. 新解是否被当前前沿中的某个解支配？ → isDominated()
     * 2. 新解是否支配当前前沿中的某些解？ → getDominated()
     * 3. 新解是否可以被接受（即不被支配）？ → canAdd()
     * 
     * @param newVector 新解的目标向量
     * @return 分析结果（包含支配关系信息）
     */
    public AddAnalysis analyzeAdd(ObjectiveVector newVector) {
        // 步骤1: 检查新解是否被当前非支配集中的任何解支配
        boolean isDominated = comparator.isDominatedBySet(newVector, getObjectiveVectors());
        
        // 步骤2: 如果新解未被支配，找出它支配的旧解
        List<NonDominatedSolution> dominatedSolutions = new ArrayList<>();
        if (!isDominated) {
            for (NonDominatedSolution solution : solutions) {
                if (comparator.dominates(newVector, solution.getObjectiveVector())) {
                    dominatedSolutions.add(solution);
                }
            }
        }
        
        // canAdd = 新解不被支配（即可以加入前沿）
        return new AddAnalysis(!isDominated, isDominated, dominatedSolutions);
    }

    /**
     * 强制将新解添加到非支配集中（由外部算法决策后调用）
     * 
     * 操作逻辑：
     * 1. 删除所有被新解支配的旧解（保持前沿的非支配性）
     * 2. 添加新解到前沿
     * 
     * 注意：此方法不检查新解是否被支配！调用前必须确保新解值得加入。
     * 
     * @param newVector 新解的目标向量
     * @param newAssignments 新解的分配方案
     */
    public void forceAdd(ObjectiveVector newVector, List<Assignment> newAssignments) {
        // 1. 找出所有被新解支配的旧解
        List<NonDominatedSolution> toRemove = new ArrayList<>();
        for (NonDominatedSolution solution : solutions) {
            if (comparator.dominates(newVector, solution.getObjectiveVector())) {
                toRemove.add(solution);
            }
        }
        // 2. 批量删除被支配的旧解
        solutions.removeAll(toRemove);
        
        // 3. 添加新解
        solutions.add(new NonDominatedSolution(newVector, newAssignments));
    }

    // ========== 通用工具方法 ==========

    /**
     * 获取所有非支配解的目标向量列表（用于分析或归一化）
     */
    public List<ObjectiveVector> getObjectiveVectors() {
        List<ObjectiveVector> vectors = new ArrayList<>();
        for (NonDominatedSolution solution : solutions) {
            vectors.add(solution.getObjectiveVector());
        }
        return vectors;
    }

    public boolean isEmpty() { return solutions.isEmpty(); }
    public int size() { return solutions.size(); }
    public NonDominatedSolution get(int index) { return solutions.get(index); }
    
    /**
     * 获取所有非支配解的副本（防止外部修改）
     */
    public List<NonDominatedSolution> getSolutions() { 
        return new ArrayList<>(solutions); 
    }
    
    /**
     * 清空非支配集（用于重新初始化）
     */
    public void clear() { solutions.clear(); }

    /**
     * 获取所有目标类型的值范围（用于初始化归一化器和温度）
     * 返回每个目标类型对应的 [min, max] 数组
     */
    public Map<ObjectiveType, double[]> getValueRange() {
        Map<ObjectiveType, double[]> range = new HashMap<>();
        
        for (ObjectiveType type : ObjectiveType.values()) {
            double min = getBestValue(type);
            double max = getWorstValue(type);
            
            if (Double.isNaN(min) || Double.isNaN(max)) {
                range.put(type, new double[]{Double.NaN, Double.NaN});
            } else {
                range.put(type, new double[]{min, max});
            }
        }
        return range;
    }

    /**
     * 根据目标类型获取最优值（最小值，因为所有目标已统一为“越小越好”）
     */
    public double getBestValue(ObjectiveType objectiveType) {
        if (solutions.isEmpty()) {
            return Double.NaN;
        }

        double best = Double.POSITIVE_INFINITY;
        for (NonDominatedSolution solution : solutions) {
            double value = solution.getObjectiveVector().getComparableValue(objectiveType);
            if (value < best) {
                best = value;
            }
        }
        return best == Double.POSITIVE_INFINITY ? Double.NaN : best;
    }

    /**
     * 根据目标类型获取最差值（最大值）
     */
    public double getWorstValue(ObjectiveType objectiveType) {
        if (solutions.isEmpty()) {
            return Double.NaN;
        }

        double worst = Double.NEGATIVE_INFINITY;
        for (NonDominatedSolution solution : solutions) {
            double value = solution.getObjectiveVector().getComparableValue(objectiveType);
            if (value > worst) {
                worst = value;
            }
        }
        return worst == Double.NEGATIVE_INFINITY ? Double.NaN : worst;
    }

    // ========== 分析结果封装类 ==========
    
    /**
     * 新解与非支配集的关系分析结果
     * 
     * 该类封装了 analyzeAdd() 的返回信息，供外部算法决策使用。
     */
    public static class AddAnalysis {
        private final boolean canAdd;                    // 新解是否可以被接受（不被支配）
        private final boolean isDominated;              // 新解是否被当前前沿支配
        private final List<NonDominatedSolution> dominated; // 新解支配的旧解列表

        public AddAnalysis(boolean canAdd, boolean isDominated, List<NonDominatedSolution> dominated) {
            this.canAdd = canAdd;
            this.isDominated = isDominated;
            this.dominated = new ArrayList<>(dominated);
        }

        /** 是否可以添加新解（即新解不被支配） */
        public boolean canAdd() { return canAdd; }
        
        /** 新解是否被支配 */
        public boolean isDominated() { return isDominated; }
        
        /** 获取被新解支配的旧解列表 */
        public List<NonDominatedSolution> getDominated() { 
            return new ArrayList<>(dominated); 
        }
        
        /** 是否支配了至少一个旧解 */
        public boolean hasDominatedSolutions() { 
            return !dominated.isEmpty(); 
        }
    }
}