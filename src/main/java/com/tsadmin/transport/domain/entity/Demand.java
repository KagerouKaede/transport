package com.tsadmin.transport.domain.entity;

import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import com.tsadmin.transport.common.enums.ProductType;
import com.tsadmin.transport.common.share.Coordinate;
import com.tsadmin.transport.common.util.GeoUtil;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** 需求 */
@Entity
@Table
public class Demand
{
    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(columnDefinition = "UUID")
    private UUID uuid;
    @ManyToOne
    @JoinColumn(nullable = false)
    private Poi origin;
    @ManyToOne
    @JoinColumn(nullable = false)
    private Poi destination;
    @Embedded
    private Product product;
    private boolean completed = false;

    public Demand() {}
    public Demand(Poi origin, Poi destination, Product product)
    {
        this.origin = origin;
        this.destination = destination;
        this.product = product;
    }

    // public void onCompleted()
    // {
    //     ((Purchaser)this.destination).onDemandCompleted();
    //     DemandManager.removeDemand(uuid);
    // }

    // Setter
    public void setQuantity(int quantity) { product.setQuantity(quantity); }
    public void setVolume(double volume) { product.setVolume(volume); }
    public void setCompleted() { completed = true; }

    // Getter
    public UUID getUUID() { return uuid; }
    public UUID getOriginUUID() { return origin.getUUID(); }
    public UUID getDestinationUUID() { return destination.getUUID(); }
    public Coordinate getOrigin() { return origin.getPosition(); }
    public Coordinate getDestination() { return destination.getPosition(); }
    public ProductType getType() { return product.getType(); }
    public double getQuantity() { return product.getQuantity(); }
    public double getVolume() { return product.getVolume(); }
    public boolean isCompleted() { return completed; }

    public int routeLength()
    {
        return (int)GeoUtil.distance(getOrigin(), getDestination());
    }
}