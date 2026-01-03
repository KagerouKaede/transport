// events-panel.js - 事件控制面板
export class EventsPanel {
    constructor(eventManager) {
        this.eventManager = eventManager;
        this.panel = null;
        this.initialized = false;
        this.activeEvents = new Map();
        
        this.selectedEventType = 'weather';  // 默认选择天气事件
        this.selectedWeatherType = 'rain';   // 默认选择降雨
        this.selectedSeverity = 'medium';    // 默认中等强度
        this.updateInterval = null;          // 用于内存管理
    }

    init() {
        if (this.initialized) return;
        
        this.createPanel();
        this.setupEventListeners();
        this.startUpdateLoop();
        
        this.initialized = true;
        console.log('事件面板初始化完成');
    }

    createPanel() {
        // 创建事件面板容器
        this.panel = document.createElement('div');
        this.panel.id = 'events-panel';
        this.panel.className = 'floating-panel events-panel';
        this.panel.style.cssText = `
            position: absolute;
            bottom: 24px;
            right: 24px;
            width: 380px; /* 稍微加宽 */
            max-height: 500px;
            background: rgba(25, 25, 35, 0.95);
            backdrop-filter: blur(16px);
            border-radius: 16px;
            box-shadow: 0 12px 24px rgba(0, 0, 0, 0.15);
            z-index: 1000;
            overflow: hidden;
            display: none;
            border: 1px solid rgba(255, 255, 255, 0.1);
        `;

        const header = document.createElement('div');
        header.className = 'panel-header';
        header.style.cssText = `
            background: linear-gradient(135deg, rgba(255, 87, 34, 0.95) 0%, rgba(244, 67, 54, 0.95) 100%);
            color: white;
            padding: 16px 20px;
            display: flex;
            align-items: center;
            justify-content: space-between;
            border-bottom: 1px solid rgba(255, 255, 255, 0.2);
        `;

        const title = document.createElement('h3');
        title.className = 'panel-title';
        title.textContent = '随机事件控制';
        title.style.cssText = `
            margin: 0;
            font-size: 16px;
            font-weight: 600;
            display: flex;
            align-items: center;
            gap: 10px;
        `;

        const titleIcon = document.createElement('span');
        titleIcon.textContent = '⚠️';
        
        const closeBtn = document.createElement('button');
        closeBtn.className = 'close-btn';
        closeBtn.textContent = '×';
        closeBtn.title = '关闭';
        closeBtn.style.cssText = `
            background: rgba(255, 255, 255, 0.15);
            border: 1px solid rgba(255, 255, 255, 0.2);
            color: white;
            font-size: 20px;
            cursor: pointer;
            padding: 4px 10px;
            border-radius: 6px;
            transition: all 0.3s ease;
        `;

        title.appendChild(titleIcon);
        title.appendChild(document.createTextNode(' 随机事件控制'));
        
        header.appendChild(title);
        header.appendChild(closeBtn);

        const content = document.createElement('div');
        content.className = 'panel-content';
        content.style.cssText = `
            padding: 16px;
            max-height: 400px;
            overflow-y: auto;
            scrollbar-width: thin;
            scrollbar-color: rgba(255, 87, 34, 0.5) transparent;
        `;

        // 创建事件生成器部分
        content.innerHTML = `
            <div class="events-generator" style="margin-bottom: 20px;">
                <h4 style="color: white; margin-bottom: 12px; font-size: 14px; opacity: 0.9;">生成事件</h4>
                
                <div style="margin-bottom: 16px;">
                    <div style="display: flex; gap: 8px; margin-bottom: 10px;">
                        <button id="generate-random-btn" class="generate-btn" style="flex: 1; padding: 8px; background: rgba(52, 152, 219, 0.2); border: 1px solid rgba(52, 152, 219, 0.3); color: white; border-radius: 6px; cursor: pointer; font-size: 12px;">🎲 随机事件</button>
                        <button id="clear-events-btn" class="generate-btn" style="flex: 1; padding: 8px; background: rgba(76, 175, 80, 0.2); border: 1px solid rgba(76, 175, 80, 0.3); color: white; border-radius: 6px; cursor: pointer; font-size: 12px;">🗑️ 清除所有事件</button>
                    </div>
                    
                    <div style="color: rgba(255, 255, 255, 0.7); font-size: 12px; margin-bottom: 15px; padding: 8px; background: rgba(0, 0, 0, 0.2); border-radius: 6px;">
                        选择特定事件类型生成：
                    </div>
                    
                    <!-- 事件类型选择 -->
                    <div style="display: grid; grid-template-columns: repeat(2, 1fr); gap: 8px; margin-bottom: 15px;">
                        <!-- 天气事件 -->
                        <button class="event-type-btn" data-type="weather" data-weather="rain" style="padding: 10px; background: rgba(52, 152, 219, 0.1); border: 1px solid rgba(52, 152, 219, 0.3); color: white; border-radius: 6px; cursor: pointer; text-align: center;">
                            <div>🌧️ 降雨</div>
                            <div style="font-size: 10px; opacity: 0.7;">天气事件</div>
                        </button>
                        
                        <button class="event-type-btn" data-type="weather" data-weather="storm" style="padding: 10px; background: rgba(231, 76, 60, 0.1); border: 1px solid rgba(231, 76, 60, 0.3); color: white; border-radius: 6px; cursor: pointer; text-align: center;">
                            <div>⛈️ 暴雨</div>
                            <div style="font-size: 10px; opacity: 0.7;">天气事件</div>
                        </button>
                        
                        <button class="event-type-btn" data-type="weather" data-weather="sandstorm" style="padding: 10px; background: rgba(243, 156, 18, 0.1); border: 1px solid rgba(243, 156, 18, 0.3); color: white; border-radius: 6px; cursor: pointer; text-align: center;">
                            <div>🌪️ 沙尘暴</div>
                            <div style="font-size: 10px; opacity: 0.7;">天气事件</div>
                        </button>
                        
                        <button class="event-type-btn" data-type="weather" data-weather="snow" style="padding: 10px; background: rgba(236, 240, 241, 0.1); border: 1px solid rgba(236, 240, 241, 0.3); color: white; border-radius: 6px; cursor: pointer; text-align: center;">
                            <div>❄️ 降雪</div>
                            <div style="font-size: 10px; opacity: 0.7;">天气事件</div>
                        </button>
                        
                        <!-- 其他事件 -->
                        <button class="event-type-btn" data-type="road_closure" style="padding: 10px; background: rgba(155, 89, 182, 0.1); border: 1px solid rgba(155, 89, 182, 0.3); color: white; border-radius: 6px; cursor: pointer; text-align: center;">
                            <div>🚧 道路封闭</div>
                            <div style="font-size: 10px; opacity: 0.7;">交通事件</div>
                        </button>
                        
                        <button class="event-type-btn" data-type="traffic_jam" style="padding: 10px; background: rgba(241, 196, 15, 0.1); border: 1px solid rgba(241, 196, 15, 0.3); color: white; border-radius: 6px; cursor: pointer; text-align: center;">
                            <div>🚗 交通拥堵</div>
                            <div style="font-size: 10px; opacity: 0.7;">交通事件</div>
                        </button>
                        
                        <button class="event-type-btn" data-type="accident" style="padding: 10px; background: rgba(231, 76, 60, 0.1); border: 1px solid rgba(231, 76, 60, 0.3); color: white; border-radius: 6px; cursor: pointer; text-align: center;">
                            <div>🚨 交通事故</div>
                            <div style="font-size: 10px; opacity: 0.7;">紧急事件</div>
                        </button>
                        
                        <button class="event-type-btn" data-type="special_event" style="padding: 10px; background: rgba(46, 204, 113, 0.1); border: 1px solid rgba(46, 204, 113, 0.3); color: white; border-radius: 6px; cursor: pointer; text-align: center;">
                            <div>🎉 特殊事件</div>
                            <div style="font-size: 10px; opacity: 0.7;">其他事件</div>
                        </button>
                    </div>
                    
                    <!-- 事件强度选择 -->
                    <div style="margin-bottom: 15px;">
                        <div style="color: rgba(255, 255, 255, 0.7); font-size: 12px; margin-bottom: 8px;">事件强度</div>
                        <div style="display: flex; gap: 8px;">
                            <button class="severity-btn" data-severity="low" style="flex: 1; padding: 8px; background: rgba(46, 204, 113, 0.2); border: 1px solid rgba(46, 204, 113, 0.3); color: white; border-radius: 6px; cursor: pointer; font-size: 12px;">低强度</button>
                            <button class="severity-btn" data-severity="medium" style="flex: 1; padding: 8px; background: rgba(241, 196, 15, 0.2); border: 1px solid rgba(241, 196, 15, 0.3); color: white; border-radius: 6px; cursor: pointer; font-size: 12px;">中强度</button>
                            <button class="severity-btn" data-severity="high" style="flex: 1; padding: 8px; background: rgba(231, 76, 60, 0.2); border: 1px solid rgba(231, 76, 60, 0.3); color: white; border-radius: 6px; cursor: pointer; font-size: 12px;">高强度</button>
                        </div>
                    </div>
                    
                    <!-- 当前位置按钮 -->
                    <button id="generate-at-center-btn" style="width: 100%; padding: 10px; background: rgba(52, 152, 219, 0.2); border: 1px solid rgba(52, 152, 219, 0.3); color: white; border-radius: 6px; cursor: pointer; font-size: 12px; margin-bottom: 10px;">
                        🎯 在当前视图中心生成
                    </button>
                    
                    <div style="font-size: 11px; color: rgba(255, 255, 255, 0.5); text-align: center; margin-top: 10px;">
                        自动随机事件: <span id="auto-events-status" style="color: #2ecc71;">已启用</span>
                    </div>
                </div>
            </div>
            
            <div class="events-stats" style="margin-bottom: 16px; border-top: 1px solid rgba(255, 255, 255, 0.1); padding-top: 16px;">
                <div style="display: flex; justify-content: space-between; margin-bottom: 12px;">
                    <div style="font-size: 12px; color: rgba(255, 255, 255, 0.7);">活动事件: <span id="active-events-count" style="color: #FF5722; font-weight: bold;">0</span></div>
                    <div style="font-size: 12px; color: rgba(255, 255, 255, 0.7);">受影响车辆: <span id="affected-vehicles-count" style="color: #FF5722; font-weight: bold;">0</span></div>
                </div>
            </div>
            
            <div id="events-list" class="events-list">
                <div style="text-align: center; padding: 20px; color: rgba(255, 255, 255, 0.5); font-size: 14px;">
                    暂无活动事件
                </div>
            </div>
        `;

        // 样式表
        const style = document.createElement('style');
        style.textContent = `
            .events-list {
                display: flex;
                flex-direction: column;
                gap: 8px;
            }
            
            .event-item {
                background: rgba(255, 255, 255, 0.07);
                backdrop-filter: blur(8px);
                border-radius: 8px;
                padding: 12px;
                border-left: 3px solid #FF5722;
                cursor: pointer;
                transition: all 0.3s ease;
            }
            
            .event-item:hover {
                background: rgba(255, 255, 255, 0.12);
                transform: translateX(-2px);
            }
            
            .event-header {
                display: flex;
                justify-content: space-between;
                align-items: center;
                margin-bottom: 8px;
            }
            
            .event-type {
                font-size: 12px;
                font-weight: 600;
                color: white;
                background: rgba(255, 87, 34, 0.3);
                padding: 2px 8px;
                border-radius: 10px;
                text-transform: uppercase;
            }
            
            .event-severity {
                font-size: 10px;
                padding: 2px 6px;
                border-radius: 10px;
                font-weight: bold;
            }
            
            .event-details {
                font-size: 11px;
                color: rgba(255, 255, 255, 0.7);
                line-height: 1.4;
            }
            
            .event-progress {
                height: 4px;
                background: rgba(255, 255, 255, 0.1);
                border-radius: 2px;
                margin-top: 8px;
                overflow: hidden;
            }
            
            .event-progress-bar {
                height: 100%;
                background: #FF5722;
                border-radius: 2px;
                transition: width 1s linear;
            }
            
            /* 按钮悬停效果 */
            .event-type-btn:hover {
                transform: translateY(-2px);
                box-shadow: 0 4px 8px rgba(0, 0, 0, 0.2);
            }
            
            .severity-btn:hover {
                transform: translateY(-2px);
                box-shadow: 0 4px 8px rgba(0, 0, 0, 0.2);
            }
            
            .generate-btn:hover {
                transform: translateY(-2px);
                box-shadow: 0 4px 8px rgba(0, 0, 0, 0.2);
            }
            
            /* 选中的按钮样式 */
            .event-type-btn.active {
                background: rgba(255, 87, 34, 0.3) !important;
                border-color: #FF5722 !important;
            }
            
            .severity-btn.active {
                background: rgba(255, 87, 34, 0.3) !important;
                border-color: #FF5722 !important;
            }
        `;

        this.panel.appendChild(header);
        this.panel.appendChild(content);
        document.body.appendChild(this.panel);
        document.head.appendChild(style);

        closeBtn.addEventListener('click', () => this.hide());
    }

    setupEventListeners() {
        // 随机事件按钮
        document.getElementById('generate-random-btn').addEventListener('click', () => {
            this.generateRandomEvent();
        });

        // 清除所有事件按钮
        document.getElementById('clear-events-btn').addEventListener('click', () => {
            this.eventManager.clearAllEvents();
            this.updatePanel();
        });

        // 事件类型按钮
        document.querySelectorAll('.event-type-btn').forEach(btn => {
            btn.addEventListener('click', (e) => {
                // 移除所有按钮的active类
                document.querySelectorAll('.event-type-btn').forEach(b => b.classList.remove('active'));
                // 给当前按钮添加active类
                btn.classList.add('active');
                
                // 存储选中的事件类型
                this.selectedEventType = btn.dataset.type;
                this.selectedWeatherType = btn.dataset.weather || null;
                
                console.log(`选中事件类型: ${this.selectedEventType}, 天气类型: ${this.selectedWeatherType}`);
            });
        });

        // 强度选择按钮
        document.querySelectorAll('.severity-btn').forEach(btn => {
            btn.addEventListener('click', (e) => {
                // 移除所有按钮的active类
                document.querySelectorAll('.severity-btn').forEach(b => b.classList.remove('active'));
                // 给当前按钮添加active类
                btn.classList.add('active');
                
                // 存储选中的强度
                this.selectedSeverity = btn.dataset.severity;
            });
        });

        // 默认选中中等强度
        document.querySelector('.severity-btn[data-severity="medium"]').click();
        
        // 默认选中第一个天气事件按钮
        const firstWeatherBtn = document.querySelector('.event-type-btn[data-type="weather"]');
        if (firstWeatherBtn) {
            firstWeatherBtn.click();
        }

        // 在当前视图中心生成事件
        document.getElementById('generate-at-center-btn').addEventListener('click', () => {
            this.generateEventAtCenter();
        });

        // 修改定时器管理（修复问题5）
        this.startUpdateLoop();
    }

    // 生成特定类型的事件
generateSpecificEvent() {
    const eventType = this.selectedEventType || 'weather';
    const severity = this.selectedSeverity || 'medium';
    
    const center = window.map ? window.map.getCenter() : { lng: 104.10248, lat: 30.67646 };
    const position = [
        center.lng + (Math.random() - 0.5) * 0.02,
        center.lat + (Math.random() - 0.5) * 0.02
    ];
    
    let options = {};
    
    if (eventType === 'weather') {
        const weatherType = this.selectedWeatherType || 'rain';
        options.weatherType = weatherType;
        options.intensity = Math.random() * 0.8 + 0.2;
        
        // 根据天气类型调整强度
        if (weatherType === 'sandstorm') {
            // 沙尘暴通常影响范围更大
            options.intensity = Math.random() * 0.6 + 0.4;
        }
    } else if (eventType === 'traffic_jam') {
        options.congestionLevel = Math.random();
        options.expectedDelay = Math.floor(Math.random() * 30) + 5;
    } else if (eventType === 'road_closure') {
        options.closureType = Math.random() > 0.5 ? 'full' : 'partial';
        options.reason = ['construction', 'maintenance', 'event'][Math.floor(Math.random() * 3)];
    } else if (eventType === 'accident') {
        options.severity = severity;
        options.lanesAffected = Math.floor(Math.random() * 3) + 1;
    }
    
    const event = this.eventManager.addManualEvent(
        eventType,
        severity,
        position,
        options
    );
    
    this.updatePanel();
    
    // 显示通知
    let eventName = this.getEventTypeName(eventType);
    if (eventType === 'weather') {
        const weatherName = this.getWeatherName(options.weatherType);
        eventName = `${eventName} - ${weatherName}`;
    }
    
    this.showNotification(`事件已生成: ${eventName} (${severity})`);
}

// 在当前视图中心生成事件
generateEventAtCenter() {
    if (!window.map) return;
    
    const center = window.map.getCenter();
    const eventType = this.selectedEventType || 'weather';
    const severity = this.selectedSeverity || 'medium';
    
    const position = [center.lng, center.lat];
    
    let options = {};
    
    if (eventType === 'weather') {
        const weatherType = this.selectedWeatherType || 'rain';
        options.weatherType = weatherType;
        options.intensity = Math.random() * 0.8 + 0.2;
    } else if (eventType === 'traffic_jam') {
        options.congestionLevel = Math.random();
        options.expectedDelay = Math.floor(Math.random() * 30) + 5;
    }
    
    const event = this.eventManager.addManualEvent(
        eventType,
        severity,
        position,
        options
    );
    
    this.updatePanel();
    
    let eventName = this.getEventTypeName(eventType);
    if (eventType === 'weather') {
        const weatherName = this.getWeatherName(options.weatherType);
        eventName = `${eventName} - ${weatherName}`;
    }
    
    this.showNotification(`在视图中心生成事件: ${eventName}`);
}

// 更新getWeatherName方法，添加沙尘暴
getWeatherName(weatherType) {
    const names = {
        clear: '晴朗',
        rain: '降雨',
        snow: '降雪',
        fog: '大雾',
        storm: '暴雨',
        sandstorm: '沙尘暴'  // 添加沙尘暴
    };
    return names[weatherType] || weatherType;
}

// 修改生成测试事件方法，使用选中的类型
    generateTestEvent() {
        if (this.selectedEventType) {
            this.generateSpecificEvent();
        } else {
            // 如果没选中类型，生成随机事件
            this.generateRandomEvent();
        }
    }

    // 原来的generateRandomEvent方法重命名
    generateRandomEvent() {
        const eventTypes = ['weather', 'road_closure', 'traffic_jam', 'accident', 'special_event'];
        const randomType = eventTypes[Math.floor(Math.random() * eventTypes.length)];
        
        const severities = ['low', 'medium', 'high', 'critical'];
        const randomSeverity = severities[Math.floor(Math.random() * severities.length)];
        
        const center = window.map ? window.map.getCenter() : { lng: 104.10248, lat: 30.67646 };
        const position = [
            center.lng + (Math.random() - 0.5) * 0.02,
            center.lat + (Math.random() - 0.5) * 0.02
        ];
        
        let options = {};
        if (randomType === 'weather') {
            const weatherTypes = ['rain', 'storm', 'sandstorm', 'snow'];
            options.weatherType = weatherTypes[Math.floor(Math.random() * weatherTypes.length)];
            options.intensity = Math.random() * 0.8 + 0.2;
        } else if (randomType === 'traffic_jam') {
            options.congestionLevel = Math.random();
            options.expectedDelay = Math.floor(Math.random() * 30) + 5;
        }
        
        const event = this.eventManager.addManualEvent(
            randomType,
            randomSeverity,
            position,
            options
        );
        
        this.updatePanel();
        
        this.showNotification(`随机事件已生成: ${this.getEventTypeName(randomType)} (${randomSeverity})`);
    }

    startUpdateLoop() {
        // 清理旧的定时器（内存管理）
        if (this.updateInterval) {
            clearInterval(this.updateInterval);
        }
        
        this.updateInterval = setInterval(() => {
            this.updatePanel();
        }, 1000);
    }

    updatePanel() {
        if (!this.panel || this.panel.style.display === 'none') return;

        const events = this.eventManager.getActiveEvents();
        const eventsList = document.getElementById('events-list');
        const activeCount = document.getElementById('active-events-count');
        const affectedCount = document.getElementById('affected-vehicles-count');

        // 计算受影响车辆总数
        let totalAffected = 0;
        events.forEach(event => {
            totalAffected += event.affectedVehicles.size;
        });

        activeCount.textContent = events.length;
        affectedCount.textContent = totalAffected;

        if (events.length === 0) {
            eventsList.innerHTML = `
                <div style="text-align: center; padding: 20px; color: rgba(255, 255, 255, 0.5); font-size: 14px;">
                    暂无活动事件
                </div>
            `;
            return;
        }

        let html = '';
        events.forEach(event => {
            const progress = Math.max(0, Math.min(100, 
                ((event.endTime - Date.now()) / event.duration) * 100
            ));
            
            const severityColors = {
                low: '#4CAF50',
                medium: '#FF9800',
                high: '#F44336',
                critical: '#9C27B0'
            };

            html += `
                <div class="event-item" data-event-id="${event.id}">
                    <div class="event-header">
                        <span class="event-type">${this.getEventTypeName(event.type)}</span>
                        <span class="event-severity" style="background: ${severityColors[event.severity] || '#FF5722'}; color: white;">
                            ${event.severity}
                        </span>
                    </div>
                    <div class="event-details">
                        <div>位置: ${event.position[0].toFixed(4)}, ${event.position[1].toFixed(4)}</div>
                        <div>半径: ${event.radius.toFixed(0)}米</div>
                        <div>影响车辆: ${event.affectedVehicles.size}辆</div>
                        ${event.options.weatherType ? `<div>天气: ${this.getWeatherName(event.options.weatherType)}</div>` : ''}
                        ${event.options.expectedDelay ? `<div>预计延迟: ${event.options.expectedDelay}分钟</div>` : ''}
                    </div>
                    <div class="event-progress">
                        <div class="event-progress-bar" style="width: ${progress}%"></div>
                    </div>
                </div>
            `;
        });

        eventsList.innerHTML = html;

        // 添加点击事件
        document.querySelectorAll('.event-item').forEach(item => {
            item.addEventListener('click', (e) => {
                const eventId = item.dataset.eventId;
                const event = events.find(e => e.id === eventId);
                if (event) {
                    this.eventManager.showEventDetails(event);
                    // 将地图中心移动到事件位置
                    if (window.map && event.position) {
                        window.map.setCenter(event.position);
                        window.map.setZoom(15);
                    }
                }
            });
        });
    }

    destroy() {
        if (this.updateInterval) {
            clearInterval(this.updateInterval);
            this.updateInterval = null;
        }
        
        if (this.panel && this.panel.parentNode) {
            this.panel.parentNode.removeChild(this.panel);
        }
    }

    getEventTypeName(type) {
        const names = {
            weather: '天气',
            road_closure: '道路封闭',
            traffic_jam: '交通拥堵',
            accident: '事故',
            special_event: '特殊事件'
        };
        return names[type] || type;
    }

    getWeatherName(weatherType) {
        const names = {
            clear: '晴朗',
            rain: '降雨',
            snow: '降雪',
            fog: '大雾',
            storm: '暴风雨'
        };
        return names[weatherType] || weatherType;
    }

    showNotification(message) {
        const notification = document.createElement('div');
        notification.style.cssText = `
            position: fixed;
            top: 80px;
            right: 24px;
            background: rgba(25, 25, 35, 0.95);
            color: white;
            padding: 12px 20px;
            border-radius: 8px;
            box-shadow: 0 4px 12px rgba(0,0,0,0.3);
            z-index: 10001;
            animation: slideIn 0.5s ease;
            border-left: 4px solid #FF5722;
            max-width: 300px;
        `;
        
        notification.textContent = message;
        document.body.appendChild(notification);
        
        setTimeout(() => {
            notification.style.animation = 'slideOut 0.5s ease';
            setTimeout(() => {
                document.body.removeChild(notification);
            }, 500);
        }, 3000);
    }

    show() {
        if (this.panel) {
            this.panel.style.display = 'block';
            this.updatePanel();
        }
    }

    hide() {
        if (this.panel) {
            this.panel.style.display = 'none';
        }
    }

    toggle() {
        if (this.panel.style.display === 'none') {
            this.show();
        } else {
            this.hide();
        }
    }
}