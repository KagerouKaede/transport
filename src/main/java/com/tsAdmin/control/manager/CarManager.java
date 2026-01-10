package com.tsAdmin.control.manager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.tsAdmin.common.ConfigLoader;
import com.tsAdmin.common.Coordinate;
import com.tsAdmin.control.DBManager;
import com.tsAdmin.control.Main;
import com.tsAdmin.model.Car;
import com.tsAdmin.model.Car.CarState;
import com.tsAdmin.model.CarStatistics;

public class CarManager
{
    // TODO: 封装为私有
    public static Map<String, Car> carMap = new HashMap<>();

    // LOADS 元素数一定要等于 VOLUME 元素数
    private static final int[] LOADS = { 2, 5, 8, 12, 18, 24, 30, 35 };
    private static final int[] VOLUMES = { 12, 16, 32, 48, 64, 86, 108, 140 };
    private static final Coordinate defaultLocation = new Coordinate(30.67646, 104.10248);

    public static void init()
    {
        carMap.clear();

        if (DBManager.getCount("car") <= 0)
        {
            int carNum = ConfigLoader.getInt("CarManager.car_num");

            for (int i = 0; i < carNum; i++)
            {
                String uuid = UUID.randomUUID().toString().replace("-", "");

                int randIdx = Main.RANDOM.nextInt(LOADS.length);
                int maxLoad = LOADS[randIdx];
                int maxVolume = VOLUMES[randIdx];

                Car car = new Car(uuid, maxLoad, maxVolume, new Coordinate(getRandomLocation()));
                carMap.put(uuid, car);
            }
        }
        else
        {
            // 从数据库读取车辆数据并恢复车辆状态和统计数据
            List<Map<String, Object>> dataList = DBManager.getCarList();
            for (Map<String, Object> data : dataList)
            {
                String uuid = data.get("UUID").toString();
                int maxLoad = getIntValue(data.get("maxload"));
                int maxVolume = getIntValue(data.get("maxvolume"));
                double lat = getDoubleValue(data.get("location_lat"));
                double lon = getDoubleValue(data.get("location_lon"));

                // 创建车辆对象
                Car car = new Car(uuid, maxLoad, maxVolume, new Coordinate(lat, lon));

                // 设置车辆基本属性
                int load = getIntValue(data.get("load"));
                int volume = getIntValue(data.get("volume"));
                car.setLoad(load);
                car.setVolume(volume);

                // 设置车辆状态
                String currStateStr = data.get("currState") != null ? data.get("currState").toString() : null;
                String prevStateStr = data.get("preState") != null ? data.get("preState").toString() : null;
                
                if (currStateStr != null)
                {
                    try
                    {
                        CarState currState = CarState.valueOf(currStateStr);
                        CarState prevState = prevStateStr != null ? CarState.valueOf(prevStateStr) : CarState.AVAILABLE;
                        
                        // 先设置上一状态，再设置当前状态
                        car.setState(prevState);
                        car.setState(currState);
                    }
                    catch (IllegalArgumentException e)
                    {
                        // 如果状态字符串无效，使用默认状态
                        car.setState(CarState.AVAILABLE);
                        car.setState(CarState.AVAILABLE);
                    }
                }
                else
                {
                    // 如果状态为空，使用默认状态
                    car.setState(CarState.AVAILABLE);
                    car.setState(CarState.AVAILABLE);
                }

                // 设置车辆统计数据
                CarStatistics statistics = car.getStatistics();
                statistics.setWaitingTime(getDoubleValue(data.get("waitingTime")));
                statistics.setEmptyDistance(getDoubleValue(data.get("emptyDistance")));
                statistics.setWastedLoad(getDoubleValue(data.get("wastedLoad")));
                statistics.setTotalWeight(getDoubleValue(data.get("totalWeight")));
                statistics.setCarbonEmission(getDoubleValue(data.get("carbonEmission")));
                statistics.setTotalDistance(getDoubleValue(data.get("totalDistance")));
                statistics.setCompletedOrders(getIntValue(data.get("completedOrders")));
                statistics.setAverageOrderCycle(getDoubleValue(data.get("averageOrderCycle")));

                carMap.put(uuid, car);
            }
        }
    }

    public static void onStop() { DBManager.saveCarMap(carMap); }

    /**
     * 安全地从 Map 中获取整数值
     * @param value 可能为 null 的值
     * @return 整数值，如果为 null 则返回 0
     */
    private static int getIntValue(Object value)
    {
        if (value == null) return 0;
        if (value instanceof Integer) return (Integer)value;
        if (value instanceof Number) return ((Number)value).intValue();
        try
        {
            return Integer.parseInt(value.toString());
        }
        catch (NumberFormatException e)
        {
            return 0;
        }
    }

    /**
     * 安全地从 Map 中获取双精度浮点数值
     * @param value 可能为 null 的值
     * @return 双精度浮点数值，如果为 null 则返回 0.0
     */
    private static double getDoubleValue(Object value)
    {
        if (value == null) return 0.0;
        if (value instanceof Double) return (Double)value;
        if (value instanceof Number) return ((Number)value).doubleValue();
        try
        {
            return Double.parseDouble(value.toString());
        }
        catch (NumberFormatException e)
        {
            return 0.0;
        }
    }

    /**
     * 生成随机方位点
     * @return 随机方位点
     */
    private static Coordinate getRandomLocation()
    {
        // 最大半径约2公里
        double maxRadius = 0.12;

        double angle = Main.RANDOM.nextDouble() * 2 * Math.PI;
        double distance = Math.sqrt(Main.RANDOM.nextDouble()) * maxRadius;

        // 计算偏移量
        double latOffset = distance * Math.sin(angle);
        double lngOffset = distance * Math.cos(angle) ;

        return new Coordinate(
            defaultLocation.lat + latOffset,
            defaultLocation.lon + lngOffset
        );
    }
}
