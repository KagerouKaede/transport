package com.tsadmin.transport.domain.entity;

import com.tsadmin.transport.common.enums.ProductType;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/** 产品 */
@Embeddable
public class Product
{
    @Enumerated(EnumType.STRING)
    private ProductType type;
    private double quantity;
    private double volume;

    protected Product() {}
    public Product(ProductType type, double quantity, double volume)
    {
        this.type = type;
        this.quantity = quantity;
        this.volume = volume;
    }

    // Setter
    public void setQuantity(double quantity) { this.quantity = quantity; }
    public void setVolume(double volume) { this.volume = volume; }

    // Getter
    public ProductType getType() { return type; }
    public double getQuantity() { return quantity; }
    public double getVolume() { return volume; }
}
