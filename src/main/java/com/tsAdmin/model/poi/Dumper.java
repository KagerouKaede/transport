package com.tsAdmin.model.poi;

import com.tsAdmin.model.Product;
import com.tsAdmin.model.ProductType;

public interface Dumper
{
    double getStock();
    ProductType getProductType();

    default Product packProduct(int quantity)
    {
        double volume = getProductType().getRandVolume(quantity);
        return new Product(getProductType(), quantity, volume);
    }

    default boolean isAvailable(int need)
    {
        return getStock() >= need;
    }
}
