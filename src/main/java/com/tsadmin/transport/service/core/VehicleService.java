package com.tsadmin.transport.service.core;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;

import com.tsadmin.transport.common.share.Coordinate;
import com.tsadmin.transport.common.util.JsonUtil;
import com.tsadmin.transport.common.util.JsonUtil.JsonMap;
import com.tsadmin.transport.domain.entity.Vehicle;
import com.tsadmin.transport.repository.VehicleRepository;

import jakarta.persistence.EntityNotFoundException;

public class VehicleService
{
    // LOADS 元素数一定要等于 VOLUME 元素数
    // private static final int[] LOADS = { 2, 5, 8, 12, 18, 24, 30, 35 };
    // private static final int[] VOLUMES = { 12, 16, 32, 48, 64, 86, 108, 140 };
    @Autowired
    private VehicleRepository vehRepo;

    /**
     * 新增车辆并将其加入数据库
     * @param fullJson 车辆的初始化配置，应为 Json 对象，对象应有属性如下:<br>
     * - "max_load": {@code Integer}, 车辆最大载重<br>
     * - "max_volume": {@code Integer}, 车辆最大容积<br>
     * - "position": {"lat": {@code Double}, "lng": {@code Double}}, 车辆所处位置
     * @return 新车辆的 UUID
     */
    public UUID registerVehicle(String fullJson)
    {
        JsonMap jsonMap = JsonUtil.toMap(fullJson);
        int maxLoad = JsonUtil.getObject(jsonMap, "max_load", Integer.class);
        int maxVolume = JsonUtil.getObject(jsonMap, "max_volume", Integer.class);
        double latitude = JsonUtil.getObject(jsonMap, "position/lat", Double.class);
        double longitude = JsonUtil.getObject(jsonMap, "position/lng", Double.class);
        Coordinate position = new Coordinate(latitude, longitude);

        Vehicle veh = new Vehicle(maxLoad, maxVolume, position);
        Vehicle saved = vehRepo.save(veh);
        return saved.getUUID();
    }

    /**
     * 修改车辆位置
     * @param fullJson 要修改车辆的 Json 对象，应有属性如下:<br>
     * - "uuid": {@code String}, 车辆的 UUID<br>
     * - "position": {"lat": {@code Double}, "lng": {@code Double}}, 车辆新位置
     */ 
    public void moveVehicle(String fullJson)
    {
        JsonMap jsonMap = JsonUtil.toMap(fullJson);
        UUID uuid = UUID.fromString(JsonUtil.getObject(jsonMap, "uuid", String.class));
        double latitude = JsonUtil.getObject(jsonMap, "position/lat", Double.class);
        double longitude = JsonUtil.getObject(jsonMap, "position/lng", Double.class);
        Coordinate position = new Coordinate(latitude, longitude);

        Vehicle veh = vehRepo.findById(uuid).orElseThrow(() -> new EntityNotFoundException());
        veh.setPosition(position);
    }

    public void removeVehicle(UUID uuid)
    {
        vehRepo.deleteById(uuid);
    }
}
