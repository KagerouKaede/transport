package com.tsadmin.transport.entity.poi;

import com.tsadmin.transport.domain.share.Coordinate;
import com.tsadmin.transport.domain.share.ProductType;

public abstract class Poi
{
    protected final String uuid;
    protected final String name;
    protected final ProductType productType;
    protected final Coordinate position;
    protected final int maxStock;

    protected double stock;

    public Poi(String uuid, String name, ProductType productType, Coordinate position, int maxStock)
    {
        this.uuid = uuid;
        this.name = name;
        this.productType = productType;
        this.position = position;
        this.maxStock = maxStock;
    }

    /** 更新当前兴趣点 */
    public abstract void update();

    public String getUUID() { return uuid; }
    public ProductType getProductType() { return productType; }
    public Coordinate getPosition() { return position; }

    public void setStock(double stock) { this.stock = stock; }
    public double getStock() { return stock; }
}
