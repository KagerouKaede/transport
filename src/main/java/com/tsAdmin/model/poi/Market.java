package com.tsAdmin.model.poi;

import com.tsAdmin.common.Coordinate;
import com.tsAdmin.model.ProductType;

public final class Market extends Purchaser
{
    private static double SALES_RATE;

    public static void setSalesRate(int rate) { SALES_RATE = rate / 100.0; }

    public Market(String uuid, String name, ProductType productType, Coordinate position, int maxStock)
    {
        super(uuid, name, productType, position, maxStock);
    }

    @Override
    public void update()
    {
        stock -= stock * SALES_RATE;
        tryGenerateDemand(stock);
    }
}
