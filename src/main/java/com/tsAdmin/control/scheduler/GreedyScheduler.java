package com.tsAdmin.control.scheduler;

import com.tsAdmin.common.PathNode;
import com.tsAdmin.common.algorithm.multiobjective.MultiObjectiveEvaluator;
import com.tsAdmin.control.manager.DemandManager;
import com.tsAdmin.model.Assignment;
import com.tsAdmin.model.Demand;
import com.tsAdmin.model.Car;
import com.tsAdmin.control.manager.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 贪心调度器（基于5目标评估器）
 * 使用多目标评估器计算综合成本，为车辆分配需求操作
 * 
 * 修改逻辑：
 * 1. 使用 MultiObjectiveEvaluator 评估每个候选分配方案
 * 2. 将5个目标加权组合成单一成本值
 * 3. 选择成本最小的车辆进行分配
 */
public class GreedyScheduler extends BaseScheduler
{
    // 5个目标的权重（可根据实际需求调整）
    private static final double WAITING_WEIGHT = 0.25;
    private static final double EMPTY_DISTANCE_WEIGHT = 0.20;
    private static final double LOAD_WASTE_WEIGHT = 0.20;
    private static final double TONNAGE_WEIGHT = 0.20; // 注意：运量是越大越好，所以权重为负
    private static final double CARBON_WEIGHT = 0.15;
    
    @Override
    public List<Assignment> schedule()
    {
        List<Assignment> assignments = new ArrayList<>();
        
        // ========== 第一步：筛选未分配完成的demand ==========
        List<Demand> newDemands = new ArrayList<>();
        for (Demand demand : DemandManager.getList())
        {
            if (!demand.isAssigned()) {
                newDemands.add(demand);
            }
        }
        
        // 如果没有新的demand，直接返回空的分配结果
        if (newDemands.isEmpty()) {
            return assignments;
        }
        
        // ========== 第二步：深拷贝车辆 ==========
        List<Car> carsCopy = new ArrayList<>();
        for (Car car : CarManager.carMap.values())
        {
            carsCopy.add(new Car(car)); // 深拷贝车辆
        }

        // 创建多目标评估器
        MultiObjectiveEvaluator evaluator = new MultiObjectiveEvaluator();

        // ========== 第三步：贪心分配新的demand ==========
        for (Demand demand : newDemands)
        {
            if (demand.isAssigned()) continue; // 安全检查

            Car bestCar = null;
            double minCost = Double.MAX_VALUE;

            PathNode startNode = new PathNode(demand, true);
            PathNode endNode = new PathNode(demand, false);

            for (Car car : carsCopy) {
                if (!canAddDemandPairToSequence(car, startNode, endNode)) {
                    continue;
                }

                // 临时添加并评估
                List<PathNode> originalNodes = new ArrayList<>(car.getNodeList());
                car.getNodeList().add(startNode);
                car.getNodeList().add(endNode);

                double cost = calculateMultiObjectiveCost(car, evaluator);

                car.getNodeList().clear();
                car.getNodeList().addAll(originalNodes);

                if (cost < minCost) {
                    minCost = cost;
                    bestCar = car;
                }
            }
                
                // ========== 分配结果处理 ==========
                if (bestCar != null) {
                    Assignment assignment = getAssignmentForCar(assignments, bestCar);
                    assignment.addPathNode(startNode);
                    assignment.addPathNode(endNode);

                    bestCar.addPathNode(startNode);
                    bestCar.addPathNode(endNode);

                    // 更新订单分配状态

                    int quantity = Math.min(demand.getQuantity(), bestCar.getMaxLoad());
                    double volume = quantity / (demand.getQuantity() / demand.getVolume());
                    demand.setQuantity(demand.getQuantity() - quantity);
                    demand.setVolume(demand.getVolume() - volume);
                    demand.setAssigned();
                }
            }
        

        // ========== 第四步：同步结果 ==========
        syncAssignmentsToCars(assignments);
        return assignments;
    }

    /**
     * 基于5个目标计算综合成本
     * 将5个目标加权组合成单一成本值（越小越好）
     */
    private double calculateMultiObjectiveCost(Car car, MultiObjectiveEvaluator evaluator) {
        // 创建临时的 Assignment 用于评估
        Assignment tempAssignment = new Assignment(car);
        
        // 计算5个目标值
        MultiObjectiveEvaluator.ObjectiveVector vector = evaluator.evaluateAll(tempAssignment);
        
        double waitingTime = vector.getWaitingTime();
        double emptyDistance = vector.getEmptyDistance();
        double loadWaste = vector.getLoadWaste();
        double tonnage = vector.getDeliveredTonnage(); // 越大越好
        double carbonEmission = vector.getCarbonEmission();
        
        // 综合成本 = 各目标加权和（运量取负值，因为越大越好）
        double totalCost = 
            waitingTime * WAITING_WEIGHT +
            emptyDistance * EMPTY_DISTANCE_WEIGHT +
            loadWaste * LOAD_WASTE_WEIGHT +
            carbonEmission * CARBON_WEIGHT -
            tonnage * TONNAGE_WEIGHT; // 减去运量，因为运量越大成本越低
        
        return totalCost;
    }

    /**
     * 模拟整个序列执行过程，判断是否可以添加一对起点-终点操作
     * 重要：需要模拟从车辆当前位置开始，执行整个序列后的载重和体积状态
     */
    private boolean canAddDemandPairToSequence(Car car, PathNode startNode, PathNode endNode) {
        double remainingLoad = car.getRemainingLoad();
        double remainingVolume = car.getRemainingVolume();
        
        // 特殊处理大货量订单
        if(startNode.getDemand().getQuantity() > 30000) {
            if(car.getMaxLoad() < 0.33 * startNode.getDemand().getQuantity()) 
                return false;
            else 
                return true;
        }

        // 模拟执行现有序列
        for (PathNode existingNode : car.getNodeList()) {
            if (existingNode.isOrigin()) {
                remainingLoad -= existingNode.getDemand().getQuantity();
                remainingVolume -= existingNode.getDemand().getVolume();
            } else {
                remainingLoad += existingNode.getDemand().getQuantity();
                remainingVolume += existingNode.getDemand().getVolume();
            }
            
            if (remainingLoad < 0 || remainingLoad > car.getMaxLoad() || 
                remainingVolume < 0 || remainingVolume > car.getMaxVolume()) {
                return false;
            }
        }
        
        // 模拟执行新添加的起点-终点对
        remainingLoad -= startNode.getDemand().getQuantity();
        remainingVolume -= startNode.getDemand().getVolume();
        
        if (remainingLoad < 0 || remainingVolume < 0) {
            return false;
        }
        
        remainingLoad += endNode.getDemand().getQuantity();
        remainingVolume += endNode.getDemand().getVolume();
        
        if (remainingLoad < 0 || remainingLoad > car.getMaxLoad() || 
            remainingVolume < 0 || remainingVolume > car.getMaxVolume()) {
            return false;
        }
        
        return true;
    }

    // 获取该车辆的Assignment对象，如果没有则创建一个新的
    private Assignment getAssignmentForCar(List<Assignment> assignments, Car car) {
        for (Assignment assignment : assignments) {
            if (assignment.getCar().getUUID().equals(car.getUUID())) {
                return assignment;
            }
        }
        Assignment newAssignment = new Assignment(car);
        assignments.add(newAssignment);
        return newAssignment;
    }
}