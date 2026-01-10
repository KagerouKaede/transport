package com.tsAdmin.control;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

//import com.tsAdmin.control.scheduler.MOSAScheduler;
//import com.tsAdmin.model.Assignment;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.jfinal.core.Controller;
import com.jfinal.kit.JsonKit;
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

    /**
     * 获取所有兴趣点数据
     * <p>返回数据格式：[{"UUID":{@code String},"class":{@code String},"type":{@code String}, "name":{@code String}, "lat":{@code Double}, "lon":{@code Double}}, {...}, ...]
     * 其中 {@code class} 值可能为 {@code ResourcePlant, ProcessPlant, Market} ；{@code type} 值可能为 {@code "WOOD", "STEEL", "PHARMA"}
     */
    public void getPoiData()
    {
        List<Map<String, Object>> dataList = DBManager.getPoiList();
        renderJson(JsonKit.toJson(dataList));
    }

    /**
     * 获取所有车辆数据
     * <p>返回数据格式：[{"UUID":{@code String}, "type":{@code String}, "maxload":{@code Integer}, "maxvolume":{@code Integer}, "lat":{@code Double}, "lon":{@code Double}}, {...}, ...]
     */
    public void getCarData()
    {
        List<Map<String, Object>> posList = DBManager.getCarList();
        renderJson(JsonKit.toJson(posList));
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
        CarStatistics statistics = car.getStatistics();
        statistics.calculateLoad_utilization_rate(car);
        statistics.calculateCapacity_utilization_rate(car);
        Map<String, String> data = new HashMap<>();
        data.put("UUID", car.getUUID());
        data.put("position_lat", String.valueOf(car.getPosition().lat));
        data.put("position_lon",  String.valueOf(car.getPosition().lon));
        data.put("state", car.getState().toString());
        data.put("load", String.valueOf(car.getLoad()));

        // 获取 CarStatistics 类的所有 Getter
        Method[] methods = CarStatistics.class.getMethods();
        for (Method method : methods)
        {
            if (method.getName().startsWith("get"))
            {
                // 去掉 get 前缀，首字母小写
                String methodName = method.getName();
                String varName = Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);

                // 调用 Getter 方法获取值并转换为字符串
                try {
                    Object value = method.invoke(statistics);
                    data.put(varName, String.valueOf(value));
                } catch (Exception e) {
                    logger.warn("Failed to invoke getter {} on CarStatistics: {}", method.getName(), e.getMessage());
                    data.put(varName, "null");
                }
            }
        }
        // 保存统计数据到数据库
        DBManager.saveStatisticsToCarDB(car);
        //Map<Double,List<Map<String,String>>> finaldata = new HashMap<>();
        //finaldata.put(cycleCost, carData);
        renderJson(JsonKit.toJson(data));
    }

    /**
     * 获取所有车辆的统计数据，返回全部车辆的统计指标均值和方差
     */
    public void getAllCarsStatistics()
    {
        Map<String, String> data = new HashMap<>();
        int Available_count=0, Malfunction_count=0;
        List<Double> loadRates = new ArrayList<>();
        List<Double> capacityRates = new ArrayList<>();
        
        for(Car car : CarManager.carMap.values())
        {
            car.getStatistics().calculateLoad_utilization_rate(car);
            car.getStatistics().calculateCapacity_utilization_rate(car);
            loadRates.add(car.getStatistics().getLoad_utilization_rate());
            capacityRates.add(car.getStatistics().getCapacity_utilization_rate());
            if(car.getState()==CarState.AVAILABLE) Available_count++;
            else if(car.getState()==CarState.FREEZE) Malfunction_count++;
        }
        
        // 计算装载率的平均值和方差
        double loadMean = loadRates.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double loadVariance = loadRates.stream().mapToDouble(x -> Math.pow(x - loadMean, 2)).average().orElse(0.0);
        
        // 计算运力利用率的平均值和方差
        double capacityMean = capacityRates.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double capacityVariance = capacityRates.stream().mapToDouble(x -> Math.pow(x - capacityMean, 2)).average().orElse(0.0);
        
        data.put("Available_count", String.valueOf(Available_count));
        data.put("Malfunction_count", String.valueOf(Malfunction_count));
        data.put("Load_utilization_rate_mean", String.valueOf(loadMean));
        data.put("Load_utilization_rate_variance", String.valueOf(loadVariance));
        data.put("Capacity_utilization_rate_mean", String.valueOf(capacityMean));
        data.put("Capacity_utilization_rate_variance", String.valueOf(capacityVariance));
        
        // 保存统计数据到数据库
        String jsonContent = JsonKit.toJson(data);
        DBManager.saveToSandbox(loadMean,
                               loadVariance,
                               capacityMean,
                               capacityVariance,
                               null, null, null, null, null);
        
        renderJson(jsonContent);
    }

    /**获取服务质量指标 */
    public void getServiceQualityMetrics()
    {
        Map<String, String> data = new HashMap<>();
        if(StateChangeTimes == 0) StateChangeTimes = 1; // 防止除以零
        double Ontime_delivery_rate = FreezeTimes / ((double)StateChangeTimes/5);
        data.put("Ontime_delivery_rate", String.valueOf(Ontime_delivery_rate));
        data.put("Total_delay_time", String.valueOf(totalDelayTime));

        if(FreezeTimes == 0) FreezeTimes = 1; // 防止除以零
        double averageDelayTime = totalDelayTime / (double)FreezeTimes;
        data.put("Average_delay_time", String.valueOf(averageDelayTime));
        double order_cycle = 0.0;
        for(Car car : CarManager.carMap.values())
        {
            order_cycle += car.getStatistics().getAverageOrderCycle();
        }
        double Average_order_cycle = 0.0;
        if (CarManager.carMap.size() >0 ) {
            Average_order_cycle = order_cycle / CarManager.carMap.size();
        }
        data.put("Average_order_cycle", String.valueOf(Average_order_cycle));

        // 保存统计数据到数据库
        String jsonContent = JsonKit.toJson(data);
        DBManager.saveToSandbox(null, null, null, null,
                               Ontime_delivery_rate,
                               totalDelayTime,
                               averageDelayTime,
                               Average_order_cycle,
                               null);

        renderJson(jsonContent);
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
        String jsonContent = JsonKit.toJson(data);
        DBManager.saveToSandbox(null, null, null, null,
                               null, null, null, null,
                               systemCriticalLoad);

        renderJson(jsonContent);
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

        car.getStatistics().incrementCompleteOrderCycle();
        // 车辆计时器滴答一次并在计时器归零时进行车辆状态转换
        car.tick(car.getState());
        if(car.getStateTimer().timeUp()) {car.changeState();StateChangeTimes++;}
        if(car.getState() == CarState.FREEZE) {FreezeTimes++;
            Random rand = new Random();
            int randomNum = rand.nextInt(70) + 3; // 生成3到72之间的随机数
            totalDelayTime += randomNum;
        }

        // 仅在车辆进入了接单行驶/运货行驶状态时给dest赋值，其他状态返回的dest为null
        switch (car.getState())
        {
            case ORDER_TAKEN:
            {
                PathNode pathnode = car.fetchFirstNode();
                dest = Map.of(
                    "lat", pathnode.getDemand().getOrigin().lat,
                    "lon", pathnode.getDemand().getOrigin().lon
                );
                break;
            }

            case TRANSPORTING:
            {
                PathNode pathnode = car.fetchFirstNode();
                dest = Map.of(
                    "lat", pathnode.getDemand().getDestination().lat,
                    "lon", pathnode.getDemand().getDestination().lon
                );
                break;
            }

            default:
                break;
        }
        renderJson(JsonKit.toJson(dest));
    }
}
