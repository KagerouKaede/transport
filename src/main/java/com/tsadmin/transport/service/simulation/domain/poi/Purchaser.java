package com.tsadmin.transport.service.simulation.domain.poi;

import java.util.List;
import java.util.UUID;

import com.tsadmin.transport.domain.entity.Demand;
import com.tsadmin.transport.domain.entity.Poi;

import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;

@Entity
public abstract class Purchaser extends PoiSimuAttr
{
    // protected static double PURCHASE_THRESHOLD;

    @ManyToOne
    protected List<Poi> upstreamPoi;
    /** 运往本 POI 的订单，若无则为 {@code null} */
    protected Demand demand = null;

    // public static void setPurchaseThreshold(int threshold) { PURCHASE_THRESHOLD = threshold / 100.0; }

    protected Purchaser() {}
    public Purchaser(UUID sandboxId, UUID poiId, int maxStock)
    {
        super(sandboxId, poiId, maxStock);
    }

    // public void addUpstream(Poi poi)
    // {
    //     upstreamPoi.add(poi);
    // }

    // TODO:remove upstream

    // /**
    //  * 进行判断并在符合条件时尝试生成订单
    //  * @param stock 等于 现有库存 + 计算损耗后的加工中库存（如果有的话）
    //  */
    // protected void tryGenerateDemand(double stock)
    // {
    //     if (demand != null ||
    //         !DemandManager.allowNewDemand() ||
    //         stock > PURCHASE_THRESHOLD * maxStock)
    //         return;

    //     int quantity = productType.getRandQuantity();
    //     if (stock + quantity > maxStock) return;

    //     Poi targetUpstream = null;
    //     for (Poi poi : upstreamPoi)
    //     {
    //         if (poi instanceof Dumper)
    //         {
    //             Dumper dumper = (Dumper) poi;
    //             if (dumper.isAvailable(quantity))
    //             {
    //                 targetUpstream = poi;
    //                 break;
    //             }
    //         }
    //         else throw new IllegalArgumentException("Resource POI must be an instance of Dumper!");
    //     }
    //     if (targetUpstream == null) return;

    //     demand = DemandManager.generateDemand(targetUpstream, this, quantity);
    // }

    // public void onDemandCompleted()
    // {
    //     stock += demand.getQuantity();
    //     demand = null;
    // }
}
