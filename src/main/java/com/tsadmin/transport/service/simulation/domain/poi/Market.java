package com.tsadmin.transport.service.simulation.domain.poi;

import java.util.UUID;

import jakarta.persistence.Entity;

@Entity
public final class Market extends Purchaser
{
    // private static double SALES_RATE;

    // public static void setSalesRate(int rate) { SALES_RATE = rate / 100.0; }

    protected Market() {}
    public Market(UUID sandboxId, UUID poiId, int maxStock)
    {
        super(sandboxId, poiId, maxStock);
    }

    // @Override
    // public void update()
    // {
    //     stock -= stock * SALES_RATE;
    //     tryGenerateDemand(stock);
    // }
}
