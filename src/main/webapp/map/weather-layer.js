//天气特效图层
export class WeatherEffectLayer 
{
    constructor(map) {
        this.map = map;
        this.canvas = null;
        this.ctx = null;
        this.animationId = null;
        this.effects = new Map();
        this.visible = true;
        
        this.init();
    }
    
    init() {
        this.canvas = document.createElement('canvas');
        this.canvas.style.cssText = `
            position: absolute;
            top: 0;
            left: 0;
            pointer-events: none;
            z-index: 10;
        `;
        this.ctx = this.canvas.getContext('2d');
        
        const container = this.map.getContainer();
        container.appendChild(this.canvas);
        
        this.resize();
        this.startAnimation();
        
        // 监听地图变化
        this.map.on('resize', () => this.resize());
        this.map.on('zoomchange', () => this.render());
        this.map.on('moveend', () => this.render());
    }
    
    resize() {
        const size = this.map.getSize();
        this.canvas.width = size.width;
        this.canvas.height = size.height;
        this.canvas.style.width = size.width + 'px';
        this.canvas.style.height = size.height + 'px';
        this.render();
    }
    
    addWeatherEffect(event) {
        const effectId = event.id;
        
        // 调试信息
        console.log(`添加天气效果: ${event.weatherType}, 事件对象:`, event);
        
        switch (event.weatherType) {
            case 'rain':
                this.addRainEffect(event);
                break;
            case 'snow':
                this.addSnowEffect(event);
                break;
            case 'fog':
                this.addFogEffect(event);
                break;
            case 'storm':
                this.addStormEffect(event);
                break;
            case 'sandstorm':
                this.addSandstormEffect(event);
                break;
            default:
                console.warn(`未知的天气类型: ${event.weatherType}`);
        }
    }
    
    addRainEffect(event) {
        const effectId = event.id;
        const particles = [];
        // 使用event.intensity而不是event.options.intensity
        const intensity = event.intensity || 0.5;
        const particleCount = Math.floor(300 * intensity);
        
        // 获取事件中心在画布上的像素坐标
        const pixelPos = this.map.lngLatToContainer(event.position);
        const radiusInPixels = this.metersToPixels(event.radius, event.position[1]);
        
        console.log(`添加雨效果: 位置=${pixelPos.x},${pixelPos.y}, 半径=${radiusInPixels}px, 粒子数=${particleCount}`);
        
        // 创建雨滴粒子
        for (let i = 0; i < particleCount; i++) {
            // 在圆形区域内随机位置生成粒子
            const angle = Math.random() * Math.PI * 2;
            const r = Math.random() * radiusInPixels;
            const x = pixelPos.x + Math.cos(angle) * r;
            const y = pixelPos.y + Math.sin(angle) * r;
            
            particles.push({
                x: x,
                y: y,
                length: 10 + Math.random() * 10,
                speed: 10 + Math.random() * 20 * intensity,
                opacity: 0.3 + Math.random() * 0.3,
                sway: Math.random() * 2 - 1
            });
        }
        
        this.effects.set(effectId, {
            type: 'rain',
            particles,
            position: event.position,
            radius: event.radius,
            intensity,
            // 添加热力圆环属性
            heatRing: {
                pulse: 0,
                pulseSpeed: 0.5 + Math.random() * 0.5,
                color: this.getHeatRingColor('rain', intensity)
            },
            lastUpdate: Date.now()
        });
    }

    addSnowEffect(event) {
        const effectId = event.id;
        const particles = [];
        const intensity = event.intensity || 0.5;
        const particleCount = Math.floor(150 * intensity);
        
        // 获取事件中心在画布上的像素坐标
        const pixelPos = this.map.lngLatToContainer(event.position);
        const radiusInPixels = this.metersToPixels(event.radius, event.position[1]);
        
        // 创建雪花粒子
        for (let i = 0; i < particleCount; i++) {
            // 在圆形区域内随机位置生成粒子
            const angle = Math.random() * Math.PI * 2;
            const r = Math.random() * radiusInPixels;
            const x = pixelPos.x + Math.cos(angle) * r;
            const y = pixelPos.y + Math.sin(angle) * r;
            
            particles.push({
                x: x,
                y: y,
                size: 1 + Math.random() * 3,
                speed: 0.5 + Math.random() * 1 * intensity,
                opacity: 0.3 + Math.random() * 0.4,
                sway: Math.random() * 1 - 0.5,
                rotation: Math.random() * Math.PI * 2
            });
        }
        
        this.effects.set(effectId, {
            type: 'snow',
            particles,
            position: event.position,
            radius: event.radius,
            intensity,
            // 添加热力圆环属性
            heatRing: {
                pulse: 0,
                pulseSpeed: 0.3 + Math.random() * 0.4,
                color: this.getHeatRingColor('snow', intensity)
            },
            lastUpdate: Date.now()
        });
    }
    
    addFogEffect(event) {
        const effectId = event.id;
        const intensity = event.intensity || 0.5;
        
        this.effects.set(effectId, {
            type: 'fog',
            position: event.position,
            radius: event.radius,
            intensity,
            opacity: 0.1 + intensity * 0.3,
            density: 20 + intensity * 30,
            // 添加热力圆环属性
            heatRing: {
                pulse: 0,
                pulseSpeed: 0.2 + Math.random() * 0.3,
                color: this.getHeatRingColor('fog', intensity)
            }
        });
    }
    
    addStormEffect(event) {
        const effectId = event.id;
        const particles = [];
        const intensity = event.intensity || 0.5;
        const particleCount = Math.floor(400 * intensity);
        
        // 获取事件中心在画布上的像素坐标
        const pixelPos = this.map.lngLatToContainer(event.position);
        const radiusInPixels = this.metersToPixels(event.radius, event.position[1]);
        
        // 创建暴风雨粒子
        for (let i = 0; i < particleCount; i++) {
            // 在圆形区域内随机位置生成粒子
            const angle = Math.random() * Math.PI * 2;
            const r = Math.random() * radiusInPixels;
            const x = pixelPos.x + Math.cos(angle) * r;
            const y = pixelPos.y + Math.sin(angle) * r;
            
            particles.push({
                x: x,
                y: y,
                length: 15 + Math.random() * 15,
                speed: 20 + Math.random() * 30 * intensity,
                opacity: 0.4 + Math.random() * 0.3,
                sway: 2 + Math.random() * 4,
                wind: Math.random() * 10 - 5
            });
        }
        
        this.effects.set(effectId, {
            type: 'storm',
            particles,
            position: event.position,
            radius: event.radius,
            intensity,
            flash: 0,
            // 添加热力圆环属性
            heatRing: {
                pulse: 0,
                pulseSpeed: 0.8 + Math.random() * 0.7,
                color: this.getHeatRingColor('storm', intensity)
            },
            lastUpdate: Date.now()
        });
    }
    
    addSandstormEffect(event) {
        const effectId = event.id;
        const particles = [];
        const intensity = event.intensity || 0.5;
        const particleCount = Math.floor(400 * intensity);
        
        // 获取事件中心在画布上的像素坐标
        const pixelPos = this.map.lngLatToContainer(event.position);
        const radiusInPixels = this.metersToPixels(event.radius, event.position[1]);
        
        // 创建沙尘暴粒子
        for (let i = 0; i < particleCount; i++) {
            // 在圆形区域内随机位置生成粒子
            const angle = Math.random() * Math.PI * 2;
            const r = Math.random() * radiusInPixels;
            const x = pixelPos.x + Math.cos(angle) * r;
            const y = pixelPos.y + Math.sin(angle) * r;
            
            particles.push({
                x: x,
                y: y,
                size: 0.5 + Math.random() * 2,
                speed: 3 + Math.random() * 10 * intensity,
                opacity: 0.4 + Math.random() * 0.4,
                sway: Math.random() * 3 - 1.5,
                rotation: Math.random() * Math.PI * 2,
                color: `rgba(${Math.floor(200 + Math.random() * 55)}, 
                            ${Math.floor(150 + Math.random() * 105)}, 
                            ${Math.floor(50 + Math.random() * 50)}, 0.8)`
            });
        }
        
        this.effects.set(effectId, {
            type: 'sandstorm',
            particles,
            position: event.position,
            radius: event.radius,
            intensity,
            // 添加热力圆环属性
            heatRing: {
                pulse: 0,
                pulseSpeed: 0.6 + Math.random() * 0.5,
                color: this.getHeatRingColor('sandstorm', intensity)
            },
            lastUpdate: Date.now()
        });
    }

    updateParticles(effectId, effect) 
    {
        const now = Date.now();
        const deltaTime = (now - effect.lastUpdate) / 1000;
        
        // 获取事件中心在画布上的像素坐标
        const pixelPos = this.map.lngLatToContainer(effect.position);
        const radiusInPixels = this.metersToPixels(effect.radius, effect.position[1]);
        
        // 更新热力圆环脉冲
        if (effect.heatRing) {
            effect.heatRing.pulse += effect.heatRing.pulseSpeed * deltaTime;
            if (effect.heatRing.pulse > Math.PI * 2) {
                effect.heatRing.pulse -= Math.PI * 2;
            }
        }
        
        switch (effect.type) {
            case 'rain':
                this.updateRainParticles(effect, deltaTime, pixelPos, radiusInPixels);
                break;
            case 'snow':
                this.updateSnowParticles(effect, deltaTime, pixelPos, radiusInPixels);
                break;
            case 'storm':
                this.updateStormParticles(effect, deltaTime, pixelPos, radiusInPixels);
                break;
            case 'sandstorm':
                this.updateSandstormParticles(effect, deltaTime, pixelPos, radiusInPixels);
                break;
            // 雾效果不需要更新粒子
        }
        
        effect.lastUpdate = now;
    }
    
    updateRainParticles(effect, deltaTime, pixelPos, radiusInPixels) {
        effect.particles.forEach(p => {
            p.y += p.speed * deltaTime * 60;
            p.x -= 2 * deltaTime * 30;
            
            const dx = p.x - pixelPos.x;
            const dy = p.y - pixelPos.y;
            const distance = Math.sqrt(dx * dx + dy * dy);
            
            if (distance > radiusInPixels || p.y > this.canvas.height) {
                const angle = Math.random() * Math.PI * 2;
                const r = Math.random() * radiusInPixels;
                p.x = pixelPos.x + Math.cos(angle) * r;
                p.y = pixelPos.y + Math.sin(angle) * r - radiusInPixels;
            }
        });
    }

    updateSnowParticles(effect, deltaTime, pixelPos, radiusInPixels) {
        effect.particles.forEach(p => {
            p.y += p.speed * deltaTime * 30;
            p.x += p.sway * deltaTime * 20;
            p.rotation += deltaTime;
            
            const dx = p.x - pixelPos.x;
            const dy = p.y - pixelPos.y;
            const distance = Math.sqrt(dx * dx + dy * dy);
            
            if (distance > radiusInPixels) {
                const angle = Math.random() * Math.PI * 2;
                const r = Math.random() * radiusInPixels;
                p.x = pixelPos.x + Math.cos(angle) * r;
                p.y = pixelPos.y + Math.sin(angle) * r - radiusInPixels;
            }
        });
    }

    updateStormParticles(effect, deltaTime, pixelPos, radiusInPixels) {
        effect.particles.forEach(p => {
            p.y += p.speed * deltaTime * 60;
            p.x += (p.sway + p.wind) * deltaTime * 30;
            
            const dx = p.x - pixelPos.x;
            const dy = p.y - pixelPos.y;
            const distance = Math.sqrt(dx * dx + dy * dy);
            
            if (distance > radiusInPixels || p.y > this.canvas.height) {
                const angle = Math.random() * Math.PI * 2;
                const r = Math.random() * radiusInPixels;
                p.x = pixelPos.x + Math.cos(angle) * r;
                p.y = pixelPos.y + Math.sin(angle) * r - radiusInPixels;
            }
        });
        
        if (Math.random() < 0.001 * effect.intensity) {
            effect.flash = 1;
        } else if (effect.flash > 0) {
            effect.flash -= deltaTime;
        }
    }

    updateSandstormParticles(effect, deltaTime, pixelPos, radiusInPixels) {
        effect.particles.forEach(p => {
            p.y += p.speed * deltaTime * 40;
            p.x += p.sway * deltaTime * 30;
            p.rotation += deltaTime * 2;
            
            const dx = p.x - pixelPos.x;
            const dy = p.y - pixelPos.y;
            const distance = Math.sqrt(dx * dx + dy * dy);
            
            if (distance > radiusInPixels) {
                const angle = Math.random() * Math.PI * 2;
                const r = Math.random() * radiusInPixels;
                p.x = pixelPos.x + Math.cos(angle) * r;
                p.y = pixelPos.y + Math.sin(angle) * r - radiusInPixels;
            }
        });
    }

    render() {
        if (!this.visible || !this.ctx) return;
        
        this.ctx.clearRect(0, 0, this.canvas.width, this.canvas.height);
        
        for (const [effectId, effect] of this.effects) {
            const pixelPos = this.map.lngLatToContainer(effect.position);
            const radiusInPixels = this.metersToPixels(effect.radius, effect.position[1]);
            
            // 先绘制热力圆环（在裁剪区域外）
            if (effect.heatRing) {
                this.renderHeatRing(effect, pixelPos, radiusInPixels);
            }
            
            // 保存上下文状态
            this.ctx.save();
            
            // 创建圆形裁剪区域（只影响内部粒子）
            this.ctx.beginPath();
            this.ctx.arc(pixelPos.x, pixelPos.y, radiusInPixels, 0, Math.PI * 2);
            this.ctx.clip();
            
            // 更新和渲染粒子
            this.updateParticles(effectId, effect);
            
            switch (effect.type) {
                case 'rain':
                    this.renderRain(effect, pixelPos, radiusInPixels);
                    break;
                case 'snow':
                    this.renderSnow(effect);
                    break;
                case 'fog':
                    this.renderFog(effect, pixelPos, radiusInPixels);
                    break;
                case 'storm':
                    this.renderStorm(effect);
                    break;
                case 'sandstorm':
                    this.renderSandstorm(effect); 
                    break;
            }
            
            this.ctx.restore();
        }
    }
    
    // 渲染热力圆环
    renderHeatRing(effect, pixelPos, radiusInPixels) {
        const ring = effect.heatRing;
        const pulseValue = Math.sin(ring.pulse) * 0.5 + 0.5; // 0到1的脉冲值
        
        // 根据强度调整圆环宽度和透明度
        const ringWidth = 4 + effect.intensity * 6;
        const baseOpacity = 0.3 + effect.intensity * 0.3;
        
        // 创建径向渐变（空心圆环效果）
        const gradient = this.ctx.createRadialGradient(
            pixelPos.x, pixelPos.y, radiusInPixels - ringWidth,
            pixelPos.x, pixelPos.y, radiusInPixels
        );
        
        // 根据天气类型和强度调整颜色
        const ringColor = this.getHeatRingColor(effect.type, effect.intensity);
        
        // 渐变从透明到颜色再到透明
        gradient.addColorStop(0, `rgba(${ringColor.r}, ${ringColor.g}, ${ringColor.b}, 0)`);
        gradient.addColorStop(0.5, `rgba(${ringColor.r}, ${ringColor.g}, ${ringColor.b}, ${baseOpacity * pulseValue})`);
        gradient.addColorStop(1, `rgba(${ringColor.r}, ${ringColor.g}, ${ringColor.b}, 0)`);
        
        // 绘制圆环
        this.ctx.beginPath();
        this.ctx.arc(pixelPos.x, pixelPos.y, radiusInPixels - ringWidth/2, 0, Math.PI * 2);
        this.ctx.strokeStyle = gradient;
        this.ctx.lineWidth = ringWidth;
        this.ctx.stroke();
        
        // 添加外发光效果
        this.ctx.beginPath();
        this.ctx.arc(pixelPos.x, pixelPos.y, radiusInPixels, 0, Math.PI * 2);
        this.ctx.strokeStyle = `rgba(${ringColor.r}, ${ringColor.g}, ${ringColor.b}, ${0.1 * pulseValue})`;
        this.ctx.lineWidth = 2;
        this.ctx.stroke();
    }
    
    // 获取热力圆环颜色
    getHeatRingColor(weatherType, intensity) {
        switch(weatherType) {
            case 'rain':
                return {r: 100, g: 150, b: 255}; // 蓝色
            case 'snow':
                return {r: 240, g: 240, b: 255}; // 淡蓝色/白色
            case 'fog':
                return {r: 180, g: 180, b: 180}; // 灰色
            case 'storm':
                return {r: 50, g: 80, b: 180}; // 深蓝色
            case 'sandstorm':
                return {r: 210, g: 180, b: 100}; // 沙黄色
            default:
                return {r: 200, g: 200, b: 200}; // 默认灰色
        }
    }
    
    renderRain(effect, pixelPos, radiusInPixels) {
        this.ctx.strokeStyle = 'rgba(100, 150, 255, 0.6)';
        this.ctx.lineWidth = 1;
        this.ctx.lineCap = 'round';
        
        effect.particles.forEach(p => {
            this.ctx.globalAlpha = p.opacity;
            
            this.ctx.beginPath();
            this.ctx.moveTo(p.x, p.y);
            this.ctx.lineTo(p.x - 2, p.y + p.length);
            this.ctx.stroke();
            
            this.ctx.beginPath();
            this.ctx.arc(p.x - 2, p.y + p.length, 0.5, 0, Math.PI * 2);
            this.ctx.fillStyle = 'rgba(100, 150, 255, 0.6)';
            this.ctx.fill();
        });
    }
    
    renderSnow(effect) {
        effect.particles.forEach(p => {
            this.ctx.save();
            this.ctx.globalAlpha = p.opacity;
            this.ctx.translate(p.x, p.y);
            this.ctx.rotate(p.rotation);
            
            this.ctx.fillStyle = 'rgba(255, 255, 255, 0.8)';
            this.ctx.beginPath();
            this.ctx.arc(0, 0, p.size, 0, Math.PI * 2);
            this.ctx.fill();
            
            this.ctx.restore();
        });
    }
    
    renderFog(effect) {
        const pixelPos = this.map.lngLatToContainer(effect.position);
        const radiusInPixels = this.metersToPixels(effect.radius, effect.position[1]);
        
        const gradient = this.ctx.createRadialGradient(
            pixelPos.x, pixelPos.y, 0,
            pixelPos.x, pixelPos.y, radiusInPixels
        );
        
        gradient.addColorStop(0, `rgba(200, 200, 200, ${effect.opacity * 0.3})`);
        gradient.addColorStop(0.5, `rgba(180, 180, 180, ${effect.opacity * 0.6})`);
        gradient.addColorStop(1, `rgba(150, 150, 150, ${effect.opacity})`);
        
        this.ctx.fillStyle = gradient;
        this.ctx.fillRect(0, 0, this.canvas.width, this.canvas.height);
    }
    
    renderStorm(effect) {
        this.renderRain(effect);
        
        if (effect.flash > 0) {
            this.ctx.fillStyle = `rgba(255, 255, 200, ${effect.flash * 0.3})`;
            this.ctx.fillRect(0, 0, this.canvas.width, this.canvas.height);
        }
    }

    renderSandstorm(effect) {
        effect.particles.forEach(p => {
            this.ctx.save();
            this.ctx.globalAlpha = p.opacity;
            this.ctx.translate(p.x, p.y);
            this.ctx.rotate(p.rotation);
            
            this.ctx.fillStyle = p.color;
            this.ctx.beginPath();
            this.ctx.ellipse(0, 0, p.size * 1.5, p.size, 0, 0, Math.PI * 2);
            this.ctx.fill();
            
            this.ctx.restore();
        });
    }
    
    metersToPixels(meters, latitude) {
        const metersPerPixel = 156543.03392 * Math.cos(latitude * Math.PI / 180) / Math.pow(2, this.map.getZoom());
        return meters / metersPerPixel;
    }
    
    startAnimation() {
        const animate = () => {
            this.render();
            this.animationId = requestAnimationFrame(animate);
        };
        animate();
    }
    
    removeEffect(effectId) {
        this.effects.delete(effectId);
    }
    
    clearEffects() {
        this.effects.clear();
    }
    
    show() {
        this.visible = true;
        this.canvas.style.display = 'block';
    }
    
    hide() {
        this.visible = false;
        this.canvas.style.display = 'none';
    }
    
    destroy() {
        if (this.animationId) {
            cancelAnimationFrame(this.animationId);
        }
        if (this.canvas && this.canvas.parentNode) {
            this.canvas.parentNode.removeChild(this.canvas);
        }
    }
}
