package com.tsAdmin.control;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

/**
 * 数据控制器
 * 主要处理前端发出的请求，返回JSON数据
 */
public class DataController extends Controller
{
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
     * 获取仪表盘数据
     * TODO: 修改仪表盘数据获取
     */
    public void getDashboardData()
    {
        List<Map<String,String>> carData = new ArrayList<>();
        Double cycleCost = 0.0;

        try
        {
            for(Car car : CarManager.carMap.values())
            {
                CarStatistics statistics = car.getStatistics();
                Map<String, String> data = new HashMap<>();
                data.put("UUID", car.getUUID());


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
                        Object value = method.invoke(statistics);
                        data.put(varName, String.valueOf(value));
                    }
                }
                carData.add(data);
            }
        }
        catch (Exception e)
        {
            logger.error("Failed to get statistics", e);
        }

        Map<Double,List<Map<String,String>>> finaldata = new HashMap<>();
        finaldata.put(cycleCost, carData);
        renderJson(JsonKit.toJson(finaldata));
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

        // 车辆计时器滴答一次并在计时器归零时进行车辆状态转换
        car.tick(car.getState());
        if(car.getStateTimer().timeUp()) car.changeState();

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
