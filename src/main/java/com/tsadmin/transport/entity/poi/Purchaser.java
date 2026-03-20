package com.tsadmin.transport.entity.poi;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.tsadmin.transport.entity.Demand;
import com.tsadmin.transport.domain.DemandManager;
import com.tsadmin.transport.domain.PoiManager;
import com.tsadmin.transport.domain.share.Coordinate;
import com.tsadmin.transport.domain.share.ProductType;

public abstract class Purchaser extends Poi
{
    private static final Logger logger = LogManager.getLogger(Purchaser.class);

    protected static double PURCHASE_THRESHOLD;

    protected List<String> upstreamPoiUuid = new ArrayList<>();
    /** 运往本 POI 的订单，若无则为 {@code null} */
    protected Demand demand = null;

    public static void setPurchaseThreshold(int threshold) { PURCHASE_THRESHOLD = threshold / 100.0; }

    public Purchaser(String uuid, String name, ProductType productType, Coordinate position, int maxStock)
    {
        super(uuid, name, productType, position, maxStock);
    }

    public void addUpstream(String uuid)
    {
        if (upstreamPoiUuid.contains(uuid))
        {
            logger.warn("Duplicated UUID({}) was added for POI(UUID:{}), operation skipped", uuid, this.uuid);
            return;
        }

        upstreamPoiUuid.add(uuid);
    }

    /**
     * 进行判断并在符合条件时尝试生成订单
     * @param stock 等于 现有库存 + 计算损耗后的加工中库存（如果有的话）
     */
    protected void tryGenerateDemand(double stock)
    {
        if (demand != null ||
            !DemandManager.allowNewDemand() ||
            stock > PURCHASE_THRESHOLD * maxStock)
            return;

        int quantity = productType.getRandQuantity();
        if (stock + quantity > maxStock) return;

        Poi targetUpstream = null;
        for (String uuid : upstreamPoiUuid)
        {
            Poi requestedUpstream = PoiManager.getPoi(uuid);
            if (requestedUpstream instanceof Dumper)
            {
                Dumper dumper = (Dumper) requestedUpstream;
                if (dumper.isAvailable(quantity))
                {
                    targetUpstream = requestedUpstream;
                    break;
                }
            }
            else throw new IllegalArgumentException("Resource POI must be an instance of Dumper!");
        }
        if (targetUpstream == null) return;

        demand = DemandManager.generateDemand(targetUpstream, this, quantity);
    }

    public void onDemandCompleted()
    {
        stock += demand.getQuantity();
        demand = null;
    }
}
