package com.tsAdmin.model;

/** 产品 */
public class Product
{
    private final ProductType type;
    private int quantity;
    private double volume;

    public Product(ProductType type, int quantity, double volume)
    {
        this.type = type;
        this.quantity = quantity;
        this.volume = volume;
    }

    // Setter
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public void setVolume(double volume) { this.volume = volume; }

    // Getter
    public ProductType getType() { return type; }
    public int getQuantity() { return quantity; }
    public double getVolume() { return volume; }
}
