package com.tsadmin.transport.common.util;

import java.util.Map;

import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

@Component
public final class JsonUtil
{
    private static final ObjectMapper OM = new ObjectMapper();

    /**
     * 配置数据类，包含一个配置文件的所有配置，放在一个字典中
     */
    public static class JsonMap
    {
        private Map<String, Object> jsonMap;

        /** 加载 Json 文件中的配置 */
        private JsonMap(String fullJson)
        {
            try
            {
                jsonMap = OM.readValue(fullJson, OM.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
            }
            catch (Exception e)
            {
                throw new RuntimeException("Failed to parse Json: " + fullJson, e);
            }
        }

        /** 获取数据字典的原始数据（只读） */
        private Map<String, ?> raw() { return jsonMap; }
    }

    /**
     * 获取某 Json 对象的单个属性
     * @param <T> {@code type}所指代的类型
     * @param jsonMap 目标配置的{@code JsonMap}对象
     * @param keyPath 键路径，应类似{@code "A/B/C"}，对应{@code {"A":{"B":{"C":...}}}}
     * @param type 指定的数据返回类型
     * @return 路径所对应的值
     * @throws RuntimeException 当路径不合法或未找到对应值
     */
    public static <T> T getObject(JsonMap jsonMap, String path, Class<T> type) throws RuntimeException
    {
        if (path == null || path.isEmpty()) return OM.convertValue(jsonMap.raw(), type);

        String[] pathList = path.split("/");
        Object current = jsonMap.raw();
        for (String segment : pathList)
        {
            if (segment.isEmpty()) continue;
            if (!(current instanceof Map)) throw new RuntimeException("Cannot navigate into non-map value at segment: " + segment);

            Map<?, ?> map = (Map<?, ?>) current;
            current = map.get(segment);
            if (current == null) throw new RuntimeException("Path not found: " + segment);
        }

        return OM.convertValue(current, type);
    }

    public static JsonMap toMap(String fullJson) { return new JsonMap(fullJson); }
    public static String toJson(Map<String, ?> map)
    {
        return OM.writeValueAsString(map);
    }
}

/* 
 * {"sandbox_name":"Sandbox #0","create_time":"2026-01-11 04:22:03","simulation_cycle":0,"configs":{"Main.update_interval":5,"Main.random_seed":1129227483087588537,"Timer.tick_speed":30,"CarManager.car_num":100,"DemandManager.max_demand_per_cycle":30,"ResourcePlant.stock_growth_rate":10,"ProcessPlant.processing_loss":10,"ProcessPlant.processing_speed":5,"Purchaser.purchase_threshold":50,"Market.sales_rate":5,"MultiObjective.enable_total_wait_time":"true","MultiObjective.enable_empty_distance":"true","MultiObjective.enable_vehicle_utilization":"true","MultiObjective.enable_total_weight":"true","MultiObjective.enable_carbon_emission":"true","Event.global_enabled":"true","Event.max_active_events":8,"Event.global_probability":"0.6","Event.weather.enabled":"true","Event.weather.max_count":3,"Event.weather.min_distance":5000,"Event.weather.duration_min":60,"Event.weather.duration_max":180,"Event.traffic_jam.enabled":"true","Event.traffic_jam.probability":"0.05","Event.traffic_jam.max_count":3,"Event.traffic_jam.duration_min":10,"Event.traffic_jam.duration_max":60,"Event.accident.enabled":"true","Event.accident.probability":"0.02","Event.accident.max_count":3,"Event.accident.duration_min":10,"Event.accident.duration_max":45,"Event.road_closure.enabled":"true","Event.road_closure.probability":"0.03","Event.road_closure.max_count":2,"Event.road_closure.duration_min":15,"Event.road_closure.duration_max":90}}
 */