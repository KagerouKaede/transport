package com.tsadmin.transport.service.simulation.domain.poi;

import java.util.UUID;

import com.tsadmin.transport.common.enums.ProductType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;

@Entity
@IdClass(PoiSimuUuid.class)
@Inheritance(strategy = InheritanceType.JOINED)
public abstract class PoiSimuAttr
{
    @Id
    private UUID sandboxId;
    @Id
    private UUID poiId;
    @Enumerated(EnumType.STRING)
    protected ProductType productType;
    @Column(nullable = false)
    protected int maxStock;
    protected double stock;

    public PoiSimuAttr() {}
    public PoiSimuAttr(UUID sandboxId, UUID poiId, int maxStock)
    {
        this.sandboxId = sandboxId;
        this.poiId = poiId;
        setMaxStock(maxStock);
        setStock(0);
    }

    public void setProductType(ProductType productType) { this.productType = productType; }
    public void setMaxStock(int maxStock) { this.maxStock = maxStock; }
    public void setStock(double stock) { this.stock = stock; }

    public ProductType getProductType() { return productType; }
    public int getMaxStock() { return maxStock; }
    public double getStock() { return stock; }
}
