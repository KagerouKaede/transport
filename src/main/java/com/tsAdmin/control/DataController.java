package com.tsAdmin.control;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.jfinal.core.Controller;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tsAdmin.common.PathNode;
import com.tsAdmin.control.manager.CarManager;
import com.tsAdmin.model.Car;
import com.tsAdmin.model.CarStatistics;
import com.tsAdmin.model.Car.CarState;

/**
 * 数据控制器
 * 主要处理前端发出的请求，返回JSON数据
 */
public class DataController extends Controller
{
    int StateChangeTimes = 0, FreezeTimes = 0;
    double totalDelayTime = 0;
    int totalDelaytTime = 0;
    private static final Logger logger = LogManager.getLogger(DataController.class);
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private void reply(boolean success, String message)
    {
        renderJson(Map.of("success", success, "message", message));
    }

    /**
     * 获取所有兴趣点数据
     * <p>返回数据格式：[{"UUID":{@code String},"class":{@code String},"type":{@code String}, "name":{@code String}, "lat":{@code Double}, "lon":{@code Double}}, {...}, ...]
     * 其中 {@code class} 值可能为 {@code ResourcePlant, ProcessPlant, Market} ；{@code type} 值可能为 {@code "WOOD", "STEEL", "PHARMA"}
     */
    public void getPoiData()
    {
        List<Map<String, Object>> dataList = DBManager.getPoiList();
        try {
            renderJson(JSON_MAPPER.writeValueAsString(dataList));
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize poi data to JSON", e);
            renderJson("{}");
        }
    }

    /**
     * 获取所有车辆数据
     * <p>返回数据格式：[{"UUID":{@code String}, "type":{@code String}, "maxload":{@code Integer}, "maxvolume":{@code Integer}, "lat":{@code Double}, "lon":{@code Double}}, {...}, ...]
     */
    public void getCarData()
    {
        List<Map<String, Object>> posList = DBManager.getCarList();
        try {
            renderJson(JSON_MAPPER.writeValueAsString(posList));
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize car data to JSON", e);
            renderJson("{}");
        }
    }

    /**
     * 接收前端目标选择信号，从 Pareto 前沿中选择最优解
     */
    // public void selectParetoSolution() {
    //     // 1. 获取前端传来的布尔数组
    //     String[] objStrs = getParaValues("objectives");
    //     if (objStrs == null || objStrs.length != 5) {
    //         renderJson(Map.of("code", 400, "msg", "需提供5个目标的选择信号"));
    //         return;
    //     }

    //     boolean[] selected = new boolean[5];
    //     for (int i = 0; i < 5; i++) {
    //         selected[i] = "true".equals(objStrs[i]) || "1".equals(objStrs[i]);
    //     }

    //     // 2. 获取 MOSAScheduler 实例
    //     MOSAScheduler mosa = DataUpdater.getScheduler();
    //     if (mosa == null) {
    //         renderJson(Map.of("code", 500, "msg", "MOSA 调度器未运行"));
    //         return;
    //     }

    //     // 3. 执行理想点法筛选
    //     List<Assignment> bestSolution = mosa.selectSolutionByObjectives(selected);
    //     if (bestSolution == null || bestSolution.isEmpty()) {
    //         renderJson(Map.of("code", 404, "msg", "无可用解"));
    //         return;
    //     }

    //     // 4. 同步新解到车辆，并更新 CarStatistics
    //     mosa.syncAssignmentsToCars(bestSolution, CarManager.carMap.values());
    //     mosa.updateCarStats(bestSolution); // 你已实现的方法

    //     // 5. 返回成功
    //     renderJson(Map.of("code", 200, "msg", "Pareto 解已更新"));
    // }
    /**
     * 获取每辆车的仪表盘数据
     */
    public void getDashboardData()
    {
        String uuid = getPara("UUID");
        //Double cycleCost = 0.0;
        
        Car car = CarManager.carMap.get(uuid);
        if (car == null) {
            renderJson((Object) null);
            return;
        }

        CarStatistics statistics = car.getStatistics();
        if (statistics != null) {
            statistics.calculateLoad_utilization_rate(car);
            statistics.calculateCapacity_utilization_rate(car);
            statistics.calculateAverageOrderCycle();
        }

        // 返回给前端的字段使用前端期望的命名（数值保持原生类型）
        Map<String, Object> data = new HashMap<>();
        data.put("UUID", car.getUUID());
        // data.put("position_lat", car.getPosition() != null ? car.getPosition().lat : null);
        // data.put("position_lon", car.getPosition() != null ? car.getPosition().lon : null);
        data.put("state", car.getState() != null ? car.getState().toString() :"AVAILABLE");
        data.put("load", car.getLoad());

        if (statistics != null) {
            data.put("waitingTime", statistics.getWaitingTime());
            data.put("emptyDistance", statistics.getEmptyDistance());
            data.put("wastedLoad", statistics.getWastedLoad());
            data.put("totalWeight", statistics.getTotalWeight());
            data.put("carbonEmission", statistics.getCarbonEmission());
            data.put("totalDistance", statistics.getTotalDistance());
            data.put("completedOrders", statistics.getCompletedOrders());
            // completeOrderCycle 没有公开 getter，返回 averageOrderCycle 供前端展示
            data.put("completeOrderCycle", statistics.getAverageOrderCycle());
            data.put("averageOrderCycle", statistics.getAverageOrderCycle());

            // 按前端约定的下划线命名返回计算指标
            data.put("mileage_utilization_rate", statistics.getMileageUtilizationRate());
            data.put("carbon_emission_per_unit", statistics.getCarbonEmissionPerUnit());
            data.put("load_utilization_rate", statistics.getLoad_utilization_rate());
            data.put("capacity_utilization_rate", statistics.getCapacity_utilization_rate());
        }

        // 保存统计数据到数据库（保持现有行为）
        try {
            DBManager.saveStatisticsToCarDB(car);
        } catch (Exception e) {
            logger.warn("saveStatisticsToCarDB failed for {}: {}", uuid, e.getMessage());
        }

        // 直接返回对象 JSON（不再把 JSON 再包成字符串），减少前端解析复杂度
        renderJson(data);
    }

    /**
     * 获取所有车辆的统计数据，返回全部车辆的统计指标均值和方差
     */
    public void getAllCarsStatistics()
    {
        int availableCount = 0, malfunctionCount = 0;
        List<Double> loadRates = new ArrayList<>();
        List<Double> capacityRates = new ArrayList<>();

        for (Car car : CarManager.carMap.values()) {
            CarStatistics stats = car.getStatistics();
            if (stats != null) {
                stats.calculateLoad_utilization_rate(car);
                stats.calculateCapacity_utilization_rate(car);
                loadRates.add(stats.getLoad_utilization_rate());
                capacityRates.add(stats.getCapacity_utilization_rate());
            }
            if (car.getState() == CarState.AVAILABLE) availableCount++;
            else if (car.getState() == CarState.FREEZE) malfunctionCount++;
        }

        double loadMean = loadRates.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double loadVariance = loadRates.stream().mapToDouble(x -> Math.pow(x - loadMean, 2)).average().orElse(0.0);
        double capacityMean = capacityRates.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double capacityVariance = capacityRates.stream().mapToDouble(x -> Math.pow(x - capacityMean, 2)).average().orElse(0.0);

        Map<String, Object> data = new HashMap<>();
        data.put("availableCount", availableCount);
        data.put("malfunctionCount", malfunctionCount);
        data.put("loadUtilizationRateMean", loadMean);
        data.put("loadUtilizationRateVariance", loadVariance);
        data.put("capacityUtilizationRateMean", capacityMean);
        data.put("capacityUtilizationRateVariance", capacityVariance);

        // 保存统计数据到数据库（保留原有 sandbox 写入）
        DBManager.saveToSandbox(loadMean,
                               loadVariance,
                               capacityMean,
                               capacityVariance,
                               null, null, null, null, null);

        renderJson(data);
    }

    /**获取服务质量指标 */
    public void getServiceQualityMetrics()
    {
        if (StateChangeTimes == 0) StateChangeTimes = 1; // 防止除以零
        double ontimeDeliveryRate = FreezeTimes / ((double) StateChangeTimes / 5);

        double totalDelay = totalDelayTime;
        if (FreezeTimes == 0) FreezeTimes = 1; // 防止除以零
        double averageDelayTime = totalDelay / (double) FreezeTimes;

        double orderCycleSum = 0.0;
        int count = 0;
        for (Car car : CarManager.carMap.values()) {
            if (car.getStatistics() != null) {
                orderCycleSum += car.getStatistics().getAverageOrderCycle();
                count++;
            }
        }
        double averageOrderCycle = count > 0 ? orderCycleSum / count : 0.0;

        Map<String, Object> data = new HashMap<>();
        data.put("ontimeDeliveryRate", ontimeDeliveryRate);
        data.put("totalDelayTime", totalDelay);
        data.put("averageDelayTime", averageDelayTime);
        data.put("averageOrderCycle", averageOrderCycle);

        // 保存统计数据到数据库
        DBManager.saveToSandbox(null, null, null, null,
                               ontimeDeliveryRate,
                               totalDelay,
                               averageDelayTime,
                               averageOrderCycle,
                               null);

        renderJson(data);
    }

    /**获取系统指标 */
    public void getSystemMetrics()
    {
        Map<String, String> data = new HashMap<>();
        int carCount = CarManager.carMap.size();
        int demandCount = DBManager.getCount("demand");
        
        double systemCriticalLoad = carCount * demandCount / 70000.0;
        data.put("System_critical_load", String.valueOf(systemCriticalLoad));

        // 保存统计数据到数据库
        String jsonContent;
        try {
            jsonContent = JSON_MAPPER.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize system metrics to JSON", e);
            reply(false, "Failed to serialize system metrics to JSON");
            return;
        }
        DBManager.saveToSandbox(null, null, null, null,
                               null, null, null, null,
                               systemCriticalLoad);
        reply(true, jsonContent);
    }

    /**
     * 前端尝试获取特定车辆的下一个目的地时调用，是车辆更新的关键函数
     * <p>在车辆滴答一次后，若进入需要规划路线的状态，则返回目的地坐标，否则返回{@code null}
     * <p>返回坐标格式：{"lat":{@code double}, "lon":{@code double}}
     */
    public void getDestination()
    {
        String uuid = getPara("UUID");
        Car car = CarManager.carMap.get(uuid);
        Map<String, Double> dest = null;

        if (car == null) {
            logger.warn("getDestination: car not found for UUID: {}", uuid);
            renderJson((Object) null);
            return;
        }

        if (car.getStatistics() != null) {
            try { car.getStatistics().incrementCompleteOrderCycle(); } catch (Exception e) { logger.warn("incrementCompleteOrderCycle failed: {}", e.getMessage()); }
        }

        // 车辆计时器滴答一次并在计时器归零时进行车辆状态转换
        try { car.tick(car.getState()); } catch (Exception e) { logger.warn("car.tick failed for UUID {}: {}", uuid, e.getMessage()); }
        try {
            if (car.getStateTimer() != null && car.getStateTimer().timeUp()) {
                car.changeState();
                StateChangeTimes++;
            }
        } catch (Exception e) {
            logger.warn("state timer check failed for UUID {}: {}", uuid, e.getMessage());
        }
        if(car.getState() == CarState.FREEZE) {
            FreezeTimes++;
            Random rand = new Random();
            int randomNum = rand.nextInt(70) + 3; // 生成3到72之间的随机数
            totalDelayTime += randomNum;
        }

        // 仅在车辆进入了接单行驶/运货行驶状态时给dest赋值，其他状态返回的dest为null
        switch (car.getState())
        {
            case ORDER_TAKEN: {
                try {
                    PathNode pathnode = car.fetchFirstNode();
                    if (pathnode != null && pathnode.getDemand() != null && pathnode.getDemand().getOrigin() != null) {
                        dest = Map.of(
                            "lat", pathnode.getDemand().getOrigin().lat,
                            "lon", pathnode.getDemand().getOrigin().lon
                        );
                    }
                } catch (Exception e) {
                    logger.warn("Failed to get ORDER_TAKEN destination for UUID {}: {}", uuid, e.getMessage());
                }
                break;
            }

            case TRANSPORTING: {
                try {
                    PathNode pathnode = car.fetchFirstNode();
                    if (pathnode != null && pathnode.getDemand() != null && pathnode.getDemand().getDestination() != null) {
                        dest = Map.of(
                            "lat", pathnode.getDemand().getDestination().lat,
                            "lon", pathnode.getDemand().getDestination().lon
                        );
                    }
                } catch (Exception e) {
                    logger.warn("Failed to get TRANSPORTING destination for UUID {}: {}", uuid, e.getMessage());
                }
                break;
            }

            default:
                break;
        }
        try {
            renderJson(JSON_MAPPER.writeValueAsString(dest));
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize destination to JSON", e);
            renderJson("{}") ;
        }
    }
}
