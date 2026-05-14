package com.tsadmin.transport.service.optimization.multiobject;  // 包声明：多目标优化算法包

import java.util.List;
import com.tsAdmin.common.algorithm.multiobjective.MultiObjectiveEvaluator.ObjectiveVector;

/**
 * 支配关系判断模块
 * 实现帕累托支配关系判断（新解是否被支配、是否支配旧解、是否互补支配）
 * 支持多目标向量的比较
 * 
 * 支配关系定义（针对最小化问题）：
 * - 解A支配解B：A在所有目标上都不劣于B，且至少在一个目标上严格优于B
 * - 解A被解B支配：B支配A  
 * - 解A和解B互补支配（互不支配）：A不支配B，且B不支配A
 */
public class DominanceComparator {
    
    /**
     * 支配关系结果枚举
     */
    public enum DominanceRelation {
        DOMINATES,      // 第一个解支配第二个解
        DOMINATED,      // 第一个解被第二个解支配
        NON_DOMINATED   // 互补支配（互不支配）
    }

    /**
     * 判断两个目标向量之间的支配关系
     * 
     * @param vector1 第一个目标向量
     * @param vector2 第二个目标向量
     * @return 支配关系（从vector1的角度）
     */
    public DominanceRelation compare(ObjectiveVector vector1, ObjectiveVector vector2) {
        boolean vector1Better = false;  // vector1是否至少在一个目标上严格优于vector2
        boolean vector2Better = false;  // vector2是否至少在一个目标上严格优于vector1

        // 只比较5个优化目标（WAITING_TIME, EMPTY_DISTANCE, LOAD_WASTE, DELIVERED_TONNAGE, CARBON_EMISSION）
        for (MultiObjectiveEvaluator.ObjectiveType type : MultiObjectiveEvaluator.ObjectiveType.values()) {
            double value1 = vector1.getComparableValue(type);  // 已统一成"越小越好"的指标值
            double value2 = vector2.getComparableValue(type);

            if (value1 < value2) {
                vector1Better = true;
            }
            else if (value1 > value2) {
                vector2Better = true;
            }

            // 两方都曾在某目标上领先，已确定互补支配，提前退出
            if (vector1Better && vector2Better) {
                break;
            }
        }

        if (vector1Better && !vector2Better) {
            return DominanceRelation.DOMINATES;
        }
        else if (!vector1Better && vector2Better) {
            return DominanceRelation.DOMINATED;
        }
        return DominanceRelation.NON_DOMINATED;
    }

    /**
     * 判断vector1是否支配vector2
     */
    public boolean dominates(ObjectiveVector vector1, ObjectiveVector vector2) {
        return compare(vector1, vector2) == DominanceRelation.DOMINATES;
    }

    /**
     * 判断vector1是否被vector2支配
     */
    public boolean isDominatedBy(ObjectiveVector vector1, ObjectiveVector vector2) {
        return compare(vector1, vector2) == DominanceRelation.DOMINATED;
    }

    /**
     * 判断两个向量是否互不支配
     */
    public boolean isNonDominated(ObjectiveVector vector1, ObjectiveVector vector2) {
        return compare(vector1, vector2) == DominanceRelation.NON_DOMINATED;
    }

    /**
     * 判断新解是否被非支配集中的任何解支配
     */
    public boolean isDominatedBySet(ObjectiveVector newVector, List<ObjectiveVector> nonDominatedVectors) {
        for (ObjectiveVector existingVector : nonDominatedVectors) {
            if (isDominatedBy(newVector, existingVector)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断新解是否支配非支配集中的某些解
     */
    public List<ObjectiveVector> findDominatedSolutions(ObjectiveVector newVector, List<ObjectiveVector> nonDominatedVectors) {
        List<ObjectiveVector> dominated = new java.util.ArrayList<>();
        for (ObjectiveVector existingVector : nonDominatedVectors) {
            if (dominates(newVector, existingVector)) {
                dominated.add(existingVector);
            }
        }
        return dominated;
    }

    /**
     * 判断新解与非支配集的关系
     */
    public DominanceResult analyzeRelation(ObjectiveVector newVector, List<ObjectiveVector> nonDominatedVectors) {
        boolean isDominated = isDominatedBySet(newVector, nonDominatedVectors);
        List<ObjectiveVector> dominatedSolutions = findDominatedSolutions(newVector, nonDominatedVectors);
        boolean isNonDominated = !isDominated && dominatedSolutions.isEmpty();

        return new DominanceResult(isDominated, dominatedSolutions, isNonDominated);
    }

    /**
     * 支配关系分析结果类
     */
    public static class DominanceResult {
        private final boolean isDominated;
        private final List<ObjectiveVector> dominated;
        private final boolean isNonDominated;

        public DominanceResult(boolean isDominated, List<ObjectiveVector> dominated, boolean isNonDominated) {
            this.isDominated = isDominated;
            this.dominated = new java.util.ArrayList<>(dominated);
            this.isNonDominated = isNonDominated;
        }

        public boolean isDominated() { return isDominated; }
        public List<ObjectiveVector> getDominated() { return new java.util.ArrayList<>(dominated); }
        public boolean isNonDominated() { return isNonDominated; }

        @Override
        public String toString() {
            if (isDominated) {
                return "新解被支配";
            }
            else if (!dominated.isEmpty()) {
                return String.format("新解支配了%d个旧解", dominated.size());
            }
            else {
                return "新解互不支配";
            }
        }
    }

    /**
     * 判断两个目标向量是否相等（使用默认误差1e-9）
     */
    public boolean equals(ObjectiveVector vector1, ObjectiveVector vector2) {
        return equals(vector1, vector2, 1e-9);
    }

    /**
     * 判断两个目标向量是否相等（所有目标值都相同）
     */
    public boolean equals(ObjectiveVector vector1, ObjectiveVector vector2, double epsilon) {
        for (MultiObjectiveEvaluator.ObjectiveType type : MultiObjectiveEvaluator.ObjectiveType.values()) {
            if (Math.abs(vector1.getComparableValue(type) - vector2.getComparableValue(type)) >= epsilon) {
                return false;
            }
        }
        return true;
    }
}