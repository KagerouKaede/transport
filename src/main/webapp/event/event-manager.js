// 事件管理器模块
import { WeatherEffectLayer } from '/weather-layer.js';
export class EventManager {
    constructor(map, vehicles, functions) {
        this.map = map;
        this.vehicles = vehicles;
        this.functions = functions || {};
        this.activeEvents = {
            weather: [],
            trafficJam: [],
            accidents: [],
            roadClosures: []
        };
        this.weatherLayer = null;
        this.eventConfig = null;
        this.timeManager = null;
        this.closureMarkers = [];
        this.closurePaths = [];
        this.jamMarkers = [];
        this.jamPaths = [];
        this.currentWeather = 'clear';
        this.currentTimeState = 'day';
        this.simulationCycle = 0; // 添加周期计数器
        
        // 事件信息窗口容器
        this.infoWindows = new Map();

        // 速度管理器
        this.speedManager = new SpeedManager();
        
        // 事件图标
        this.eventIcons = {
            accident: '/resources/Accident.png',
            roadClosureStart: '/resources/RoadClosureStart.png',
            roadClosureEnd: '/resources/RoadClosureEnd.png',
            trafficJam: '/resources/TrafficJam.png'
        };
        
        // 时间映射配置
        this.timeConfig = {
            morningPeak: { start: 8, end: 9.5 },
            eveningPeak: { start: 17.5, end: 19 },
            night: { start: 19, end: 6 },
            day:{start1:6,end1:8,start2:9.5,end2:17.5},
            daySpeed: 1.0,
            nightSpeed: 0.8
        };

    }
    
    async initialize(config) {
        this.eventConfig = config;
        
        // 初始化天气图层
        if (typeof WeatherEffectLayer !== 'undefined') {
            this.weatherLayer = new WeatherEffectLayer(this.map);
        }
        
        // 初始化时间管理器
        this.timeManager = new TimeManager(this.timeConfig);
        
        console.log('事件管理器初始化完成');
        return this;
    }
    
    // 更新事件状态（每个周期调用）
    update() {
        // 更新当前时间和状态
        this.simulationCycle++;
    
        
        if (this.timeManager) {
            this.timeManager.update();
            this.currentTimeState = this.timeManager.getCurrentState();
        }
        
        // 清理过期事件
        this.cleanupExpiredEvents();
        
        // 根据时间状态调整概率
        const adjustedConfig = this.adjustProbabilitiesByTime();
        
        // 生成新事件
        this.generateEvents(adjustedConfig);
        
        // 更新所有事件效果
        this.updateEventEffects();
        
        // 更新UI显示
        this.updateEventDisplay();
    }
    
    // 根据时间调整事件概率
    adjustProbabilitiesByTime() {
        const config = JSON.parse(JSON.stringify(this.eventConfig));
        
        switch(this.currentTimeState) {
            case 'morning_peak':
            case 'evening_peak':
                if (config['Event.traffic_jam.probability']) {
                    config['Event.traffic_jam.probability'] *= 1.5;
                }
                if (config['Event.accident.probability']) {
                    config['Event.accident.probability'] *= 1.3;
                }
                break;
            case 'night':
                if (config['Event.accident.probability']) {
                    config['Event.accident.probability'] *= 1.4;
                }
                if (config['Event.road_closure.probability']) {
                    config['Event.road_closure.probability'] *= 1.3;
                }
                break;
        }
        
        return config;
    }
    
    // 生成事件
    generateEvents(config) {
        // 检查是否达到最大事件数
        const totalEvents = this.getTotalActiveEvents();
        if (totalEvents >= config['Event.max_active_events']) {
            return;
        }
        
        // 全局事件生成概率
        if (Math.random() > config['Event.global_probability']) {
            return;
        }
        
        // 生成天气事件
        if (config['Event.weather.enabled']) {
            this.generateWeatherEvent(config);
        }
        
        // 为有路径的运输中车辆生成事件
        this.vehicles.forEach(vehicle => {
            if (vehicle.status === 1 && vehicle.info && vehicle.info.route) {
                this.generateVehicleEvents(vehicle, config);
            }
        });
    }
    
    // 生成天气事件
    generateWeatherEvent(config) {
        // 检查是否达到最大天气事件数
        const maxWeatherEvents = config['Event.weather.max_count'] || 1;
        if (this.activeEvents.weather.length >= maxWeatherEvents) return;
        
        const weatherTypes = config['Event.weather.types_allowed'] || ['rain', 'snow', 'storm', 'sandstorm', 'fog'];
        const probabilities = config['Event.weather.type_probabilities'] || {
            rain: 0.4, snow: 0.2, storm: 0.1, sandstorm: 0.1, fog: 0.2
        };
        
        // 选择天气类型
        let random = Math.random();
        let selectedType = 'clear';
        let cumulative = 0;
        
        for (const [type, prob] of Object.entries(probabilities)) {
            cumulative += prob;
            if (random <= cumulative && weatherTypes.includes(type)) {
                selectedType = type;
                break;
            }
        }
        
        if (selectedType === 'clear') return;
        
        // 检查与已有天气事件的距离
        const minDistance = config['Event.weather.min_distance'] || 5000; // 米
        let validPosition = false;
        let position;
        let attempts = 0;
        
        // 尝试生成远离其他天气事件的位置
        while (!validPosition && attempts < 10) {
            position = this.getRandomMapPosition();
            validPosition = true;
            
            for (const existingEvent of this.activeEvents.weather) {
                const distance = this.calculateDistance(
                    position[0], position[1],
                    existingEvent.position[0], existingEvent.position[1]
                );
                
                if (distance < minDistance) {
                    validPosition = false;
                    break;
                }
            }
            attempts++;
        }
        
        if (!validPosition) return; // 找不到合适位置
        
        // 选择严重程度
        const severityDist = config['Event.weather.severity_distribution'] || {
            low: 0.5, medium: 0.3, high: 0.15, critical: 0.05
        };
        
        random = Math.random();
        cumulative = 0;
        let selectedSeverity = 'low';
        
        for (const [severity, prob] of Object.entries(severityDist)) {
            cumulative += prob;
            if (random <= cumulative) {
                selectedSeverity = severity;
                break;
            }
        }
        
        // 生成事件
        const event = {
            id: `weather_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
            type: 'weather',
            weatherType: selectedType,
            severity: selectedSeverity,
            position: position,
            radius: 2000 + Math.random() * 3000,
            intensity: this.getSeverityFactor(selectedSeverity),
            startTime: Date.now(),
            duration: this.getRandomDuration(config['Event.weather.duration_range']),
            speedFactor: this.getWeatherSpeedFactor(selectedType, selectedSeverity, config),
            options: {
                intensity: this.getSeverityFactor(selectedSeverity),
                weatherType: selectedType,
                severity: selectedSeverity
            }
        };
        
        this.activeEvents.weather.push(event);
        
        // 添加天气效果
        if (this.weatherLayer) {
            this.weatherLayer.addWeatherEffect(event);
        }
        
        // 添加天气事件信息窗口
        this.addWeatherInfoWindow(event);
        
        console.log(`生成天气事件: ${selectedType}, 严重程度: ${selectedSeverity}, 位置: ${position}`);
    }
    
    // 为车辆生成事件
    generateVehicleEvents(vehicle, config) {
        // 事故事件
        if (config['Event.accident.enabled'] && 
            this.activeEvents.accidents.length < config['Event.accident.max_count'] &&
            !vehicle.isEvent) {
            
            const accidentProb = config['Event.accident.probability'] || 0.02;
            if (Math.random() < accidentProb) {
                this.generateAccidentEvent(vehicle, config);
            }
        }
        
        // 拥堵事件
        if (config['Event.traffic_jam.enabled']) {
            const jamProb = config['Event.traffic_jam.probability'] || 0.05;
            if (Math.random() < jamProb) {
                this.generateTrafficJamEvent(vehicle, config);
            }
        }
        
        // 道路封闭事件
        if (config['Event.road_closure.enabled'] && 
            this.activeEvents.roadClosures.length < config['Event.road_closure.max_count'] &&
            !vehicle.isEvent) {
            
            const closureProb = config['Event.road_closure.probability'] || 0.03;
            if (Math.random() < closureProb) {
                this.generateRoadClosureEvent(vehicle, config);
            }
        }
    }
    
    // 生成事故事件
    generateAccidentEvent(vehicle, config) {
        if (!vehicle.info || !vehicle.info.route) return;
        
        // 选择严重程度
        const severityDist = config['Event.accident.severity_distribution'] || {
            low: 0.6, medium: 0.3, high: 0.08, critical: 0.02
        };
        
        const selectedSeverity = this.selectByProbability(severityDist);
        
        // 获取车辆路径并选择触发点
        const path = this.parseRouteToPath(vehicle.info.route);
        if (path.length < 5) return;
        
        // 在路径中间偏前的位置设置事故点
        const triggerIndex = Math.floor(path.length * 0.3) + Math.floor(Math.random() * path.length * 0.4);
        const triggerPoint = path[triggerIndex];
        
        const event = {
            id: `accident_${vehicle.UUID}_${Date.now()}`,
            type: 'accident',
            vehicleId: vehicle.UUID,
            severity: selectedSeverity,
            position: triggerPoint,
            triggerPoint: triggerPoint,
            triggerIndex: triggerIndex,
            triggered: false,
            startTime: Date.now(),
            duration: this.getRandomDuration(config['Event.accident.duration_range']),
            stopDuration: config['Event.accident.stop_durations'][selectedSeverity] || 0,
            speedFactor: config['Event.accident.speed_factors'][selectedSeverity] || 1.0,
            isActive: true
        };
        
        // 关联到车辆
        vehicle.accidentEvent = event;
        
        this.activeEvents.accidents.push(event);
        
        // 添加事故信息窗口
        this.addAccidentInfoWindow(event, vehicle);
    }
    
    // 生成交通拥堵事件
    generateTrafficJamEvent(vehicle, config) {
        if (!vehicle.info || !vehicle.info.route) return;
        
        // 检查是否达到最大拥堵事件数
        const maxJamEvents = config['Event.traffic_jam.max_count'] || 3;
        if (this.activeEvents.trafficJam.length >= maxJamEvents) return;
        
        // 选择严重程度
        const severityDist = config['Event.traffic_jam.severity_distribution'] || {
            low: 0.4, medium: 0.4, high: 0.15, critical: 0.05
        };
        
        const selectedSeverity = this.selectByProbability(severityDist);
        
        // 获取车辆路径
        const path = this.parseRouteToPath(vehicle.info.route);
        if (path.length < 20) return;
        
        // 随机选择路径中的一段
        const startIndex = Math.floor(Math.random() * (path.length - 15));
        const endIndex = startIndex + 10 + Math.floor(Math.random() * 10);
        
        const jamPath = path.slice(startIndex, endIndex);
        
        const event = {
            id: `jam_${vehicle.UUID}_${Date.now()}`,
            type: 'traffic_jam',
            vehicleId: vehicle.UUID,
            severity: selectedSeverity,
            path: jamPath,
            startIndex: startIndex,
            endIndex: endIndex,
            startTime: Date.now(),
            duration: this.getRandomDuration(config['Event.traffic_jam.duration_range']),
            speedFactor: config['Event.traffic_jam.speed_factors'][selectedSeverity] || 0.5,
            isActive: true,
            triggered: false
        };
        
        // 关联到车辆
        vehicle.trafficJamEvent = event;
        
        // 添加拥堵路径到全局
        this.addJamPath(jamPath, event);
        
        this.activeEvents.trafficJam.push(event);
        
        // 添加拥堵事件信息窗口
        this.addJamInfoWindow(event);
        
        console.log(`生成拥堵事件: ${event.id}, 严重程度: ${selectedSeverity}`);
    }
    
    // 生成道路封闭事件
    generateRoadClosureEvent(vehicle, config) {
        if (!vehicle.info || !vehicle.info.route) return;
        
        // 选择封闭类型
        const closureTypes = config['Event.road_closure.closure_types'] || {
            full: 0.3, partial: 0.7
        };
        
        const closureType = Math.random() < closureTypes.full ? 'full' : 'partial';
        
        // 选择严重程度
        const severityDist = config['Event.road_closure.severity_distribution'] || {
            low: 0.3, medium: 0.4, high: 0.25, critical: 0.05
        };
        
        const selectedSeverity = this.selectByProbability(severityDist);
        
        // 获取车辆路径
        const path = this.parseRouteToPath(vehicle.info.route);
        if (path.length < 15) return;
        
        // 选择封闭起点（在路径前半段）
        const triggerIndex = Math.floor(path.length * 0.2) + Math.floor(Math.random() * path.length * 0.3);
        const triggerPoint = path[triggerIndex];
        
        // 计算封闭长度
        let closureLength;
        switch(selectedSeverity) {
            case 'low': closureLength = 5; break;
            case 'medium': closureLength = 10; break;
            case 'high': closureLength = 15; break;
            case 'critical': closureLength = 20; break;
            default: closureLength = 10;
        }
        
        const endIndex = Math.min(triggerIndex + closureLength, path.length - 1);
        const closurePath = path.slice(triggerIndex, endIndex);
        
        const event = {
            id: `closure_${vehicle.UUID}_${Date.now()}`,
            type: 'road_closure',
            vehicleId: vehicle.UUID,
            severity: selectedSeverity,
            closureType: closureType,
            path: closurePath,
            triggerIndex: triggerIndex,
            triggerPoint: triggerPoint,
            endIndex: endIndex,
            startTime: Date.now(),
            duration: this.getRandomDuration(config['Event.road_closure.duration_range']),
            isActive: true,
            triggered: false,
            triggerVehicle: vehicle.UUID
        };
        
        // 关联到车辆
        vehicle.roadClosureEvent = event;
        
        // 添加封闭路径到全局
        this.addClosurePath(closurePath, event);
        
        this.activeEvents.roadClosures.push(event);
        
        // 添加道路封闭信息窗口
        this.addClosureInfoWindow(event);
    }
    
    // 检查车辆事件触发（综合检查）
    checkVehicleEvents(vehicle) {
        const currentPos = vehicle.marker.getPosition();
        let eventTriggered = false;
        
        // 检查事故事件
        if (vehicle.accidentEvent && !vehicle.accidentEvent.triggered) {
            const distance = this.calculateDistance(
                currentPos.lng, currentPos.lat,
                vehicle.accidentEvent.triggerPoint.lng, vehicle.accidentEvent.triggerPoint.lat
            );
            
            if (distance < 0.01) { // 约10米
                this.triggerAccidentEvent(vehicle);
                vehicle.accidentEvent.triggered = true;
                eventTriggered = true;
            }
        }
        
        // 检查道路封闭事件（保持原有接口）
        if (vehicle.roadClosureEvent && !vehicle.roadClosureEvent.triggered) {
            const distance = this.calculateDistance(
                currentPos.lng, currentPos.lat,
                vehicle.roadClosureEvent.triggerPoint.lng, vehicle.roadClosureEvent.triggerPoint.lat
            );
            
            if (distance < 0.01) {
                this.triggerRoadClosureEvent(vehicle);
                vehicle.roadClosureEvent.triggered = true;
                eventTriggered = true;
            }
        }
        
        // 检查拥堵事件（进入拥堵区域）
        if (vehicle.trafficJamEvent && !vehicle.trafficJamEvent.triggered) {
            this.applyTrafficJamEffect(vehicle);
        }
        
        return eventTriggered;
    }
    
    // 触发事故事件
    triggerAccidentEvent(vehicle) {
        const event = vehicle.accidentEvent;
        if (!event) return;
        
        console.log(`触发事故事件: ${event.id}`);
        
        // 设置车辆状态为紧急停止
        vehicle.isEvent = 1;
        vehicle.status = 2;
        
        // 改变车辆图标为事故图标
        const accidentIcon = new AMap.Icon({
            size: new AMap.Size(16, 16),
            image: this.eventIcons.accident,
            imageSize: new AMap.Size(16, 16)
        });
        vehicle.marker.setIcon(accidentIcon);
        
        // 停止持续时间
        const stopDuration = event.stopDuration * 60000; // 转为毫秒
        
        // 停止后恢复行驶
        setTimeout(() => {
            if (vehicle.isEvent === 1) {
                vehicle.isEvent = 0;
                vehicle.status = 1;
                
                // 恢复车辆图标
                const carIcon = new AMap.Icon({
                    size: new AMap.Size(16, 16),
                    image: '/resources/CarIcon.png',
                    imageSize: new AMap.Size(16, 16)
                });
                vehicle.marker.setIcon(carIcon);
                
                console.log(`车辆 ${vehicle.UUID} 事故处理完成，恢复行驶`);
            }
        }, stopDuration);
    }
    
    // 触发道路封闭事件（保持原有函数名）
    triggerRoadClosureEvent(vehicle) {
        if (!vehicle.roadClosureEvent) return;
        
        console.log(`触发道路封闭事件: ${vehicle.roadClosureEvent.id}`);
        
        // 设置车辆状态为紧急停止
        vehicle.isEvent = 3;
        vehicle.status = 2;
        
        // 停止车辆动画
        if (vehicle.animationTimer) {
            clearInterval(vehicle.animationTimer);
        }
        
        // 记录当前信息
        const closureInfo = {
            originalRoute: vehicle.info.route,
            originalPath: this.parseRouteToPath(vehicle.info.route),
            closureStartIndex: vehicle.roadClosureEvent.triggerIndex,
            originalEnd: vehicle.info.endMarker.getPosition(),
            stopTime: Date.now()
        };
        
        vehicle.closureInfo = closureInfo;
        
        // 紧急停止持续时间（2-7秒）
        const stopDuration = 2000 + Math.random() * 5000;
        
        // 重新规划路径
        setTimeout(() => {
            this.replanRouteForClosure(vehicle);
        }, stopDuration);
    }
    
    // 应用拥堵效果
    applyTrafficJamEffect(vehicle) {
        const event = vehicle.trafficJamEvent;
        if (!event || event.triggered) return;
        
        // 检查车辆是否在拥堵路段
        const currentPos = vehicle.marker.getPosition();
        let isInJam = false;
        
        for (let i = 0; i < event.path.length - 1; i++) {
            const distance = this.calculateDistanceToLineSegment(
                currentPos,
                event.path[i],
                event.path[i + 1]
            );
            
            if (distance < 0.05) { // 50米范围内认为在拥堵路段
                isInJam = true;
                break;
            }
        }
        
        if (isInJam && !event.triggered) {
            console.log(`车辆 ${vehicle.UUID} 进入拥堵路段`);
            event.triggered = true;
            vehicle.isEvent = 2;
        }
    }
    
    // 保持原有接口的函数（为了兼容性）
    checkRoadClosureTrigger(vehicle) {
        // 保持原有逻辑
        if (vehicle.isEvent !== 3 || !vehicle.roadClosureEvent) return false;
        
        if (vehicle.roadClosureEvent.triggered) return false;
        
        const currentPos = vehicle.marker.getPosition();
        const triggerPoint = vehicle.roadClosureEvent.triggerPoint;
        
        const distance = this.calculateDistance(
            currentPos.lng, currentPos.lat,
            triggerPoint.lng, triggerPoint.lat
        );
        
        return distance < 0.01;
    }
    
    // 处理道路封闭触发
    handleRoadClosureTrigger(vehicle) {
        if (!vehicle.roadClosureEvent) return;
        
        console.log(`触发道路封闭事件: ${vehicle.roadClosureEvent.id}`);
        
        vehicle.roadClosureEvent.triggered = true;
        
        // 设置车辆状态为紧急停止
        vehicle.status = 2;
        vehicle.isEvent = 3;
        
        // 停止车辆动画
        if (vehicle.animationTimer) {
            clearInterval(vehicle.animationTimer);
        }
        
        // 记录当前信息
        const closureInfo = {
            originalRoute: vehicle.info.route,
            originalPath: this.parseRouteToPath(vehicle.info.route),
            closureStartIndex: vehicle.roadClosureEvent.triggerIndex,
            originalEnd: vehicle.info.endMarker.getPosition(),
            stopTime: Date.now()
        };
        
        vehicle.closureInfo = closureInfo;
        
        // 紧急停止持续时间（2-7秒）
        const stopDuration = 2000 + Math.random() * 5000;
        
        // 重新规划路径
        setTimeout(() => {
            this.replanRouteForClosure(vehicle);
        }, stopDuration);
    }
    
    // 获取车辆总速度因子
    getVehicleSpeedFactor(vehicle) {
        let factor = 1.0;
        
        // 时间因素
        switch(this.currentTimeState) {
            case 'morning_peak':
            case 'evening_peak':
                factor *= 0.9; // 高峰期减速
                break;
            case 'night':
                factor *= this.timeConfig.nightSpeed || 0.8;
                break;
            default:
                factor *= this.timeConfig.daySpeed || 1.0;
        }
        
        // 天气因素
        if (this.activeEvents.weather.length > 0) {
            const weatherEvent = this.activeEvents.weather[0];
            factor *= weatherEvent.speedFactor;
        }
        
        // 事故因素
        if (vehicle.isEvent === 1 && vehicle.accidentEvent) {
            factor *= vehicle.accidentEvent.speedFactor;
        }
        
        // 拥堵因素
        if (vehicle.isEvent === 2 && vehicle.trafficJamEvent) {
            factor *= vehicle.trafficJamEvent.speedFactor;
        }
        
        // 道路封闭因素（已触发）
        if (vehicle.isEvent === 3) {
            factor = 0; // 完全停止
        }
        
        // 存储速度因子
        this.speedManager.updateFactor(vehicle.UUID, factor);
        
        return Math.max(0.1, Math.min(2.0, factor));
    }
    
    // 添加拥堵路径
    addJamPath(path, event) {
        // 绘制拥堵路段
        const jamLine = new AMap.Polyline({
            path: path,
            strokeColor: "#FFFF00", // 黄色
            strokeOpacity: 0.8,
            strokeWeight: 6,
            strokeStyle: "dashed",
            map: this.map
        });
        
        // 添加起点和终点标记（使用道路封闭图标）
        const startIcon = new AMap.Icon({
            size: new AMap.Size(16, 16),
            image: this.eventIcons.roadClosureStart,
            imageSize: new AMap.Size(16, 16)
        });
        
        const endIcon = new AMap.Icon({
            size: new AMap.Size(16, 16),
            image: this.eventIcons.roadClosureEnd,
            imageSize: new AMap.Size(16, 16)
        });
        
        const startMarker = new AMap.Marker({
            position: path[0],
            icon: startIcon,
            map: this.map,
            zIndex: 100
        });
        
        const endMarker = new AMap.Marker({
            position: path[path.length - 1],
            icon: endIcon,
            map: this.map,
            zIndex: 100
        });
        
        this.jamPaths.push({
            polyline: jamLine,
            startMarker: startMarker,
            endMarker: endMarker,
            event: event
        });
    }
    
    // 添加封闭路径
    addClosurePath(path, event) {
        // 绘制封闭路段
        const closureLine = new AMap.Polyline({
            path: path,
            strokeColor: "#FF0000",
            strokeOpacity: 0.8,
            strokeWeight: 8,
            map: this.map
        });
        
        // 添加起点和终点标记
        const startIcon = new AMap.Icon({
            size: new AMap.Size(16, 16),
            image: this.eventIcons.roadClosureStart,
            imageSize: new AMap.Size(16, 16)
        });
        
        const endIcon = new AMap.Icon({
            size: new AMap.Size(16, 16),
            image: this.eventIcons.roadClosureEnd,
            imageSize: new AMap.Size(16, 16)
        });
        
        const startMarker = new AMap.Marker({
            position: path[0],
            icon: startIcon,
            map: this.map
        });
        
        const endMarker = new AMap.Marker({
            position: path[path.length - 1],
            icon: endIcon,
            map: this.map
        });
        
        this.closurePaths.push({
            polyline: closureLine,
            startMarker: startMarker,
            endMarker: endMarker,
            event: event
        });
    }
    
    // 清理过期事件
    cleanupExpiredEvents() {
        const now = Date.now();
        
        // 清理天气事件
        this.activeEvents.weather = this.activeEvents.weather.filter(event => {
            if (now - event.startTime > event.duration * 60000) {
                if (this.weatherLayer) {
                    this.weatherLayer.removeEffect(event.id);
                }
                
                // 移除天气信息窗口
                this.removeInfoWindow(event.id);
                
                return false;
            }
            return true;
        });
        
        // 清理事故
        this.activeEvents.accidents = this.activeEvents.accidents.filter(event => {
            if (now - event.startTime > event.duration * 60000) {
                // 恢复车辆图标
                const vehicle = this.vehicles.find(v => v.UUID === event.vehicleId);
                if (vehicle) {
                    vehicle.isEvent = 0;
                    vehicle.accidentEvent = null;
                    const carIcon = new AMap.Icon({
                        size: new AMap.Size(16, 16),
                        image: '/resources/CarIcon.png',
                        imageSize: new AMap.Size(16, 16)
                    });
                    vehicle.marker.setIcon(carIcon);
                }
                
                // 移除事故信息窗口
                this.removeInfoWindow(event.id);
                
                return false;
            }
            return true;
        });
        
        // 清理拥堵
        this.activeEvents.trafficJam = this.activeEvents.trafficJam.filter(event => {
            if (now - event.startTime > event.duration * 60000) {
                // 移除拥堵路径
                const jamIndex = this.jamPaths.findIndex(j => j.event.id === event.id);
                if (jamIndex !== -1) {
                    this.map.remove(this.jamPaths[jamIndex].startMarker);
                    this.map.remove(this.jamPaths[jamIndex].endMarker);
                    this.map.remove(this.jamPaths[jamIndex].polyline);
                    this.jamPaths.splice(jamIndex, 1);
                }
                
                // 清理车辆关联
                const vehicle = this.vehicles.find(v => v.UUID === event.vehicleId);
                if (vehicle && vehicle.trafficJamEvent && vehicle.trafficJamEvent.id === event.id) {
                    vehicle.trafficJamEvent = null;
                    if (vehicle.isEvent === 2) {
                        vehicle.isEvent = 0;
                    }
                }
                
                // 移除拥堵信息窗口
                this.removeInfoWindow(event.id);
                
                return false;
            }
            return true;
        });
        
        // 清理道路封闭
        this.activeEvents.roadClosures = this.activeEvents.roadClosures.filter(event => {
            if (now - event.startTime > event.duration * 60000) {
                // 移除封闭路径
                const closureIndex = this.closurePaths.findIndex(c => c.event.id === event.id);
                if (closureIndex !== -1) {
                    this.map.remove(this.closurePaths[closureIndex].polyline);
                    this.map.remove(this.closurePaths[closureIndex].startMarker);
                    this.map.remove(this.closurePaths[closureIndex].endMarker);
                    this.closurePaths.splice(closureIndex, 1);
                }
                
                // 清理车辆关联
                const vehicle = this.vehicles.find(v => v.UUID === event.vehicleId);
                if (vehicle && vehicle.roadClosureEvent && vehicle.roadClosureEvent.id === event.id) {
                    vehicle.roadClosureEvent = null;
                    if (vehicle.isEvent === 3) {
                        vehicle.isEvent = 0;
                    }
                }
                
                // 移除封闭信息窗口
                this.removeInfoWindow(event.id);
                
                return false;
            }
            return true;
        });
        
        // 如果没有天气事件，设为晴天
        if (this.activeEvents.weather.length === 0) {
            this.currentWeather = 'clear';
        }
    }
    
    // 添加天气事件信息窗口
addWeatherInfoWindow(event) {
    const content = `
        <div class="event-info">
            <h4>天气事件</h4>
            <p><strong>类型:</strong> ${this.getWeatherName(event.weatherType)}</p>
            <p><strong>严重程度:</strong> ${this.getSeverityName(event.severity)}</p>
            <p><strong>剩余时间:</strong> ${Math.round(event.duration - (Date.now() - event.startTime) / 60000)}分钟</p>
            <p><strong>速度影响:</strong> ${Math.round((1 - event.speedFactor) * 100)}%减速</p>
        </div>
    `;
    
    const infoWindow = new AMap.InfoWindow({
        content: content,
        offset: new AMap.Pixel(0, -30),
        closeWhenClickMap: true
    });
    
    const marker = new AMap.Marker({
        position: [event.position[0], event.position[1]],
        map: this.map,
        zIndex: 200,
        visible: false // 隐藏标记，只显示信息窗口
    });
    
    infoWindow.open(this.map, marker.getPosition());
    this.infoWindows.set(event.id, { infoWindow, marker });
    
}

// 添加拥堵事件信息窗口
addJamInfoWindow(event) {
    const content = `
        <div class="event-info">
            <h4>交通拥堵</h4>
            <p><strong>严重程度:</strong> ${this.getSeverityName(event.severity)}</p>
            <p><strong>剩余时间:</strong> ${Math.round(event.duration - (Date.now() - event.startTime) / 60000)}分钟</p>
            <p><strong>速度影响:</strong> ${Math.round((1 - event.speedFactor) * 100)}%减速</p>
            <p><strong>路段长度:</strong> ${Math.round(this.calculatePathDistance(event.path) * 1000)}米</p>
        </div>
    `;
    
    // 在拥堵路段中间位置显示信息窗口
    const middleIndex = Math.floor(event.path.length / 2);
    const position = event.path[middleIndex];
    
    const infoWindow = new AMap.InfoWindow({
        content: content,
        offset: new AMap.Pixel(0, -30),
        closeWhenClickMap: true
    });
    
    const marker = new AMap.Marker({
        position: position,
        map: this.map,
        zIndex: 200,
        visible: false
    });
    
    infoWindow.open(this.map, marker.getPosition());
    this.infoWindows.set(event.id, { infoWindow, marker });
    
}

// 添加事故事件信息窗口
addAccidentInfoWindow(event, vehicle) {
    const content = `
        <div class="event-info">
            <h4>交通事故</h4>
            <p><strong>严重程度:</strong> ${this.getSeverityName(event.severity)}</p>
            <p><strong>剩余时间:</strong> ${Math.round(event.duration - (Date.now() - event.startTime) / 60000)}分钟</p>
            <p><strong>停止时间:</strong> ${event.stopDuration}分钟</p>
            <p><strong>速度影响:</strong> ${Math.round((1 - event.speedFactor) * 100)}%减速</p>
            <p><strong>涉及车辆:</strong> ${vehicle.UUID.substring(0, 8)}...</p>
        </div>
    `;
    
    const infoWindow = new AMap.InfoWindow({
        content: content,
        offset: new AMap.Pixel(0, -30),
        closeWhenClickMap: true
    });
    
    const marker = new AMap.Marker({
        position: event.position,
        map: this.map,
        zIndex: 200,
        visible: false
    });
    
    infoWindow.open(this.map, marker.getPosition());
    this.infoWindows.set(event.id, { infoWindow, marker });

}

    // 添加道路封闭信息窗口
    addClosureInfoWindow(event) {
        const content = `
            <div class="event-info">
                <h4>道路封闭</h4>
                <p><strong>类型:</strong> ${event.closureType === 'full' ? '完全封闭' : '部分封闭'}</p>
                <p><strong>严重程度:</strong> ${this.getSeverityName(event.severity)}</p>
                <p><strong>剩余时间:</strong> ${Math.round(event.duration - (Date.now() - event.startTime) / 60000)}分钟</p>
                <p><strong>封闭长度:</strong> ${Math.round(this.calculatePathDistance(event.path) * 1000)}米</p>
                <p><strong>触发车辆:</strong> ${event.triggerVehicle.substring(0, 8)}...</p>
            </div>
        `;
        
        // 在封闭路段中间位置显示信息窗口
        const middleIndex = Math.floor(event.path.length / 2);
        const position = event.path[middleIndex];
        
        const infoWindow = new AMap.InfoWindow({
            content: content,
            offset: new AMap.Pixel(0, -30),
            closeWhenClickMap: true,
            isAutoMove:false
        });
        
        const marker = new AMap.Marker({
            position: position,
            map: this.map,
            zIndex: 200,
            visible: false
        });
        
        infoWindow.open(this.map, marker.getPosition());
        this.infoWindows.set(event.id, { infoWindow, marker });
        
    }

    // 移除信息窗口
    removeInfoWindow(eventId) {
        if (this.infoWindows.has(eventId)) {
            const { infoWindow, marker } = this.infoWindows.get(eventId);
            infoWindow.close();
            this.map.remove(marker);
            this.infoWindows.delete(eventId);
        }
    }

    // 工具方法：获取天气名称
    getWeatherName(weatherType) {
        const weatherNames = {
            'clear': '晴',
            'rain': '雨',
            'snow': '雪',
            'storm': '暴雨',
            'sandstorm': '沙尘暴',
            'fog': '雾'
        };
        return weatherNames[weatherType] || weatherType;
    }

    // 工具方法：获取严重程度名称
    getSeverityName(severity) {
        const severityNames = {
            'low': '低',
            'medium': '中',
            'high': '高',
            'critical': '严重'
        };
        return severityNames[severity] || severity;
    }

    // 更新事件效果
    updateEventEffects() {
        // 更新天气效果
        if (this.weatherLayer) {
            // weatherLayer会自动更新动画
        }
        
        // 更新事故车辆状态
        this.activeEvents.accidents.forEach(event => {
            const vehicle = this.vehicles.find(v => v.UUID === event.vehicleId);
            if (vehicle && vehicle.isEvent === 1) {
                // 检查是否过了停止时间
                const now = Date.now();
                const elapsed = (now - event.startTime) / 1000;
                
                if (elapsed > event.stopDuration * 60) {
                    // 停止时间结束，恢复行驶
                    vehicle.isEvent = 0;
                    vehicle.accidentEvent = null;
                    
                    // 恢复车辆图标
                    const carIcon = new AMap.Icon({
                        size: new AMap.Size(16, 16),
                        image: '/resources/CarIcon.png',
                        imageSize: new AMap.Size(16, 16)
                    });
                    vehicle.marker.setIcon(carIcon);
                    
                    // 标记事件为非活跃
                    event.isActive = false;
                }
            }
        });
    }
    
    // 重新规划路径（道路封闭后）
    async replanRouteForClosure(vehicle) {
        if (!vehicle.closureInfo) return;
        
        const closureEvent = vehicle.roadClosureEvent;
        const closureInfo = vehicle.closureInfo;
        
        // 获取避让区域（所有封闭路段）
        const avoidPolygons = this.closurePaths.map(cp => cp.event.path);
        
        // 从当前位置到原终点，避开封闭路段
        const currentPos = vehicle.marker.getPosition();
        const endPos = closureInfo.originalEnd;
        
        try {
            // 使用改进的路径规划（考虑避让）
            const newRoute = await this.planRouteWithAvoidance(
                [currentPos.lng, currentPos.lat],
                [endPos.lng, endPos.lat],
                avoidPolygons
            );
            
            if (newRoute) {
                // 计算已行驶部分的距离和时间的百分比
                const originalPath = closureInfo.originalPath;
                const traveledRatio = closureEvent.triggerIndex / originalPath.length;
                
                // 原路径已行驶部分
                const traveledDistance = closureInfo.originalRoute.distance * traveledRatio;
                const traveledTime = closureInfo.originalRoute.time * traveledRatio;
                
                // 新路径的完整距离和时间
                const newDistance = newRoute.distance;
                const newTime = newRoute.time;
                
                // 总距离 = 已行驶距离 + 新路径距离
                vehicle.info.additionalDistance = traveledDistance + newDistance;
                vehicle.info.additionalTime = traveledTime + newTime;
                
                // 记录重规划信息
                vehicle.info.replanned = true;
                vehicle.info.originalDistance = closureInfo.originalRoute.distance;
                vehicle.info.originalTime = closureInfo.originalRoute.time;
                
                // 恢复车辆状态
                vehicle.status = 1;
                vehicle.isEvent = 0;
                
                // 重新开始动画
                if (this.functions.VideoCars) {
                    this.functions.VideoCars(vehicle.marker, newRoute, vehicle.UUID);
                }
            }
        } catch (error) {
            console.error('重新规划路径失败:', error);
        }
    }
    
    // 带避让的路径规划
    async planRouteWithAvoidance(start, end, avoidPolygons) {
        return new Promise((resolve, reject) => {
            // 使用高德的避让区域参数
            const avoidPoly = avoidPolygons.map(poly => {
                return poly.map(p => new AMap.LngLat(p.lng, p.lat));
            });
            
            const driving = new AMap.Driving({
                policy: AMap.DrivingPolicy.LEAST_TIME,
                avoidPolygons: avoidPoly,
                ferry: 1
            });
            
            driving.search(
                new AMap.LngLat(start[0], start[1]),
                new AMap.LngLat(end[0], end[1]),
                { policy: AMap.DrivingPolicy.LEAST_TIME },
                (status, result) => {
                    if (status === 'complete' && result.routes && result.routes.length) {
                        resolve(result.routes[0]);
                    } else {
                        if (this.functions.planRoute) {
                            this.functions.planRoute(start, end).then(resolve).catch(reject);
                        } else {
                            reject(new Error('路径规划函数未提供'));
                        }
                    }
                }
            );
        });
    }
    
    // 计算点到线段的距离
    calculateDistanceToLineSegment(point, lineStart, lineEnd) {
        const A = point.lng - lineStart.lng;
        const B = point.lat - lineStart.lat;
        const C = lineEnd.lng - lineStart.lng;
        const D = lineEnd.lat - lineStart.lat;
        
        const dot = A * C + B * D;
        const lenSq = C * C + D * D;
        let param = -1;
        
        if (lenSq !== 0) {
            param = dot / lenSq;
        }
        
        let xx, yy;
        
        if (param < 0) {
            xx = lineStart.lng;
            yy = lineStart.lat;
        } else if (param > 1) {
            xx = lineEnd.lng;
            yy = lineEnd.lat;
        } else {
            xx = lineStart.lng + param * C;
            yy = lineStart.lat + param * D;
        }
        
        const dx = point.lng - xx;
        const dy = point.lat - yy;
        
        return Math.sqrt(dx * dx + dy * dy);
    }
    
    // 工具方法
    selectByProbability(distribution) {
        let random = Math.random();
        let cumulative = 0;
        
        for (const [key, prob] of Object.entries(distribution)) {
            cumulative += prob;
            if (random <= cumulative) {
                return key;
            }
        }
        
        return Object.keys(distribution)[0];
    }
    
    getRandomDuration(range) {
        if (!range || range.length !== 2) return 15;
        return range[0] + Math.random() * (range[1] - range[0]);
    }
    
    getRandomMapPosition() {
        const bounds = this.map.getBounds();
        const ne = bounds.getNorthEast();
        const sw = bounds.getSouthWest();
        
        const lng = sw.lng + Math.random() * (ne.lng - sw.lng);
        const lat = sw.lat + Math.random() * (ne.lat - sw.lat);
        
        return [lng, lat];
    }
    
    getSeverityFactor(severity) {
        switch(severity) {
            case 'low': return 0.5;
            case 'medium': return 0.7;
            case 'high': return 0.85;
            case 'critical': return 1.0;
            default: return 0.5;
        }
    }
    
    getWeatherSpeedFactor(weatherType, severity, config) {
        const speedFactors = config['Event.weather.speed_factors'] || {
            rain: { low: 0.8, medium: 0.6, high: 0.4, critical: 0.2 },
            snow: { low: 0.7, medium: 0.5, high: 0.3, critical: 0.1 },
            storm: { low: 0.6, medium: 0.4, high: 0.2, critical: 0.1 },
            sandstorm: { low: 0.5, medium: 0.3, high: 0.15, critical: 0.05 },
            fog: { low: 0.9, medium: 0.7, high: 0.5, critical: 0.3 }
        };
        
        if (speedFactors[weatherType] && speedFactors[weatherType][severity]) {
            return speedFactors[weatherType][severity];
        }
        
        return 1.0;
    }
    
    calculateDistance(lng1, lat1, lng2, lat2) {
        const R = 6371;
        const dLat = (lat2 - lat1) * Math.PI / 180;
        const dLng = (lng2 - lng1) * Math.PI / 180;
        const a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) *
                Math.sin(dLng/2) * Math.sin(dLng/2);
        const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        return R * c;
    }
    
    calculatePathDistance(path) {
        let total = 0;
        for (let i = 1; i < path.length; i++) {
            total += this.calculateDistance(
                path[i-1].lng, path[i-1].lat,
                path[i].lng, path[i].lat
            );
        }
        return total;
    }
    
    parseRouteToPath(route) {
        if (this.functions.parseRouteToPath) {
            return this.functions.parseRouteToPath(route);
        }
        
        // 默认实现
        let path = [];
        if (route && route.steps) {
            for (let i = 0, l = route.steps.length; i < l; i++) {
                let step = route.steps[i];
                for (let j = 0, n = step.path.length; j < n; j++) {
                    path.push(step.path[j]);
                }
            }
        }
        return path;
    }
    
    getTotalActiveEvents() {
        return this.activeEvents.weather.length +
               this.activeEvents.accidents.length +
               this.activeEvents.trafficJam.length +
               this.activeEvents.roadClosures.length;
    }
    
    // 更新UI显示
    updateEventDisplay() {
        // 更新地图中心上方的信息显示
        this.updateTimeWeatherDisplay();
    
        // 调试信息
        console.log(`周期: ${this.simulationCycle}, 时间状态: ${this.currentTimeState}, 天气: ${this.currentWeather}`);
    }

    // 更新时间和天气显示
    updateTimeWeatherDisplay() {
        // 获取地图中心点
        const center = this.map.getCenter();
        const displayRange = this.eventConfig['Event.weather.display_range'] || 10000; // 米
        
        let nearestWeather = null;
        let minDistance = Infinity;
        
        // 查找范围内最近的天气事件
        for (const weatherEvent of this.activeEvents.weather) {
            const distance = this.calculateDistance(
                center.lng, center.lat,
                weatherEvent.position[0], weatherEvent.position[1]
            );
            
            if (distance <= displayRange && distance < minDistance) {
                minDistance = distance;
                nearestWeather = weatherEvent;
            }
        }
        
        // 更新当前天气显示
        if (nearestWeather) {
            this.currentWeather = nearestWeather.weatherType;
        } else {
            this.currentWeather = 'clear';
        }
        
        // 这里需要与map.html中的显示集成
        // 实际实现中会在map.html添加对应的DOM元素
    }
    
    // 销毁清理
    destroy() {
        if (this.weatherLayer) {
            this.weatherLayer.destroy();
        }
        
        // 清理所有事件标记
        this.closurePaths.forEach(cp => {
            this.map.remove(cp.polyline);
            this.map.remove(cp.startMarker);
            this.map.remove(cp.endMarker);
        });
        
        this.jamPaths.forEach(jp => {
            this.map.remove(jp.polyline);
        });
        
        this.closurePaths = [];
        this.jamPaths = [];
    }
}

// 时间管理器（优化版）
class TimeManager {
    constructor(config) {
        this.config = config;
        this.startTime = new Date();
        this.startTime.setHours(8, 0, 0, 0);
        this.currentSimulationTime = this.startTime;
        this.simulationSpeed = 360; // 360倍速：1小时仿真 = 10秒现实
        this.lastUpdate = Date.now();
        this.currentState = 'day';
    }
    
    update() {
        const now = Date.now();
        const elapsed = (now - this.lastUpdate) / 1000;
        
        if (elapsed >= 1) {
            // 每秒更新一次
            const simulationHoursElapsed = elapsed * (this.simulationSpeed / 3600);
            this.currentSimulationTime = new Date(
                this.currentSimulationTime.getTime() + simulationHoursElapsed * 3600000
            );
            
            this.currentState = this.getCurrentState();
            this.lastUpdate = now;
        }
    }
    
    getCurrentState() {
        const hour = this.currentSimulationTime.getHours();
        const minute = this.currentSimulationTime.getMinutes();
        const decimalHour = hour + minute / 60;
        
        // 调试信息
        console.log(`当前时间: ${hour}:${minute}, 小数小时: ${decimalHour.toFixed(2)}`);
        
        // 夜晚判断逻辑
        // 夜晚：19:00-6:00
        if (decimalHour >= this.config.night.start || decimalHour < this.config.night.end) {
            return 'night';
        }
        // 早高峰：8:00-9:30
        else if (decimalHour >= this.config.morningPeak.start && decimalHour < this.config.morningPeak.end) {
            return 'morning_peak';
        }
        // 晚高峰：17:30-19:00
        else if (decimalHour >= this.config.eveningPeak.start && decimalHour < this.config.eveningPeak.end) {
            return 'evening_peak';
        }
        // 白天：其他时间
        else {
            return 'day';
        }
    }
    
    getFormattedTime() {
        const hours = this.currentSimulationTime.getHours().toString().padStart(2, '0');
        const minutes = this.currentSimulationTime.getMinutes().toString().padStart(2, '0');
        const seconds = this.currentSimulationTime.getSeconds().toString().padStart(2, '0');
        return `${hours}:${minutes}:${seconds}`;
    }
    
    getSimulationInfo() {
        return {
            time: this.getFormattedTime(),
            state: this.currentState,
            speedFactor: this.getSpeedFactorForState()
        };
    }
    
    getSpeedFactorForState() {
        switch(this.currentState) {
            case 'morning_peak':
            case 'evening_peak':
                return 0.9;
            case 'night':
                return this.config.nightSpeed || 0.8;
            case 'day':
            default:
                return this.config.daySpeed || 1.0;
        }
    }
}

// 速度管理器
class SpeedManager {
    constructor() {
        this.factors = new Map();
    }
    
    updateFactor(vehicleId, factor) {
        this.factors.set(vehicleId, factor);
    }
    
    getFactor(vehicleId) {
        return this.factors.get(vehicleId) || 1.0;
    }
}
