package com.tsAdmin.control;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.jfinal.plugin.activerecord.Record;
import com.jfinal.plugin.activerecord.Db;
import com.tsAdmin.common.ConfigLoader;
import com.tsAdmin.model.Car;
import com.tsAdmin.model.Demand;
import com.tsAdmin.model.poi.Poi;

public class DBManager
{
    private static final Logger logger = LogManager.getLogger(DBManager.class);

    public static int getCount(String tableName)
    {
        String[] allowedTables = {"car", "demand", "poi_stock"};
        boolean isValidTable = false;
        for (String allowed : allowedTables)
        {
            if (allowed.equals(tableName))
            {
                isValidTable = true;
                break;
            }
        }

        if (!isValidTable)
        {
            logger.warn("Invalid table name: {}", tableName);
            return 0;
        }

        String sql = String.format("SELECT COUNT(*) AS count FROM %s WHERE sandbox_UUID = ?", tableName);
        Record record = Db.findFirst(sql, ConfigLoader.getConfigUUID());
        return record != null ? record.getInt("count") : 0;
    }

    /**
     * 获取POI数据列表
     * @return 所有该类型 POI 数据的列表，每一条数据包含 UUID, class, upstream, maxstock, type, name, lat, lon
     */
    public static List<Map<String, Object>> getPoiList()
    {
        try
        {
            Map<String, String> POIs = Map.of(
                "poi_market", "Market",
                "poi_process_plant", "ProcessPlant",
                "poi_resource_plant", "ResourcePlant"
            );
            List<Map<String, Object>> poiList = new ArrayList<>();

            for (String table : POIs.keySet())
            {
                String sql = table == "poi_resource_plant" ?
                ("SELECT location_ID, maxstock, product_type, name, location_lat, location_lon FROM " + table) :
                ("SELECT location_ID, upstream_suppliers, maxstock, product_type, name, location_lat, location_lon FROM " + table);
                List<Record> rawData = Db.find(sql);

                if (rawData == null || rawData.isEmpty()) break;

                for (Record record : rawData)
                {
                    Map<String, Object> element = Map.of(
                        "UUID", record.get("location_ID"),
                        "class", POIs.get(table),
                        "upstream", record.get("upstream_suppliers", ""),
                        "maxstock", record.get("maxstock"),
                        "type", record.get("product_type"),
                        "name", record.get("name"),
                        "lat", record.get("location_lat"),
                        "lon", record.get("location_lon")
                    );
                    poiList.add(element);
                }
            }

            logger.debug("Got POI list from SQL (total {} records)", poiList.size());
            return poiList;
        }
        catch (Exception e)
        {
            logger.error("Failed to get POI list from SQL", e);
            return null;
        }
    }

    public static double getStock(String poiUuid)
    {
        String sql = "SELECT stock FROM poi_stock WHERE sandbox_UUID = ? AND poi_UUID = ? LIMIT 1";
        Record record = Db.findFirst(sql, ConfigLoader.getConfigUUID(), poiUuid);
        return record.getDouble("stock");
    }

    public static List<Map<String, String>> getDemandList()
    {
        try
        {
            List<Map<String, String>> demandList = new ArrayList<>();
            String sql = "SELECT UUID, type, quantity, volume, origin_UUID, destination_UUID FROM demand WHERE sandbox_UUID = ?";
            List<Record> rawData = Db.find(sql, ConfigLoader.getConfigUUID());
            
            if (rawData != null && !rawData.isEmpty())
            {
                for (Record record : rawData)
                {
                    Map<String, String> element = Map.of(
                        "UUID", record.get("UUID"),
                        "type", record.get("type"),
                        "quantity", record.get("quantity").toString(),
                        "volume", record.get("volume").toString(),
                        "origin_UUID", record.get("origin_UUID").toString(),
                        "destination_UUID", record.get("destination_UUID").toString()
                    );
                    demandList.add(element);
                }
            }

            logger.debug("Got demand list from SQL (total {} records)", demandList.size());
            return demandList;
        }
        catch (Exception e)
        {
            logger.error("Failed to get demand list from SQL", e);
            return null;
        }
    }

    public static List<Map<String, Object>> getCarList()
    {
        try
        {
            List<Map<String, Object>> carList = new ArrayList<>();
            String sql = "SELECT * FROM car WHERE sandbox_UUID = ?";
            List<Record> rawData = Db.find(sql, ConfigLoader.getConfigUUID());

            if (rawData != null && !rawData.isEmpty())
            {
                for (Record record : rawData)
                {
                    Map<String, Object> element = new HashMap<>();
                    
                    // 添加所有SQL查询的字段到Map中
                    element.put("UUID", record.get("UUID"));
                    element.put("location_lat", record.get("location_lat"));
                    element.put("location_lon", record.get("location_lon"));
                    element.put("maxload", record.get("maxload"));
                    element.put("maxvolume", record.get("maxvolume"));
                    element.put("load", record.get("load"));
                    element.put("volume", record.get("volume"));
                    element.put("currState", record.get("currState"));
                    element.put("prevState", record.get("prevState"));
                    element.put("waitingTime", record.get("waitingTime"));
                    element.put("emptyDistance", record.get("emptyDistance"));
                    element.put("wastedLoad", record.get("wastedLoad"));
                    element.put("totalWeight", record.get("totalWeight"));
                    element.put("carbonEmission", record.get("carbonEmission"));
                    element.put("totalDistance", record.get("totalDistance"));
                    element.put("completedOrders", record.get("completedOrders"));
                    element.put("averageOrderCycle", record.get("averageOrderCycle"));
                    
                    carList.add(element);
                }
            }

            logger.debug("Got car map from SQL (total {} records)", carList.size());
            return carList;
        }
        catch (Exception e)
        {
            logger.error("Failed to get car list from SQL", e);
            return null;
        }
    }

    public static List<Map<String, String>> getSandboxList()
    {
        try
        {
            List<Map<String, String>> sandboxList = new ArrayList<>();
            String sql = "SELECT UUID, content FROM sandbox";
            List<Record> rawData = Db.find(sql);

            if (rawData != null && !rawData.isEmpty())
            {
                for (Record record : rawData)
                {
                    Map<String, String> element = Map.of(
                        "UUID", record.getStr("UUID"),
                        "content", record.getStr("content")
                    );
                    sandboxList.add(element);
                }
            }

            logger.info("Got sandbox list from SQL (total {} records)", sandboxList.size());
            return sandboxList;
        }
        catch (Exception e)
        {
            logger.error("Failed to get sandbox list from SQL", e);
            return null;
        }
    }

    public static String getSandbox(String uuid)
    {
        try
        {
            String sql = "SELECT content FROM sandbox WHERE UUID = ? LIMIT 1";
            Record record = Db.findFirst(sql, uuid);

            if (record != null)
            {
                String content = record.getStr("content");
                int length = content != null ? content.length() : 0;

                logger.info("Got sandbox(UUID: {}) from SQL, content length: {}", uuid, length);
                return content;
            }
            else
            {
                logger.warn("Sandbox(UUID: {}) not found, null returned", uuid);
                return null;
            }
        }
        catch (Exception e)
        {
            logger.error("Failed to get sandbox(UUID: {}) from SQL", uuid, e);
            return null;
        }
    }

    public static int saveDemandMap(Map<String, Demand> demandMap)
    {
        try
        {
            List<String> uuidInDB = new ArrayList<>();
            String selectSql = "SELECT UUID FROM demand WHERE sandbox_UUID = ?";
            for (Record record : Db.find(selectSql, ConfigLoader.getConfigUUID()))
            {
                uuidInDB.add(record.getStr("UUID"));
            }

            int addLine = 0;
            for (String uuid : demandMap.keySet())
            {
                if (!uuidInDB.contains(uuid))
                {
                    // 数据库中不存在的新订单，存入数据库
                    Demand demand = demandMap.get(uuid);
                    Record demandRecord = new Record();
                    demandRecord.set("UUID", demand.getUUID())
                                .set("sandbox_UUID", ConfigLoader.getConfigUUID())
                                .set("type", demand.getType().name())
                                .set("quantity",demand.getQuantity())
                                .set("volume", demand.getVolume())
                                .set("origin_UUID", demand.getOriginUuid())
                                .set("destination_UUID",demand.getDestinationUuid());
                    addLine += Db.save("demand", demandRecord) ? 1 : 0;
                }
                else
                {
                    // 数据库中已有的历史订单，在数据库中保留，从 uuidInDB 列表中移除
                    uuidInDB.remove(uuid);
                }
            }

            // 删除 uuidInDB 列表中剩余订单，即已完成订单
            int delLine = 0;
            String delSql = "DELETE FROM demand WHERE UUID = ?";
            for (String uuid : uuidInDB)
            {
                delLine += Db.delete(delSql, uuid);
            }

            logger.info("Demand list saved to SQL (inserted {} records, deleted {} records, total {} records)",
                        addLine, delLine, addLine + delLine);
            return addLine + delLine;
        }
        catch (Exception e)
        {
            logger.error("Failed to save demand map to SQL", e);
            return -1;
        }
    }

    public static int saveCarMap(Map<String, Car> carMap)
    {
        try
        {
            List<String> uuidInDB = new ArrayList<>();
            String selectSql = "SELECT UUID FROM car WHERE sandbox_UUID = ?";
            for (Record record : Db.find(selectSql, ConfigLoader.getConfigUUID()))
            {
                uuidInDB.add(record.getStr("UUID"));
            }

            int addLine = 0;
            int updateLine = 0;

            for (Map.Entry<String, Car> entry : carMap.entrySet())
            {
                String uuid = entry.getKey();
                Car car = entry.getValue();

                if (!uuidInDB.contains(uuid))
                {
                    Record carRecord = new Record();
                    carRecord.set("UUID", car.getUUID())
                             .set("sandbox_UUID", ConfigLoader.getConfigUUID())
                             .set("maxload", car.getMaxLoad())
                             .set("maxvolume", car.getMaxVolume());

                    if (car.getPosition() != null)
                    {
                        carRecord.set("location_lat", car.getPosition().lat)
                                 .set("location_lon", car.getPosition().lon);
                    }

                    carRecord.set("load", car.getLoad())
                             .set("volume", car.getVolume())
                             .set("currState", car.getState() != null ? car.getState().toString() : null)
                             .set("prevState", car.getPrevState() != null ? car.getPrevState().toString() : null);

                    if (car.getStatistics() != null)
                    {
                        carRecord.set("waitingTime", car.getStatistics().getWaitingTime())
                                 .set("emptyDistance", car.getStatistics().getEmptyDistance())
                                 .set("wastedLoad", car.getStatistics().getWastedLoad())
                                 .set("totalWeight", car.getStatistics().getTotalWeight())
                                 .set("carbonEmission", car.getStatistics().getCarbonEmission())
                                 .set("totalDistance", car.getStatistics().getTotalDistance())
                                 .set("completedOrders", car.getStatistics().getCompletedOrders())
                                 .set("averageOrderCycle", car.getStatistics().getAverageOrderCycle());
                    }

                    addLine += Db.save("car", carRecord) ? 1 : 0;
                }
                else
                {
                    String updateSql = "UPDATE car SET location_lat = ?, location_lon = ?, load = ?, volume = ?, currState = ?, prevState = ?, waitingTime = ?, emptyDistance = ?, wastedLoad = ?, totalWeight = ?, carbonEmission = ?, totalDistance = ?, completedOrders = ?, averageOrderCycle = ? WHERE UUID = ? AND sandbox_UUID = ?";

                    Object lat = car.getPosition() != null ? car.getPosition().lat : null;
                    Object lon = car.getPosition() != null ? car.getPosition().lon : null;

                    // Use safe access for statistics
                    Object waitingTime = null, emptyDistance = null, wastedLoad = null, totalWeight = null, carbonEmission = null, totalDistance = null, completedOrders = null, averageOrderCycle = null;
                    if (car.getStatistics() != null)
                    {
                        waitingTime = car.getStatistics().getWaitingTime();
                        emptyDistance = car.getStatistics().getEmptyDistance();
                        wastedLoad = car.getStatistics().getWastedLoad();
                        totalWeight = car.getStatistics().getTotalWeight();
                        carbonEmission = car.getStatistics().getCarbonEmission();
                        totalDistance = car.getStatistics().getTotalDistance();
                        completedOrders = car.getStatistics().getCompletedOrders();
                        averageOrderCycle = car.getStatistics().getAverageOrderCycle();
                    }

                    int rows = Db.update(updateSql,
                                         lat,
                                         lon,
                                         car.getLoad(),
                                         car.getVolume(),
                                         car.getState() != null ? car.getState().toString() : null,
                                         car.getPrevState() != null ? car.getPrevState().toString() : null,
                                         waitingTime,
                                         emptyDistance,
                                         wastedLoad,
                                         totalWeight,
                                         carbonEmission,
                                         totalDistance,
                                         completedOrders,
                                         averageOrderCycle,
                                         car.getUUID(),
                                         ConfigLoader.getConfigUUID());

                    updateLine += rows;
                    uuidInDB.remove(uuid);
                }
            }

            logger.info("Car map saved to SQL (inserted {} records, updated {} records)", addLine, updateLine);
            return addLine + updateLine;
        }
        catch (Exception e)
        {
            logger.error("Failed to save car map to SQL", e);
            return -1;
        }
    }

    public static int savePoiStock(Map<String, Poi> PoiMap)
    {
        try
        {
            List<String> uuidInDB = new ArrayList<>();
            String selectSql = "SELECT poi_UUID FROM poi_stock WHERE sandbox_UUID = ?";
            for (Record record : Db.find(selectSql, ConfigLoader.getConfigUUID()))
            {
                uuidInDB.add(record.getStr("poi_UUID"));
            }

            int addLine = 0;
            int updateLine = 0;

            for (Map.Entry<String, Poi> entry : PoiMap.entrySet())
            {
                String uuid = entry.getKey();
                Poi poi = entry.getValue();

                if (!uuidInDB.contains(uuid))
                {
                    Record poiRecord = new Record();
                    poiRecord.set("poi_UUID", uuid)
                             .set("sandbox_UUID", ConfigLoader.getConfigUUID())
                             .set("stock", poi.getStock());
                    addLine += Db.save("poi_stock", poiRecord) ? 1 : 0;
                }
                else
                {
                    String updateSql = "UPDATE poi_stock SET stock = ? WHERE poi_UUID = ? AND sandbox_UUID = ?";
                    int rows = Db.update(updateSql, poi.getStock(), uuid, ConfigLoader.getConfigUUID());
                    updateLine += rows;
                    uuidInDB.remove(uuid);
                }
            }

            logger.info("POI stock saved to SQL (inserted {} records, updated {} records)", addLine, updateLine);
            return addLine + updateLine;
        }
        catch (Exception e)
        {
            logger.error("Failed to save POI stock to SQL", e);
            return -1;
        }
    }

    /**
     * 保存车辆到数据库
     * <p><i>仅在初始化车辆数不足时调用</i>
     * @param car 要保存的车辆
     * @return 此次保存成功与否
     */
    @Deprecated
    public static boolean saveCar(Car car)
    {
        Record carRecord = new Record();
        carRecord.set("UUID", car.getUUID())
                 .set("maxLoad", car.getMaxLoad())
                 .set("maxVolume", car.getMaxVolume())
                 .set("location_lat", car.getPosition().lat)
                 .set("location_lon", car.getPosition().lon);
        return Db.save("car", carRecord);
    }

    /**
     * 保存沙箱到数据库
     * @param isNew 是否为新沙箱
     * @param uuid 将保存沙箱的 UUID
     * @param content 沙箱内容，格式同resources/config.json
     * @return 此次保存操作成功与否
     */
    public static boolean saveSandbox(boolean isNew, String uuid, String content)
    {
        try
        {
            boolean success = false;

            if (isNew)
            {
                Record sandboxRecord = new Record();
                sandboxRecord.set("UUID", uuid)
                            .set("content", content);
                success = Db.save("sandbox", sandboxRecord);
            }
            else
            {
                String updateSql = "UPDATE sandbox SET content = ? WHERE UUID = ?";
                success = Db.update(updateSql, content, uuid) > 0;
            }

            logger.debug("Saved sandbox(UUID: {}), success status: {}", uuid, success);
            return success;
        }
        catch (Exception e)
        {
            logger.error("Failed to save sandbox(UUID: {})", uuid, e);
            return false;
        }
    }

    public static boolean removeSandbox(String uuid)
    {
        try
        {
            boolean success = false;
            String sql = "DELETE FROM sandbox WHERE UUID = ?";
            success = Db.delete(sql, uuid) > 0;

            logger.debug("Removed sandbox(UUID: {}), success status: {}", uuid, success);
            return success;
        }
        catch (Exception e)
        {
            logger.error("Failed to remove sandbox(UUID: {})", uuid, e);
            return false;
        }
    }

    public static boolean saveStatisticsToCarDB(Car car)
    {
        try
        {
            String sql = "UPDATE car SET location_lat = ?, location_lon = ?,load = ?,currState = ?, prevState = ?, waitingTime = ?, emptyDistance = ?, wastedLoad = ?, totalWeight = ?, carbonEmission = ?, totalDistance = ?, completedOrders = ?, averageOrderCycle = ? WHERE UUID = ? AND sandbox_UUID = ?";
            int rowsAffected = Db.update(sql,
                car.getPosition().lat,
                car.getPosition().lon,
                car.getLoad(),
                car.getState().toString(),
                car.getPrevState().toString(),
                car.getStatistics().getWaitingTime(),
                car.getStatistics().getEmptyDistance(),
                car.getStatistics().getWastedLoad(),
                car.getStatistics().getTotalWeight(),
                car.getStatistics().getCarbonEmission(),
                car.getStatistics().getTotalDistance(),
                car.getStatistics().getCompletedOrders(),
                car.getStatistics().getAverageOrderCycle(),
                car.getUUID(),
                ConfigLoader.getConfigUUID()
            );
            logger.debug("Updated car statistics for UUID: {}, rows affected: {}", car.getUUID(), rowsAffected);
            return rowsAffected > 0;
        }
        catch (Exception e)
        {
            logger.error("Failed to save statistics to car DB for UUID: {}", car.getUUID(), e);
            return false;
        }
    }

    /**
     * 保存数据到 sandbox 表
     * 使用 ConfigLoader.getConfigUUID() 作为 UUID，如果记录存在则更新，不存在则插入
     */
    public static boolean saveToSandbox(Double loadUtilizationRateMean,
                                       Double loadUtilizationRateVariance,
                                       Double capacityUtilizationRateMean,
                                       Double capacityUtilizationRateVariance,
                                       Double ontimeDeliveryRate,
                                       Double totalDelayTime,
                                       Double averageDelayTime,
                                       Double averageOrderCycle,
                                       Double systemCriticalLoad)
    {
        try
        {
            String uuid = ConfigLoader.getConfigUUID();

            // 先查询是否存在该 UUID 的记录
            String selectSql = "SELECT UUID FROM sandbox WHERE UUID = ? LIMIT 1";
            Record existingRecord = Db.findFirst(selectSql, uuid);
            
            if (existingRecord != null)
            {
                // 记录存在，执行更新操作
                StringBuilder updateSql = new StringBuilder("UPDATE sandbox SET ");
                List<Object> params = new ArrayList<>();
                boolean hasUpdate = false;
                                
                if (loadUtilizationRateMean != null)
                {
                    if (hasUpdate) updateSql.append(", ");
                    updateSql.append("load_utilization_rate_mean = ?");
                    params.add(loadUtilizationRateMean);
                    hasUpdate = true;
                }
                
                if (loadUtilizationRateVariance != null)
                {
                    if (hasUpdate) updateSql.append(", ");
                    updateSql.append("load_utilization_rate_variance = ?");
                    params.add(loadUtilizationRateVariance);
                    hasUpdate = true;
                }
                
                if (capacityUtilizationRateMean != null)
                {
                    if (hasUpdate) updateSql.append(", ");
                    updateSql.append("capacity_utilization_rate_mean = ?");
                    params.add(capacityUtilizationRateMean);
                    hasUpdate = true;
                }
                
                if (capacityUtilizationRateVariance != null)
                {
                    if (hasUpdate) updateSql.append(", ");
                    updateSql.append("capacity_utilization_rate_variance = ?");
                    params.add(capacityUtilizationRateVariance);
                    hasUpdate = true;
                }
                
                if (ontimeDeliveryRate != null)
                {
                    if (hasUpdate) updateSql.append(", ");
                    updateSql.append("ontime_delivery_rate = ?");
                    params.add(ontimeDeliveryRate);
                    hasUpdate = true;
                }
                
                if (totalDelayTime != null)
                {
                    if (hasUpdate) updateSql.append(", ");
                    updateSql.append("total_delay_time = ?");
                    params.add(totalDelayTime);
                    hasUpdate = true;
                }
                
                if (averageDelayTime != null)
                {
                    if (hasUpdate) updateSql.append(", ");
                    updateSql.append("average_delay_time = ?");
                    params.add(averageDelayTime);
                    hasUpdate = true;
                }
                
                if (averageOrderCycle != null)
                {
                    if (hasUpdate) updateSql.append(", ");
                    updateSql.append("average_order_cycle = ?");
                    params.add(averageOrderCycle);
                    hasUpdate = true;
                }
                
                if (systemCriticalLoad != null)
                {
                    if (hasUpdate) updateSql.append(", ");
                    updateSql.append("system_critical_load = ?");
                    params.add(systemCriticalLoad);
                    hasUpdate = true;
                }
                
                if (!hasUpdate)
                {
                    logger.debug("No fields to update for sandbox UUID: {}", uuid);
                    return true;
                }
                
                updateSql.append(" WHERE UUID = ?");
                params.add(uuid);
                
                int rowsAffected = Db.update(updateSql.toString(), params.toArray());
                logger.debug("Updated sandbox table, UUID: {}, rows affected: {}", uuid, rowsAffected);
                return rowsAffected > 0;
            }
            else
            {
                // 记录不存在，执行插入操作
                Record sandboxRecord = new Record();
                sandboxRecord.set("UUID", uuid);
                
                if (loadUtilizationRateMean != null)
                    sandboxRecord.set("load_utilization_rate_mean", loadUtilizationRateMean);
                if (loadUtilizationRateVariance != null)
                    sandboxRecord.set("load_utilization_rate_variance", loadUtilizationRateVariance);
                if (capacityUtilizationRateMean != null)
                    sandboxRecord.set("capacity_utilization_rate_mean", capacityUtilizationRateMean);
                if (capacityUtilizationRateVariance != null)
                    sandboxRecord.set("capacity_utilization_rate_variance", capacityUtilizationRateVariance);
                if (ontimeDeliveryRate != null)
                    sandboxRecord.set("ontime_delivery_rate", ontimeDeliveryRate);
                if (totalDelayTime != null)
                    sandboxRecord.set("total_delay_time", totalDelayTime);
                if (averageDelayTime != null)
                    sandboxRecord.set("average_delay_time", averageDelayTime);
                if (averageOrderCycle != null)
                    sandboxRecord.set("average_order_cycle", averageOrderCycle);
                if (systemCriticalLoad != null)
                    sandboxRecord.set("system_critical_load", systemCriticalLoad);
                
                boolean success = Db.save("sandbox", sandboxRecord);
                logger.debug("Inserted data to sandbox table, UUID: {}, success: {}", uuid, success);
                return success;
            }
        }
        catch (Exception e)
        {
            logger.error("Failed to save data to sandbox table", e);
            return false;
        }
    }    

    /* ================== 以下内容会导致模拟时无法保证情况相同，暂时废弃 ================== */

    @Deprecated
    public static void updateCarPos(Car car)
    {
        String sql = "UPDATE car SET location_lat = ?, location_lon = ? WHERE UUID = ?";
        Db.update(sql, car.getPosition().lat, car.getPosition().lon, car.getUUID());
    }

    @Deprecated
    public static void updateCarState(Car car)
    {
        String sql = "UPDATE car SET state = ?, prevState = ? WHERE UUID = ?";
        Db.update(sql, car.getState().toString(), car.getPrevState().toString(), car.getUUID().toString());
    }

    @Deprecated
    public static void updateCarTime(Car car)
    {
        String sql = "UPDATE car SET time = ? WHERE UUID = ?";
        Db.update(sql, car.getStateTimer().getTime(), car.getUUID());
    }

    /**
     * 在数据库中更新订单剩余质量
     * @param demand 更新的订单
     */
    @Deprecated
    public static void updateDemandQuantity(Demand demand)
    {
        String sql = "UPDATE demand SET quantity = ? WHERE UUID = ?";
        Db.update(sql, demand.getQuantity(), demand.getUUID());
    }

    /**
     * 从数据库删除订单
     * @param demand 要删除订单
     * @return {@code true} 如果删除成功
     */
    @Deprecated
    public static boolean rmvDemand(Demand demand)
    {
        try
        {
            String sql = "DELETE FROM demand WHERE UUID = ?";
            Db.update(sql, demand.getUUID());
            return true;
        }
        catch (Exception e)
        {
            logger.error("Failed to remove demand from database", e);
            return false;
        }
    }

    // /**根据关键字和城市搜索POI点并插入数据库 */
    // public static void insertIntoDB(String keywords) 
    // {
    //     String[] cities= {"成都市","自贡市","攀枝花市","泸州市","德阳市","绵阳市","广元市","遂宁市","内江市","乐山市","南充市","眉山市","宜宾市","广安市","达州市","雅安市","巴中市","资阳市","阿坝州","甘孜州","凉山州"};
    //     for(String city:cities){
    //         String RequestURL="https://restapi.amap.com/v3/place/text?"
    //                 + "keywords="+keywords+"&city="+city+"&offset=100&key=3a58ca26430baffeffba0a4e1698f51a&extensions=base"; 
    //         String resText=HttpKit.get(RequestURL);
    //         //解析并存储到数据库
    //         JSONObject resOBJ=JSONObject.parseObject(resText);//把文本格式的json字符转为对象，方便取值
    //         if(resOBJ.getJSONArray("pois")==null) continue;//如果没有pois数组，跳过
    //         JSONArray pois=resOBJ.getJSONArray("pois");//拿到JSON里的pois数组
            
    //         String tablename = "pharmaceutical_market";
    //         String tablename2 = "pharmaceutical_producer";
    //         String tablename3 = "pharmaceutical_processor";
    //         //pois中，数据是数组形式，用循环解析
    //         for(int i=0;i<100 && i<pois.size();i++) 
    //         {
    //             String objString=pois.get(i).toString();//第i个对象转为字符串
    //             JSONObject singleObj=JSONObject.parseObject(objString);//再把对象由String转为jsonObj
    //             String locationID=singleObj.getString("id");//ID
    //             String POI_pname=singleObj.get("pname").toString();//省
    //             String POI_cityname=singleObj.get("cityname").toString();//市
    //             String POI_location=singleObj.get("location").toString();
    //             String POI_lat=POI_location.split(",")[1];//,之后的为纬度
    //             String POI_lon=POI_location.split(",")[0];//,之前的为经度
    //             String POI_name=singleObj.get("name").toString();//具体名称
    //             //调试输出一下试试
    //             System.out.println(i+"  location_ID  "+locationID+"  地点是  "+POI_name+"  纬度是  "+POI_lat+"  经度是  "+POI_lon);
    //             //经过调试，可以解析到这些数据，把这些数据存放到数据库中，注意如果数据库组已经存在该地点，则不用再添加
    //             try 
    //             {
    //                 if(!(ifexist(tablename,locationID)||ifexist(tablename2, locationID)||ifexist(tablename3, locationID))) //不存在时插入数据
    //                 {
    //                     //插入数据
    //                     Record e_poiRecord=new Record();
    //                     e_poiRecord.set("location_ID", locationID).set("pname", POI_pname).set("cityname", POI_cityname);
    //                     e_poiRecord.set("location_lat",POI_lat).set("location_lon",POI_lon).set("name", POI_name);
    //                     Db.save(tablename,e_poiRecord);
    //                 }
    //             }
    //             catch(Exception e) 
    //             {
    //                 e.printStackTrace();  // 打印异常信息
    //             }
    //         }
    //     }
    // }

    // /**POI点是否已存在于数据库 */
    // public static boolean ifexist(String table,String locationID) 
    // {
    //     List<Record> results = Db.find("SELECT * FROM " + table + " WHERE location_id = ?", locationID);
    //     // 检查结果集是否为空
    //     if (!results.isEmpty()) 
    //     {
    //         return true;//表示已经存在
    //     } else 
    //     {
    //         return false;//表示不存在
    //     }
    // }

    // /** 厂商类 */
    // private static class Supplier
    // {
    //     String id;
    //     Coordinate coordinate;
        
    //     Supplier(String id, Coordinate coordinate)
    //     {
    //         this.id = id;
    //         this.coordinate = coordinate;
    //     }
    // }

    // /**
    //  * 插入上游厂商ID
    //  * 虽然这种多对多的关系规范做法是建立关系表，但为了简化数据库设计，这里直接将上游供应商ID列表存储在下游表的upstream_suppliers字段中
    //  */
    // public static void insert_upstream_suppliers(String downstream_table,int n)
    // {
    //     Map<String, String> downstream_upstream = Map.of(
    //         "pharmaceutical_market", "pharmaceutical_processor",
    //         "pharmaceutical_processor", "pharmaceutical_producer",
    //         "steel_processor", "steel_processor",
    //         "steel_processor", "steel_producer",
    //         "wood_processor", "wood_processor",
    //         "wood_processor", "wood_producer"
    //     );
    //     String upstream_table = downstream_upstream.get(downstream_table);
    //     if (upstream_table == null)
    //     {
    //         System.err.println("错误: 找不到下游表 " + downstream_table + " 对应的上游表");
    //         return;
    //     }
        
    //     System.out.println("开始为 " + downstream_table + " 表匹配上游供应商，上游表: " + upstream_table);
        
    //     try {
    //         // 1. 获取所有下游厂商数据
    //         List<Supplier> downstreamSuppliers = getSuppliers(downstream_table);
    //         System.out.println("获取到 " + downstreamSuppliers.size() + " 个下游厂商");
            
    //         // 2. 获取所有上游厂商数据
    //         List<Supplier> upstreamSuppliers = getSuppliers(upstream_table);
    //         System.out.println("获取到 " + upstreamSuppliers.size() + " 个上游厂商");
            
    //         // 如果上游厂商数量为0，直接返回
    //         if (upstreamSuppliers.isEmpty()) {
    //             System.out.println("警告: 上游表 " + upstream_table + " 中没有数据");
    //             return;
    //         }
            
    //         // 3. 为每个下游厂商匹配最近的n个上游厂商
    //         int updatedCount = 0;
    //         for (Supplier ds : downstreamSuppliers) {
    //             List<String> nearestUpstreamIds = findNearestSuppliers(ds.coordinate, upstreamSuppliers, n);
    //             updateDownstreamSupplier(downstream_table, ds.id, nearestUpstreamIds);
    //             updatedCount++;
                
    //             // 每处理10个打印一次进度
    //             if (updatedCount % 10 == 0) {
    //                 System.out.println("已处理 " + updatedCount + "/" + downstreamSuppliers.size() + " 个下游厂商");
    //             }
    //         }
            
    //         System.out.println("完成! 共为 " + updatedCount + " 个下游厂商匹配了上游供应商");
            
    //     } catch (SQLException e) {
    //         System.err.println("数据库操作失败: " + e.getMessage());
    //         e.printStackTrace();
    //     }
    // }
    // /**
    //  * 获取厂商列表
    //  */
    // private static List<Supplier> getSuppliers(String tableName) throws SQLException {
    //     List<Supplier> suppliers = new ArrayList<>();
    //     String query = "SELECT location_ID, location_lat, location_lon FROM " + tableName;
    //     List<Record> rawData = Db.find(query);
    //     for (Record record : rawData) {
    //         String id = record.getStr("location_ID");
    //         Double lat = record.getDouble("location_lat");
    //         Double lon = record.getDouble("location_lon");
            
    //         // 处理可能为 null 的情况
    //         if (lat == null || lon == null || lat == 0.0 && lon == 0.0)
    //         {
    //             System.out.println("警告: 跳过ID=" + id + " 的无效坐标记录");
    //             continue;
    //         }
            
    //         suppliers.add(new Supplier(id, new Coordinate(lat, lon)));
    //     }
        
    //     return suppliers;
    // }
    // /**
    //  * 供应商距离类（用于优先队列）
    //  */
    // private static class SupplierDistance {
    //     String supplierId;
    //     double distance;
        
    //     SupplierDistance(String supplierId, double distance) {
    //         this.supplierId = supplierId;
    //         this.distance = distance;
    //     }
    // }

    // /**
    //  * 查找距离最近的上游供应商
    //  */
    // private static List<String> findNearestSuppliers(Coordinate target, 
    //                                                   List<Supplier> upstreamSuppliers, 
    //                                                   int n) {
    //     // 使用优先队列（大顶堆）来维护最近的n个供应商
    //     PriorityQueue<SupplierDistance> maxHeap = new PriorityQueue<>((a, b) -> 
    //         Double.compare(b.distance, a.distance));
        
    //     for (Supplier us : upstreamSuppliers) {
    //         double distance = Coordinate.distance(target, us.coordinate);
            
    //         if (maxHeap.size() < n) {
    //             maxHeap.offer(new SupplierDistance(us.id, distance));
    //         } else if (distance < maxHeap.peek().distance) {
    //             maxHeap.poll(); // 移除最远的
    //             maxHeap.offer(new SupplierDistance(us.id, distance));
    //         }
    //     }
        
    //     // 将结果转换为ID列表
    //     List<String> result = new ArrayList<>();
    //     while (!maxHeap.isEmpty()) {
    //         result.add(0, maxHeap.poll().supplierId); // 按距离从小到大排序
    //     }        
    //     return result;
    // }

    // /**
    //  * 更新下游厂商的上游供应商列表
    //  */
    // private static void updateDownstreamSupplier(String table, String downstreamId, 
    //                                              List<String> upstreamIds) throws SQLException {
    //     // 将ID列表转换为JSON数组字符串
    //     String jsonString = JSONArray.toJSONString(upstreamIds);
        
    //     String updateSQL = "UPDATE " + table + " SET upstream_suppliers = ? WHERE location_ID = ?";
    //     Db.update(updateSQL, jsonString, downstreamId);
    // }
}
