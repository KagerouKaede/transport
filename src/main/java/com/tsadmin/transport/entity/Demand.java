package com.tsadmin.transport.entity;

import com.tsadmin.transport.domain.DemandManager;
import com.tsadmin.transport.domain.share.Coordinate;
import com.tsadmin.transport.domain.share.ProductType;
import com.tsadmin.transport.entity.poi.Poi;
import com.tsadmin.transport.entity.poi.Purchaser;
import com.tsadmin.transport.util.GeoUtil;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** 需求 */
@Entity
@Table(name = "demands")
public class Demand
{
    private String uuid;
    private Poi origin;
    private Poi destination;
    private Product product;

    private boolean isAssigned;

    public Demand(String uuid, Poi origin, Poi destination, Product product)
    {
        this.uuid = uuid;
        this.origin = origin;
        this.destination = destination;
        this.product = product;
        this.isAssigned = false;
    }

    public void onCompleted()
    {
        ((Purchaser)this.destination).onDemandCompleted();
        DemandManager.removeDemand(uuid);
    }

    // Setter
    public void setQuantity(int quantity) { this.product.setQuantity(quantity); }
    public void setVolume(double volume) { this.product.setVolume(volume); }
    public void setAssigned() { this.isAssigned = true; }

    // Getter
    public String getUUID() { return uuid; }
    public Coordinate getOrigin() { return origin.getPosition(); }
    public String getOriginUuid() { return origin.getUUID(); }
    public Coordinate getDestination() { return destination.getPosition(); }
    public String getDestinationUuid() { return destination.getUUID(); }
    public ProductType getType() { return product.getType(); }
    public int getQuantity() { return product.getQuantity(); }
    public double getVolume() { return product.getVolume(); }
    public boolean isAssigned() { return isAssigned; }

    public int routeLength()
    {
        return (int)GeoUtil.distance(getOrigin(), getDestination());
    }
}