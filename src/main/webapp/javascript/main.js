// key.js is git-ignored
import { ApiSrc } from "/key.js";

const carIconSrc = '/resources/CarIcon.png';
const originIconSrc = '/resources/Origin.png';
const destinationIconSrc = '/resources/Destination.png';
const POIIconSrc = {
    "pharmaProducer" : '/resources/PharmaceuticalProducer.png',
    "steelProducer"  : '/resources/SteelProducer.png',
    "woodProducer"   : '/resources/WoodProducer.png',
    "pharmaProcessor": '/resources/PharmaceuticalProcessor.png',
    "steelProcessor" : '/resources/SteelProcessor.png',
    "woodProcessor"  : '/resources/WoodProcessor.png'
};

const script = document.createElement('script');
script.src = ApiSrc;
script.onload = () => main();
script.onerror = () => console.error('高德地图 API 加载失败');
document.head.appendChild(script);

const updateInterval = 5000;    // 更新间隔5000ms
const duration = 20; // 每段停留时间（单位：毫秒）
const POIs = [];//poi点数组
const cars = [];//车辆数组

let isCarUpdating = false;

let map;//map对象
let driving;//driving对象
let carIcon;


async function main()
{
    try
    {
        map = new AMap.Map("container", {
            center: [104.10248, 30.67646],
            zoom: 14
        });

        map.addControl(new AMap.ToolBar());
        map.addControl(new AMap.Scale());
        map.addControl(new AMap.ControlBar());

        console.log('地图初始化完成');

        await initPOI();
        await initCar();
    }
    catch (error)
    {
        console.error('初始化失败: ', error);
    }

    try
    {
        setInterval(() => update(), updateInterval);//总更新函数
    }
    catch (error)
    {
        console.error('运行时出错: ', error);
    }
}



async function initPOI()//生成poi点
{
    // 遍历 POIIconSrc 对象的所有键值对
    for (const [type, iconSrc] of Object.entries(POIIconSrc))
    {
        let data = await getPOIData(type);

        const icon = new AMap.Icon({
            size: new AMap.Size(16,16),
            image: iconSrc,
            imageSize: new AMap.Size(16,16)
        });

        data.forEach(poi => {
            const marker = new AMap.Marker({
                title: poi.name,
                position: new AMap.LngLat(poi.lon, poi.lat),
                icon: icon,
                map: map
            });

            POIs.push({
                UUID: poi.UUID,
                marker: marker
            });
        });
        console.log(`${type} 兴趣点添加成功, 数量: ${data.length}`);
    }
}

async function initCar()//生成车辆
{
    let data = await getCarData();

    let drivingOption = {
        policy: AMap.DrivingPolicy.LEAST_TIME,
        ferry: 1,
        province: '川'
    };
    driving = new AMap.Driving(drivingOption);

    carIcon = new AMap.Icon({
        size: new AMap.Size(16, 16),
        image: carIconSrc,
        imageSize: new AMap.Size(16, 16)
    });

    data.forEach(car => {
        const marker = new AMap.Marker({
            position: new AMap.LngLat(car.lon, car.lat),
            icon: carIcon,
            map: map
        });

        cars.push({
            UUID: car.UUID,
            marker: marker,
            status:0,
            info:{
                startMarker:marker,
                endMarker:marker,
                route:null,
                Time:0
            }
        });
    });
    console.log(`车辆添加成功, 数量: ${data.length}`);
}

/**
 * 获取POI的数据信息列表
 * @param {string} type
 * @returns 包含UUID, name, lat, lon属性的类
 */
async function getPOIData(type)//后端poi点获取
{
    try
    {
        let url = new URL('/data/getPoiData', window.location.origin);
        url.searchParams.append("type", type);

        const response = await fetch(url);
        if (!response.ok) throw new Error('POI 数据获取失败');

        let data = await response.json();
        if (!Array.isArray(data)) throw new Error('POI 数据无效', { cause: data+'无效' });

        data.forEach(item => {
            let isValid = item.hasOwnProperty("UUID")
                       && item.hasOwnProperty("name")
                       && item.hasOwnProperty("lat")
                       && item.hasOwnProperty("lon");
            if (!isValid) throw new Error('POI 数据解析失败', { cause: item+'解析出错' });
        });
        return data;
    }
    catch (error)
    {
        console.error('POI 数据获取错误: ', error);
        return [{"Exception": "error occurs!"}];
    }
}

/**
 * 获取车辆的数据信息
 * @returns 包含UUID, lat, lon属性的类
 */
async function getCarData()//后端车辆坐标获取
{
    try
    {
        let url = new URL('/data/getCarData', window.location.origin);

        let response = await fetch(url);
        if (!response.ok) throw new Error('车辆数据获取失败');

        let data = await response.json();
        if (!Array.isArray(data)) throw new Error('车辆数据无效', { cause: data+'无效' });

        // data.forEach(item => {
        //     let isValid = item.hasOwnProperty("UUID")
        //             && item.hasOwnProperty("lat")
        //             && item.hasOwnProperty("lon");
        //     if (!isValid) throw new Error('车辆数据解析失败', { cause: item+'解析出错' });
        // });
        return data;
    }
    catch (error)
    {
        console.error('车辆数据获取错误: ', error);
        return [{"Exception": "error occurs!"}];
    }
}

async function update()
{
    await updateCars();//车辆位置更新
}
/**
 * 车辆位置及状态更新
 */
async function updateCars() {
    console.log("调用 updateCars");

    if (isCarUpdating) return;
    isCarUpdating = true;

    try {
        for (const car of cars) 
        {
            const uuid = car.UUID;
            if (car.status === 0) 
            {
                const recivdata = await sendPara(uuid,0,0);
                console.log(`车辆 ${uuid} 收到目的地:`, recivdata);

                // 校验目的地格式和合法性
                if (
                    !recivdata 
                ) {
                    console.warn(`车辆 ${uuid} 目标坐标无效，跳过：`, recivdata);
                    continue;
                }

                const end = [recivdata.lon, recivdata.lat];

                // 获取当前位置
                const currentLngLat = car.marker.getPosition();
                const start = [currentLngLat.lng, currentLngLat.lat];

                // setPosition 调用
                 //car.marker.setPosition(end);

                try 
                {
                    // 执行路径规划
                    const route = await planRoute(start, end);

                    // 检查路径是否有效
                    if (!route || !route.steps || route.steps.length === 0 || typeof route.distance !== 'number') 
                    {
                        console.error(`车辆 ${uuid} 路径无效，跳过`);
                        continue;
                    }
                    car.status = 1;
                    const routeInfo = await drawRoute(route);
                    car.info = routeInfo;
                    cartransporting(routeInfo, uuid);
                    VideoCars(car.marker,route);

                } catch (err) {
                    console.error(`车辆 ${uuid} 路径规划失败:`, err);
                }

            } else if (car.status === 1) 
            {
                cartransporting(car.info, uuid);
            }
        }

    } catch (error) {
        console.error("车辆更新总过程出错：", error);
    } finally {
        isCarUpdating = false;
    }
}

async function VideoCars(marker, route)
{
    const path = parseRouteToPath(route);

    for (let i = 0; i < path.length; i++)
    {
        const point = path[i];
        const lng = typeof point.getLng === 'function' ? point.getLng() : point.lng;
        const lat = typeof point.getLat === 'function' ? point.getLat() : point.lat;

        marker.setPosition([lng, lat]);

        await new Promise(resolve => setTimeout(resolve, duration));
    }

    console.log("路径跳点完成");
}

async function sendPara(uuid,distance,time) 
{
    console.log("已调用sendPara");
    try {
        // 构造包含 UUID 
        let url = new URL('/data/getDestination', window.location.origin);
        url.searchParams.append("UUID", uuid);
        url.searchParams.append("Distance", distance);
        url.searchParams.append("Time", time);
        const response = await fetch(url);
        let data = await response.json();
        console.log("后端响应:",data);
        return data;
    } catch (error) {
        console.error('发送 UUID 到后端时出错:', error);
    }
}
//根据起终点规划路径
function planRoute(start, end) 
{
    return new Promise((resolve, reject) => 
    { 
    driving.search(new AMap.LngLat(start[0], start[1]), new AMap.LngLat(end[0], end[1]), 
        function (status, result)
        {
            if (status === 'complete' && result.routes && result.routes.length) 
            {
                var route= result.routes[0];
                resolve(route);
            } 
            else 
            {
                console.error('请求失败，状态:', status+'结果'+result);
                reject(new Error('请求失败，状态: ' + status +'结果'+result));
            }
        }
     );
    });
}

// 绘制路线函数
function drawRoute(Route)
{
    let path = parseRouteToPath(Route);

    let originIcon = new AMap.Icon({
        size: new AMap.Size(16,16),
        image: originIconSrc,
        imageSize: new AMap.Size(16,16)
    });
    let startMarker = new AMap.Marker({
        position: path[0],
        icon: originIcon,
        map: map
    });//起点图标

    let destinationIcon = new AMap.Icon({
        size: new AMap.Size(16,16),
        image: destinationIconSrc,
        imageSize: new AMap.Size(16,16)
    });

    let endMarker = new AMap.Marker({
        position: path[path.length - 1],
        icon: destinationIcon,
        map: map
    });//终点图标

    Route = new AMap.Polyline({
        path: path,
        isOutline: true,
        outlineColor: '#ffeeee',
        borderWeight: 2,
        strokeWeight: 3,
        strokeOpacity: 0.9,
        strokeColor: '#0091ff',
        lineJoin: 'round'
    });//路线的各种参数，不用管他

    map.add(Route);
    console.log("路线已规划"+path.length);
//车辆运行
   const time=path.length*duration;
    let routeInfo = {
        startMarker:startMarker,
        endMarker:endMarker,
        route:Route,
        Time:time
    };
    console.log("路径信息："+routeInfo);
    return routeInfo;
}

// 获取路径数组
function parseRouteToPath(route)
{
    let path = [];
    for (let i = 0, l = route.steps.length; i < l; i++)
    {
        let step = route.steps[i];
        for (let j = 0, n = step.path.length; j < n; j++)
        {
            path.push(step.path[j]);
        }
    }
    return path;
}

//用于转换车辆状态，倒计时结束则回到空闲状态
async function cartransporting(routeInfo,carUUID)
{
     // 设置车辆运输完成的倒计时
     if(routeInfo.Time>0)
    {
        routeInfo.Time-=5000;
        console.log("车辆"+carUUID+"已减少倒计时");
     }
     else {
        const carIndex = cars.findIndex(car => car.UUID === carUUID);
        if (carIndex !== -1) 
        {
            // 恢复车辆空闲状态
            cars[carIndex].status = 0;
            let distance=routeInfo.route.distance;
            let time=routeInfo.route.time;
            // 清理路线相关元素
            map.remove(routeInfo.startMarker);
            map.remove(routeInfo.endMarker);
            map.remove(routeInfo.route);
           const nousedc=await sendPara(carUUID,distance,time);
            console.log(`车辆 ${carUUID} 已完成运输`);
        }
          }
}



