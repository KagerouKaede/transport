package com.tsadmin.transport.service.core;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import com.tsadmin.transport.common.share.Coordinate;
import com.tsadmin.transport.common.util.JsonUtil;
import com.tsadmin.transport.common.util.JsonUtil.JsonMap;
import com.tsadmin.transport.domain.entity.Poi;
import com.tsadmin.transport.repository.PoiRepository;

import jakarta.persistence.EntityNotFoundException;

public class PoiService
{
    @Autowired
    private PoiRepository poiRepo;

    private static UUID toUuid(Coordinate pos)
    {
        byte[] nameBytes = pos.toString().getBytes(StandardCharsets.UTF_8);
        return UUID.nameUUIDFromBytes(nameBytes);
    }

    /**
     * 新增兴趣点并将其加入数据库
     * @param fullJson 兴趣点的初始化配置，应为 Json 对象，对象应有属性如下:<br>
     * - "poi_name": {@code String}, 兴趣点名称<br>
     * - "position": {"lat": {@code Double}, "lng": {@code Double}}, 兴趣点所处位置
     * @return 新兴趣点的 UUID
     */
    public UUID registerPoi(String fullJson)
    {
        JsonMap jsonMap = JsonUtil.toMap(fullJson);
        String name = JsonUtil.getObject(jsonMap, "poi_name", String.class);
        double latitude = JsonUtil.getObject(jsonMap, "position/lat", Double.class);
        double longitude = JsonUtil.getObject(jsonMap, "position/lng", Double.class);
        Coordinate position = new Coordinate(latitude, longitude);

        UUID uuid = toUuid(position);
        if (poiRepo.existsById(uuid)) throw new DuplicateKeyException("POI has already existed with position: " + position.toString());

        Poi poi = new Poi(uuid, name, position);

        poiRepo.save(poi);
        return uuid;
    }

    /**
     * 重命名兴趣点
     * @param fullJson 要修改兴趣点的 Json 对象，应有属性如下:<br>
     * - "uuid": {@code String}, 兴趣点的 UUID<br>
     * - "name": {@code String}, 兴趣点新名称
     */
    public void renamePoi(String fullJson)
    {
        JsonMap jsonMap = JsonUtil.toMap(fullJson);
        UUID uuid = UUID.fromString(JsonUtil.getObject(jsonMap, "uuid", String.class));
        String name = JsonUtil.getObject(jsonMap, "name", String.class);

        Poi poi = poiRepo.findById(uuid).orElseThrow(() -> new EntityNotFoundException());
        poi.setName(name);
    }

    /**
     * 修改兴趣点位置
     * @param fullJson 要修改兴趣点的 Json 对象，应有属性如下:<br>
     * - "uuid": {@code String}, 兴趣点的 UUID<br>
     * - "position": {"lat": {@code Double}, "lng": {@code Double}}, 兴趣点新位置
     * @return 兴趣点的新{@code UUID}
     */
    @Transactional
    public UUID movePoi(String fullJson)
    {
        JsonMap jsonMap = JsonUtil.toMap(fullJson);
        UUID uuid = UUID.fromString(JsonUtil.getObject(jsonMap, "uuid", String.class));
        String posJson = JsonUtil.getObject(jsonMap, "position", String.class);
        Poi poi = poiRepo.findById(uuid).orElseThrow(() -> new EntityNotFoundException());

        String name = poi.getName();
        Map<String, Object> poiMap = Map.of(
            "poi_name", name,
            "position", posJson
        );
        String poiJson = JsonUtil.toJson(poiMap);

        removePoi(uuid);
        return registerPoi(poiJson);
    }

    public void removePoi(UUID uuid)
    {
        poiRepo.deleteById(uuid);
    }
}
