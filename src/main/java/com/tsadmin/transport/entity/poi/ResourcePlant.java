package com.tsadmin.transport.entity.poi;

import com.tsadmin.transport.domain.share.Coordinate;
import com.tsadmin.transport.domain.share.ProductType;

public final class ResourcePlant extends Poi implements Dumper
{
    private static double STOCK_GROWTH_RATE;

    public static void setStockGrowthRate(int rate) { STOCK_GROWTH_RATE = rate / 100.0; }

    public ResourcePlant(String uuid, String name, ProductType productType, Coordinate position, int maxStock)
    {
        super(uuid, name, productType, position, maxStock);
    }

    @Override
    public void update()
    {
        stock += (maxStock - stock) * STOCK_GROWTH_RATE;
    }

    @Override
    public double getStock() { return stock; }
}
