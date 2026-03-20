package com.tsadmin.transport.entity.poi;

import com.tsadmin.transport.domain.share.ProductType;
import com.tsadmin.transport.entity.Product;

public interface Dumper
{
    void setStock(double stock);
    double getStock();
    ProductType getProductType();

    default Product packProduct(int quantity)
    {
        double volume = getProductType().getRandVolume(quantity);
        setStock(getStock() - quantity);
        return new Product(getProductType(), quantity, volume);
    }

    default boolean isAvailable(int need)
    {
        return getStock() >= need;
    }
}
