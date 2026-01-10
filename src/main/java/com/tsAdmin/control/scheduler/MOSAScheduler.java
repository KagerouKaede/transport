package com.tsAdmin.control.scheduler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.tsAdmin.common.ConfigLoader;
import com.tsAdmin.common.PathNode;
import com.tsAdmin.common.algorithm.multiobjective.*;
import com.tsAdmin.common.algorithm.multiobjective.MultiObjectiveEvaluator.ObjectiveVector;
import com.tsAdmin.model.*; 
import com.tsAdmin.control.manager.*;
/**
 * 多目标模拟退火调度器（MOSA - Multi-Objective Simulated Annealing）
 * 整合所有模块，实现完整的MOSA算法流程
 * 
 * 算法流程：
 * 1. 初始化：使用贪心算法生成初始解，进行初步采样
 * 2. 邻域生成：通过交换或转移操作生成新解
 * 3. 支配关系判断：判断新解与非支配集的关系
 * 4. 非支配集更新：根据支配关系更新非支配集
 * 5. 概率接受：当新解互补支配时，使用归一化能量差计算接受概率
 * 6. 动态归一化更新：更新归一化范围
 */
public class MOSAScheduler extends BaseScheduler
{
    private boolean[] enabledObjectives;
    // ========== MOSA算法核心组件 ==========
    
    /**
     * 多目标评估器：用于计算解的目标函数值（距离、时间、成本）
     */
    private final MultiObjectiveEvaluator evaluator;
    
    /**
     * 非支配集：存储所有非支配解（帕累托最优解）
     */
    private final NonDominatedSet nonDominatedSet;
    
    /**
     * 动态归一化器：用于归一化目标函数值，便于计算能量差
     */
    private final DynamicNormalizer normalizer;
    
    /**
     * 概率接受机制：用于决定是否接受新解
     */
    private final ProbabilityAcceptance acceptance;
    private static final int MAX_NEIGHBOR_ATTEMPTS = 20;
    
    // ========== MOSA算法参数 ==========
    
    /**
     * 最小温度：当温度低于此值时，算法停止
     * 默认值：1.0
     */
    private final double MIN_TEMPERATURE = 1.0;
    
    /**
     * 冷却率：每次迭代后温度的衰减系数
     * 公式：新温度 = 旧温度 × COOLING_RATE
     * 默认值：0.95（每次迭代温度降低5%）
     */
    private final double COOLING_RATE = 0.95;
    
    /**
     * 最大迭代次数：算法最多执行的迭代次数
     * 默认值：500
     */
    private final int MAX_ITERATION_TIME = 500;
    
    /**
     * 初始采样数量：用于计算上界阈值的初始采样解数量
     * 默认值：50
     */
    private final int INITIAL_SAMPLE_SIZE = 50;

    /**
     * 默认构造函数：创建MOSA调度器，初始化所有核心组件
     */
    public MOSAScheduler()
    {
        this.evaluator = new MultiObjectiveEvaluator();  // 创建多目标评估器
        this.nonDominatedSet = new NonDominatedSet();  // 创建非支配集
        this.normalizer = new DynamicNormalizer();  // 创建动态归一化器
        
        // 创建概率接受机制，传入归一化器（共享同一个归一化器）
        this.acceptance = new ProbabilityAcceptance(normalizer);
             // 从配置加载目标开关
        this.enabledObjectives = ConfigLoader.getBooleanArray("MultiObjective.enabled_targets");
        if (this.enabledObjectives == null || this.enabledObjectives.length != 5) {
            this.enabledObjectives = new boolean[]{true, true, true, true, true}; // 默认全开
        }
        this.evaluator.setEnabledObjectives(this.enabledObjectives); // 让 evaluator 知道哪些目标启用
    
    }

    /**
     * 调度方法，使用多目标模拟退火算法优化分配方案
     * 
     * @return 分配结果列表（从非支配集中选择）
     */
    @Override
    public List<Assignment> schedule()
    {
        // ========== 第一步：生成初始解 ==========
        // 使用贪心算法生成初始解，作为算法的起点
        List<Assignment> initialAssignments = new GreedyScheduler().schedule();
        
        // ========== 第二步：初步采样 ==========
        // 生成多个采样解，用于计算上界阈值和初始化归一化范围
        List<List<Assignment>> sampleAssignments = performInitialSampling(INITIAL_SAMPLE_SIZE);
        
        // ========== 第三步：初始化非支配集和归一化器 ==========
        // 将初始解添加到非支配集中
        initializeNonDominatedSet(initialAssignments);
        
        // 使用采样结果更新归一化范围（用于上界计算）
        // 如果采样结果不为空，计算采样解的目标向量并更新归一化器
        if (!sampleAssignments.isEmpty())
        {  
            List<ObjectiveVector> sampleVectors = new ArrayList<>();
            for (List<Assignment> sample : sampleAssignments)
            {
                if (sample == null || sample.isEmpty())
                {
                    continue;
                }
                sampleVectors.add(calculateTotalObjectiveVector(sample));
            }
            if (!sampleVectors.isEmpty())
            {
                normalizer.update(sampleVectors);
            }
        }
        
        // 从非支配集更新归一化范围（用于确定下界和范围）
        updateNormalizer();

        // ========== 第四步：设置初始温度 ==========
        // 根据非支配集中解的多样性计算初始温度
         double temperature = calculateInitialTemperature();

        // ========== 第五步：MOSA主循环 ==========
        // 迭代优化，直到达到最大迭代次数或温度低于最小值
        for (int iteration = 0; iteration < MAX_ITERATION_TIME && temperature > MIN_TEMPERATURE; iteration++)
        {
            // 5.1 生成邻域解
            // 从当前非支配集中选择一个解作为当前解
            List<Assignment> currentAssignments = getCurrentAssignments();
            // 通过邻域操作生成新解（交换、转移等）
            List<Assignment> newAssignments = generateNeighborSolution(currentAssignments);

            // 5.2 计算新解的多目标向量（所有车辆的总和）
            // 将所有车辆的目标值相加，得到总目标向量
            ObjectiveVector newVector = calculateTotalObjectiveVector(newAssignments);

            // 5.3 分析新解与非支配集的关系
            NonDominatedSet.AddAnalysis analysis = nonDominatedSet.analyzeAdd(newVector);

            // 5.4 计算接受概率
            ProbabilityAcceptance.AcceptanceResult result = acceptance.calculateMultiObjectiveAcceptance(
                newVector, nonDominatedSet, temperature, analysis);

            // 5.5 如果接受，更新非支配集
            if (result.isAccepted()) {
                // 强制添加新解（会自动删除被支配的旧解）
                nonDominatedSet.forceAdd(newVector, newAssignments);
                updateNormalizer();
            }
            // 5.6降温
            // 按照冷却率降低温度：新温度 = 旧温度 × 冷却率
            temperature *= COOLING_RATE;

            // 5.7温度扰动机制（防止过早收敛）
            // 每10%的迭代次数，轻微提高温度，增加探索能力
            if (iteration % (MAX_ITERATION_TIME / 10) == 0)
            {
                // 温度提高5%，增加接受劣解的概率，防止过早收敛
                temperature *= 1.05;
            }
        }

                // ========== 第六步：根据配置自动选择最终解 ==========
            List<Assignment> finalAssignments;
            if (nonDominatedSet.isEmpty()) {
                finalAssignments = new GreedyScheduler().schedule();
            } else {
                // 使用配置中的 enabledObjectives 作为偏好，自动选解
                finalAssignments = findIdealPointSolution(this.enabledObjectives);
            }

        // ========== 第七步：同步结果 ==========
        // 将最终分配方案同步到车辆对象中
        syncAssignmentsToCars(finalAssignments, CarManager.carMap.values());
         // ========== 第八步：更新 CarStat！==========
       updateCarStats(finalAssignments); // ← 新增方法

        // 返回最终分配方案
        return finalAssignments;
    }

    /**
     * 初始化非支配集
     * 将初始解添加到非支配集中
     */
    /**
     * 初始化非支配集
     * 将初始解添加到非支配集中
     * @param assignments 初始分配方案列表
     */
    private void initializeNonDominatedSet(List<Assignment> assignments)
    {
        nonDominatedSet.clear();

        if (assignments == null || assignments.isEmpty())
        {
            return;
        }

        // 使用整套车辆方案的合计目标向量，避免单车数据支配全局解
        ObjectiveVector vector = calculateTotalObjectiveVector(assignments);
        // 保存深拷贝，保证后续迭代不会污染初始解
        nonDominatedSet.forceAdd(vector, deepCopyAssignments(assignments));
    }

    /**
     * 更新归一化器
     * 从非支配集更新归一化范围
     */
    private void updateNormalizer()
    {
        // 从非支配集中提取所有目标向量，更新归一化范围
        normalizer.updateFromNonDominatedSet(nonDominatedSet);
    }

    /**
     * 计算初始温度
     * 基于非支配集中解的多样性（目标值的范围）
     * @return 初始温度值
     */
    private double calculateInitialTemperature()
    {
        // 如果非支配集为空，使用默认初始温度
        if (nonDominatedSet.isEmpty())
        {
            return 1000.0; // 默认初始温度
        }

        // 计算非支配集中所有目标类型的值范围
        // 返回每个目标类型的[min, max]数组
        Map<MultiObjectiveEvaluator.ObjectiveType, double[]> ranges = nonDominatedSet.getValueRange();
        
        // 找出所有目标类型中范围最大的值
        double maxRange = 0.0;
        // 遍历所有目标类型的范围
        for (double[] range : ranges.values())
        {
            // 计算当前目标类型的范围大小（最大值-最小值）
            double rangeSize = range[1] - range[0];
            // 如果当前范围更大，更新最大范围
            if (rangeSize > maxRange)
            {
                maxRange = rangeSize;
            }
        }

        // 初始温度设为最大范围的10倍，若范围过小则退回默认温度
        double temperature = maxRange * 10.0;
        return temperature > 1e-6 ? temperature : 1000.0;
    }

    /**
     * 获取当前非支配集中的解（用于邻域生成）
     */
    /**
     * 获取当前非支配集中的解（用于邻域生成）
     * @return 当前解（分配方案列表）
     */
    private List<Assignment> getCurrentAssignments()
    {
        // 如果非支配集为空，使用贪心算法生成新解
        if (nonDominatedSet.isEmpty())
        {
            return new GreedyScheduler().schedule();
        }

        // 随机选择一个索引（0到size-1之间）
        int index = random.nextInt(nonDominatedSet.size());
        // 获取指定索引的非支配解
        NonDominatedSet.NonDominatedSolution solution = nonDominatedSet.get(index);
        // 返回该解的分配方案列表
        return solution.getAssignments();
    }

   
   public List<Assignment> selectSolutionByObjectives(boolean[] selectedObjectives) {
    if (nonDominatedSet.isEmpty()) {
        return new GreedyScheduler().schedule();
    }
    return findIdealPointSolution(selectedObjectives);
}

private List<Assignment> findIdealPointSolution(boolean[] selectedObjectives) {
    List<MultiObjectiveEvaluator.ObjectiveType> types = new ArrayList<>();
    MultiObjectiveEvaluator.ObjectiveType[] allTypes = MultiObjectiveEvaluator.ObjectiveType.values();
    
    for (int i = 0; i < 5; i++) {
        if (selectedObjectives[i]) {
            types.add(allTypes[i]);
        }
    }

    if (types.isEmpty()) {
        return nonDominatedSet.get(0).getAssignments(); // 默认返回第一个
    }

    double minDistance = Double.MAX_VALUE;
    List<Assignment> best = null;

    for (NonDominatedSet.NonDominatedSolution sol : nonDominatedSet.getSolutions()) {
        double sum = 0.0;
        for (MultiObjectiveEvaluator.ObjectiveType type : types) {
            double normVal = normalizer.normalize(type, sol.getObjectiveVector().getValue(type));
            sum += normVal * normVal;
        }
        double distance = Math.sqrt(sum);
        if (distance < minDistance) {
            minDistance = distance;
            best = sol.getAssignments();
        }
    }

    return best != null ? best : nonDominatedSet.get(0).getAssignments();
    }

    /**
     * 初步采样：随机或启发式生成N条路径
     * 用于计算上界阈值
     * 
     * @param sampleSize 采样数量
     * @return 采样得到的分配方案列表
     */
    /**
     * 初步采样：随机或启发式生成N条路径
     * 用于计算上界阈值
     * @param sampleSize 采样数量
     * @return 采样得到的分配方案列表
     */
    private List<List<Assignment>> performInitialSampling(int sampleSize)
    {
        List<List<Assignment>> samples = new ArrayList<>();

        for (int i = 0; i < sampleSize / 2; i++)
        {
            List<Assignment> greedySolution = new GreedyScheduler().schedule();
            if (containsValidRoute(greedySolution))
            {
                samples.add(deepCopyAssignments(greedySolution));
            }
        }

        for (int i = 0; i < sampleSize / 2; i++)
        {
            List<Assignment> randomSolution = generateRandomSolution();
            if (containsValidRoute(randomSolution))
            {
                samples.add(randomSolution);
            }
        }

        return samples;
    }


    /**
     * 生成随机解（用于采样）
     * 随机分配需求到车辆，生成一个初始解
     * @return 随机生成的分配方案列表
     */
    private List<Assignment> generateRandomSolution()
    {
        List<Assignment> assignments = new ArrayList<>();
        for (Car car : CarManager.carMap.values())
        {
            assignments.add(new Assignment(car));
        }

        List<Demand> pendingDemands = new ArrayList<>();
        for (Demand demand : DemandManager.getList())
        {
            if (!demand.isAssigned())
            {
                pendingDemands.add(demand);
            }
        }

        Collections.shuffle(pendingDemands, random);
        Set<String> assignedDemands = new HashSet<>();
        List<Demand> unassigned = new ArrayList<>();

        for (Demand demand : pendingDemands)
        {
            if (assignedDemands.contains(demand.getUUID()))
            {
                continue;
            }

            List<Assignment> shuffledAssignments = new ArrayList<>(assignments);
            Collections.shuffle(shuffledAssignments, random);

            boolean inserted = false;
            for (Assignment assignment : shuffledAssignments)
            {
                if (tryInsertDemand(assignment, demand))
                {
                    assignedDemands.add(demand.getUUID());
                    inserted = true;
                    break;
                }
            }

            if (!inserted)
            {
                unassigned.add(demand);
            }
        }

        if (!unassigned.isEmpty())
        {
            for (Demand demand : unassigned)
            {
                if (assignedDemands.contains(demand.getUUID()))
                {
                    continue;
                }

                if (insertDemandWithCapacityPriority(assignments, demand))
                {
                    assignedDemands.add(demand.getUUID());
                }
            }
        }

        return assignments;
    }

    /**
     * 获取非支配集（用于外部访问）
     */
    public NonDominatedSet getNonDominatedSet()
    {
        return nonDominatedSet;
    }

    /**
     * 生成邻域解
     * 复用父类的邻域生成逻辑（简化版：使用贪心算法生成新解）
     * 注意：实际应用中应该实现完整的邻域生成逻辑（交换、转移等操作）
     */
    private List<Assignment> generateNeighborSolution(List<Assignment> assignments)
    {
        if (assignments == null || assignments.isEmpty())
        {
            return new GreedyScheduler().schedule();
        }

        for (int attempt = 0; attempt < MAX_NEIGHBOR_ATTEMPTS; attempt++)
        {
            List<Assignment> candidate = deepCopyAssignments(assignments);
            if (applyRandomNeighbor(candidate))
            {
                return candidate;
            }
        }

        return deepCopyAssignments(assignments);
    }

    /**
     * 应用随机邻域操作
     * 随机选择一个操作，并尝试应用该操作
     * @param assignments 当前分配方案
     * @return 是否成功应用随机邻域操作
     */
    private boolean applyRandomNeighbor(List<Assignment> assignments)
    {
        if (assignments.isEmpty())
        {
            return false;
        }

        int operation = random.nextInt(3);
        switch (operation)
        {
            case 0:
                return relocateDemand(assignments);
            case 1:
                return swapDemandsBetweenVehicles(assignments);
            default:
                return reorderWithinRoute(assignments);
        }
    }

    /**
     * 随机 relocate 需求
     * 随机选择一个需求，并将其从当前车辆中移除，并将其插入到另一个车辆中
     * @param assignments 当前分配方案
     * @return 是否成功随机 relocate 需求
     */
    private boolean relocateDemand(List<Assignment> assignments)
    {
        Assignment from = pickRandomNonEmptyAssignment(assignments);
        if (from == null)
        {
            return false;
        }

        if (assignments.size() == 1)
        {
            return false;
        }

        Assignment to = assignments.get(random.nextInt(assignments.size()));
        int guard = 0;
        while (to == from && guard++ < assignments.size())
        {
            to = assignments.get(random.nextInt(assignments.size()));
        }

        if (to == from)
        {
            return false;
        }

        PathNode[] pair = extractPair(from);
        if (pair == null)
        {
            return false;
        }

        List<PathNode> fromBackup = new ArrayList<>(from.getNodeList());
        List<PathNode> toBackup = new ArrayList<>(to.getNodeList());

        from.getNodeList().remove(pair[1]);
        from.getNodeList().remove(pair[0]);

        if (!insertPairAtRandomPositions(to, pair[0], pair[1]))
        {
            from.getNodeList().clear();
            from.getNodeList().addAll(fromBackup);
            to.getNodeList().clear();
            to.getNodeList().addAll(toBackup);
            return false;
        }

        if (isFeasibleRoute(from) && isFeasibleRoute(to))
        {
            return true;
        }

        from.getNodeList().clear();
        from.getNodeList().addAll(fromBackup);
        to.getNodeList().clear();
        to.getNodeList().addAll(toBackup);
        return false;
    }

    /**
     * 随机 swap 需求
     * 随机选择两个需求，并将其从两个车辆中移除，并将其插入到两个车辆中
     * @param assignments 当前分配方案
     * @return 是否成功随机 swap 需求
     */
    private boolean swapDemandsBetweenVehicles(List<Assignment> assignments)
    {
        if (assignments.size() < 2)
        {
            return false;
        }

        Assignment first = pickRandomNonEmptyAssignment(assignments);
        Assignment second = pickRandomNonEmptyAssignment(assignments, first);
        if (first == null || second == null)
        {
            return false;
        }

        PathNode[] pairA = extractPair(first);
        PathNode[] pairB = extractPair(second);
        if (pairA == null || pairB == null)
        {
            return false;
        }

        List<PathNode> firstBackup = new ArrayList<>(first.getNodeList());
        List<PathNode> secondBackup = new ArrayList<>(second.getNodeList());

        first.getNodeList().remove(pairA[1]);
        first.getNodeList().remove(pairA[0]);
        second.getNodeList().remove(pairB[1]);
        second.getNodeList().remove(pairB[0]);

        boolean insertedIntoSecond = insertPairAtRandomPositions(second, pairA[0], pairA[1]);
        boolean insertedIntoFirst = insertPairAtRandomPositions(first, pairB[0], pairB[1]);

        if (insertedIntoFirst && insertedIntoSecond &&
            isFeasibleRoute(first) && isFeasibleRoute(second))
        {
            return true;
        }

        first.getNodeList().clear();
        first.getNodeList().addAll(firstBackup);
        second.getNodeList().clear();
        second.getNodeList().addAll(secondBackup);
        return false;
    }


    /**
     * 随机 reorder 需求
     * 随机选择一个需求，并将其从当前车辆中移除，并将其插入到同一个车辆中
     * @param assignments
     * @return
     */
    private boolean reorderWithinRoute(List<Assignment> assignments)
    {
        Assignment assignment = pickRandomNonEmptyAssignment(assignments);
        if (assignment == null)
        {
            return false;
        }

        PathNode[] pair = extractPair(assignment);
        if (pair == null)
        {
            return false;
        }

        List<PathNode> backup = new ArrayList<>(assignment.getNodeList());
        assignment.getNodeList().remove(pair[1]);
        assignment.getNodeList().remove(pair[0]);

        if (insertPairAtRandomPositions(assignment, pair[0], pair[1]) && isFeasibleRoute(assignment))
        {
            return true;
        }

        assignment.getNodeList().clear();
        assignment.getNodeList().addAll(backup);
        return false;
    }



   
    /**
     * 尝试插入
     * @param assignment
     * @param demand
     * @return
     */
    private boolean tryInsertDemand(Assignment assignment, Demand demand)
    {
        PathNode startNode = new PathNode(demand, true);
        PathNode endNode = new PathNode(demand, false);
        return insertPairAtRandomPositions(assignment, startNode, endNode);
    }

    /**
     * 插入需求，优先考虑容量
     * @param assignments
     * @param demand
     * @return
     */
    private boolean insertDemandWithCapacityPriority(List<Assignment> assignments, Demand demand)
    {
        List<Assignment> orderedAssignments = new ArrayList<>(assignments);
        orderedAssignments.sort((a, b) -> Double.compare(
            capacityScore(b), capacityScore(a)));

        for (Assignment assignment : orderedAssignments)
        {
            if (tryInsertDemand(assignment, demand))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * 容量得分
     * @param assignment
     * @return
     */
    private double capacityScore(Assignment assignment)
    {
        Car car = assignment.getCar();
        double loadSlack = Math.max(0.0, car.getRemainingLoad());
        double volumeSlack = Math.max(0.0, car.getRemainingVolume());
        return Math.min(loadSlack, volumeSlack);
    }

 
    /**
     * 计算所有分配方案的总目标向量
     * 将所有车辆的目标值相加，得到整个分配方案的总目标值
     * @param assignments 分配方案列表（每个车辆一个分配方案）
     * @return 总目标向量（所有车辆目标值的总和）
     */
    private ObjectiveVector calculateTotalObjectiveVector(List<Assignment> assignments)
    {
        // 初始化总目标值为0
        double totalWaiting = 0.0;       // 总等待时间
        double totalEmptyDistance = 0.0; // 总空驶里程
        double totalLoadWaste = 0.0;     // 总载重浪费
        double totalTonnage = 0.0;       // 总运输吨位
        double totalCarbon = 0.0;        // 总碳排

        // 遍历所有分配方案（每辆车一个分配方案）
        for (Assignment assignment : assignments)
        {
            // 计算当前车辆分配方案的多目标向量
            ObjectiveVector vector = evaluator.evaluateAll(assignment);
         
            totalWaiting += vector.getWaitingTime();
            totalEmptyDistance += vector.getEmptyDistance();
            totalLoadWaste += vector.getLoadWaste();
            totalTonnage += vector.getDeliveredTonnage();
            totalCarbon += vector.getCarbonEmission();
        }

        EnumMap<MultiObjectiveEvaluator.ObjectiveType, Double> values =
            new EnumMap<>(MultiObjectiveEvaluator.ObjectiveType.class);
        values.put(MultiObjectiveEvaluator.ObjectiveType.WAITING_TIME, totalWaiting);
        values.put(MultiObjectiveEvaluator.ObjectiveType.EMPTY_DISTANCE, totalEmptyDistance);
        values.put(MultiObjectiveEvaluator.ObjectiveType.LOAD_WASTE, totalLoadWaste);
        values.put(MultiObjectiveEvaluator.ObjectiveType.DELIVERED_TONNAGE, totalTonnage);
        values.put(MultiObjectiveEvaluator.ObjectiveType.CARBON_EMISSION, totalCarbon);

        return new ObjectiveVector(values);
    }

    /**
     * 获取归一化器（用于外部访问）
     */
    public DynamicNormalizer getNormalizer()
    {
        return normalizer;
    }

    public MultiObjectiveEvaluator getEvaluator()
    {
        return evaluator;
    }

    
    // ===== 新增方法：更新 CarStat =====
public void updateCarStats(List<Assignment> assignments) {
    for (Assignment assignment : assignments) {
        Car car = CarManager.carMap.get(assignment.getCar().getUUID());
        if (car == null) continue;

        ObjectiveVector vec = evaluator.evaluateAll(assignment);
        CarStatistics stat = car.getStatistics();
        if(stat!=null) {
            stat.setWaitingTime(vec.getWaitingTime());
            stat.setEmptyDistance(vec.getEmptyDistance());
            stat.setWastedLoad(vec.getLoadWaste());
            stat.setTotalWeight(vec.getDeliveredTonnage());
            stat.setCarbonEmission(vec.getCarbonEmission());
        }
    }
}
}

