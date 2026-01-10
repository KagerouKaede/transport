document.addEventListener('DOMContentLoaded', function()
{
    const configManager = new SandboxManager();
    configManager.init();
});

class SandboxManager
{
    constructor()
    {
        this.template = {};
        this.currentInfo = null;
        this.sandboxList = [];
    }

    toast(type, message)
    {
        const toastEl = document.getElementById('toast');
        toastEl.className = '';
        toastEl.textContent = message;
        toastEl.classList.add(type);
        toastEl.classList.remove('hide');
        
        setTimeout(() => {
            toastEl.classList.add('hide');
        }, 3000);
    }

    async init()
    {
        await this.fetchSandboxList();
        await this.fetchConfigTemplate();

        this.renderSandboxPanel();
        this.renderConfigPanel();
        this.initCreatePanel();
        this.initButtons();
    }

    /** 获取所有数据沙箱 */
    async fetchSandboxList()
    {
        try
        {
            let url = new URL('/sandbox/getAllSandbox', window.location.origin);
            const response = await fetch(url);
            if (!response.ok) throw new Error(`HTTP ${response.status}: ${response.statusText}`);

            const data = await response.json();

            if (Array.isArray(data))
            {
                data.forEach(item => {
                    try
                    {
                        const content = JSON.parse(item.content || '{}');
                        let sandbox = {
                            uuid: item.UUID,
                            name: content.sandbox_name || 'Sandbox #'+this.sandboxList.length,
                            createTime: content.create_time || 'Unknown',
                            simulationCycle: content.simulation_cycle || 0,
                            configs: content.configs || {}
                        }

                        this.sandboxList.push(sandbox);
                    }
                    catch (e)
                    {
                        console.warn('Failed to fetch sandbox:', item, e);
                    }
                });
            }

            if (this.sandboxList.length > 0)
            {
                this.currentInfo = this.sandboxList[0];
                document.getElementById('sandbox-empty-tips').classList.add('hide');
                document.getElementById('config-empty-tips').classList.add('hide');
            }
        }
        catch (error)
        {
            console.error('Failed to fetch sandbox list:', error);
            this.toast('error', '获取沙箱列表失败: ' + error.message);
        }
    }

    /** 获取沙箱数据格式信息 */
    async fetchConfigTemplate()
    {
        try
        {
            let url = new URL('/sandbox/getConfigTemplate', window.location.origin);
            const response = await fetch(url);
            if (!response.ok) throw new Error(`HTTP ${response.status}: ${response.statusText}`);

            const result = await response.json();
            if (result.success)
            {
                try
                {
                    this.template = JSON.parse(result.message);
                }
                catch (parseError)
                {
                    console.error('Failed to parse config template:', parseError, 'raw message:', result.message);
                    this.toast('error', '配置模板格式错误，请检查控制台');
                }
            }
            else
            {
                throw new Error(result.message);
            }
        }
        catch (error)
        {
            console.error('Failed to fetch config template:', error);
            this.toast('error', '获取配置模板失败: ' + error.message);
        }
    }

    renderSandboxPanel()
    {
        const sandboxListEl = document.getElementById('sandbox-list');
        const emptyTips = document.getElementById('sandbox-empty-tips');

        sandboxListEl.innerHTML = '';
        sandboxListEl.appendChild(emptyTips);

        if (this.sandboxList.length === 0) {
            emptyTips.classList.remove('hide');
            return;
        }

        emptyTips.classList.add('hide');

        this.sandboxList.forEach((sandbox, index) => {
            const sandboxEl = document.createElement('li');
            sandboxEl.className = 'sandbox-block';
            sandboxEl.dataset.uuid = sandbox.uuid;

            if (this.currentInfo && this.currentInfo.uuid === sandbox.uuid) {
                sandboxEl.classList.add('selected');
            }

            sandboxEl.innerHTML = `
                <div class="sandbox-title" title="${sandbox.name}">${sandbox.name}</div>
                <div class="sandbox-detail">
                    创建时间: ${sandbox.createTime}<br>
                    模拟周期: ${sandbox.simulationCycle}
                </div>
            `;

            sandboxEl.addEventListener('click', () => {
                document.querySelectorAll('.sandbox-block.selected').forEach(el => {
                    el.classList.remove('selected');
                });

                sandboxEl.classList.add('selected');

                this.currentInfo = sandbox;
                this.renderConfigPanel();
            });

            sandboxListEl.appendChild(sandboxEl);
        });
    }

    renderConfigPanel()
    {
        const configContentEl = document.getElementById('config-content');
        const emptyTips = document.getElementById('config-empty-tips');
        const buttonEntryEl = document.getElementById('button-entry');

        if (!this.currentInfo) {
            configContentEl.innerHTML = '';
            configContentEl.appendChild(emptyTips);
            emptyTips.classList.remove('hide');
            buttonEntryEl.innerHTML = '';
            return;
        }

        emptyTips.classList.add('hide');
        configContentEl.innerHTML = '';

        const infoSection = document.createElement('div');
        infoSection.className = 'config-info';
        infoSection.innerHTML = `
            <div class="config-info-item">
                <span class="config-info-label">沙箱名称</span>
                <span class="config-info-value">${this.currentInfo.name}</span>
            </div>
            <div class="config-info-item">
                <span class="config-info-label">创建时间</span>
                <span class="config-info-value">${this.currentInfo.createTime}</span>
            </div>
            <div class="config-info-item">
                <span class="config-info-label">模拟周期数</span>
                <span class="config-info-value">${this.currentInfo.simulationCycle}</span>
            </div>
            <div class="config-info-item">
                <span class="config-info-label">UUID</span>
                <span class="config-info-value" style="font-family: monospace; font-size: 12px;">${this.currentInfo.uuid}</span>
            </div>
        `;
        configContentEl.appendChild(infoSection);

        if (this.template.groups && this.template.configs) {
            this.template.groups.forEach(group => {
                const groupEl = document.createElement('div');
                groupEl.className = 'config-group';

                const groupHeader = document.createElement('div');
                groupHeader.className = 'config-group-header';
                groupHeader.innerHTML = `
                    <span>${group.title}</span>
                    <span class="toggle-icon">▼</span>
                `;

                const groupContent = document.createElement('div');
                groupContent.className = 'config-group-content';

                group.content.forEach(configKey => {
                    const config = this.template.configs[configKey];
                    if (!config) return;

                    const currentValue = this.currentInfo.configs[configKey] !== undefined 
                        ? this.currentInfo.configs[configKey] 
                        : config.value;

                    const configItem = document.createElement('div');
                    configItem.className = `config-item ${config.const ? 'const' : ''}`;

                    let valueElement = '';
                    if (config.type === 'Select') {
                        valueElement = `
                            <select disabled>
                                ${config.allow.map(opt => 
                                    `<option value="${opt}" ${currentValue === opt ? 'selected' : ''}>${opt}</option>`
                                ).join('')}
                            </select>
                        `;
                    } else if (config.type === 'Integer' || config.type === 'Long') {
                        const range = config.range ? `范围: [${config.range[0]}, ${config.range[1]}]` : '';
                        valueElement = `
                            <input type="number" value="${currentValue || ''}" disabled>
                            <span class="config-item-unit">${config.unit || ''}</span>
                            ${range ? `<span class="config-item-range">${range}</span>` : ''}
                        `;
                    } else {
                        valueElement = `<input type="text" value="${currentValue || ''}" disabled>`;
                    }

                    configItem.innerHTML = `
                        <div class="config-item-header">
                            <div class="config-item-name">${config.name}</div>
                            <div class="config-item-key">${configKey}</div>
                        </div>
                        <div class="config-item-desc">${config.desc}</div>
                        <div class="config-item-value">
                            ${valueElement}
                        </div>
                    `;

                    groupContent.appendChild(configItem);
                });
                
                groupHeader.addEventListener('click', () => {
                    groupEl.classList.toggle('collapsed');
                });
                
                groupEl.appendChild(groupHeader);
                groupEl.appendChild(groupContent);
                configContentEl.appendChild(groupEl);
            });
        }
        
        buttonEntryEl.innerHTML = `
            <li>
                <button class="rect-button shining-button success" id="enter-btn">
                    进入沙箱
                </button>
            </li>
            <li>
                <button class="rect-button shining-button" id="edit-btn">
                    编辑配置
                </button>
            </li>
            <li>
                <button class="rect-button shining-button" id="create-from-copy-btn">
                    复制新建
                </button>
            </li>
            <li>
                <button class="rect-button shining-button danger" id="remove-btn">
                    删除沙箱
                </button>
            </li>
        `;
        
        this.initButtons();
    }

    initCreatePanel()
    {
        const panel = document.getElementById('create-panel');
        if (panel) {
            panel.querySelector('.create-panel-close').addEventListener('click', () => {
                panel.classList.add('hide');
            });
            
            document.getElementById('create-panel-cancel').addEventListener('click', () => {
                panel.classList.add('hide');
            });
            
            panel.addEventListener('click', (e) => {
                if (e.target === panel) {
                    panel.classList.add('hide');
                }
            });
        }
    }

    openCreatePanel(editMode = false, sourceSandbox = null)
    {
        const panel = document.getElementById('create-panel');
        const body = document.getElementById('create-panel-body');
        const header = panel.querySelector('.create-panel-header span');
        const submitBtn = document.getElementById('create-panel-submit');

        if (editMode) {
            header.textContent = '编辑沙箱配置';
            submitBtn.textContent = '保存修改';
        } else {
            header.textContent = '创建新沙箱';
            submitBtn.textContent = '创建';
        }

        body.innerHTML = '';

        const form = document.createElement('form');
        form.id = 'create-form';

        const nameInput = document.createElement('div');
        nameInput.className = 'config-item';
        nameInput.innerHTML = `
            <div class="config-item-header" style="justify-content: center; font-size:20px">
                <div class="config-item-name">沙箱名称</div>
            </div>
            <div class="config-item-desc">为您的沙箱指定一个易于识别的名称</div>
            <div class="config-item-value">
                <input type="text" id="sandbox-name" value="${sourceSandbox ? sourceSandbox.name : `Sandbox #${this.sandboxList.length}`}" required>
            </div>
        `;
        form.appendChild(nameInput);

        if (this.template.groups && this.template.configs) {
            this.template.groups.forEach(group => {
                const groupEl = document.createElement('div');
                groupEl.className = 'config-group';

                const groupHeader = document.createElement('div');
                groupHeader.className = 'config-group-header';
                groupHeader.innerHTML = `
                    <span>${group.title}</span>
                    <span class="toggle-icon">▼</span>
                `;

                const groupContent = document.createElement('div');
                groupContent.className = 'config-group-content';

                group.content.forEach(configKey => {
                    const config = this.template.configs[configKey];
                    if (!config) return;

                    const isDisabled = editMode && config.const;

                    let initialValue = config.value;
                    if (sourceSandbox && sourceSandbox.configs[configKey] !== undefined) {
                        initialValue = sourceSandbox.configs[configKey];
                    }

                    const configItem = document.createElement('div');
                    configItem.className = `config-item ${isDisabled ? 'const' : ''}`;

                    let valueElement = '';
                    if (config.type === 'Select') {
                        valueElement = `
                            <select name="${configKey}" ${isDisabled ? 'disabled' : ''}>
                                ${config.allow.map(opt => 
                                    `<option value="${opt}" ${initialValue === opt ? 'selected' : ''}>${opt}</option>`
                                ).join('')}
                            </select>
                        `;
                    } else if (config.type === 'Integer' || config.type === 'Long') {
                        const min = config.range ? config.range[0] : '';
                        const max = config.range ? config.range[1] : '';
                        valueElement = `
                            <input type="number" name="${configKey}" value="${initialValue || ''}" 
                                   ${min !== '' ? `min="${min}"` : ''} 
                                   ${max !== '' ? `max="${max}"` : ''}
                                   ${isDisabled ? 'disabled' : ''}>
                            <span class="config-item-unit">${config.unit || ''}</span>
                        `;
                    } else {
                        valueElement = `<input type="text" name="${configKey}" value="${initialValue || ''}" ${isDisabled ? 'disabled' : ''}>`;
                    }

                    configItem.innerHTML = `
                        <div class="config-item-header">
                            <div class="config-item-name">${config.name} ${isDisabled ? '(常量)' : ''}</div>
                            <div class="config-item-key">${configKey}</div>
                        </div>
                        <div class="config-item-desc">${config.desc}</div>
                        <div class="config-item-value">
                            ${valueElement}
                        </div>
                    `;

                    groupContent.appendChild(configItem);
                });

                groupHeader.addEventListener('click', () => {
                    groupEl.classList.toggle('collapsed');
                });

                groupEl.appendChild(groupHeader);
                groupEl.appendChild(groupContent);
                form.appendChild(groupEl);
            });
        }

        body.appendChild(form);

        const submitHandler = async (e) => {
            e.preventDefault();
            
            const formData = new FormData(document.getElementById('create-form'));
            const configs = {};
            
            if (this.template.configs) {
                Object.keys(this.template.configs).forEach(key => {
                    const config = this.template.configs[key];
                    if (!config.const || !editMode) {
                        const value = formData.get(key);
                        if (value !== null) {
                            configs[key] = config.type === 'Integer' || config.type === 'Long' 
                                ? parseInt(value) || config.value 
                                : value;
                        }
                    }
                });
            }

            const sandboxName = document.getElementById('sandbox-name').value;
            const now = new Date();
            const createTime = `${now.getFullYear()}-${(now.getMonth() + 1).toString().padStart(2, '0')}-${now.getDate().toString().padStart(2, '0')} ${now.getHours().toString().padStart(2, '0')}:${now.getMinutes().toString().padStart(2, '0')}:${now.getSeconds().toString().padStart(2, '0')}`;
            
            const content = {
                sandbox_name: sandboxName,
                create_time: createTime,
                simulation_cycle: 0,
                configs: configs
            };
            
            try {
                const url = new URL('/sandbox/saveSandbox', window.location.origin);
                url.searchParams.append("content", JSON.stringify(content));

                const response = await fetch(url);
                
                if (response.ok) {
                    const result = await response.json();
                    if (result.success) {
                        this.toast('success', editMode ? '沙箱配置已更新' : '沙箱创建成功');
                        panel.classList.add('hide');
                        
                        this.sandboxList = [];
                        await this.fetchSandboxList();
                        this.renderSandboxPanel();
                        
                        if (editMode && this.currentInfo) {
                            const newSandbox = this.sandboxList.find(s => s.uuid === this.currentInfo.uuid);
                            if (newSandbox) {
                                this.currentInfo = newSandbox;
                                this.renderConfigPanel();
                            }
                        }
                    } else {
                        this.toast('error', result.message || '操作失败');
                    }
                } else {
                    this.toast('error', `HTTP ${response.status}: ${response.statusText}`);
                }
            } catch (error) {
                console.error('Failed to save sandbox:', error);
                this.toast('error', '保存失败: ' + error.message);
            }
        };
        
        submitBtn.replaceWith(submitBtn.cloneNode(true));
        const newSubmitBtn = document.getElementById('create-panel-submit');
        newSubmitBtn.addEventListener('click', submitHandler);
        
        panel.classList.remove('hide');
    }

    initButtons()
    {
        let targetBtn = document.getElementById('create-btn');
        if (targetBtn)
        {
            targetBtn.addEventListener('click', () => {
                this.openCreatePanel(false);
            });
        }

        targetBtn = document.getElementById('create-from-copy-btn');
        if (targetBtn)
        {
            targetBtn.addEventListener('click', () => {
                if (this.currentInfo) {
                    this.openCreatePanel(false, this.currentInfo);
                } else {
                    this.toast('error', '请先选择一个沙箱');
                }
            });
        }

        targetBtn = document.getElementById('edit-btn');
        if (targetBtn)
        {
            targetBtn.addEventListener('click', () => {
                if (this.currentInfo) {
                    this.openCreatePanel(true, this.currentInfo);
                } else {
                    this.toast('error', '请先选择一个沙箱');
                }
            });
        }

        targetBtn = document.getElementById('enter-btn');
        if (targetBtn)
        {
            targetBtn.addEventListener('click', async () => {
                if (!this.currentInfo) {
                    this.toast('error', '请先选择一个沙箱');
                    return;
                }

                try {
                    let url = new URL('/sandbox/startSimulation', window.location.origin);
                    url.searchParams.append('UUID', this.currentInfo.uuid);
                    
                    const response = await fetch(url);
                    if (!response.ok) throw new Error(`HTTP ${response.status}: ${response.statusText}`);

                    const result = await response.json();
                    if (result.success) {
                        this.toast('success', '正在进入沙箱...');
                    } else {
                        this.toast('error', result.message || '进入沙箱失败');
                    }
                } catch (error) {
                    console.error('Failed to enter sandbox:', error);
                    this.toast('error', '进入沙箱失败: ' + error.message);
                }
            });
        }

        targetBtn = document.getElementById('remove-btn');
        if (targetBtn)
        {
            targetBtn.addEventListener('click', async () => {
                if (!this.currentInfo) {
                    this.toast('error', '请先选择一个沙箱');
                    return;
                }

                if (!confirm(`确定要删除沙箱 "${this.currentInfo.name}" 吗？此操作不可撤销。`)) {
                    return;
                }

                try {
                    let url = new URL('/sandbox/removeSandbox', window.location.origin);
                    url.searchParams.append('UUID', this.currentInfo.uuid);
                    
                    const response = await fetch(url);
                    if (!response.ok) throw new Error(`HTTP ${response.status}: ${response.statusText}`);

                    const result = await response.json();
                    if (result.success) {
                        this.toast('success', '沙箱删除成功');
                        
                        const index = this.sandboxList.findIndex(s => s.uuid === this.currentInfo.uuid);
                        if (index !== -1) {
                            this.sandboxList.splice(index, 1);
                        }
                        
                        if (this.sandboxList.length > 0) {
                            this.currentInfo = this.sandboxList[0];
                        } else {
                            this.currentInfo = null;
                        }
                        
                        this.renderSandboxPanel();
                        this.renderConfigPanel();
                    } else {
                        this.toast('error', result.message || '删除失败');
                    }
                } catch (error) {
                    console.error('Failed to remove sandbox:', error);
                    this.toast('error', '删除失败: ' + error.message);
                }
            });
        }
    }
}