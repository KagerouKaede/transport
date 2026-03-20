package com.tsadmin.transport.domain.share;

import com.tsadmin.transport.entity.Demand;

/** 车辆路径点 */
public class PathNode
{
    private Demand demand;
    private boolean isOrigin;

    public PathNode(Demand demand, boolean isOrigin)
    {
        this.demand = demand;
        this.isOrigin = isOrigin;
    }

    public Demand getDemand() { return demand; }
    public boolean isOrigin() { return isOrigin; }

    /**
     * 根据订单生成路径点对
     * @param demand 路径点对生成的订单目标
     * @return 一个由两个元素（路径点）组成的数组，0号元素为起点，1号元素为终点
     */
    public static PathNode[] createNodePair(Demand demand)
    {
        PathNode[] ret = {
            new PathNode(demand, true),
            new PathNode(demand, false)
        };
        return ret;
    }
}
