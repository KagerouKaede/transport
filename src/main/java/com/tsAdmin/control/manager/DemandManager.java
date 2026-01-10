package com.tsAdmin.control.manager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.tsAdmin.common.ConfigLoader;
import com.tsAdmin.control.DBManager;
import com.tsAdmin.model.Demand;
import com.tsAdmin.model.Product;
import com.tsAdmin.model.ProductType;
import com.tsAdmin.model.poi.*;

public class DemandManager
{
    private static Map<String, Demand> demandMap = new HashMap<>();
    private static int MAX_DEMAND_PER_CYCLE = 0;
    private static int demandThisCycle = 0;

    public static void init()
    {
        demandMap.clear();

        MAX_DEMAND_PER_CYCLE = ConfigLoader.getInt("DemandManager.max_demand_per_cycle");

        if (DBManager.getCount("demand") <= 0) return;

        List<Map<String, String>> dataList = DBManager.getDemandList();
        for (Map<String, String> data : dataList)
        {
            String uuid = data.get("UUID");
            Poi origin = PoiManager.poiList.get(data.get("origin_UUID"));
            Poi destination = PoiManager.poiList.get(data.get("destination_UUID"));

            ProductType type = ProductType.valueOf(data.get("type"));
            int quantity = Integer.parseInt(data.get("quantity"));
            int volume = Integer.parseInt(data.get("volume"));
            Product product = new Product(type, quantity, volume);

            Demand demand = new Demand(uuid, origin, destination, product);
            demandMap.put(uuid, demand);
        }
    }

    public static void onStop() { DBManager.saveDemandMap(demandMap); }

    /**
     * 生成新的订单并自动将其加入订单表
     * @param origin 起点，必须是 {@code Dumper} 的实现
     * @param destination 终点，必须是 {@code Purchaser} 或其子类，兴趣点调用时一般为 {@code this}
     * @param quantity 需求的质量
     * @return 生成的订单
     */
    public static Demand generateDemand(Poi origin, Poi destination, int quantity)
    {
        Product product = ((Dumper)origin).packProduct(quantity);
        String uuid = UUID.randomUUID().toString().replace("-", "");

        Demand demand = new Demand(uuid, origin, destination, product);

        demandThisCycle++;
        demandMap.put(uuid, demand);
        return demand;
    }

    public static boolean allowNewDemand() { return MAX_DEMAND_PER_CYCLE > demandThisCycle; }
    public static void resetDemandThisCycle() { demandThisCycle = 0; }

    /** 获取所有订单组成的列表<p><b>对其的修改不会同步到本类内的列表</b> */
    public static List<Demand> getList() { return new ArrayList<>(demandMap.values()); }
    public static void removeDemand(String uuid) { demandMap.remove(uuid); }
    public static boolean isEmpty() { return demandMap.isEmpty(); }
}
