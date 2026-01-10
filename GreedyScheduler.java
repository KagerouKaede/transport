package com.tsAdmin.control;

import com.tsAdmin.model.Assignment;
import com.tsAdmin.model.Car;
import com.tsAdmin.model.CarList;
import com.tsAdmin.model.Demand;
import com.tsAdmin.model.DemandList;
import com.tsAdmin.model.PathNode;

import java.util.ArrayList;
import java.util.List;

/**
 * 贪心调度器，实现了Scheduler接口，
 * 按照贪心策略为车辆分配需求操作。
 * 
 * 修改后的逻辑：
 * 1. 只处理新的demand（isNew=true）
 * 2. 使用深拷贝避免修改原始数据
 * 3. 先遍历新demand，在每个demand内遍历车辆
 * 4. 选车逻辑：模拟整个序列执行过程判断约束，满足约束才计算cost，选择cost最小的车
 * 5. 分配成功则将isFullyAssigned改为true，否则保持为false
 * 6. 只修改序列，不修改车辆的实际属性（载重、时间、位置等）
 */
public class GreedyScheduler extends Scheduler
{
    @Override
    public List<Assignment> schedule()
    {
        List<Assignment> assignments = new ArrayList<>();
        
        // ========== 第一步：筛选未分配完成的demand ==========
        List<Demand> newDemands = new ArrayList<>();
        for (Demand demand : DemandList.demandList.values())
        {
            if (!demand.isFullyAssigned()) {
                newDemands.add(demand);
            }
        }
        
        // 如果没有新的demand，直接返回空的分配结果
        if (newDemands.isEmpty()) {
            return assignments;
        }
        
        // ========== 第二步：深拷贝车辆 ==========
        List<Car> carsCopy = new ArrayList<>();
        for (Car car : CarList.carList.values())
        {
            carsCopy.add(new Car(car)); // 深拷贝车辆
        }

        // ========== 第三步：贪心分配新的demand ==========
        // 遍历所有新的demand
        for (Demand demand : newDemands) {
            Car bestCar = null;
            double minCost = Double.MAX_VALUE;
            
            // 为当前demand创建起点和终点PathNode
            PathNode startNode = new PathNode(demand, true);   // 起点（装货）
            PathNode endNode = new PathNode(demand, false);    // 终点（卸货）

            // 遍历所有车辆，选择最佳车辆来处理当前demand
            for (Car car : carsCopy) {
                // ========== 模拟整个序列执行过程，判断约束 ==========
                if (!canAddDemandPairToSequence(car, startNode, endNode)) {
                    // 不满足约束，跳过这辆车，不计算cost
                    continue;
                }
                
                // ========== 满足约束，计算cost ==========
                // 临时添加起点-终点对到车辆序列进行cost计算
                car.addPathNode(startNode);
                car.addPathNode(endNode);
                
                // 使用调度器的totalCost方法计算整个序列的成本
                double cost = totalCost(car, car.getNodeList());
                
                // 移除临时添加的节点，恢复原序列
                car.getNodeList().remove(car.getNodeList().size() - 1); // 移除终点
                car.getNodeList().remove(car.getNodeList().size() - 1); // 移除起点
                
                // 更新最佳车辆
                if (cost < minCost) {
                    minCost = cost;
                    bestCar = car;
                }
            }
            // ========== 分配结果处理 ==========
            if (bestCar != null) {
                // 找到合适的车辆，进行分配
                Assignment assignment = getAssignmentForCar(assignments, bestCar);
                assignment.addPathNode(startNode);  // 先添加起点
                assignment.addPathNode(endNode);    // 再添加终点，保持顺序
                
                // 将操作同步到Car类中（只修改序列）
                bestCar.addPathNode(startNode);
                bestCar.addPathNode(endNode);
                
                // 分配成功，标记demand为已分配
                demand.setProcessed();
                //System.out.println("成功为需求 " + demand.getUUID() + " 分配车辆 " + bestCar.getUUID() + "，成本: " + minCost);
            } else {
                //没有找到合适的车辆，订单无变化
                //System.out.println("无法为需求 " + demand.getUUID() + " 分配车辆，保持订单");
            }
        }

        // ========== 第四步：同步结果到原始车辆 ==========
        syncAssignmentsToCars(assignments);

        return assignments;
    }

    /**
     * 模拟整个序列执行过程，判断是否可以添加一对起点-终点操作到车辆序列末尾
     * 重要：需要模拟从车辆当前位置开始，执行整个序列（包括新添加的操作）后的载重和体积状态
     * 
     * @param car 车辆对象（副本）
     * @param startNode 起点操作（装货）
     * @param endNode 终点操作（卸货）
     * @return true表示可以添加，false表示不能添加
     */
    private boolean canAddDemandPairToSequence(Car car, PathNode startNode, PathNode endNode) {
        // 创建车辆状态的临时副本用于模拟
        // 从车辆当前的实际状态开始模拟，因为车辆可能已经有部分载重
        double remainingLoad = car.getRemainingLoad();
        double remainingVolume = car.getRemainingVolume();
        
        // ========== 第一步：模拟执行现有序列 ==========
        for (PathNode existingNode : car.getNodeList()) {
            // 模拟执行现有操作
            if (existingNode.isOrigin()) {
                // 装货：减少剩余载重和体积
                remainingLoad -= existingNode.getQuantity();
                remainingVolume -= existingNode.getDemand().getVolume();
            } else {
                // 卸货：增加剩余载重和体积
                remainingLoad += existingNode.getQuantity();
                remainingVolume += existingNode.getDemand().getVolume();
            }
            
            // 检查约束是否满足
            if (remainingLoad < 0 || remainingLoad > car.getMaxLoad() || 
                remainingVolume < 0 || remainingVolume > car.getMaxVolume()) {
                return false; // 现有序列已经违反约束
            }
        }
        
        // ========== 第二步：模拟执行新添加的起点-终点对 ==========
        // 模拟起点操作（装货）
        remainingLoad -= startNode.getQuantity();
        remainingVolume -= startNode.getDemand().getVolume();
        
        // 检查起点操作后的约束
        if (remainingLoad < 0 || remainingVolume < 0 ) 
        {
            return false;
        }
        
        // 模拟终点操作（卸货）
        remainingLoad += endNode.getQuantity();
        remainingVolume += endNode.getDemand().getVolume();
        
        // 检查终点操作后的约束
        if (remainingLoad < 0 || remainingLoad > car.getMaxLoad() || 
            remainingVolume < 0 || remainingVolume > car.getMaxVolume()) {
            return false;
        }
        
        return true; // 所有约束都满足
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
