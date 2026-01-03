import { WeatherEffectLayer } from './weather-layer.js';//引入天气特效图层
//随机事件管理模块
export const EventType = {
    WEATHER: 'weather',
    ROAD_CLOSURE: 'road_closure',
    TRAFFIC_JAM: 'traffic_jam',
    ACCIDENT: 'accident',
    SPECIAL_EVENT: 'special_event'
};

export const WeatherType = {
    CLEAR: 'clear',
    RAIN: 'rain',
    SNOW: 'snow',
    FOG: 'fog',
    STORM: 'storm',
    SANDSTORM: 'sandstorm'  // 添加沙尘暴
};

export const EventSeverity = {
    LOW: 'low',
    MEDIUM: 'medium',
    HIGH: 'high',
    CRITICAL: 'critical'
};

export class RandomEvent {
    constructor(type, severity, position, radius, duration, options = {}) {
        this.id = `event_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
        this.type = type;
        this.severity = severity;
        this.position = position; // [lng, lat]
        this.radius = radius; // 影响半径(m)
        this.duration = duration; // 持续时间(ms)
        this.startTime = Date.now();
        this.endTime = this.startTime + duration;
        this.options = options;
        this.marker = null;
        this.circle = null;
        this.active = true;
        this.affectedVehicles = new Set();
    }

    isExpired() {
        return Date.now() > this.endTime;
    }

    isActive() {
        return this.active && !this.isExpired();
    }

    deactivate() {
        this.active = false;
        this.affectedVehicles.clear();
    }
}

export class EventManager {
     static EVENT_PRIORITY = {
        [EventType.ACCIDENT]: 5,       // 最高优先级
        [EventType.ROAD_CLOSURE]: 4,
        [EventType.SPECIAL_EVENT]: 3,
        [EventType.TRAFFIC_JAM]: 2,
        [EventType.WEATHER]: 1         // 最低优先级
    };
    constructor(map, cars, config = {}) {
        this.map = map;
        this.cars = cars;
        this.events = new Map();
         this.weatherLayer = null;  //天气特效
        this.heatmap = null;//热力图（交通堵塞）
        this.eventIcons = {
            [EventType.WEATHER]: './resources/WeatherEvent.png',
            [EventType.ROAD_CLOSURE]: './resources/RoadClosure.png',
            [EventType.TRAFFIC_JAM]: './resources/TrafficJam.png',
            [EventType.ACCIDENT]: './resources/Accident.png',
            [EventType.SPECIAL_EVENT]: './resources/SpecialEvent.png'
        };
         this.eventGenerationTimer = null;
    this.eventUpdateTimer = null;
    this.vehicleEffectCheckTimer = null;
        // 配置参数
        this.config = {
            eventInterval: 15000, // 15秒
            minEventDuration: 60000, // 1分钟
            maxEventDuration: 300000, // 5分钟
            eventProbability: 0.8, // 60%概率
            maxActiveEvents: 8, // 最多8个事件
            checkVehicleInterval: 2000,
            ...config
        };
        
        this.eventHandlers = new Map();
        this.vehicleEffects = new Map();
        this.initialized = false;
        
        // 初始化事件图标
        this.initIcons();
    }

    init() {
        if (this.initialized) return;
        
        console.log('事件管理器初始化...');
        this.initialized = true;
        
        // 注册事件处理器
        this.registerEventHandlers();
        
        // 开始事件生成
        this.startEventGeneration();
        
        // 开始事件更新循环
        this.startEventUpdate();
        
        // 开始车辆影响检查
        this.startVehicleEffectCheck();

        //初始化天气图层和热力图
        this.initVisualLayers();
        
        console.log('事件管理器初始化完成');
    }

      initVisualLayers() {
        //初始化天气图层
        if (!this.weatherLayer && this.map) {
            this.weatherLayer = new WeatherEffectLayer(this.map);
        }
        
        //初始化热力图
        this.initHeatmap();
    }

    initIcons() {
        // 预加载事件图标
        this.eventIcons = {
            [EventType.WEATHER]: {
                url: './resources/WeatherEvent.png',
                size: [32, 32]
            },
            [EventType.ROAD_CLOSURE]: {
                url: './resources/RoadClosure.png',
                size: [32, 32]
            },
            [EventType.TRAFFIC_JAM]: {
                url: './resources/TrafficJam.png',
                size: [32, 32]
            },
            [EventType.ACCIDENT]: {
                url: './resources/Accident.png',
                size: [32, 32]
            },
            [EventType.SPECIAL_EVENT]: {
                url: './resources/SpecialEvent.png',
                size: [32, 32]
            }
        };
    }

    

    registerEventHandlers() {
        // 天气事件处理器
        this.registerEventHandler(EventType.WEATHER, this.handleWeatherEvent.bind(this));
        
        // 道路封闭处理器
        this.registerEventHandler(EventType.ROAD_CLOSURE, this.handleRoadClosure.bind(this));
        
        // 交通拥堵处理器
        this.registerEventHandler(EventType.TRAFFIC_JAM, this.handleTrafficJam.bind(this));
        
        // 事故处理器
        this.registerEventHandler(EventType.ACCIDENT, this.handleAccident.bind(this));
        
        // 特殊事件处理器
        this.registerEventHandler(EventType.SPECIAL_EVENT, this.handleSpecialEvent.bind(this));
    }

    registerEventHandler(eventType, handler) {
        if (!this.eventHandlers.has(eventType)) {
            this.eventHandlers.set(eventType, []);
        }
        this.eventHandlers.get(eventType).push(handler);
    }

    startEventGeneration() {
        // 清理旧的定时器
        if (this.eventGenerationTimer) {
            clearInterval(this.eventGenerationTimer);
        }
        
        this.eventGenerationTimer = setInterval(() => {
            if (this.shouldGenerateEvent()) {
                this.generateRandomEvent();
            }
        }, this.config.eventInterval);
    }

    startEventUpdate() {
        if (this.eventUpdateTimer) {
            clearInterval(this.eventUpdateTimer);
        }
        
        this.eventUpdateTimer = setInterval(() => {
            this.updateEvents();
        }, 5000);
    }


    startVehicleEffectCheck() {
        if (this.vehicleEffectCheckTimer) {
            clearInterval(this.vehicleEffectCheckTimer);
        }
        
        this.vehicleEffectCheckTimer = setInterval(() => {
            this.checkVehicleEffects();
        }, 3000);
    }

    shouldGenerateEvent() {
        if (this.events.size >= this.config.maxActiveEvents) {
            return false;
        }
        
        const chance = Math.random();
        return chance <= this.config.eventProbability;
    }

    generateRandomEvent() {
        const eventTypes = Object.values(EventType);
        const randomType = eventTypes[Math.floor(Math.random() * eventTypes.length)];
        const severityLevels = Object.values(EventSeverity);
        const randomSeverity = severityLevels[Math.floor(Math.random() * severityLevels.length)];
        
        // 在地图可见区域内随机生成位置
        const bounds = this.map.getBounds();
        const sw = bounds.getSouthWest();
        const ne = bounds.getNorthEast();
        
        const lng = sw.lng + Math.random() * (ne.lng - sw.lng);
        const lat = sw.lat + Math.random() * (ne.lat - sw.lat);
        
        const duration = this.config.minEventDuration + 
                        Math.random() * (this.config.maxEventDuration - this.config.minEventDuration);
        
        let event;
        const position = [lng, lat];
        
        switch (randomType) {
            case EventType.WEATHER:
                const weatherTypes = Object.values(WeatherType);
                const randomWeather = weatherTypes[Math.floor(Math.random() * weatherTypes.length)];
                event = new RandomEvent(
                    randomType,
                    randomSeverity,
                    position,
                    500 + Math.random() * 1500, // 500-2000米半径
                    duration,
                    {
                        weatherType: randomWeather,
                        intensity: Math.random() * 0.8 + 0.2 // 0.2-1.0强度
                    }
                );
                break;
                
            case EventType.ROAD_CLOSURE:
                event = new RandomEvent(
                    randomType,
                    randomSeverity,
                    position,
                    100 + Math.random() * 400, // 100-500米半径
                    duration,
                    {
                        closureType: Math.random() > 0.5 ? 'full' : 'partial',
                        reason: ['construction', 'maintenance', 'event'][Math.floor(Math.random() * 3)]
                    }
                );
                break;
                
            case EventType.TRAFFIC_JAM:
                event = new RandomEvent(
                    randomType,
                    randomSeverity,
                    position,
                    300 + Math.random() * 700, // 300-1000米半径
                    duration,
                    {
                        congestionLevel: Math.random(),
                        expectedDelay: Math.floor(Math.random() * 30) + 5 // 5-35分钟
                    }
                );
                break;
                
            case EventType.ACCIDENT:
                event = new RandomEvent(
                    randomType,
                    randomSeverity,
                    position,
                    200 + Math.random() * 300, // 200-500米半径
                    duration,
                    {
                        severity: randomSeverity,
                        lanesAffected: Math.floor(Math.random() * 3) + 1
                    }
                );
                break;
                
            default:
                event = new RandomEvent(
                    randomType,
                    randomSeverity,
                    position,
                    300 + Math.random() * 700,
                    duration
                );
        }
        
        this.addEvent(event);
        this.notifyEventGenerated(event);
        
        return event;
    }

    // 添加清理过期车辆影响的方法
    cleanupExpiredVehicleEffects() {
        const now = Date.now();
        const toRemove = [];
        
        for (const [id, effect] of this.vehicleEffects) {
            // 查找对应的事件
            const event = this.events.get(effect.eventId);
            if (!event || !event.isActive()) {
                toRemove.push(id);
            }
        }
        
        // 清理无效的影响
        toRemove.forEach(id => {
            this.vehicleEffects.delete(id);
        });
    }

    addEvent(event) {
        this.events.set(event.id, event);
        this.addEventToMap(event);
        this.applyEventEffects(event);
        
        console.log(`事件生成: ${event.type} (${event.severity}) 在位置 [${event.position[0].toFixed(4)}, ${event.position[1].toFixed(4)}]`);
    }

    addEventToMap(event) {
    const severityColors = {
        low: '#4CAF50',
        medium: '#FF9800',
        high: '#F44336',
        critical: '#9C27B0'
    };
    
    // 只创建影响范围圆形，不创建图标标记
    const circleColor = this.getSeverityColor(event.severity);
    const circle = new AMap.Circle({
        center: event.position,
        radius: event.radius,
        strokeColor: circleColor,
        strokeOpacity: 0.6, // 提高透明度以便看清楚
        strokeWeight: 3,
        fillColor: circleColor,
        fillOpacity: 0.2,
        map: this.map,
        zIndex: 99,
        strokeStyle: 'solid', // 改为实线
        strokeDasharray: null // 移除虚线
    });
    
    // 创建脉冲效果
    const pulseInterval = this.createPulseEffect(event, circle, circleColor);
    
    // 添加点击事件到圆形区域
    circle.on('click', () => {
        this.showEventDetails(event);
    });
    
    // 存储动画ID以便清理
    event.pulseInterval = pulseInterval;
    event.circle = circle;
    event.marker = null; // 不创建marker，设为null
    
    // 如果是天气事件，添加到天气图层
    if (event.type === EventType.WEATHER && this.weatherLayer) {
        this.weatherLayer.addWeatherEffect(event);
    }
}

    //创建脉冲效果
    createPulseEffect(event, circle, color) {
        let pulseCount = 0;
        const pulseInterval = setInterval(() => {
            if (!event.active) {
                clearInterval(pulseInterval);
                return;
            }
            
            const pulseCircle = new AMap.Circle({
                center: circle.getCenter(),
                radius: circle.getRadius(),
                strokeColor: color,
                strokeOpacity: 0.6 - (pulseCount * 0.1),
                strokeWeight: 2,
                fillColor: color,
                fillOpacity: 0.2 - (pulseCount * 0.05),
                map: this.map,
                zIndex: 98
            });
            
            const duration = 2000;
            const startTime = Date.now();
            
            const animatePulse = () => {
                const elapsed = Date.now() - startTime;
                const progress = Math.min(elapsed / duration, 1);
                
                if (progress < 1) {
                    pulseCircle.setRadius(circle.getRadius() * (1 + progress * 0.5));
                    pulseCircle.setOptions({
                        strokeOpacity: 0.6 * (1 - progress),
                        fillOpacity: 0.2 * (1 - progress)
                    });
                    requestAnimationFrame(animatePulse);
                } else {
                    pulseCircle.setMap(null);
                }
            };
            
            animatePulse();
            pulseCount = (pulseCount + 1) % 3;
            
        }, 1000);
        
        return pulseInterval;
    }

    removeEventFromMap(event) {
        if (event.marker) {
            event.marker.setMap(null);
            event.marker = null;
        }
        if (event.circle) {
            event.circle.setMap(null);
            event.circle = null;
        }
        
        // 清理动画
        if (event.floatInterval) {
            clearInterval(event.floatInterval);
        }
        if (event.pulseInterval) {
            clearInterval(event.pulseInterval);
        }
        
        // 从天气图层移除
        if (event.type === EventType.WEATHER && this.weatherLayer) {
            this.weatherLayer.removeEffect(event.id);
        }
    }

    updateEvents() {
        const now = Date.now();
        const expiredEvents = [];
        
        for (const [id, event] of this.events) {
            if (event.isExpired()) {
                expiredEvents.push(id);
                this.removeEvent(event);
            } else if (event.isActive()) {
                this.updateEventVisuals(event);
            }
        }
        
        // 移除过期事件
        expiredEvents.forEach(id => {
            this.events.delete(id);
        });
        
        // 清理过期的车辆影响（内存管理）
        this.cleanupExpiredVehicleEffects();
    }

    removeEvent(event) {
        this.removeEventFromMap(event);
        this.removeEventEffects(event);
        event.deactivate();
        
        console.log(`事件结束: ${event.type} (${event.id})`);
        this.notifyEventEnded(event);
    }

    updateEventVisuals(event) {
        if (event.circle) {
            //让影响范围闪烁，根据事件类型不同效果
            const time = Date.now();
            
            switch (event.type) {
                case EventType.WEATHER:
                    //天气事件使用波浪效果
                    const waveOpacity = 0.1 + 0.1 * Math.sin(time / 1000);
                    event.circle.setOptions({
                        fillOpacity: waveOpacity,
                        strokeDasharray: [Math.sin(time / 500) * 5 + 10, 5]
                    });
                    break;
                    
                case EventType.TRAFFIC_JAM:
                    //拥堵事件呼吸效果
                    const breath = 0.05 * Math.sin(time / 800);
                    event.circle.setOptions({
                        fillOpacity: 0.1 + breath,
                        strokeOpacity: 0.3 + breath
                    });
                    break;
                    
                case EventType.ACCIDENT:
                    //事故事件快速闪烁
                    const flash = Math.sin(time / 200) > 0 ? 1 : 0;
                    event.circle.setOptions({
                        fillOpacity: 0.2 * flash,
                        strokeOpacity: 0.5 * flash
                    });
                    break;
                    
                default:
                    //默认效果
                    const opacity = 0.1 + 0.1 * Math.sin(time / 500);
                    event.circle.setOptions({
                        fillOpacity: opacity
                    });
            }
        }
    }

    getSeverityColor(severity) {
        const colors = {
            [EventSeverity.LOW]: '#4CAF50', // 绿色
            [EventSeverity.MEDIUM]: '#FF9800', // 橙色
            [EventSeverity.HIGH]: '#F44336', // 红色
            [EventSeverity.CRITICAL]: '#9C27B0' // 紫色
        };
        return colors[severity] || '#757575';
    }

    getEventTitle(event) {
        const titles = {
            [EventType.WEATHER]: `${this.getWeatherName(event.options.weatherType)} - ${event.severity}`,
            [EventType.ROAD_CLOSURE]: `道路${event.options.closureType === 'full' ? '封闭' : '部分封闭'} - ${event.severity}`,
            [EventType.TRAFFIC_JAM]: `交通拥堵 - ${event.severity}`,
            [EventType.ACCIDENT]: `交通事故 - ${event.severity}`,
            [EventType.SPECIAL_EVENT]: `特殊事件 - ${event.severity}`
        };
        return titles[event.type] || '随机事件';
    }

    getWeatherName(weatherType) {
        const names = {
            [WeatherType.CLEAR]: '晴朗',
            [WeatherType.RAIN]: '降雨',
            [WeatherType.SNOW]: '降雪',
            [WeatherType.FOG]: '大雾',
            [WeatherType.STORM]: '暴风雨'
        };
        return names[weatherType] || '未知天气';
    }

    applyEventEffects(event) {
        const handlers = this.eventHandlers.get(event.type) || [];
        handlers.forEach(handler => {
            try {
                handler(event);
            } catch (error) {
                console.error(`事件处理器错误: ${error.message}`);
            }
        });
    }

    removeEventEffects(event) {
        // 移除对车辆的影响
        event.affectedVehicles.forEach(vehicleId => {
            this.removeVehicleEffect(vehicleId, event.id);
        });
    }

    handleWeatherEvent(event) {
        // 查找影响范围内的车辆
        this.cars.forEach(car => {
            if (this.isVehicleInRange(car, event)) {
                this.applyWeatherEffect(car, event);
                event.affectedVehicles.add(car.UUID);
            }
        });
        
        // 更新地图视觉效果
        this.applyWeatherVisuals(event);
    }

    handleRoadClosure(event) {
        // 道路封闭影响车辆路径规划
        this.cars.forEach(car => {
            if (this.isVehicleInRange(car, event)) {
                this.applyRoadClosureEffect(car, event);
                event.affectedVehicles.add(car.UUID);
            }
        });
    }

    handleTrafficJam(event) {
        // 交通拥堵影响车辆速度
        this.cars.forEach(car => {
            if (this.isVehicleInRange(car, event)) {
                this.applyTrafficJamEffect(car, event);
                event.affectedVehicles.add(car.UUID);
            }
        });
    }

    handleAccident(event) {
        // 事故可能导致车辆绕行
        this.cars.forEach(car => {
            if (this.isVehicleInRange(car, event)) {
                this.applyAccidentEffect(car, event);
                event.affectedVehicles.add(car.UUID);
            }
        });
    }

    handleSpecialEvent(event) {
        // 特殊事件可能有各种影响
        this.cars.forEach(car => {
            if (this.isVehicleInRange(car, event)) {
                this.applySpecialEventEffect(car, event);
                event.affectedVehicles.add(car.UUID);
            }
        });
    }

    isVehicleInRange(car, event) {
        if (!car.marker) return false;
        
        const carPos = car.marker.getPosition();
        const eventPos = new AMap.LngLat(event.position[0], event.position[1]);
        
        const distance = carPos.distance(eventPos);
        return distance <= event.radius;
    }

    applyWeatherEffect(car, event) {
        const effectId = `${car.UUID}_${event.id}`;
        
        // 根据天气类型和强度计算影响因子
        let speedFactor = 1.0;
        let consumptionFactor = 1.0;
        
        switch (event.options.weatherType) {
            case WeatherType.RAIN:
                speedFactor = 0.7 + (1 - event.options.intensity) * 0.3;
                consumptionFactor = 1.0 + event.options.intensity * 0.3;
                break;
            case WeatherType.SNOW:
                speedFactor = 0.5 + (1 - event.options.intensity) * 0.3;
                consumptionFactor = 1.0 + event.options.intensity * 0.5;
                break;
            case WeatherType.FOG:
                speedFactor = 0.6 + (1 - event.options.intensity) * 0.3;
                consumptionFactor = 1.0 + event.options.intensity * 0.2;
                break;
            case WeatherType.STORM:
                speedFactor = 0.4;
                consumptionFactor = 1.5;
                break;
            case WeatherType.SANDSTORM:
                speedFactor = 0.3; // 沙尘暴影响更大
                consumptionFactor = 1.8;
                break;
        }
        
        // 根据事件严重程度调整影响
        const severityFactor = {
            [EventSeverity.LOW]: 0.9,
            [EventSeverity.MEDIUM]: 0.7,
            [EventSeverity.HIGH]: 0.5,
            [EventSeverity.CRITICAL]: 0.3
        }[event.severity] || 0.8;
        
        speedFactor *= severityFactor;
        
        this.vehicleEffects.set(effectId, {
            vehicleId: car.UUID,
            eventId: event.id,
            type: 'weather',
            speedFactor: speedFactor,
            consumptionFactor: consumptionFactor,
            message: `${this.getWeatherName(event.options.weatherType)}影响中`
        });
        
        console.log(`车辆 ${car.UUID} 受到天气影响: ${event.options.weatherType}, 速度因子: ${speedFactor.toFixed(2)}`);
    }

    applyRoadClosureEffect(car, event) {
        const effectId = `${car.UUID}_${event.id}`;
        
        this.vehicleEffects.set(effectId, {
            vehicleId: car.UUID,
            eventId: event.id,
            type: 'road_closure',
            requiresReroute: true,
            message: '前方道路封闭，需要重新规划路线'
        });
        
        // 标记车辆需要重新规划路线
        if (car.marker) {
            car.marker.setzIndex(101); // 提高层级以突出显示
        }
        
        console.log(`车辆 ${car.UUID} 遇到道路封闭`);
    }

    applyTrafficJamEffect(car, event) {
        const effectId = `${car.UUID}_${event.id}`;
        
        // 根据拥堵等级计算速度影响
        const congestion = event.options.congestionLevel;
        const speedFactor = 1.0 - congestion * 0.7; // 最多降低70%速度
        
        this.vehicleEffects.set(effectId, {
            vehicleId: car.UUID,
            eventId: event.id,
            type: 'traffic_jam',
            speedFactor: speedFactor,
            expectedDelay: event.options.expectedDelay,
            message: `交通拥堵，预计延迟${event.options.expectedDelay}分钟`
        });
        
        console.log(`车辆 ${car.UUID} 遇到交通拥堵，速度因子: ${speedFactor.toFixed(2)}`);
    }

    applyAccidentEffect(car, event) {
        const effectId = `${car.UUID}_${event.id}`;
        
        const severityFactor = {
            [EventSeverity.LOW]: 0.8,
            [EventSeverity.MEDIUM]: 0.6,
            [EventSeverity.HIGH]: 0.4,
            [EventSeverity.CRITICAL]: 0.2
        }[event.severity] || 0.5;
        
        this.vehicleEffects.set(effectId, {
            vehicleId: car.UUID,
            eventId: event.id,
            type: 'accident',
            speedFactor: severityFactor,
            requiresReroute: event.severity === EventSeverity.CRITICAL || event.severity === EventSeverity.HIGH,
            message: `前方事故(${event.severity})，${event.severity === EventSeverity.CRITICAL || event.severity === EventSeverity.HIGH ? '建议绕行' : '减速慢行'}`
        });
        
        console.log(`车辆 ${car.UUID} 遇到交通事故，严重程度: ${event.severity}`);
    }

    applySpecialEventEffect(car, event) {
        const effectId = `${car.UUID}_${event.id}`;
        
        // 特殊事件可能有随机效果
        const randomEffect = Math.random();
        let effect;
        
        if (randomEffect < 0.3) {
            effect = {
                type: 'special_speed_boost',
                speedFactor: 1.3,
                message: '特殊事件：道路畅通，速度提升'
            };
        } else if (randomEffect < 0.6) {
            effect = {
                type: 'special_consumption_reduction',
                consumptionFactor: 0.8,
                message: '特殊事件：能耗降低'
            };
        } else {
            effect = {
                type: 'special_reroute',
                requiresReroute: true,
                message: '特殊事件：建议更换路线'
            };
        }
        
        this.vehicleEffects.set(effectId, {
            vehicleId: car.UUID,
            eventId: event.id,
            ...effect
        });
        
        console.log(`车辆 ${car.UUID} 受到特殊事件影响: ${effect.type}`);
    }

    applyWeatherVisuals(event) {
        // 根据天气类型添加地图视觉效果
        switch (event.options.weatherType) {
            case WeatherType.RAIN:
                // 可以添加雨滴效果
                this.addRainEffect(event);
                break;
            case WeatherType.FOG:
                // 可以添加雾效果
                this.addFogEffect(event);
                break;
            case WeatherType.SNOW:
                // 可以添加雪效果
                this.addSnowEffect(event);
                break;
            case WeatherType.SANDSTORM:
                this.addSandstormEffect(event);  // 添加沙尘暴效果
                break;
        }
    }

    addRainEffect(event) {
        // 这里可以添加雨滴的视觉效果
        // 实际实现可能需要使用canvas或叠加图层
        console.log(`降雨效果在位置 [${event.position[0]}, ${event.position[1]}]`);
    }

    addFogEffect(event) {
        // 添加雾效果
        console.log(`雾效果在位置 [${event.position[0]}, ${event.position[1]}]`);
    }

    addSnowEffect(event) {
        // 添加雪效果
        console.log(`降雪效果在位置 [${event.position[0]}, ${event.position[1]}]`);
    }

    addSandstormEffect(event) {
        // 添加沙尘暴视觉效果
        console.log(`沙尘暴效果在位置 [${event.position[0]}, ${event.position[1]}]`);
        
        // 可以在这里添加沙尘暴的Canvas效果
        if (this.weatherLayer) {
            // 通过天气图层添加沙尘暴效果
            this.weatherLayer.addSandstormEffect(event);
        }
    }

    checkVehicleEffects() {
        // 定期检查车辆是否仍在事件影响范围内
        for (const [id, event] of this.events) {
            if (!event.isActive()) continue;
            
            // 检查当前受影响的车辆
            const toRemove = [];
            event.affectedVehicles.forEach(vehicleId => {
                const car = this.cars.find(c => c.UUID === vehicleId);
                if (!car || !this.isVehicleInRange(car, event)) {
                    toRemove.push(vehicleId);
                }
            });
            
            // 移除不在范围内的车辆
            toRemove.forEach(vehicleId => {
                event.affectedVehicles.delete(vehicleId);
                this.removeVehicleEffect(vehicleId, event.id);
            });
            
            // 检查是否有新车辆进入范围
            this.cars.forEach(car => {
                if (!event.affectedVehicles.has(car.UUID) && this.isVehicleInRange(car, event)) {
                    this.applyEventEffects(event);
                }
            });
        }
    }

    removeVehicleEffect(vehicleId, eventId) {
        const effectId = `${vehicleId}_${eventId}`;
        if (this.vehicleEffects.has(effectId)) {
            const effect = this.vehicleEffects.get(effectId);
            console.log(`移除车辆 ${vehicleId} 的事件影响: ${effect.type}`);
            this.vehicleEffects.delete(effectId);
        }
    }

    getVehicleEffects(vehicleId) {
        const effects = [];
        const priorityEffects = new Map(); // 按类型存储优先级最高的影响
        
        for (const [id, effect] of this.vehicleEffects) {
            if (effect.vehicleId === vehicleId) {
                const priority = EVENT_PRIORITY[effect.eventType] || 0;
                
                // 如果已经有同类型的影响，比较优先级
                if (!priorityEffects.has(effect.type) || 
                    priority > priorityEffects.get(effect.type).priority) {
                    priorityEffects.set(effect.type, {
                        ...effect,
                        priority: priority
                    });
                }
            }
        }
        
        // 将优先级最高的影响转换为数组
        for (const [type, effect] of priorityEffects) {
            effects.push(effect);
        }
        
        // 应用影响叠加规则
        return this.applyEffectCombinationRules(effects);
    }

    applyEffectCombinationRules(effects) {
        if (effects.length === 0) return [];
        
        // 初始化最终影响
        const combinedEffect = {
            speedFactor: 1.0,
            consumptionFactor: 1.0,
            requiresReroute: false,
            messages: []
        };
        
        // 按优先级排序
        effects.sort((a, b) => (b.priority || 0) - (a.priority || 0));
        
        effects.forEach(effect => {
            // 速度因子取最小值（最严重的影响）
            if (effect.speedFactor !== undefined) {
                combinedEffect.speedFactor = Math.min(
                    combinedEffect.speedFactor, 
                    effect.speedFactor
                );
            }
            
            // 能耗因子取最大值（最严重的影响）
            if (effect.consumptionFactor !== undefined) {
                combinedEffect.consumptionFactor = Math.max(
                    combinedEffect.consumptionFactor,
                    effect.consumptionFactor
                );
            }
            
            // 重新规划路线标志，任意事件触发都需要
            if (effect.requiresReroute) {
                combinedEffect.requiresReroute = true;
            }
            
            // 收集所有消息
            if (effect.message) {
                combinedEffect.messages.push(effect.message);
            }
        });
        
        return [combinedEffect];
    }

    showEventDetails(event) {
        // 显示事件详情
        const details = `
            <div style="padding: 10px;">
                <h3>${this.getEventTitle(event)}</h3>
                <p><strong>类型:</strong> ${event.type}</p>
                <p><strong>严重程度:</strong> ${event.severity}</p>
                <p><strong>位置:</strong> ${event.position[0].toFixed(4)}, ${event.position[1].toFixed(4)}</p>
                <p><strong>影响半径:</strong> ${event.radius.toFixed(0)}米</p>
                <p><strong>剩余时间:</strong> ${Math.max(0, Math.ceil((event.endTime - Date.now()) / 60000))}分钟</p>
                ${event.options.weatherType ? `<p><strong>天气类型:</strong> ${this.getWeatherName(event.options.weatherType)}</p>` : ''}
                ${event.options.intensity ? `<p><strong>强度:</strong> ${(event.options.intensity * 100).toFixed(0)}%</p>` : ''}
                ${event.options.expectedDelay ? `<p><strong>预计延迟:</strong> ${event.options.expectedDelay}分钟</p>` : ''}
                <p><strong>受影响的车辆:</strong> ${event.affectedVehicles.size}辆</p>
            </div>
        `;
        
        // 这里可以使用弹窗或侧边栏显示详情
        // 简单实现：使用信息窗口
        const infoWindow = new AMap.InfoWindow({
            content: details,
            offset: new AMap.Pixel(0, -30)
        });
        
        infoWindow.open(this.map, event.position);
        
        // 5秒后自动关闭
        setTimeout(() => {
            infoWindow.close();
        }, 5000);
    }

    notifyEventGenerated(event) {
        // 可以在这里添加事件通知逻辑
        // 例如：显示系统通知、更新UI等
        const notification = document.createElement('div');
        notification.className = 'event-notification';
        notification.style.cssText = `
            position: fixed;
            top: 20px;
            right: 20px;
            background: ${this.getSeverityColor(event.severity)};
            color: white;
            padding: 12px 20px;
            border-radius: 8px;
            box-shadow: 0 4px 12px rgba(0,0,0,0.3);
            z-index: 10000;
            animation: slideIn 0.5s ease;
        `;
        
        notification.innerHTML = `
            <strong>新事件发生!</strong><br>
            ${this.getEventTitle(event)}
        `;
        
        document.body.appendChild(notification);
        
        // 5秒后移除通知
        setTimeout(() => {
            notification.style.animation = 'slideOut 0.5s ease';
            setTimeout(() => {
                document.body.removeChild(notification);
            }, 500);
        }, 5000);
    }

    notifyEventEnded(event) {
        // 事件结束通知
        const notification = document.createElement('div');
        notification.className = 'event-notification';
        notification.style.cssText = `
            position: fixed;
            top: 20px;
            right: 20px;
            background: #4CAF50;
            color: white;
            padding: 12px 20px;
            border-radius: 8px;
            box-shadow: 0 4px 12px rgba(0,0,0,0.3);
            z-index: 10000;
            animation: slideIn 0.5s ease;
        `;
        
        notification.innerHTML = `
            <strong>事件已结束</strong><br>
            ${this.getEventTitle(event)}
        `;
        
        document.body.appendChild(notification);
        
        setTimeout(() => {
            notification.style.animation = 'slideOut 0.5s ease';
            setTimeout(() => {
                document.body.removeChild(notification);
            }, 500);
        }, 3000);
    }

    // 获取所有活动事件
    getActiveEvents() {
        const active = [];
        for (const [id, event] of this.events) {
            if (event.isActive()) {
                active.push(event);
            }
        }
        return active;
    }

    // 手动添加事件（用于测试）
    addManualEvent(type, severity, position, options = {}) {
        const duration = this.config.minEventDuration + 
                        Math.random() * (this.config.maxEventDuration - this.config.minEventDuration);
        
        const event = new RandomEvent(type, severity, position, 500, duration, options);
        this.addEvent(event);
        
        return event;
    }

    // 清除所有事件
    clearAllEvents() {
        for (const [id, event] of this.events) {
            this.removeEvent(event);
        }
        this.events.clear();
        this.vehicleEffects.clear();
        
        //清理天气图层效果
        if (this.weatherLayer) {
            this.weatherLayer.clearEffects();
        }
        
        console.log('所有事件已清除');
    }

    //销毁管理器
    destroy() {
        // 清理定时器
        if (this.eventGenerationTimer) {
            clearInterval(this.eventGenerationTimer);
            this.eventGenerationTimer = null;
        }
        
        if (this.eventUpdateTimer) {
            clearInterval(this.eventUpdateTimer);
            this.eventUpdateTimer = null;
        }
        
        if (this.vehicleEffectCheckTimer) {
            clearInterval(this.vehicleEffectCheckTimer);
            this.vehicleEffectCheckTimer = null;
        }
        
        // 清理天气图层
        if (this.weatherLayer) {
            this.weatherLayer.destroy();
            this.weatherLayer = null;
        }
        
        // 清理所有事件
        this.clearAllEvents();
        
        // 清理车辆影响
        this.vehicleEffects.clear();
    }

    initHeatmap() {
        if (!window.AMap.Heatmap) {
            // 动态加载热力图插件
            const script = document.createElement('script');
            script.src = 'https://webapi.amap.com/maps?v=2.0&key=a377af7b97013dde174c4f91b7e823c9&plugin=AMap.Heatmap';
            document.head.appendChild(script);
            script.onload = () => this.createHeatmap();
        } else {
            this.createHeatmap();
        }
    }

    createHeatmap() {
        this.heatmap = new AMap.Heatmap(this.map, {
            radius: 30,
            opacity: [0, 0.8],
            gradient: {
                0.4: 'blue',
                0.65: 'green',
                0.8: 'yellow',
                0.95: 'orange',
                1.0: 'red'
            },
            zIndex: 50
        });
    }

    updateTrafficHeatmap(events) {
        if (!this.heatmap) return;
        
        const points = events
            .filter(e => e.type === EventType.TRAFFIC_JAM)
            .map(e => ({
                lng: e.position[0],
                lat: e.position[1],
                count: e.options.congestionLevel * 10
            }));
        
        this.heatmap.setDataSet({
            data: points,
            max: 10
        });
    }

}

// 添加CSS动画
const style = document.createElement('style');
style.textContent = `
    @keyframes slideIn {
        from { transform: translateX(100%); opacity: 0; }
        to { transform: translateX(0); opacity: 1; }
    }
    
    @keyframes slideOut {
        from { transform: translateX(0); opacity: 1; }
        to { transform: translateX(100%); opacity: 0; }
    }
`;
document.head.appendChild(style);

export default EventManager;