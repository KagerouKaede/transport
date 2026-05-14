package com.tsadmin.transport.service.optimization.graph;

import java.util.*;

import com.tsAdmin.common.Coordinate;

/**
 * 图算法工具类
 * 实现Dijkstra和A*算法，用于计算起点到终点的最短时间、最短成本、最短距离
 * 作为约束阈值的下界计算基础
 */
public class GraphAlgorithm
{
    /**
     * 图节点类
     */
    public static class GraphNode
    {
        private final String id;
        private final Coordinate coordinate;
        private final Map<String, Edge> neighbors; // 邻接边，key为邻居节点ID

        public GraphNode(String id, Coordinate coordinate)
        {
            this.id = id;
            this.coordinate = coordinate;
            this.neighbors = new HashMap<>();
        }

        public String getId() { return id; }
        public Coordinate getCoordinate() { return coordinate; }
        public Map<String, Edge> getNeighbors() { return neighbors; }

        public void addNeighbor(String neighborId, Edge edge)
        {
            neighbors.put(neighborId, edge);
        }
    }

    /**
     * 边类，包含距离、时间、成本三个权重
     */
    public static class Edge
    {
        private final String from;
        private final String to;
        private final double distance;  // 距离
        private final double time;      // 时间
        private final double cost;      // 成本

        public Edge(String from, String to, double distance, double time, double cost)
        {
            this.from = from;
            this.to = to;
            this.distance = distance;
            this.time = time;
            this.cost = cost;
        }

        public String getFrom() { return from; }
        public String getTo() { return to; }
        public double getDistance() { return distance; }
        public double getTime() { return time; }
        public double getCost() { return cost; }
    }

    /**
     * 路径结果类
     */
    public static class PathResult
    {
        private final List<String> path;      // 路径节点ID序列
        private final double totalDistance;   // 总距离
        private final double totalTime;      // 总时间
        private final double totalCost;      // 总成本

        public PathResult(List<String> path, double totalDistance, double totalTime, double totalCost)
        {
            this.path = new ArrayList<>(path);
            this.totalDistance = totalDistance;
            this.totalTime = totalTime;
            this.totalCost = totalCost;
        }

        public List<String> getPath() { return path; }
        public double getTotalDistance() { return totalDistance; }
        public double getTotalTime() { return totalTime; }
        public double getTotalCost() { return totalCost; }
    }

    /**
     * 优化目标类型
     */
    public enum ObjectiveType
    {
        DISTANCE,  // 最短距离
        TIME,      // 最短时间
        COST       // 最低成本
    }

    private final Map<String, GraphNode> nodes;  // 图节点集合

    public GraphAlgorithm()
    {
        this.nodes = new HashMap<>();
    }

    /**
     * 添加节点到图中
     * @param id 节点ID
     * @param coordinate 节点坐标
     */
    public void addNode(String id, Coordinate coordinate)
    {
        if (!nodes.containsKey(id))
        {
            nodes.put(id, new GraphNode(id, coordinate));
        }
    }

    /**
     * 添加边到图中
     * @param fromId 起点节点ID
     * @param toId 终点节点ID
     * @param distance 距离
     * @param time 时间
     * @param cost 成本
     */
    public void addEdge(String fromId, String toId, double distance, double time, double cost)
    {
        if (!nodes.containsKey(fromId) || !nodes.containsKey(toId))
        {
            throw new IllegalArgumentException("节点不存在，无法添加边");
        }
        nodes.get(fromId).addNeighbor(toId, new Edge(fromId, toId, distance, time, cost));
    }

    /**
     * 基于坐标自动添加边（使用直线距离计算）
     * @param fromId 起点节点ID
     * @param toId 终点节点ID
     * @param averageSpeed 平均速度（单位：距离/时间单位），用于计算时间
     * @param costPerDistance 单位距离成本，用于计算成本
     */
    public void addEdgeByCoordinate(String fromId, String toId, double averageSpeed, double costPerDistance)
    {
        GraphNode fromNode = nodes.get(fromId);
        GraphNode toNode = nodes.get(toId);
        if (fromNode == null || toNode == null)
        {
            throw new IllegalArgumentException("节点不存在，无法添加边");
        }

        double distance = Coordinate.distance(fromNode.getCoordinate(), toNode.getCoordinate());
        double time = distance / averageSpeed;
        double cost = distance * costPerDistance;

        addEdge(fromId, toId, distance, time, cost);
    }

    /**
     * 使用Dijkstra算法计算最短路径
     * @param startId 起点节点ID
     * @param endId 终点节点ID
     * @param objectiveType 优化目标类型（距离/时间/成本）
     * @return 路径结果，如果不存在路径则返回null
     */
    public PathResult dijkstra(String startId, String endId, ObjectiveType objectiveType)
    {
        if (!nodes.containsKey(startId) || !nodes.containsKey(endId))
        {
            return null;
        }

        // 距离表：从起点到各节点的最短距离/时间/成本
        Map<String, Double> dist = new HashMap<>();
        // 前驱节点表：用于重建路径
        Map<String, String> prev = new HashMap<>();
        // 已访问节点集合
        Set<String> visited = new HashSet<>();
        // 优先队列：按距离/时间/成本排序
        PriorityQueue<NodeDistance> pq = new PriorityQueue<>(Comparator.comparingDouble(NodeDistance::getDistance));

        // 初始化：所有节点距离为无穷大
        for (String nodeId : nodes.keySet())
        {
            dist.put(nodeId, Double.MAX_VALUE);
        }
        dist.put(startId, 0.0);
        pq.offer(new NodeDistance(startId, 0.0));

        while (!pq.isEmpty())
        {
            NodeDistance current = pq.poll();
            String currentNodeId = current.getNodeId();

            if (visited.contains(currentNodeId))
            {
                continue;
            }

            if (currentNodeId.equals(endId))
            {
                // 找到终点，重建路径并返回结果
                return buildPathResult(startId, endId, prev, dist, objectiveType);
            }

            visited.add(currentNodeId);
            GraphNode currentNode = nodes.get(currentNodeId);

            // 遍历所有邻居节点
            for (Map.Entry<String, Edge> neighborEntry : currentNode.getNeighbors().entrySet())
            {
                String neighborId = neighborEntry.getKey();
                Edge edge = neighborEntry.getValue();

                if (visited.contains(neighborId))
                {
                    continue;
                }

                // 根据优化目标选择权重
                double weight = getWeight(edge, objectiveType);
                double newDist = dist.get(currentNodeId) + weight;

                if (newDist < dist.get(neighborId))
                {
                    dist.put(neighborId, newDist);
                    prev.put(neighborId, currentNodeId);
                    pq.offer(new NodeDistance(neighborId, newDist));
                }
            }
        }

        // 无法到达终点
        return null;
    }

    /**
     * 使用A*算法计算最短路径
     * @param startId 起点节点ID
     * @param endId 终点节点ID
     * @param objectiveType 优化目标类型（距离/时间/成本）
     * @return 路径结果，如果不存在路径则返回null
     */
    public PathResult aStar(String startId, String endId, ObjectiveType objectiveType)
    {
        if (!nodes.containsKey(startId) || !nodes.containsKey(endId))
        {
            return null;
        }

        GraphNode startNode = nodes.get(startId);
        GraphNode endNode = nodes.get(endId);

        // g(n)：从起点到节点n的实际代价
        Map<String, Double> gScore = new HashMap<>();
        // f(n) = g(n) + h(n)：估计总代价
        Map<String, Double> fScore = new HashMap<>();
        // 前驱节点表
        Map<String, String> cameFrom = new HashMap<>();
        // 开放集合：待探索的节点
        PriorityQueue<NodeDistance> openSet = new PriorityQueue<>(Comparator.comparingDouble(NodeDistance::getDistance));
        // 开放集合的快速查找
        Set<String> openSetIds = new HashSet<>();
        // 关闭集合：已探索的节点
        Set<String> closedSet = new HashSet<>();

        // 初始化
        for (String nodeId : nodes.keySet())
        {
            gScore.put(nodeId, Double.MAX_VALUE);
            fScore.put(nodeId, Double.MAX_VALUE);
        }
        gScore.put(startId, 0.0);
        fScore.put(startId, heuristic(startNode, endNode, objectiveType));
        openSet.offer(new NodeDistance(startId, fScore.get(startId)));
        openSetIds.add(startId);

        while (!openSet.isEmpty())
        {
            NodeDistance current = openSet.poll();
            String currentNodeId = current.getNodeId();
            openSetIds.remove(currentNodeId);

            if (currentNodeId.equals(endId))
            {
                // 找到终点，重建路径并返回结果
                return buildPathResult(startId, endId, cameFrom, gScore, objectiveType);
            }

            closedSet.add(currentNodeId);
            GraphNode currentNode = nodes.get(currentNodeId);

            // 遍历所有邻居节点
            for (Map.Entry<String, Edge> neighborEntry : currentNode.getNeighbors().entrySet())
            {
                String neighborId = neighborEntry.getKey();
                Edge edge = neighborEntry.getValue();

                if (closedSet.contains(neighborId))
                {
                    continue;
                }

                // 计算从起点到邻居节点的实际代价
                double weight = getWeight(edge, objectiveType);
                double tentativeGScore = gScore.get(currentNodeId) + weight;

                if (tentativeGScore < gScore.get(neighborId))
                {
                    // 找到更优路径
                    cameFrom.put(neighborId, currentNodeId);
                    gScore.put(neighborId, tentativeGScore);
                    GraphNode neighborNode = nodes.get(neighborId);
                    fScore.put(neighborId, tentativeGScore + heuristic(neighborNode, endNode, objectiveType));

                    if (!openSetIds.contains(neighborId))
                    {
                        openSet.offer(new NodeDistance(neighborId, fScore.get(neighborId)));
                        openSetIds.add(neighborId);
                    }
                }
            }
        }

        // 无法到达终点
        return null;
    }

    /**
     * 启发式函数（用于A*算法）
     * 使用直线距离作为启发式估计
     */
    private double heuristic(GraphNode from, GraphNode to, ObjectiveType objectiveType)
    {
        double distance = Coordinate.distance(from.getCoordinate(), to.getCoordinate());
        
        switch (objectiveType)
        {
            case DISTANCE:
                return distance;
            case TIME:
                // 假设平均速度60单位/时间单位
                return distance / 60.0;
            case COST:
                // 假设单位距离成本为1
                return distance * 1.0;
            default:
                return distance;
        }
    }

    /**
     * 根据优化目标获取边的权重
     */
    private double getWeight(Edge edge, ObjectiveType objectiveType)
    {
        switch (objectiveType)
        {
            case DISTANCE:
                return edge.getDistance();
            case TIME:
                return edge.getTime();
            case COST:
                return edge.getCost();
            default:
                return edge.getDistance();
        }
    }

    /**
     * 重建路径并计算总距离、总时间、总成本
     */
    private PathResult buildPathResult(String startId, String endId, Map<String, String> prev, 
                                       Map<String, Double> dist, ObjectiveType objectiveType)
    {
        List<String> path = new ArrayList<>();
        String current = endId;

        // 重建路径
        while (current != null)
        {
            path.add(0, current);
            current = prev.get(current);
        }

        // 如果路径起点不是startId，说明无法到达
        if (!path.get(0).equals(startId))
        {
            return null;
        }

        // 计算路径的总距离、总时间、总成本
        double totalDistance = 0.0;
        double totalTime = 0.0;
        double totalCost = 0.0;

        for (int i = 0; i < path.size() - 1; i++)
        {
            String fromId = path.get(i);
            String toId = path.get(i + 1);
            GraphNode fromNode = nodes.get(fromId);
            Edge edge = fromNode.getNeighbors().get(toId);

            if (edge != null)
            {
                totalDistance += edge.getDistance();
                totalTime += edge.getTime();
                totalCost += edge.getCost();
            }
        }

        return new PathResult(path, totalDistance, totalTime, totalCost);
    }

    /**
     * 辅助类：用于优先队列
     */
    private static class NodeDistance
    {
        private final String nodeId;
        private final double distance;

        public NodeDistance(String nodeId, double distance)
        {
            this.nodeId = nodeId;
            this.distance = distance;
        }

        public String getNodeId() { return nodeId; }
        public double getDistance() { return distance; }
    }

    /**
     * 便捷方法：计算两点间的最短距离
     * 如果图中没有节点，直接使用直线距离；否则使用Dijkstra算法
     */
    public double shortestDistance(Coordinate start, Coordinate end)
    {
        // 如果图中没有节点，直接计算直线距离
        if (nodes.isEmpty())
        {
            return Coordinate.distance(start, end);
        }

        String startId = "temp_start";
        String endId = "temp_end";
        
        addNode(startId, start);
        addNode(endId, end);
        
        // 为所有现有节点添加到起点和终点的边（双向）
        for (GraphNode node : nodes.values())
        {
            if (!node.getId().equals(startId) && !node.getId().equals(endId))
            {
                addEdgeByCoordinate(node.getId(), startId, 60.0, 1.0);
                addEdgeByCoordinate(node.getId(), endId, 60.0, 1.0);
                addEdgeByCoordinate(startId, node.getId(), 60.0, 1.0);
                addEdgeByCoordinate(endId, node.getId(), 60.0, 1.0);
            }
        }
        
        // 添加起点到终点的直接边
        addEdgeByCoordinate(startId, endId, 60.0, 1.0);

        PathResult result = dijkstra(startId, endId, ObjectiveType.DISTANCE);
        
        // 清理临时节点
        nodes.remove(startId);
        nodes.remove(endId);
        
        return result != null ? result.getTotalDistance() : Coordinate.distance(start, end);
    }

    /**
     * 便捷方法：计算两点间的最短时间
     * 如果图中没有节点，直接使用直线距离除以速度；否则使用Dijkstra算法
     */
    public double shortestTime(Coordinate start, Coordinate end, double averageSpeed)
    {
        // 如果图中没有节点，直接计算
        if (nodes.isEmpty())
        {
            return Coordinate.distance(start, end) / averageSpeed;
        }

        String startId = "temp_start";
        String endId = "temp_end";
        
        addNode(startId, start);
        addNode(endId, end);
        
        // 为所有现有节点添加到起点和终点的边（双向）
        for (GraphNode node : nodes.values())
        {
            if (!node.getId().equals(startId) && !node.getId().equals(endId))
            {
                addEdgeByCoordinate(node.getId(), startId, averageSpeed, 1.0);
                addEdgeByCoordinate(node.getId(), endId, averageSpeed, 1.0);
                addEdgeByCoordinate(startId, node.getId(), averageSpeed, 1.0);
                addEdgeByCoordinate(endId, node.getId(), averageSpeed, 1.0);
            }
        }
        
        // 添加起点到终点的直接边
        addEdgeByCoordinate(startId, endId, averageSpeed, 1.0);

        PathResult result = dijkstra(startId, endId, ObjectiveType.TIME);
        
        nodes.remove(startId);
        nodes.remove(endId);
        
        return result != null ? result.getTotalTime() : Coordinate.distance(start, end) / averageSpeed;
    }

    /**
     * 便捷方法：计算两点间的最低成本
     * 如果图中没有节点，直接使用直线距离乘以单位成本；否则使用Dijkstra算法
     */
    public double shortestCost(Coordinate start, Coordinate end, double costPerDistance)
    {
        // 如果图中没有节点，直接计算
        if (nodes.isEmpty())
        {
            return Coordinate.distance(start, end) * costPerDistance;
        }

        String startId = "temp_start";
        String endId = "temp_end";
        
        addNode(startId, start);
        addNode(endId, end);
        
        // 为所有现有节点添加到起点和终点的边（双向）
        for (GraphNode node : nodes.values())
        {
            if (!node.getId().equals(startId) && !node.getId().equals(endId))
            {
                addEdgeByCoordinate(node.getId(), startId, 60.0, costPerDistance);
                addEdgeByCoordinate(node.getId(), endId, 60.0, costPerDistance);
                addEdgeByCoordinate(startId, node.getId(), 60.0, costPerDistance);
                addEdgeByCoordinate(endId, node.getId(), 60.0, costPerDistance);
            }
        }
        
        // 添加起点到终点的直接边
        addEdgeByCoordinate(startId, endId, 60.0, costPerDistance);

        PathResult result = dijkstra(startId, endId, ObjectiveType.COST);
        
        nodes.remove(startId);
        nodes.remove(endId);
        
        return result != null ? result.getTotalCost() : Coordinate.distance(start, end) * costPerDistance;
    }
}

