package com.tsadmin.transport.service.core;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;

import com.tsadmin.transport.common.enums.ProductType;
import com.tsadmin.transport.common.util.JsonUtil;
import com.tsadmin.transport.common.util.JsonUtil.JsonMap;
import com.tsadmin.transport.domain.entity.Demand;
import com.tsadmin.transport.domain.entity.Poi;
import com.tsadmin.transport.domain.entity.Product;
import com.tsadmin.transport.repository.DemandRepository;
import com.tsadmin.transport.repository.PoiRepository;

public class DemandService
{
    @Autowired
    private DemandRepository demRepo;
    @Autowired
    private PoiRepository poiRepo;

    /**
     * 新增订单并将其加入数据库
     * @param fullJson 订单的初始化配置，应为 Json 对象，对象应有属性如下:<br>
     * - "origin_uuid": {@code String}, 起点兴趣点的 UUID<br>
     * - "destination_uuid": {@code String}, 终点兴趣点的 UUID<br>
     * - "product": {"type": {@code String}, "quantity": {@code Double}, "volume": {@code Double}}, 订单对应产品属性<br>
     * "product/type"对应字符串应与{@link ProductType}相应枚举变量一致
     * @return 新兴趣点的 UUID
     */
    public UUID createDemand(String fullJson)
    {
        JsonMap jsonMap = JsonUtil.toMap(fullJson);
        UUID originUuid = UUID.fromString(JsonUtil.getObject(jsonMap, "origin_uuid", String.class));
        UUID destUuid = UUID.fromString(JsonUtil.getObject(jsonMap, "destination_uuid", String.class));
        ProductType type = ProductType.valueOf(JsonUtil.getObject(jsonMap, "product/type", String.class));
        double quantity = JsonUtil.getObject(jsonMap, "product/quantity", Double.class);
        double volume = JsonUtil.getObject(jsonMap, "product/volume", Double.class);

        Poi origin = poiRepo.getReferenceById(originUuid);
        Poi destination = poiRepo.getReferenceById(destUuid);
        Product product = new Product(type, quantity, volume);

        Demand dem = new Demand(origin, destination, product);
        Demand saved = demRepo.save(dem);
        return saved.getUUID();
    }

    public void completeDemand(UUID uuid)
    {
        demRepo.deleteById(uuid);   // TODO
    }

    public void cancelDemand(UUID uuid)
    {
        demRepo.deleteById(uuid);   // TODO
    }
}
