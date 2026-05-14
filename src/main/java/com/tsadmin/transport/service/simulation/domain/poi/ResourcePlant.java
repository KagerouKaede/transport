package com.tsadmin.transport.service.simulation.domain.poi;

import java.util.UUID;

import jakarta.persistence.Entity;

@Entity
public final class ResourcePlant extends PoiSimuAttr implements Dumper
{
    // private static double STOCK_GROWTH_RATE;

    // public static void setStockGrowthRate(int rate) { STOCK_GROWTH_RATE = rate / 100.0; }

    protected ResourcePlant() {}
    public ResourcePlant(UUID sandboxId, UUID poiId, int maxStock)
    {
        super(sandboxId, poiId, maxStock);
    }

    // @Override
    // public void update()
    // {
    //     stock += (maxStock - stock) * STOCK_GROWTH_RATE;
    // }

    // @Override
    // public double getStock() { return stock; }
}
