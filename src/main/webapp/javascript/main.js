// key.js is git-ignored
import { ApiSrc } from "/key.js";
import { EventManager } from '/event/event-manager.js';
window.planRoute = planRoute; // 添加到全局作用域
const carIconSrc = '/resources/CarIcon.png';
const originIconSrc = '/resources/Origin.png';
const destinationIconSrc = '/resources/Destination.png';
const POIIconSrc = {
    "pharmaProducer" : '/resources/PharmaceuticalProducer.png',
    "steelProducer"  : '/resources/SteelProducer.png',
    "woodProducer"   : '/resources/WoodProducer.png',
    "pharmaProcessor": '/resources/PharmaceuticalProcessor.png',
    "steelProcessor" : '/resources/SteelProcessor.png',
    "woodProcessor"  : '/resources/WoodProcessor.png',
    "pharmamarket": '/resources/PharmaMarket.png',
    "steelmarket": '/resources/SteelMarket.png',
    "woodmarket": '/resources/WoodMarket.png'
};

const script = document.createElement('script');
script.src = ApiSrc;
script.onload = () => main();
script.onerror = () => console.error('高德地图 API 加载失败');
document.head.appendChild(script);

// 减少主更新频率以降低主线程负载
const updateInterval = 7000;    // 更新间隔7000ms
const duration = 20; // 每段停留时间（单位：毫秒）
// const routes = [];
const POIs = [];//poi点数组
const cars = [];//车辆数组
let eventManager;
let animationManager;
let isCarUpdating = false;

let map;//map对象
let driving;//driving对象
let carIcon;

// 在交互期间隐藏/显示所有车辆标注以减轻缩放/拖拽时的渲染压力
function hideAllVehicleMarkers() {
    try {
        cars.forEach(v => {
            if (v && v.marker) {
                if (typeof v.marker.hide === 'function') v.marker.hide();
                else if (typeof v.marker.setVisible === 'function') v.marker.setVisible(false);
            }
        });
    } catch (e) { /* ignore */ }
}

function showAllVehicleMarkers() {
    try {
        cars.forEach(v => {
            if (v && v.marker) {
                if (typeof v.marker.show === 'function') v.marker.show();
                else if (typeof v.marker.setVisible === 'function') v.marker.setVisible(true);
            }
        });
    } catch (e) { /* ignore */ }
    // 交互结束后强制刷新位置
    try { if (window.animationManager && typeof window.animationManager.forceUpdateMarkers === 'function') window.animationManager.forceUpdateMarkers(); } catch(e){}
    // 如果之前推迟了散点图刷新，则现在刷新一次
    try {
        if (window.pendingScatterUpdate) {
            window.pendingScatterUpdate = false;
            if (typeof updateScatterChart === 'function') updateScatterChart();
        }
    } catch (e) { /* ignore */ }
}

class AnimationManager {
    constructor(eventManager, baseDuration = 20) {
        this.eventManager = eventManager;
        this.baseDuration = baseDuration;
        this.animations = new Map(); // vehicleId -> animation data
        this.updateInterval = null;
        this.animationSpeed = 1.0; // 全局动画速度因子
        
        // 每秒更新所有动画
            this.running = true;
            this.startAnimationLoop();
    }

        // 启动动画循环（使用 requestAnimationFrame 更省资源）
        startAnimationLoop() {
            const loop = () => {
                if (!this.running) return;
                this.updateAllAnimations();
                requestAnimationFrame(loop);
            };
            requestAnimationFrame(loop);
        }

    // 启动车辆动画
    startVehicleAnimation(vehicle, route) {
        if (!vehicle || !route) return;
        
        const path = this.eventManager.parseRouteToPath(route);
        if (path.length === 0) return;
        
        // 停止现有动画
        this.stopVehicleAnimation(vehicle.UUID);
        
        // 创建动画数据
        const animationData = {
            vehicle: vehicle,
            path: path,
            currentIndex: 0,
            lastUpdateTime: Date.now(),
            speedFactor: this.eventManager.getVehicleSpeedFactor(vehicle) || 1.0,
            isPaused: false,
            route: route  // 保存原始路由信息
        };
        
        this.animations.set(vehicle.UUID, animationData);
        
        // 立即更新一次位置
        this.updateVehicleAnimation(vehicle.UUID);
        
        console.log(`启动车辆 ${vehicle.UUID} 动画，路径长度: ${path.length}`);
    }
    
    // 更新车辆动画
    updateVehicleAnimation(vehicleId) {
        const animation = this.animations.get(vehicleId);
        if (!animation || animation.isPaused) return;
        
        const now = Date.now();
        const elapsed = now - animation.lastUpdateTime;
        
        // 动态计算速度因子
        animation.speedFactor = this.eventManager.getVehicleSpeedFactor(animation.vehicle);
        
        // 如果速度因子为0，暂停动画
        if (animation.speedFactor <= 0) {
            animation.isPaused = true;
            return;
        }
        
        // 计算应前进的帧数
        const targetDuration = this.baseDuration / animation.speedFactor;
        const framesToMove = Math.floor(elapsed / targetDuration);
        
        if (framesToMove > 0) {
            // 检查事件触发
            this.eventManager.checkVehicleEvents(animation.vehicle);
            
            // 更新位置
                    const newIndex = Math.min(animation.currentIndex + framesToMove, animation.path.length - 1);

                    if (newIndex > animation.currentIndex) {
                        animation.currentIndex = newIndex;
                        const point = animation.path[newIndex];
                        // 在地图交互（缩放/拖拽）期间避免频繁调用 setPosition，以减少主线程抖动
                        if (typeof window !== 'undefined' && window.mapInteracting) {
                            // 延迟应用位置，交互结束时一次性刷新
                            animation.pendingPosition = [point.lng, point.lat];
                        } else {
                            animation.vehicle.marker.setPosition([point.lng, point.lat]);
                        }

                        // 如果到达终点
                        if (newIndex >= animation.path.length - 1) {
                            this.onAnimationComplete(animation.vehicle);
                        }
                    }
            
            animation.lastUpdateTime = now;
        }
    }
    
    // 动画完成回调
    onAnimationComplete(vehicle) {
        this.stopVehicleAnimation(vehicle.UUID);
        
        // 触发运输完成逻辑
        if (vehicle.info) {
            cartransporting(vehicle.info, vehicle.UUID);
        }
        
        console.log(`车辆 ${vehicle.UUID} 动画完成`);
    }
    
    // 停止车辆动画
    stopVehicleAnimation(vehicleId) {
        const animation = this.animations.get(vehicleId);
        if (animation) {
            this.animations.delete(vehicleId);
        }
    }
    
    // 暂停车辆动画
    pauseVehicleAnimation(vehicleId) {
        const animation = this.animations.get(vehicleId);
        if (animation) {
            animation.isPaused = true;
        }
    }
    
    // 恢复车辆动画
    resumeVehicleAnimation(vehicleId) {
        const animation = this.animations.get(vehicleId);
        if (animation) {
            animation.isPaused = false;
            animation.lastUpdateTime = Date.now();
        }
    }
    
    // 更新所有动画
    updateAllAnimations() {
        this.animations.forEach((animation, vehicleId) => {
            this.updateVehicleAnimation(vehicleId);
        });
    }
    
    // 检查是否有动画
    hasAnimation(vehicleId) {
        return this.animations.has(vehicleId);
    }
    
    // 销毁
    destroy() {
        this.running = false;
        try { this.animations.clear(); } catch (e) { /* ignore */ }
    }

    // 在交互结束后一次性刷新所有动画标注到最新位置
    forceUpdateMarkers() {
        this.animations.forEach((animation, vehicleId) => {
            try {
                const idx = animation.currentIndex || 0;
                const point = animation.path && animation.path[idx];
                if (point) {
                    animation.vehicle.marker.setPosition([point.lng, point.lat]);
                }
                if (animation.pendingPosition) {
                    animation.vehicle.marker.setPosition(animation.pendingPosition);
                    delete animation.pendingPosition;
                }
            } catch (e) {
                // ignore individual marker errors
            }
        });
    }
}

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

        // 地图交互标志：在缩放或拖拽期间设置为 true，以便暂停高频 DOM 更新
        try { window.mapInteracting = false; } catch (e) { /* ignore */ }
        map.on('movestart', () => { try { window.mapInteracting = true; hideAllVehicleMarkers(); } catch(e){} });
        map.on('moveend', () => { try { window.mapInteracting = false; showAllVehicleMarkers(); } catch(e){} });
        map.on('zoomstart', () => { try { window.mapInteracting = true; hideAllVehicleMarkers(); } catch(e){} });
        map.on('zoomend', () => { try { window.mapInteracting = false; showAllVehicleMarkers(); } catch(e){} });

        console.log('地图初始化完成');

        await initPOI();
        await initCar();
        
        // 初始化事件管理器
        await initEventManager();
        
        // 初始化动画管理器，后于事件管理器
        animationManager = new AnimationManager(eventManager, duration);
            // 暴露到 window 以便外部（例如 map.html 的停止逻辑）能访问并销毁
            try { window.animationManager = animationManager; } catch(e) { /* ignore */ }
            try { window.eventManager = eventManager; } catch(e) { /* ignore */ }
        // 标志位：仿真是否已停止（供循环快速返回）
        try { window.simulationStopped = false; } catch(e) { /* ignore */ }

        // 添加时间和天气显示
        addTimeWeatherDisplay();

        // 每秒更新时间显示（保存到 window 以便停止时清理）
        window.timeUpdateInterval = setInterval(() => {
            if (eventManager) {
                updateTimeWeatherDisplay();
            }
        }, 1000);
    }
    catch (error)
    {
        console.error('初始化失败: ', error);
    }

    try {
        // 防止 update 重叠运行，update 内部会自行处理并发控制
        // 保存到 window 以便在停止仿真时能清除
        window.mainUpdateInterval = setInterval(() => update(), updateInterval); // 总更新函数
    } catch (error) {
        console.error('运行时出错: ', error);
    }
}

// 初始化事件管理器
async function initEventManager() {
    try {
        // 从后端获取事件配置
        const config = await fetchEventConfig();
        
        // 创建事件管理器需要的函数对象
        const eventFunctions = {
            planRoute: planRoute,
            parseRouteToPath: parseRouteToPath,
            drawRoute: drawRoute,
            VideoCars: VideoCars
        };
        
        // 初始化事件管理器
        eventManager = new EventManager(map, cars, eventFunctions);
        await eventManager.initialize(config);
        try { window.eventManager = eventManager; } catch(e) { /* ignore */ }
        
        console.log('事件管理器初始化成功');
    } catch (error) {
        console.error('事件管理器初始化失败:', error);
    }
}

// 处理特定事件类型的配置
function transformBackendConfig(backendConfig) {
    const frontendConfig = {};
    
    // 转换系统参数
    if (backendConfig.Main && backendConfig.Main.update_interval !== undefined) {
        frontendConfig['Main.update_interval'] = backendConfig.Main.update_interval;
    }
    
    if (backendConfig.Timer && backendConfig.Timer.tick_speed !== undefined) {
        frontendConfig['Timer.tick_speed'] = backendConfig.Timer.tick_speed;
    }
    
    // 转换事件配置
    if (backendConfig.Event) {
        const eventConfig = backendConfig.Event;
        
        // 基本事件参数
        frontendConfig['Event.global_enabled'] = eventConfig.global_enabled !== undefined ? eventConfig.global_enabled : true;
        frontendConfig['Event.max_active_events'] = eventConfig.max_active_events !== undefined ? eventConfig.max_active_events : 8;
        frontendConfig['Event.global_probability'] = eventConfig.global_probability !== undefined ? eventConfig.global_probability : 0.6;
        
        // 处理天气事件配置 - 将简单配置组合成复杂结构
        processWeatherConfig(backendConfig, frontendConfig);
        processTrafficJamConfig(backendConfig, frontendConfig);
        processAccidentConfig(backendConfig, frontendConfig);
        processRoadClosureConfig(backendConfig, frontendConfig);
    }
    
    // 确保所有必需字段都有值
    const defaultConfig = getDefaultEventConfig();
    Object.keys(defaultConfig).forEach(key => {
        if (frontendConfig[key] === undefined) {
            frontendConfig[key] = defaultConfig[key];
        }
    });
    
    console.log('转换后配置:', frontendConfig);
    return frontendConfig;
}

function processWeatherConfig(backendConfig, frontendConfig) {
    if (!backendConfig.Event || !backendConfig.Event.weather) return;
    
    const weather = backendConfig.Event.weather;
    
    // 基本配置
    frontendConfig['Event.weather.enabled'] = weather.enabled !== undefined ? weather.enabled : true;
    frontendConfig['Event.weather.max_count'] = weather.max_count !== undefined ? weather.max_count : 3;
    frontendConfig['Event.weather.min_distance'] = weather.min_distance !== undefined ? weather.min_distance : 5000;
    frontendConfig['Event.weather.display_range'] = weather.display_range !== undefined ? weather.display_range : 10000;
    
    // 持续时间范围
    if (weather.duration_min !== undefined && weather.duration_max !== undefined) {
        frontendConfig['Event.weather.duration_range'] = JSON.stringify([weather.duration_min, weather.duration_max]);
    }
    
    // 构建允许的天气类型数组
    const allowedTypes = [];
    if (weather.rain_allowed !== false) allowedTypes.push('rain');
    if (weather.snow_allowed !== false) allowedTypes.push('snow');
    if (weather.storm_allowed !== false) allowedTypes.push('storm');
    if (weather.sandstorm_allowed !== false) allowedTypes.push('sandstorm');
    if (weather.fog_allowed !== false) allowedTypes.push('fog');
    frontendConfig['Event.weather.types_allowed'] = JSON.stringify(allowedTypes);
    
    // 构建天气类型概率对象
    const typeProbabilities = {};
    if (weather.rain_probability !== undefined) typeProbabilities.rain = weather.rain_probability;
    if (weather.snow_probability !== undefined) typeProbabilities.snow = weather.snow_probability;
    if (weather.storm_probability !== undefined) typeProbabilities.storm = weather.storm_probability;
    if (weather.sandstorm_probability !== undefined) typeProbabilities.sandstorm = weather.sandstorm_probability;
    if (weather.fog_probability !== undefined) typeProbabilities.fog = weather.fog_probability;
    frontendConfig['Event.weather.type_probabilities'] = JSON.stringify(typeProbabilities);
    
    // 构建严重程度分布
    const severityDist = {};
    if (weather.low_severity_prob !== undefined) severityDist.low = weather.low_severity_prob;
    if (weather.medium_severity_prob !== undefined) severityDist.medium = weather.medium_severity_prob;
    if (weather.high_severity_prob !== undefined) severityDist.high = weather.high_severity_prob;
    if (weather.critical_severity_prob !== undefined) severityDist.critical = weather.critical_severity_prob;
    frontendConfig['Event.weather.severity_distribution'] = JSON.stringify(severityDist);
    
    // 固定速度因子（简化处理）
    frontendConfig['Event.weather.speed_factors'] = JSON.stringify({
        rain: { low: 0.8, medium: 0.6, high: 0.4, critical: 0.2 },
        snow: { low: 0.7, medium: 0.5, high: 0.3, critical: 0.1 },
        storm: { low: 0.6, medium: 0.4, high: 0.2, critical: 0.1 },
        sandstorm: { low: 0.5, medium: 0.3, high: 0.15, critical: 0.05 },
        fog: { low: 0.9, medium: 0.7, high: 0.5, critical: 0.3 }
    });
}

// 获取事件配置
async function fetchEventConfig() {
    try {
        let url = new URL('/sandbox/getEventConfig', window.location.origin);
        const response = await fetch(url);
        
        if (!response.ok) throw new Error('事件配置获取失败');
        
        const backendConfig = await response.json();
        console.log('后端原始配置:', backendConfig);
        
        // 转换后端配置格式
        const transformedConfig = transformBackendConfig(backendConfig);
        console.log('转换后配置:', transformedConfig);
        
        // 与默认配置合并
        const defaultConfig = getDefaultEventConfig();
        const finalConfig = { ...defaultConfig, ...transformedConfig };
        console.log('最终配置:', finalConfig);
        
        return finalConfig;
    } catch (error) {
        console.error('获取事件配置失败:', error);
        // 返回默认配置
        return getDefaultEventConfig();
    }
}


// 默认事件配置（扁平化结构）
function getDefaultEventConfig() {
    return {
        // 系统参数
        'Main.update_interval': 5,
        'Timer.tick_speed': 30,
        
        // 事件全局参数
        'Event.global_enabled': true,
        'Event.max_active_events': 8,
        'Event.global_probability': 0.6,
        
        // 天气事件 - 基本参数
        'Event.weather.enabled': true,
        'Event.weather.max_count': 3,
        'Event.weather.min_distance': 5000,
        'Event.weather.duration_range_min': 60,
        'Event.weather.duration_range_max': 180,
        'Event.weather.display_range': 10000,
        
        // 天气类型 - 使用JSON字符串存储
        'Event.weather.types_allowed': JSON.stringify(['rain', 'snow', 'storm', 'sandstorm', 'fog']),
        
        // 天气类型概率 - 使用JSON字符串存储
        'Event.weather.type_probabilities': JSON.stringify({
            rain: 0.4, 
            snow: 0.2, 
            storm: 0.1, 
            sandstorm: 0.1, 
            fog: 0.2
        }),
        
        // 天气严重程度分布 - 使用JSON字符串存储
        'Event.weather.severity_distribution': JSON.stringify({
            low: 0.5, 
            medium: 0.3, 
            high: 0.15, 
            critical: 0.05
        }),
        
        // 天气速度因子 - 使用JSON字符串存储
        'Event.weather.speed_factors': JSON.stringify({
            rain: { low: 0.8, medium: 0.6, high: 0.4, critical: 0.2 },
            snow: { low: 0.7, medium: 0.5, high: 0.3, critical: 0.1 },
            storm: { low: 0.6, medium: 0.4, high: 0.2, critical: 0.1 },
            sandstorm: { low: 0.5, medium: 0.3, high: 0.15, critical: 0.05 },
            fog: { low: 0.9, medium: 0.7, high: 0.5, critical: 0.3 }
        }),
        
        // 交通拥堵事件
        'Event.traffic_jam.enabled': true,
        'Event.traffic_jam.probability': 0.05,
        'Event.traffic_jam.max_count': 3,
        'Event.traffic_jam.duration_range_min': 10,
        'Event.traffic_jam.duration_range_max': 60,
        'Event.traffic_jam.severity_distribution': JSON.stringify({
            low: 0.4, 
            medium: 0.4, 
            high: 0.15, 
            critical: 0.05
        }),
        'Event.traffic_jam.speed_factors': JSON.stringify({
            low: 0.7, 
            medium: 0.4, 
            high: 0.2, 
            critical: 0.1
        }),
        
        // 交通事故事件
        'Event.accident.enabled': true,
        'Event.accident.probability': 0.02,
        'Event.accident.max_count': 3,
        'Event.accident.duration_range_min': 10,
        'Event.accident.duration_range_max': 45,
        'Event.accident.severity_distribution': JSON.stringify({
            low: 0.6, 
            medium: 0.3, 
            high: 0.08, 
            critical: 0.02
        }),
        'Event.accident.stop_durations': JSON.stringify({
            low: 0, 
            medium: 2, 
            high: 5, 
            critical: 10
        }),
        'Event.accident.speed_factors': JSON.stringify({
            low: 0.8, 
            medium: 0.6, 
            high: 0.4, 
            critical: 0.2
        }),
        
        // 道路封闭事件
        'Event.road_closure.enabled': true,
        'Event.road_closure.probability': 0.03,
        'Event.road_closure.max_count': 2,
        'Event.road_closure.duration_range_min': 15,
        'Event.road_closure.duration_range_max': 90,
        'Event.road_closure.severity_distribution': JSON.stringify({
            low: 0.3, 
            medium: 0.4, 
            high: 0.25, 
            critical: 0.05
        }),
        'Event.road_closure.closure_types': JSON.stringify({
            full: 0.3, 
            partial: 0.7
        })
    };
}

// 添加时间和天气显示
function addTimeWeatherDisplay() {
    const infoDiv = document.createElement('div');
    infoDiv.id = 'time-weather-info';
    infoDiv.style.cssText = `
        position: absolute;
        top: 20px;
        left: 50%;
        transform: translateX(-50%);
        background: rgba(0, 0, 0, 0.7);
        color: white;
        padding: 10px 20px;
        border-radius: 10px;
        z-index: 1000;
        font-family: Arial, sans-serif;
        font-size: 14px;
        display: flex;
        align-items: center;
        gap: 20px;
    `;
    
    infoDiv.innerHTML = `
        <div>
            <span style="font-weight: bold;">仿真时间: </span>
            <span id="simulation-time">08:00:00</span>
        </div>
        <div>
            <span style="font-weight: bold;">时间状态: </span>
            <span id="time-state">白天</span>
        </div>
        <div>
            <span style="font-weight: bold;">天气: </span>
            <span id="weather-state">晴</span>
        </div>
        <div>
            <span style="font-weight: bold;">周期: </span>
            <span id="simulation-cycle">0</span>
        </div>
    `;
    
    map.getContainer().appendChild(infoDiv);

    // 添加结束仿真按钮到时间信息栏右侧
    const stopBtn = document.createElement('button');
    stopBtn.id = 'close-transport';
    stopBtn.className = 'bottom-btn close-transport-btn';
    stopBtn.style.cssText = 'margin-left:12px;';
    stopBtn.innerHTML = `<span class="btn-icon">⏹️</span><span>结束仿真</span>`;
    infoDiv.appendChild(stopBtn);

    // 简单的前端通知函数（局部）
    function showToast(message, type = 'info') {
        const t = document.createElement('div');
        t.textContent = message;
        t.style.cssText = `position:fixed; top:80px; right:24px; background:${type==='error'?"rgba(231,76,60,0.95)":"rgba(46,204,113,0.95)"}; color:white; padding:10px 14px; border-radius:8px; z-index:20000;`;
        document.body.appendChild(t);
        setTimeout(()=>{ t.style.opacity='0'; setTimeout(()=>t.remove(),400); }, 3000);
    }

    // 绑定停止仿真逻辑
    stopBtn.addEventListener('click', async function() {
        if (!confirm('确定要结束仿真吗？这将会停止所有车辆的运行。')) return;
        stopBtn.disabled = true;
        const originalText = stopBtn.innerHTML;
        stopBtn.innerHTML = '<span class="btn-icon">⏳</span><span>停止中...</span>';

        try {
            const resp = await fetch('/sandbox/stopSimulation', { method: 'POST', headers: { 'Content-Type':'application/x-www-form-urlencoded' } });
            const result = await resp.json().catch(()=>({ success:false, message:'invalid response' }));

            if (result && result.success) {
                // 停止各类周期更新（map.html 及 main.js 中可能存在多个 interval）
                try { if (window.updateInterval) { clearInterval(window.updateInterval); window.updateInterval = null; } } catch(e) {console.warn(e);}                
                try { if (window.mainUpdateInterval) { clearInterval(window.mainUpdateInterval); window.mainUpdateInterval = null; } } catch(e) {console.warn(e);}                
                try { if (window.timeUpdateInterval) { clearInterval(window.timeUpdateInterval); window.timeUpdateInterval = null; } } catch(e) {console.warn(e);}                

                // 停止并销毁动画管理器与事件管理器（若存在）
                try { if (window.animationManager && typeof window.animationManager.destroy === 'function') window.animationManager.destroy(); } catch(e) {console.warn(e);}                
                try { if (window.eventManager && typeof window.eventManager.destroy === 'function') window.eventManager.destroy(); } catch(e) {console.warn(e);}                

                // 调用页面级清理函数（在 map.html 中定义）
                try { if (typeof updateAllVehiclesToStopped === 'function') updateAllVehiclesToStopped(); } catch(e){console.warn(e);}                
                try { if (typeof clearAllMapMarkers === 'function') clearAllMapMarkers(); } catch(e){console.warn(e);}                

                try { window.simulationStopped = true; } catch(e) { /* ignore */ }
                stopBtn.innerHTML = '<span class="btn-icon">✅</span><span>已停止</span>';
                showToast('仿真已成功停止', 'success');
            } else {
                stopBtn.disabled = false;
                stopBtn.innerHTML = originalText;
                showToast('停止仿真失败: ' + (result && result.message ? result.message : 'unknown'), 'error');
            }
        } catch (err) {
            console.error('停止仿真请求失败:', err);
            stopBtn.disabled = false;
            stopBtn.innerHTML = originalText;
            showToast('停止仿真请求失败，请检查网络连接', 'error');
        }
    });
}

async function initPOI() {
    // 获取所有POI数据
    let data = await getPOIData();
    
    // 建立 class+type 到图标键的映射关系
    const iconMapping = {
        "ResourcePlant_PHARMA": "pharmaProducer",
        "ResourcePlant_STEEL": "steelProducer",
        "ResourcePlant_WOOD": "woodProducer",
        "ProcessPlant_PHARMA": "pharmaProcessor",
        "ProcessPlant_STEEL": "steelProcessor",
        "ProcessPlant_WOOD": "woodProcessor",
        "Market_PHARMA": "pharmamarket",  // 假设Market也有type字段
        "Market_STEEL": "steelmarket",
        "Market_WOOD": "woodmarket"
        // 如果需要，可以添加Market类型的映射
    };

    data.forEach(poi => {
        // 构建映射键
        const mappingKey = `${poi.class}_${poi.type}`;
        const iconKey = iconMapping[mappingKey];
        
        if (!iconKey) {
            console.warn(`未找到POI类型对应的图标: class=${poi.class}, type=${poi.type}`);
            return; // 跳过没有对应图标的POI
        }
        
        const iconSrc = POIIconSrc[iconKey];
        if (!iconSrc) {
            console.error(`图标键 ${iconKey} 在POIIconSrc中不存在`);
            return;
        }

        const icon = new AMap.Icon({
            size: new AMap.Size(16, 16),
            image: iconSrc,
            imageSize: new AMap.Size(16, 16)
        });

        const marker = new AMap.Marker({
            title: poi.name,
            position: new AMap.LngLat(poi.lon, poi.lat),
            icon: icon,
            map: map
        });

        POIs.push({
            UUID: poi.UUID,
            marker: marker,
            class: poi.class,
            type: poi.type,
            name: poi.name
        });
    });
    
    console.log(`兴趣点添加成功, 总数: ${data.length}`);
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
            position: new AMap.LngLat(car.location_lon, car.location_lat),
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
async function getPOIData() {
    try {
        let url = new URL('/data/getPoiData', window.location.origin);
        // 不再需要type参数，获取所有POI数据

        const response = await fetch(url);
        if (!response.ok) throw new Error('POI 数据获取失败');

        let data = await response.json();
        if (!Array.isArray(data)) throw new Error('POI 数据无效', { cause: data + '无效' });

        data.forEach(item => {
            let isValid = item.hasOwnProperty("UUID")
                && item.hasOwnProperty("name")
                && item.hasOwnProperty("lat")
                && item.hasOwnProperty("lon")
                && item.hasOwnProperty("class")
                && item.hasOwnProperty("type");
            if (!isValid) throw new Error('POI 数据解析失败', { cause: item + '解析出错' });
        });
        return data;
    } catch (error) {
        console.error('POI 数据获取错误: ', error);
        return [{ "Exception": "error occurs!" }];
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

        return data;
    }
    catch (error)
    {
        console.error('车辆数据获取错误: ', error);
        return [{"Exception": "error occurs!"}];
    }
}

// 修改update函数
async function update()
{
    await updateCars();//车辆位置更新
    
    // 更新事件管理器
    if (eventManager) {
        eventManager.update();
        console.log('事件管理器更新完成');
    }
}

// 更新时间和天气显示
function updateTimeWeatherDisplay() {
    if (!eventManager || !eventManager.timeManager) return;
    
    const timeInfo = eventManager.timeManager.getSimulationInfo();
    const timeStateMap = {
        'day': '白天',
        'morning_peak': '早高峰',
        'evening_peak': '晚高峰',
        'night': '夜晚'
    };
    
    const weatherMap = {
        'clear': '晴',
        'rain': '雨',
        'snow': '雪',
        'storm': '暴雨',
        'sandstorm': '沙尘暴',
        'fog': '雾'
    };
    
    // 获取当前显示的天气
    const currentWeather = eventManager.currentWeather || 'clear';
    
    document.getElementById('simulation-time').textContent = timeInfo.time;
    document.getElementById('time-state').textContent = timeStateMap[timeInfo.state] || timeInfo.state;
    document.getElementById('weather-state').textContent = weatherMap[currentWeather] || currentWeather;
    document.getElementById('simulation-cycle').textContent = eventManager.simulationCycle || 0;
}

/**
 * 车辆位置及状态更新
 */
async function updateCars() {
    if (isCarUpdating) return; // 防止重入
    if (window.simulationStopped) return; // 快速退出以减轻停仿真时的负载
    isCarUpdating = true;

    const toProcess = cars.filter(c => c.status === 0);
    const concurrency = 3; // 并发处理车辆数（根据机器性能调整）
    let idx = 0;

    async function worker() {
        while (true) {
            const i = idx++;
            if (i >= toProcess.length) break;
            if (window.simulationStopped) break;
            const car = toProcess[i];
            try {
                const recivdata = await sendPara(car.UUID, 0, 0);
                if (!recivdata) continue;

                const end = [recivdata.lon, recivdata.lat];
                const currentLngLat = car.marker.getPosition();
                const start = [currentLngLat.lng, currentLngLat.lat];

                // 获取避让区域
                let avoidPolygons = [];
                if (eventManager && eventManager.closurePaths && eventManager.closurePaths.length > 0) {
                    avoidPolygons = eventManager.closurePaths.map(cp => cp.event.path);
                }

                // 路径规划（并发少量即可）
                let route;
                if (avoidPolygons.length > 0 && eventManager) {
                    route = await eventManager.planRouteWithAvoidance(start, end, avoidPolygons);
                } else {
                    route = await planRoute(start, end);
                }

                if (!route || !route.steps || route.steps.length === 0 || typeof route.distance !== 'number') {
                    continue;
                }

                car.status = 1;
                const routeInfo = await drawRoute(route);

                routeInfo.originalDistance = route.distance;
                routeInfo.originalTime = route.time;
                routeInfo.additionalDistance = 0;
                routeInfo.additionalTime = 0;
                car.info = routeInfo;

                car.accidentEvent = null;
                car.trafficJamEvent = null;
                car.roadClosureEvent = null;
                car.isEvent = 0;

                if (animationManager) {
                    animationManager.startVehicleAnimation(car, route);
                }
            } catch (err) {
                console.warn(`处理车辆 ${car.UUID} 时出错:`, err);
            }
        }
    }

    const workers = Array(Math.min(concurrency, toProcess.length)).fill(0).map(() => worker());
    try {
        await Promise.all(workers);
    } catch (e) {
        console.warn('部分车辆处理时出现错误', e);
    }

    // 对于处于行驶状态的车辆，触发事件检查（单线程）
    try {
        for (const car of cars) {
            if (car.status === 1 && eventManager) {
                eventManager.checkVehicleEvents(car);
            }
        }
    } catch (e) { console.warn('事件检查错误', e); }

    isCarUpdating = false;
}


async function VideoCars(marker, route, vehicleId) {
    // 保持兼容性，调用动画管理器
    const vehicle = cars.find(v => v.UUID === vehicleId);
    if (vehicle && animationManager) {
        animationManager.startVehicleAnimation(vehicle, route);
    }
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

    const polyline = new AMap.Polyline({
        path: path,
        isOutline: true,
        outlineColor: '#1a1919ff',
        borderWeight: 2,
        strokeWeight: 3,
        strokeOpacity: 0.9,
        strokeColor: '#5360d7ff',
        lineJoin: 'round'
    });

    map.add(polyline);
//车辆运行
   const time=path.length*duration;
    let routeInfo = {
        startMarker:startMarker,
        endMarker:endMarker,
        route:Route,
        Time:time
    };
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
async function cartransporting(routeInfo, carUUID) {
    const carIndex = cars.findIndex(car => car.UUID === carUUID);
    if (carIndex === -1) return;
    
    const car = cars[carIndex];
    
    // 停止动画
    if (animationManager) {
        animationManager.stopVehicleAnimation(carUUID);
    }
    
    // 恢复车辆空闲状态
    car.status = 0;
    car.isEvent = 0;
    car.accidentEvent = null;
    car.trafficJamEvent = null;
    car.roadClosureEvent = null;
    car.closureInfo = null;
    
    // 清理事件关联
    if (eventManager && eventManager.speedManager) {
        eventManager.speedManager.updateFactor(carUUID, 1.0);
    }
    
    // 计算总距离和时间
    let totalDistance = routeInfo.originalDistance || (routeInfo.route ? routeInfo.route.distance : 0);
    let totalTime = routeInfo.originalTime || (routeInfo.route ? routeInfo.route.time : 0);
    
    if (routeInfo.additionalDistance) {
        totalDistance += routeInfo.additionalDistance;
    }
    
    if (routeInfo.additionalTime) {
        totalTime += routeInfo.additionalTime;
    }
    
    // 清理路线相关元素
    if (routeInfo.startMarker) map.remove(routeInfo.startMarker);
    if (routeInfo.endMarker) map.remove(routeInfo.endMarker);
    if (routeInfo.route) map.remove(routeInfo.route);
    
    // 发送完成信息
    const nousedc = await sendPara(carUUID, totalDistance, totalTime);
    console.log(`车辆 ${carUUID} 已完成运输，总距离: ${totalDistance}, 总时间: ${totalTime}`);
}


