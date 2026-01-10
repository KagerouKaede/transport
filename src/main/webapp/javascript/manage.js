document.addEventListener('DOMContentLoaded', function() {
    const configManager = new SandboxManager();
    configManager.init();
});

class SandboxManager {
    constructor() {
        this.template = {};
        this.currentInfo = null;
        this.sandboxList = [];

        // 核心修复 1: 在构造函数中只绑定一次 this，确保函数引用永远唯一
        this.handleButtonClick = this.handleButtonClick.bind(this);
    }

    toast(type, message) {
        const toastEl = document.getElementById('toast');
        // 清除之前的定时器，防止快速触发时闪烁
        if (this.toastTimer) clearTimeout(this.toastTimer);

        toastEl.className = 'toast'; // 重置类
        toastEl.textContent = message;
        toastEl.classList.add(type);
        toastEl.classList.remove('hide');

        this.toastTimer = setTimeout(() => {
            toastEl.classList.add('hide');
        }, 3000);
    }

    async init() {
        await this.fetchSandboxList();
        await this.fetchConfigTemplate();

        this.renderSandboxPanel();
        this.renderConfigPanel();
        this.initCreatePanel();
        
        // 核心修复 2: 统一在一个地方绑定所有静态事件
        this.initGlobalEvents();
    }

    /** 初始化全局事件监听（整个生命周期只执行一次） */
    initGlobalEvents() {
        // 1. 绑定右侧按钮区域的事件委托
        // 即使 renderConfigPanel 后来修改了 button-entry 的内部 HTML，
        // 这个绑定在父元素上的监听器依然有效，且不会重复。
        const buttonEntryEl = document.getElementById('button-entry');
        if (buttonEntryEl) {
            // 防御性移除（虽然 init 只跑一次，但好习惯）
            buttonEntryEl.removeEventListener('click', this.handleButtonClick);
            buttonEntryEl.addEventListener('click', this.handleButtonClick);
        }

        // 2. 绑定左侧创建按钮 (+)
        const createBtn = document.getElementById('create-btn');
        if (createBtn) {
            // 克隆节点法移除可能存在的旧监听器
            const newCreateBtn = createBtn.cloneNode(true);
            createBtn.parentNode.replaceChild(newCreateBtn, createBtn);
            newCreateBtn.addEventListener('click', () => {
                this.openCreatePanel(false);
            });
        }
    }

    /** 处理按钮点击事件 (事件委托) */
    handleButtonClick(event) {
        // 使用 closest 确保点击按钮内的图标或span也能触发
        const target = event.target.closest('button');
        if (!target) return;

        const id = target.id;
        // console.log('按钮点击:', id); // 调试用，现在应该只会打印一次了

        // 防止短时间内重复点击（简单的防抖）
        if (this.isProcessing) return;
        
        // 对于删除操作，不需要加锁，因为有 confirm 弹窗阻断
        // 对于进入沙箱等异步操作，建议加简单的锁
        
        switch (id) {
            case 'enter-btn':
                this.handleEnterSandbox();
                break;
            case 'edit-btn':
                this.handleEditSandbox();
                break;
            case 'create-from-copy-btn':
                this.handleCopySandbox();
                break;
            case 'remove-btn':
                this.handleRemoveSandbox();
                break;
        }
    }

    /** 进入沙箱 */
    async handleEnterSandbox() {
        if (!this.currentInfo) return this.toast('error', '请先选择一个沙箱');

        this.isProcessing = true; // 加锁
        try {
            const url = new URL('/sandbox/startSimulation', window.location.origin);
            url.searchParams.append('UUID', this.currentInfo.uuid);

            const response = await fetch(url);
            if (!response.ok) throw new Error(`HTTP ${response.status}`);

            const result = await response.json();

            if (result && result.success === true) {
                this.toast('success', '正在进入沙箱...');
                window.open('/map.html');
            } else {
                this.toast('error', result.message || '进入沙箱失败');
            }
        } catch (error) {
            console.error(error);
            this.toast('error', '进入失败: ' + error.message);
        } finally {
            this.isProcessing = false; // 解锁
        }
    }

    /** 编辑沙箱 */
    handleEditSandbox() {
        if (this.currentInfo) {
            this.openCreatePanel(true, this.currentInfo);
        } else {
            this.toast('error', '请先选择一个沙箱');
        }
    }

    /** 复制沙箱 */
    handleCopySandbox() {
        if (this.currentInfo) {
            this.openCreatePanel(false, this.currentInfo);
        } else {
            this.toast('error', '请先选择一个沙箱');
        }
    }

    /** 删除沙箱 */
    async handleRemoveSandbox() {
        if (!this.currentInfo) return this.toast('error', '请先选择一个沙箱');

        // 这是一个原生阻断弹窗，会暂停 JS 执行，直到用户点击
        if (!confirm(`确定要删除沙箱 "${this.currentInfo.name}" 吗？此操作不可撤销。`)) {
            return;
        }

        try {
            const url = new URL('/sandbox/removeSandbox', window.location.origin);
            url.searchParams.append('UUID', this.currentInfo.uuid);

            const response = await fetch(url);
            if (!response.ok) throw new Error(`HTTP ${response.status}`);

            const result = await response.json();

            // console.log('删除结果:', result);

            if (result && (result.success === true || result.success === 'true')) {
                this.toast('success', '沙箱删除成功');

                // 从列表中移除
                this.sandboxList = this.sandboxList.filter(s => s.uuid !== this.currentInfo.uuid);

                // 更新选中项
                this.currentInfo = this.sandboxList.length > 0 ? this.sandboxList[0] : null;

                // 重新渲染
                this.renderSandboxPanel();
                this.renderConfigPanel();
            } else {
                this.toast('error', result.message || '删除失败');
            }
        } catch (error) {
            console.error('删除出错:', error);
            this.toast('error', '删除失败: ' + error.message);
        }
    }


    /** 获取所有数据沙箱 */
    async fetchSandboxList() {
        try {
            let url = new URL('/sandbox/getAllSandbox', window.location.origin);
            const response = await fetch(url);
            if (!response.ok) throw new Error(`HTTP ${response.status}`);

            const data = await response.json();
            this.sandboxList = [];

            if (Array.isArray(data)) {
                data.forEach(item => {
                    try {
                        const content = JSON.parse(item.content || '{}');
                        this.sandboxList.push({
                            uuid: item.UUID,
                            name: content.sandbox_name || 'Sandbox #' + this.sandboxList.length,
                            createTime: content.create_time || 'Unknown',
                            simulationCycle: content.simulation_cycle || 0,
                            configs: content.configs || {}
                        });
                    } catch (e) {
                        console.warn('解析失败:', item);
                    }
                });
            }
            
            // 自动选中第一个
            if (this.sandboxList.length > 0 && !this.currentInfo) {
                this.currentInfo = this.sandboxList[0];
            }
        } catch (error) {
            console.error(error);
            this.toast('error', '获取列表失败');
        }
    }

    /** 获取沙箱数据格式信息 */
    async fetchConfigTemplate() {
        try {
            let url = new URL('/sandbox/getConfigTemplate', window.location.origin);
            const response = await fetch(url);
            const result = await response.json();
            if (result.success) {
                this.template = JSON.parse(result.message);
            }
        } catch (error) {
            console.error('获取模板失败:', error);
        }
    }

    renderSandboxPanel() {
        const sandboxListEl = document.getElementById('sandbox-list');
        if (!sandboxListEl) return;

        let emptyTips = document.getElementById('sandbox-empty-tips');
        // 确保提示元素存在
        if (!emptyTips) {
            emptyTips = document.createElement('div');
            emptyTips.id = 'sandbox-empty-tips';
            emptyTips.className = 'sandbox-block';
            emptyTips.style.height = '256px';
            emptyTips.innerHTML = `<span>📂<b>还没有已保存的沙箱数据</b><br><br>请点击下方 <b>+</b> 按钮<br>以默认预设创建数据沙箱</span>`;
        }

        sandboxListEl.innerHTML = '';
        sandboxListEl.appendChild(emptyTips);

        if (this.sandboxList.length === 0) {
            emptyTips.classList.remove('hide');
            return;
        }

        emptyTips.classList.add('hide');

        this.sandboxList.forEach(sandbox => {
            const li = document.createElement('li');
            li.className = 'sandbox-block';
            if (this.currentInfo && this.currentInfo.uuid === sandbox.uuid) {
                li.classList.add('selected');
            }

            li.innerHTML = `
                <div class="sandbox-title" title="${sandbox.name}">${sandbox.name}</div>
                <div class="sandbox-detail">创建时间: ${sandbox.createTime}<br>模拟周期: ${sandbox.simulationCycle}</div>
            `;

            li.onclick = () => {
                this.currentInfo = sandbox;
                this.renderSandboxPanel();
                this.renderConfigPanel();
            };
            sandboxListEl.appendChild(li);
        });
    }

    renderConfigPanel() {
        const configContentEl = document.getElementById('config-content');
        const buttonEntryEl = document.getElementById('button-entry');

        if (!configContentEl || !buttonEntryEl) return;

        let emptyTips = document.getElementById('config-empty-tips');
        if (!emptyTips) {
            emptyTips = document.createElement('div');
            emptyTips.id = 'config-empty-tips';
            emptyTips.className = 'sandbox-block';
            emptyTips.style.height = 'calc(100% - 32px)';
            emptyTips.innerHTML = `<span>📦<b>还没有选择任何数据沙箱</b><br><br>请选择左侧列表任意数据沙箱<br>以查看该沙箱的详细数据</span>`;
        }

        if (!this.currentInfo) {
            configContentEl.innerHTML = '';
            configContentEl.appendChild(emptyTips);
            emptyTips.classList.remove('hide');
            buttonEntryEl.innerHTML = ''; // 清空按钮，但不删除 ul 容器
            return;
        }

        emptyTips.classList.add('hide');
        configContentEl.innerHTML = '';

        // 渲染基本信息
        configContentEl.innerHTML += `
            <div class="config-info">
                <div class="config-info-item"><span class="config-info-label">名称</span><span>${this.currentInfo.name}</span></div>
                <div class="config-info-item"><span class="config-info-label">UUID</span><span style="font-size:10px;font-family:monospace">${this.currentInfo.uuid}</span></div>
            </div>`;

        // 渲染参数组
        if (this.template.groups && this.template.configs) {
            this.template.groups.forEach(group => {
                const groupEl = document.createElement('div');
                groupEl.className = 'config-group';
                groupEl.innerHTML = `<div class="config-group-header"><span>${group.title}</span><span class="toggle-icon">▼</span></div>`;
                
                const contentEl = document.createElement('div');
                contentEl.className = 'config-group-content';
                
                group.content.forEach(key => {
                    const cfg = this.template.configs[key];
                    if (!cfg) return;
                    const val = this.currentInfo.configs[key] !== undefined ? this.currentInfo.configs[key] : cfg.value;
                    
                    let inputHtml = `<input type="text" value="${val}" disabled>`;
                    if (cfg.type === 'Select') inputHtml = `<select disabled><option selected>${val}</option></select>`;
                    
                    const item = document.createElement('div');
                    item.className = 'config-item';
                    item.innerHTML = `
                        <div class="config-item-header"><div class="config-item-name">${cfg.name}</div></div>
                        <div class="config-item-value">${inputHtml}</div>
                    `;
                    contentEl.appendChild(item);
                });
                
                groupEl.querySelector('.config-group-header').onclick = () => groupEl.classList.toggle('collapsed');
                groupEl.appendChild(contentEl);
                configContentEl.appendChild(groupEl);
            });
        }

        // 核心修复 3: 这里只负责生成 HTML 字符串，绝不绑定事件
        // 事件已经在 initGlobalEvents 中委托给 button-entry 了
        buttonEntryEl.innerHTML = `
            <li><button class="rect-button shining-button success" id="enter-btn">进入数据沙箱</button></li>
            <li><button class="rect-button shining-button" id="edit-btn">编辑参数配置</button></li>
            <li><button class="rect-button shining-button" id="create-from-copy-btn">以此为模板新建</button></li>
            <li><button class="rect-button shining-button danger" id="remove-btn">删除数据沙箱</button></li>
        `;
    }

    initCreatePanel() {
        const panel = document.getElementById('create-panel');
        if (panel) {
            const close = () => panel.classList.add('hide');
            panel.querySelector('.create-panel-close').onclick = close;
            document.getElementById('create-panel-cancel').onclick = close;
            panel.onclick = (e) => { if (e.target === panel) close(); };
        }
    }

    openCreatePanel(editMode = false, sourceSandbox = null) {
        const panel = document.getElementById('create-panel');
        const body = document.getElementById('create-panel-body');
        const header = panel.querySelector('.create-panel-header span');
        const submitBtn = document.getElementById('create-panel-submit');

        header.textContent = editMode ? '编辑配置' : '创建沙箱';
        submitBtn.textContent = editMode ? '保存' : '创建';

        // 构建表单 (简化版，复用你之前的逻辑)
        body.innerHTML = '';
        const form = document.createElement('form');
        form.id = 'create-form';
        
        // 名称输入
        form.innerHTML = `
            <div class="config-item">
                <div class="config-item-header"><div class="config-item-name">沙箱名称</div></div>
                <div class="config-item-value"><input type="text" name="sandbox_name" value="${sourceSandbox ? sourceSandbox.name : 'Sandbox #' + this.sandboxList.length}" required></div>
            </div>`;

        // 动态配置渲染
        if (this.template.groups) {
            this.template.groups.forEach(group => {
                const g = document.createElement('div');
                g.className = 'config-group';
                g.innerHTML = `<div class="config-group-header"><span>${group.title}</span></div>`;
                const c = document.createElement('div');
                c.className = 'config-group-content';
                group.content.forEach(k => {
                    const cfg = this.template.configs[k];
                    if(!cfg) return;
                    const val = (sourceSandbox && sourceSandbox.configs[k] !== undefined) ? sourceSandbox.configs[k] : cfg.value;
                    const disabled = editMode && cfg.const;
                    
                    let input = '';
                    if(cfg.type === 'Select') {
                         input = `<select name="${k}" ${disabled?'disabled':''}>${cfg.allow.map(o=>`<option value="${o}" ${String(o)===String(val)?'selected':''}>${o}</option>`).join('')}</select>`;
                    } else {
                         input = `<input type="number" name="${k}" value="${val}" ${disabled?'disabled':''}>`;
                    }
                    
                    const i = document.createElement('div');
                    i.className = 'config-item';
                    i.innerHTML = `<div class="config-item-header"><div class="config-item-name">${cfg.name} ${disabled?'(锁定)':''}</div></div><div class="config-item-value">${input}</div>`;
                    c.appendChild(i);
                });
                g.appendChild(c);
                form.appendChild(g);
            });
        }
        body.appendChild(form);

        // 核心修复 4: 替换节点以移除旧的 submit 事件
        const newSubmitBtn = submitBtn.cloneNode(true);
        submitBtn.parentNode.replaceChild(newSubmitBtn, submitBtn);

        newSubmitBtn.addEventListener('click', async () => {
            const formData = new FormData(form);
            const configs = {};
            
            // 收集数据
            Object.keys(this.template.configs || {}).forEach(key => {
                const cfg = this.template.configs[key];
                if(editMode && cfg.const && sourceSandbox) {
                    configs[key] = sourceSandbox.configs[key];
                } else {
                    const v = formData.get(key);
                    configs[key] = (cfg.type === 'Integer' || cfg.type === 'Long') ? Number(v) : v;
                }
            });

            const content = {
                sandbox_name: formData.get('sandbox_name'),
                create_time: sourceSandbox ? sourceSandbox.createTime : new Date().toLocaleString(),
                simulation_cycle: sourceSandbox ? sourceSandbox.simulationCycle : 0,
                configs: configs
            };

            // 发送请求
            try {
                const url = new URL('/sandbox/saveSandbox', window.location.origin);
                url.searchParams.append("content", JSON.stringify(content));
                if (editMode && sourceSandbox) url.searchParams.append("UUID", sourceSandbox.uuid);

                const res = await fetch(url);
                const json = await res.json();

                if (json.success) {
                    this.toast('success', '保存成功');
                    panel.classList.add('hide');
                    await this.fetchSandboxList();
                    this.renderSandboxPanel();
                    // 刷新当前选中的详情
                    if (this.currentInfo) {
                        const updated = this.sandboxList.find(s => s.uuid === (editMode ? sourceSandbox.uuid : this.currentInfo.uuid));
                        if(updated) { this.currentInfo = updated; this.renderConfigPanel(); }
                    }
                } else {
                    this.toast('error', json.message || '保存失败');
                }
            } catch (e) {
                this.toast('error', '请求出错');
            }
        });

        panel.classList.remove('hide');
    }
}