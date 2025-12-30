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
            // TODO: 车辆统计数据也需要存到数据库
            List<Map<String, Object>> dataList = DBManager.getCarList();
            for (Map<String, Object> data : dataList)
            {
                String uuid = data.get("UUID").toString();
                int maxLoad = (int)data.get("maxload");
                int maxVolume = (int)data.get("maxvolume");
                double lat = (double)data.get("lat");
                double lon = (double)data.get("lon");

                Car car = new Car(uuid, maxLoad, maxVolume, new Coordinate(lat, lon));

                // 两次setState: 第一次将上一状态set为currState，第二次set会自动将其转移至prevState
                car.setState(CarState.AVAILABLE);
                car.setState(CarState.AVAILABLE);
                car.setLoad(0);
                car.setVolume(0);

                carMap.put(uuid, car);
            }
        }
    }

    public static void onStop() { DBManager.saveCarMap(carMap); }

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
