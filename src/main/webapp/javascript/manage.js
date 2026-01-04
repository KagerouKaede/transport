document.addEventListener('DOMContentLoaded', function()
{
    const configManager = new SandboxManager();
    configManager.init();
});

class ConfigTemplate
{
    name = "Class.config_name";
    desc = "No description";
    type = "String|Integer|Boolean|Select|...";
    unit = "";
    value = "default value";
    exArgs = [];
}

class Group
{
    title = "Group Title";
    configList = [];
}

class SandboxInfo
{
    name = "Sandbox #0";
    createTime = "yyyy-MM-dd HH:mm:ss"
    simulationCycle = 0;
    configs = {};

    static groups = [];
}

class SandboxManager
{
    static configTemplates = [];

    constructor()
    {
        this.currentInfo = null;
        this.sandboxList = [];
    }

    async init()
    {
        const createBtn = document.getElementById("create-btn");
        createBtn.addEventListener('click', () => this.createSandbox());

        // TODO
        this.renderButtonEntry();

        await this.fetchSandboxList();
        this.renderSandboxPanel();

        await this.fetchConfigTemplate();
        this.renderConfigPanel();
    }

    // 获取所有数据沙箱
    async fetchSandboxList()
    {
        try
        {
            let url = new URL('/conf/getAllPresets', window.location.origin);
            const response = await fetch(url);
            if (!response.ok) throw new Error(`HTTP ${response.status}: ${response.statusText}`);

            const data = await response.json();

            if (Array.isArray(data))
            {
                data.forEach(item => {
                    try
                    {
                        const content = JSON.parse(item.content || '{}');
                        this.sandboxList.push(content);
                    }
                    catch (e)
                    {
                        console.warn('Failed to fetch sandbox:', item, e);
                    }
                });
            }
        }
        catch (error)
        {
            console.error('Failed to fetch sandbox list:', error);
            this.showStatus('获取预设列表失败: ' + error.message, 'error');
        }
    }

    async fetchConfigTemplate()
    {
        try {
            let url = new URL('/conf/getDefaultConfig', window.location.origin);
            const response = await fetch(url, { method: 'GET' });
            
            if (!response.ok) throw new Error(`HTTP ${response.status}: ${response.statusText}`);
            
            const result = await response.json();
            console.log("后端响应完整结果:", result);
        
            if (result.success) {
                try {
                    // 解析 message 字段
                    const messageData = JSON.parse(result.message);
                    console.log("解析后的 messageData:", messageData);
                    
                    // 获取 UUID
                    this.currentConfig.uuid = messageData.UUID || "0";
                    
                    // 处理content
                    let content = JSON.parse(messageData.content);
                    console.log("最终 content:", content);
                    
                    if (content && typeof content === 'object') {
                        // 更新配置名称和内容
                        this.currentConfig.name = content.name || "Default";
                        this.currentConfig.configs = content.configs || content;
                    } else {
                        console.warn("content 不是对象，无法解析配置");
                        this.currentConfig.name = "Default";
                        this.currentConfig.configs = {};
                    }
                    
                    console.log("最终配置结构:", this.currentConfig);
                    
                    this.renderConfigForm();
                    this.showStatus('配置加载成功', 'success');
                    this.isConfigModified = false;
                } catch (parseError) {
                    console.error('解析配置数据失败:', parseError, '原始数据:', result.message);
                    this.showStatus('配置数据格式错误，请检查控制台', 'error');
                }
            } else {
                throw new Error(result.message);
            }
        } catch (error) {
            console.error('获取配置数据时出错:', error);
            this.showStatus('获取配置失败: ' + error.message, 'error');
        }
    }
    
    
    // 保存配置到后端
    async saveConfigToBackend(uuid, isNew = false) {
        try {
            let url = new URL('/conf/savePreset', window.location.origin);
            
            // 确保使用当前表单数据
            this.updateConfigFromForm();
            
            // 构建保存的配置对象
            const configToSave = {
                name: this.currentConfig.name,
                configs: this.currentConfig.configs
            };
            const fullJson = JSON.stringify(configToSave);
            
            console.log("保存的配置内容:", configToSave);
            
            const params = new URLSearchParams();
            if (!isNew && uuid) {
                params.append('UUID', uuid);
            }
            params.append('content', fullJson);
            
            const response = await fetch(url, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded',
                },
                body: params.toString()
            });

            if (!response.ok) throw new Error(`HTTP ${response.status}: ${response.statusText}`);
            
            const result = await response.json();
            if (result.success) {
                try {
                    const messageData = JSON.parse(result.message);
                    return { 
                        success: true, 
                        uuid: messageData.UUID,
                        message: '配置保存成功！' 
                    };
                } catch (e) {
                    return { success: true, message: '配置保存成功！' };
                }
            } else {
                throw new Error(result.message || '保存失败');
            }
        } catch (error) {
            console.error('保存配置数据时出错:', error);
            return { success: false, error: error.message };
        }
    }
    
    // 应用预设
    async applyPreset(presetUuid) {
        try {
            let url = new URL('/conf/applyPreset', window.location.origin);
            url.searchParams.append("UUID", presetUuid);
            const response = await fetch(url, { method: 'GET' });
            
            if (!response.ok) throw new Error(`HTTP ${response.status}: ${response.statusText}`);
            
            const result = await response.json();
            
            if (result.success) {
                this.showStatus('预设已成功应用于系统', 'success');
            } else {
                throw new Error(result.message);
            }
        } catch (error) {
            console.error('应用预设失败:', error);
            this.showStatus(`应用预设失败: ${error.message}`, 'error');
        }
    }

    // 点击预设加载数据逻辑
    async loadPresetToUI(preset) {
        try {
            // 将预设的数据设置为当前配置
            this.currentConfig.uuid = preset.uuid;
            this.currentConfig.name = preset.content.name || "未命名预设";
            this.currentConfig.configs = preset.content.configs || preset.content;
            this.selectedPreset = preset;
            
            this.isConfigModified = false;
            this.renderConfigForm();
            this.renderButtonEntry();
            this.showStatus(`已加载预设: ${preset.name}`, 'success');
        } catch (error) {
            console.error('加载预设到UI失败:', error);
            this.showStatus(`加载预设失败: ${error.message}`, 'error');
        }
    }
    
    // 启动仿真系统
    async startSimulation() {
        try {
            const startBtn = document.getElementById('start-simulation-btn');
            startBtn.innerHTML = '<i>⏳</i> 启动中...';
            startBtn.disabled = true;

            let url = new URL('/conf/startSimulation', window.location.origin);
            const response = await fetch(url, { method: 'GET' });

            const result = await response.json();
            if (result.success) {
                this.showStatus('仿真系统启动成功！正在打开监控界面...', 'success');
                setTimeout(() => {
                    window.open('map.html', '_blank');
                }, 2000);
            } else {
                throw new Error(result.message);
            }
        } catch (error) {
            console.error('启动仿真系统时出错:', error);
            this.showStatus(`启动失败: ${error.message}`, 'error');
        } finally {
            const startBtn = document.getElementById('start-simulation-btn');
            startBtn.innerHTML = '<i>😛</i> 启动仿真系统';
            startBtn.disabled = false;
        }
    }
    
    // 另存为新预设
    async saveAsNewPreset() {
        // 先弹出输入预设名称
        const name = prompt("请输入预设名称:", this.currentConfig.name);
        if (!name) return;
        
        // 更新当前配置的名称
        this.currentConfig.name = name;
        
        // 将新名称设置到表单的配置名称输入框中
        const nameInput = document.getElementById('config-name');
        if (nameInput) {
            nameInput.value = name;
        }
        
        // 更新表单数据到当前配置（这会获取表单中所有参数的值）
        this.updateConfigFromForm();
        
        // 保存为新预设
        const saveResult = await this.saveConfigToBackend(null, true);
        if (saveResult.success) {
            this.currentConfig.uuid = saveResult.uuid;
            this.isConfigModified = false;
            this.showStatus(`预设 "${name}" 保存成功`, 'success');
            await this.fetchSandboxList();
        } else {
            this.showStatus(`保存预设失败: ${saveResult.error}`, 'error');
        }
    }
    
    // 保存当前配置（修改已存在的预设）
    async saveCurrentPreset() {
        // 如果是默认配置（uuid为"0"），则调用另存为
        if (!this.currentConfig.uuid || this.currentConfig.uuid === "0") {
            this.showStatus('默认配置不能直接保存，请使用"另存为"功能', 'error');
            return this.saveAsNewPreset();
        }
        
        // 更新表单数据到当前配置
        this.updateConfigFromForm();
        
        // 保存到当前UUID
        const saveResult = await this.saveConfigToBackend(this.currentConfig.uuid, false);
        if (saveResult.success) {
            this.isConfigModified = false;
            this.showStatus('配置保存成功', 'success');
            await this.fetchSandboxList();
        } else {
            this.showStatus(`保存失败: ${saveResult.error}`, 'error');
        }
    }

    // 删除预设
    async deleteSelectedPreset() {
        if (this.selectedPreset) {
            const preset = this.selectedPreset;
            if (confirm(`确定要删除预设 "${preset.name}" 吗？`)) {
                try {
                    let url = new URL('/conf/rmvPreset', window.location.origin);
                    url.searchParams.append("UUID", preset.uuid);
                    const response = await fetch(url, { method: 'GET' });

                    const result = await response.json();
                    if (result.success) {
                        this.showStatus(`预设 "${preset.name}" 已删除`, 'success');
                        this.selectedPreset = null;
                        
                        // 如果删除的是当前配置，恢复默认配置
                        if (this.currentConfig.uuid === preset.uuid) {
                            this.currentConfig.uuid = "0";
                            this.currentConfig.name = "Default";
                            await this.fetchConfigTemplate();
                        }
                        
                        await this.fetchSandboxList();
                    } else {
                        throw new Error(result.message);
                    }
                } catch (error) {
                    console.error('删除预设失败:', error);
                    this.showStatus(`删除预设失败: ${error.message}`, 'error');
                }
            }
        } else {
            this.showStatus('请先选择一个预设', 'error');
        }
    }
    
    // 渲染预设列表（已移除，保留方法以兼容现有调用）
    renderPresetList() {
        // 预设列表功能已移除，此方法保留为空以避免错误
    }
    
    // 更新表单数据到配置对象
    updateConfigFromForm() {
        // 更新配置名称
        const nameInput = document.getElementById('config-name');
        if (nameInput) {
            this.currentConfig.name = nameInput.value;
        }
        
        // 更新所有参数值
        let index = 0;
        for (const key in this.currentConfig.configs) {
            const input = document.getElementById(`param-${index}`);
            if (input) {
                this.currentConfig.configs[key].value = input.value;
            }
            index++;
        }
        
        this.isConfigModified = true;
    }

    // 渲染配置表单
    renderConfigForm() {
        console.log("渲染配置表单");
        const sectionsContainer = document.getElementById('config-content');
        sectionsContainer.innerHTML = '';

        // 配置名称和UUID显示
        const infoSection = document.createElement('div');
        infoSection.className = 'config-section';
        infoSection.innerHTML = `
            <div class="form-group">
                <label for="config-name">配置名称</label>
                <input type="text" id="config-name" class="form-control" value="${this.currentConfig.name || ''}">
                <div class="unit-display">${this.isConfigModified ? '未保存' : ''}</div>
            </div>
            <div class="form-group">
                <label>配置标识</label>
                <input type="text" class="form-control" value="${this.currentConfig.uuid || '0'}" readonly>
                <div class="unit-display">UUID</div>
            </div>
        `;
        sectionsContainer.appendChild(infoSection);

        // 参数配置部分
        const section = document.createElement('div');
        section.className = 'config-section';
        const configValues = [];
        for (const key in this.currentConfig.configs) {
            configValues.push({...this.currentConfig.configs[key], key: key});
        }
        section.innerHTML = `
            <div class="section-header">
                <div class="section-title">
                    <span class="collapse-icon">▼</span>
                    系统参数
                    <span class="section-count">${configValues.length}</span>
                </div>
            </div>
            <div class="section-content">
                <div class="config-form">
                    ${configValues.map((config, index) => this.renderParameterHTML(config, index)).join('')}
                </div>
            </div>
        `;
        sectionsContainer.appendChild(section);
        this.attachSectionEvents();
        this.attachInputEvents();
    }
    
    renderParameterHTML(config, index) {
        return `
            <div class="form-group">
                <label for="param-${index}" title="${config.key}">${config.name}</label>
                <input type="text" id="param-${index}" class="form-control" value="${config.value}">
                <div class="unit-display">${config.unit || ''}</div>
            </div>
        `;
    }
    
    attachSectionEvents() {
        document.querySelectorAll('.section-header').forEach(header => {
            header.addEventListener('click', () => {
                const content = header.nextElementSibling;
                const icon = header.querySelector('.collapse-icon');
                const isCollapsed = content.classList.contains('collapsed');
                content.classList.toggle('collapsed', !isCollapsed);
                icon.textContent = isCollapsed ? '▼' : '▶';
            });
        });
    }
    
    attachInputEvents() {
        const configNameInput = document.getElementById('config-name');
        if (configNameInput) {
            configNameInput.addEventListener('input', () => {
                this.isConfigModified = true;
            });
        }
        
        let index = 0;
        for (const key in this.currentConfig.configs) {
            const input = document.getElementById(`param-${index}`);
            if (input) {
                input.addEventListener('input', () => {
                    this.isConfigModified = true;
                });
            }
            index++;
        }
    }
    
    showStatus(message, type) {
        const statusEl = document.getElementById('status-message');
        statusEl.textContent = message;
        statusEl.className = `status-message status-${type}`;
        statusEl.style.display = 'block';
        setTimeout(() => { statusEl.style.display = 'none'; }, 5000);
    }
    
    // 渲染沙箱列表
    renderSandboxPanel() {
        const sandboxListEl = document.getElementById('sandbox-list');
        const emptyTips = document.getElementById('empty-tips');
        
        // 如果有沙箱数据，隐藏空提示
        if (this.sandboxList.length > 0 && emptyTips) {
            emptyTips.style.display = 'none';
        } else if (emptyTips) {
            emptyTips.style.display = 'block';
        }
        
        // TODO: 实现沙箱列表的渲染逻辑
        // 这里需要根据实际的沙箱数据结构来实现
    }
    
    // 渲染按钮列表
    renderButtonEntry() {
        const buttonEntry = document.getElementById('button-entry');
        if (!buttonEntry) return;
        
        buttonEntry.innerHTML = `
            <li>
                <button id="save-preset-btn">保存配置</button>
            </li>
            <li>
                <button id="save-as-btn">另存为</button>
            </li>
            <li>
                <button id="start-simulation-btn">启动仿真系统</button>
            </li>
        `;
        
        // 绑定按钮事件
        const savePresetBtn = document.getElementById('save-preset-btn');
        if (savePresetBtn) {
            savePresetBtn.addEventListener('click', () => this.saveCurrentPreset());
        }
        
        const saveAsBtn = document.getElementById('save-as-btn');
        if (saveAsBtn) {
            saveAsBtn.addEventListener('click', () => this.saveAsNewPreset());
        }
        
        const startSimulationBtn = document.getElementById('start-simulation-btn');
        if (startSimulationBtn) {
            startSimulationBtn.addEventListener('click', () => this.startSimulation());
        }
    }
    
    // 创建沙箱
    async createSandbox() {
        try {
            // TODO: 实现创建沙箱的逻辑
            // 使用默认预设创建数据沙箱
            this.showStatus('创建沙箱功能待实现', 'success');
        } catch (error) {
            console.error('创建沙箱失败:', error);
            this.showStatus(`创建沙箱失败: ${error.message}`, 'error');
        }
    }
    
    openDataSandbox() {
        window.open('sandbox.html', '_blank', 'width=1400,height=900,resizable=yes');
    }
}
