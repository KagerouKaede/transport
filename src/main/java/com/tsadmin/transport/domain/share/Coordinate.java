package com.tsadmin.transport.domain.share;

import jakarta.persistence.Embeddable;

@Embeddable
public class Coordinate
{
    private double latitude;
    private double longitude;

    public Coordinate(double latitude, double longitude)
    {
        setPosition(latitude, longitude);
    }
    public Coordinate(Coordinate coordinate)
    {
        setPosition(coordinate.getLat(), coordinate.getLng());
    }

    public double getLat() { return latitude; }
    public double getLng() { return longitude; }

    public void setPosition(double latitude, double longitude)
    {
        this.latitude = latitude;
        this.longitude = longitude;
    }
}
