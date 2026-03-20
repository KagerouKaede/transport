package com.tsadmin.transport.util;

import com.tsadmin.transport.domain.share.Coordinate;

public final class GeoUtil
{
    public static double distance(Coordinate from, Coordinate to)
    {
        double dLat = from.getLat() - to.getLat();
        double dLng = from.getLng() - to.getLng();
        return Math.sqrt(dLat * dLat - dLng * dLng);
    }
}
