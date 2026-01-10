package com.tsAdmin.control.manager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.tsAdmin.common.ConfigLoader;
import com.tsAdmin.common.Coordinate;
import com.tsAdmin.control.DBManager;
import com.tsAdmin.model.ProductType;
import com.tsAdmin.model.poi.*;

public class PoiManager
{
    private static final Logger logger = LogManager.getLogger(PoiManager.class);

    public static Map<String, Poi> poiList = new HashMap<>();

    public static Poi getPoi(String uuid) { return poiList.get(uuid); }

    public static void init()
    {
        poiList.clear();

        ResourcePlant.setStockGrowthRate(ConfigLoader.getInt("ResourcePlant.stock_growth_rate"));
        ProcessPlant.setProcessingLoss(ConfigLoader.getInt("ProcessPlant.processing_loss"));
        ProcessPlant.setProcessingSpeed(ConfigLoader.getInt("ProcessPlant.processing_speed"));
        Purchaser.setPurchaseThreshold(ConfigLoader.getInt("Purchaser.purchase_threshold"));
        Market.setSalesRate(ConfigLoader.getInt("Market.sales_rate"));

        List<Map<String, Object>> dataList = DBManager.getPoiList();

        for (Map<String, Object> data : dataList)
        {
            String uuid = (String)data.get("UUID");
            String name = (String)data.get("name");
            ProductType productType = ProductType.valueOf((String)data.get("type"));
            Coordinate position = new Coordinate(
                (double)data.get("lat"),
                (double)data.get("lon")
            );
            int maxStock = (int)data.get("maxstock");

            Poi toAdd = switch ((String)data.get("class"))
            {
                case "ResourcePlant" -> new ResourcePlant(uuid, name, productType, position, maxStock);
                case "ProcessPlant" -> new ProcessPlant(uuid, name, productType, position, maxStock);
                case "Market" -> new Market(uuid, name, productType, position, maxStock);
                default -> null;
            };
            if (toAdd == null) throw new IllegalArgumentException();

            // 为 Purchaser 增加上游兴趣点
            if (toAdd instanceof Purchaser)
            {
                for (String upstreamUuid : data.get("upstream").toString().split(","))
                {
                    ((Purchaser)toAdd).addUpstream(upstreamUuid);
                }
            }

            poiList.put(uuid, toAdd);
        }

        // 初始化兴趣点初始库存
        if (DBManager.getCount("poi_stock") > 0)
        {
            for (Poi poi : poiList.values())
            {
                double stock = DBManager.getStock(poi.getUUID());
                poi.setStock(stock);
            }
        }
        else
        {
            for (Poi poi : poiList.values())
            {
                double stock = poi.getProductType().getMaxQuantity() * 5;
                poi.setStock(stock);
            }
        }
    }

    public static void onStop() { DBManager.savePoiStock(poiList); }

    /** 更新所有兴趣点，每周期调用 */
    public static void update()
    {
        try
        {
            DemandManager.resetDemandThisCycle();

            List<String> uuidList = new ArrayList<>(poiList.keySet());

            // 操作量不大，使用同步操作更简单
            for (String uuid : uuidList)
            {
                logger.debug("uuid:{}, lat: {}", uuid, poiList.get(uuid).getPosition().lat);
                poiList.get(uuid).update();
            }

            logger.trace("POI updating completed");
        }
        catch (Exception e)
        {
            logger.error("Failed to update all POIs", e);
        }
    }
}
