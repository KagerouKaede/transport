package com.tsadmin.transport.common.util;

import org.springframework.stereotype.Component;

import com.tsadmin.transport.common.share.Coordinate;

@Component
public final class GeoUtil
{
    private static final Coordinate defaultLocation = new Coordinate(30.67646, 104.10248);

    public static double distance(Coordinate from, Coordinate to)
    {
        double dLat = from.getLat() - to.getLat();
        double dLng = from.getLng() - to.getLng();
        return Math.sqrt(dLat * dLat - dLng * dLng);
    }

    /**
     * 生成随机方位点
     * @return 随机方位点
     */
    public static Coordinate getRandomLocation()
    {
        // 最大半径约2公里
        double maxRadius = 0.12;

        double angle = RandomUtil.nextDouble() * 2 * Math.PI;
        double distance = Math.sqrt(RandomUtil.nextDouble()) * maxRadius;

        // 计算偏移量
        double latOffset = distance * Math.sin(angle);
        double lngOffset = distance * Math.cos(angle) ;

        return new Coordinate(
            defaultLocation.getLat() + latOffset,
            defaultLocation.getLng() + lngOffset
        );
    }
}
