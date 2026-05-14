package com.tsadmin.transport.service.simulation.domain.poi;

import com.tsadmin.transport.common.enums.ProductType;
import com.tsadmin.transport.domain.entity.Product;

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
