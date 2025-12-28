package com.tsAdmin.control.scheduler;

import java.util.*;

import com.tsAdmin.common.PathNode;
import com.tsAdmin.model.*;
import com.tsAdmin.control.manager.*;


/**
 * 调度器基类，提供所有调度算法共享的通用工具方法
 * 
 * 设计原则：
 * - 只包含与优化目标无关的通用逻辑
 * - 所有调度器（单目标/多目标）都继承此类
 * - 方法设为 protected 供子类使用，public 供外部调用
 */
public abstract class BaseScheduler
{
    // ========== 共享组件 ==========

    protected final Random random = new Random();

    // ========== 接口方法（必须由子类实现） ==========
    public abstract List<Assignment> schedule();

    // ========== 公共工具方法（供外部调用） ==========

    /**
     * 将Assignment中的操作同步到原始Car对象的操作列表
     * 功能：将优化后的分配方案同步回原始车辆对象，确保前端能获取到最新的调度结果
     * 
     * @param assignments 优化后的分配方案列表
     */
    public void syncAssignmentsToCars(List<Assignment> assignments) {
        for (Assignment assignment : assignments) {
            Car car = assignment.getCar();
            CarManager.carMap.get(car.getUUID()).setNodeList(assignment.getNodeList());
        }
    }

    /**
     * 将Assignment中的操作同步到指定的原始车辆列表
     * 重载版本，保持与 SimulatedAnnealingScheduler 兼容
     */
    public void syncAssignmentsToCars(List<Assignment> assignments, Collection<Car> originalCars) {
        for (Assignment assignment : assignments) {
            Car car = assignment.getCar();
            for (Car originalCar : originalCars) {
                if (car.getUUID().equals(originalCar.getUUID())) {
                    originalCar.getNodeList().clear();
                    for (PathNode node : assignment.getNodeList()) {
                        originalCar.addPathNode(node);
                    }
                    break;
                }
            }
        }
    }

    // ========== 受保护的工具方法（供子类使用） ==========

    /**
     * 将原始Demand列表转换为PathNode节点对 (每个Demand生成两个节点)
     * @param demands 需求列表
     * @return 节点对列表
     */
    protected List<PathNode> createPathNodes(List<Demand> demands) {
        List<PathNode> pairs = new ArrayList<>();
        for (Demand demand : demands) {
            pairs.add(new PathNode(demand, true));
            pairs.add(new PathNode(demand, false));
        }
        return pairs;
    }

    /**
     * 计算路径总成本
     * @param car 车辆
     * @param nodeList 路径节点列表
     * @return 总成本
     */
 
    /**
     * 对分配方案进行深拷贝
     * 功能：创建分配方案的完全独立副本，避免修改原始数据
     * 重要性：优化算法需要多次尝试不同的解，如果不深拷贝会相互影响
     * 
     * @param assignments 原分配方案
     * @return 拷贝后的分配方案
     */
    protected List<Assignment> deepCopyAssignments(List<Assignment> assignments) {
        List<Assignment> copies = new ArrayList<>();
        for (Assignment original : assignments) {
            Assignment copy = new Assignment(new Car(original.getCar()));
            for (PathNode op : original.getNodeList()) {
                copy.addPathNode(new PathNode(op.getDemand(), op.isOrigin()));
            }
            copies.add(copy);
        }
        return copies;
    }

    /**
     * 判断分配方案是否合法（载重和体积约束）
     * @param assignment 要检查的分配方案
     * @return true表示可行，false表示不可行
     */
    protected boolean isFeasibleRoute(Assignment assignment) {
        Car car = assignment.getCar();
        double remainingLoad = car.getRemainingLoad();
        double remainingVolume = car.getRemainingVolume();

        for (PathNode node : assignment.getNodeList()) {
            double quantity = node.getDemand().getQuantity();
            double volume = node.getDemand().getVolume();

            if (node.isOrigin()) {
                remainingLoad -= quantity;
                remainingVolume -= volume;
            } else {
                remainingLoad += quantity;
                remainingVolume += volume;
            }

            if (remainingLoad < 0 || remainingLoad > car.getMaxLoad() ||
                remainingVolume < 0 || remainingVolume > car.getMaxVolume()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 在节点列表中查找同一订单的配对节点
     * 
     * @param nodeList 要搜索的节点列表
     * @param orderUUID 要查找的订单UUID
     * @param isOrigin 当前选中的节点类型（true=起点，false=终点）
     * @return 配对的节点，如果找不到返回null
     */
    protected PathNode findPairNode(List<PathNode> nodeList, String orderUUID, boolean isOrigin) {
        for (PathNode node : nodeList) {
            boolean sameOrder = node.getDemand().getUUID().equals(orderUUID);
            boolean oppositeType = node.isOrigin() != isOrigin;
            if (sameOrder && oppositeType) {
                return node;
            }
        }
        return null;
    }

    /**
     * 提取需求对（起点+终点）
     * @param assignment 分配方案
     * @return 需求对数组 [origin, destination]，如果找不到返回null
     */
    protected PathNode[] extractPair(Assignment assignment) {
        List<PathNode> nodes = assignment.getNodeList();
        if (nodes.isEmpty()) {
            return null;
        }

        PathNode selected = nodes.get(random.nextInt(nodes.size()));
        String demandId = selected.getDemand().getUUID();

        for (PathNode node : nodes) {
            if (node.getDemand().getUUID().equals(demandId) && 
                node.isOrigin() != selected.isOrigin()) {
                PathNode start = selected.isOrigin() ? selected : node;
                PathNode end = selected.isOrigin() ? node : selected;
                return new PathNode[]{start, end};
            }
        }
        return null;
    }

    /**
     * 随机插入需求对到指定位置
     * @param assignment 目标分配方案
     * @param startNode 起点节点
     * @param endNode 终点节点
     * @return 是否成功插入
     */
    protected boolean insertPairAtRandomPositions(Assignment assignment, PathNode startNode, PathNode endNode) {
        List<PathNode> nodes = assignment.getNodeList();
        int size = nodes.size();
        List<int[]> candidatePositions = new ArrayList<>();
        for (int startPos = 0; startPos <= size; startPos++) {
            for (int endPos = startPos; endPos <= size; endPos++) {
                candidatePositions.add(new int[]{startPos, endPos});
            }
        }
        Collections.shuffle(candidatePositions, random);

        for (int[] position : candidatePositions) {
            nodes.add(position[0], startNode);
            nodes.add(position[1] + 1, endNode);
            if (isFeasibleRoute(assignment)) {
                return true;
            }
            nodes.remove(endNode);
            nodes.remove(startNode);
        }
        return false;
    }

    /**
     * 随机选择一个非空的分配方案
     * @param assignments 分配方案列表
     * @return 非空分配方案，如果都为空返回null
     */
    protected Assignment pickRandomNonEmptyAssignment(List<Assignment> assignments) {
        List<Assignment> candidates = new ArrayList<>();
        for (Assignment assignment : assignments) {
            if (assignment != null && !assignment.getNodeList().isEmpty()) {
                candidates.add(assignment);
            }
        }
        return candidates.isEmpty() ? null : candidates.get(random.nextInt(candidates.size()));
    }

    /**
     * 随机选择一个非空的分配方案，并排除指定方案
     * @param assignments 分配方案列表
     * @param exclude 要排除的分配方案
     * @return 非空且非排除的分配方案，如果找不到返回null
     */
    protected Assignment pickRandomNonEmptyAssignment(List<Assignment> assignments, Assignment exclude) {
        List<Assignment> candidates = new ArrayList<>();
        for (Assignment assignment : assignments) {
            if (assignment != null && assignment != exclude && !assignment.getNodeList().isEmpty()) {
                candidates.add(assignment);
            }
        }
        return candidates.isEmpty() ? null : candidates.get(random.nextInt(candidates.size()));
    }

    /**
     * 判断分配方案列表中是否包含有效的分配方案
     * @param assignments 分配方案列表
     * @return 是否包含有效方案
     */
    protected boolean containsValidRoute(List<Assignment> assignments) {
        if (assignments == null) {
            return false;
        }
        for (Assignment assignment : assignments) {
            if (assignment != null && !assignment.getNodeList().isEmpty()) {
                return true;
            }
        }
        return false;
    }
}