package com.tsAdmin.model.poi;

import com.tsAdmin.model.Product;
import com.tsAdmin.model.ProductType;

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
