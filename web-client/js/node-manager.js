/**
 * 设备节点管理模块
 * 管理在线设备节点，支持密码验证和远程资源管理
 */
const NodeManager = {
    nodes: [],
    currentNode: null,
    isAuthenticated: false,
    adminPassword: '',

    /**
     * 初始化节点管理器
     */
    init() {
        this.setupEventListeners();
        this.loadNodes();
    },

    /**
     * 设置事件监听
     */
    setupEventListeners() {
        // 刷新节点列表
        const refreshBtn = document.getElementById('btn-refresh-nodes');
        if (refreshBtn) {
            refreshBtn.addEventListener('click', () => this.refreshNodes());
        }

        // 关闭认证弹窗
        const closeAuthBtn = document.getElementById('btn-close-auth-dialog');
        if (closeAuthBtn) {
            closeAuthBtn.addEventListener('click', () => this.closeAuthDialog());
        }

        // 提交密码
        const submitAuthBtn = document.getElementById('btn-submit-auth');
        if (submitAuthBtn) {
            submitAuthBtn.addEventListener('click', () => this.submitAuth());
        }

        // 关闭资源管理弹窗
        const closeResBtn = document.getElementById('btn-close-resource-dialog');
        if (closeResBtn) {
            closeResBtn.addEventListener('click', () => this.closeResourceDialog());
        }

        // 上传文件
        const uploadBtn = document.getElementById('btn-upload-node-file');
        if (uploadBtn) {
            uploadBtn.addEventListener('click', () => this.uploadFile());
        }

        // 新建文件夹
        const newFolderBtn = document.getElementById('btn-new-node-folder');
        if (newFolderBtn) {
            newFolderBtn.addEventListener('click', () => this.createFolder());
        }

        // 重新扫描
        const rescanBtn = document.getElementById('btn-rescan-node');
        if (rescanBtn) {
            rescanBtn.addEventListener('click', () => this.rescanNode());
        }
    },

    /**
     * 加载节点列表
     */
    loadNodes() {
        try {
            const data = localStorage.getItem('hmv_nodes');
            this.nodes = data ? JSON.parse(data) : [];
        } catch (e) {
            this.nodes = [];
        }
    },

    /**
     * 保存节点列表
     */
    saveNodes() {
        localStorage.setItem('hmv_nodes', JSON.stringify(this.nodes));
    },

    /**
     * 刷新节点列表
     */
    async refreshNodes() {
        if (!Device.isConnected) {
            App.showToast('请先连接设备');
            return;
        }

        try {
            // 获取当前连接的设备信息
            const device = {
                host: Device.currentHost,
                port: Device.currentPort,
                name: `${Device.currentHost}:${Device.currentPort}`,
                online: true,
                lastSeen: Date.now()
            };

            // 检查是否已存在
            const existingIndex = this.nodes.findIndex(
                n => n.host === device.host && n.port === device.port
            );

            if (existingIndex >= 0) {
                this.nodes[existingIndex] = { ...this.nodes[existingIndex], ...device };
            } else {
                this.nodes.push(device);
            }

            this.saveNodes();
            this.renderNodes();
            App.showToast('节点列表已刷新');
        } catch (e) {
            console.error('刷新节点失败:', e);
            App.showToast('刷新失败');
        }
    },

    /**
     * 渲染节点列表
     */
    renderNodes() {
        const container = document.getElementById('node-list-container');
        if (!container) return;

        if (this.nodes.length === 0) {
            container.innerHTML = '<div class="empty-hint"><p>暂无设备节点</p></div>';
            return;
        }

        container.innerHTML = this.nodes.map((node, index) => `
            <div class="node-item ${node.online ? 'online' : 'offline'}" data-index="${index}">
                <div class="node-status">
                    <span class="status-dot ${node.online ? 'online' : 'offline'}"></span>
                </div>
                <div class="node-info">
                    <div class="node-name">${this.escapeHtml(node.alias || node.name)}</div>
                    <div class="node-address">${node.host}:${node.port}</div>
                    <div class="node-meta">
                        ${node.hasPassword ? '🔒 已设置密码' : '🔓 无密码'}
                        ${node.lastSeen ? ` · ${this.formatTime(node.lastSeen)}` : ''}
                    </div>
                </div>
                <div class="node-actions">
                    ${node.online ? `
                        <button class="btn-primary btn-manage-node" data-index="${index}">管理</button>
                    ` : `
                        <button class="btn-secondary" disabled>离线</button>
                    `}
                    <button class="btn-danger btn-remove-node" data-index="${index}">&times;</button>
                </div>
            </div>
        `).join('');

        // 绑定事件
        container.querySelectorAll('.btn-manage-node').forEach(btn => {
            btn.addEventListener('click', (e) => {
                const index = parseInt(e.target.dataset.index);
                this.openNodeManager(index);
            });
        });

        container.querySelectorAll('.btn-remove-node').forEach(btn => {
            btn.addEventListener('click', (e) => {
                const index = parseInt(e.target.dataset.index);
                this.removeNode(index);
            });
        });
    },

    /**
     * 打开节点管理器
     */
    openNodeManager(index) {
        const node = this.nodes[index];
        if (!node || !node.online) return;

        this.currentNode = node;
        this.isAuthenticated = false;

        // 显示认证弹窗
        this.showAuthDialog();
    },

    /**
     * 显示认证弹窗
     */
    showAuthDialog() {
        const dialog = document.getElementById('node-auth-dialog');
        if (dialog) {
            dialog.classList.remove('hidden');
            document.getElementById('auth-node-name').textContent = this.currentNode.name;
            document.getElementById('auth-password').value = '';
            document.getElementById('auth-error').textContent = '';
        }
    },

    /**
     * 关闭认证弹窗
     */
    closeAuthDialog() {
        const dialog = document.getElementById('node-auth-dialog');
        if (dialog) {
            dialog.classList.add('hidden');
        }
        this.currentNode = null;
    },

    /**
     * 提交认证
     */
    async submitAuth() {
        const password = document.getElementById('auth-password').value;
        const errorEl = document.getElementById('auth-error');

        if (!password) {
            errorEl.textContent = '请输入管理密码';
            return;
        }

        try {
            const response = await fetch(`${API.baseUrl}/admin/verify`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ password })
            });

            const result = await response.json();

            if (result.valid) {
                this.isAuthenticated = true;
                this.adminPassword = password;
                this.closeAuthDialog();
                this.openResourceDialog();
            } else {
                errorEl.textContent = '密码错误';
            }
        } catch (e) {
            errorEl.textContent = '验证失败: ' + e.message;
        }
    },

    /**
     * 打开资源管理弹窗
     */
    openResourceDialog() {
        const dialog = document.getElementById('node-resource-dialog');
        if (dialog) {
            dialog.classList.remove('hidden');
            document.getElementById('resource-node-name').textContent = this.currentNode.name;
            this.loadNodeResources();
        }
    },

    /**
     * 关闭资源管理弹窗
     */
    closeResourceDialog() {
        const dialog = document.getElementById('node-resource-dialog');
        if (dialog) {
            dialog.classList.add('hidden');
        }
        this.isAuthenticated = false;
        this.adminPassword = '';
    },

    /**
     * 加载节点资源
     */
    async loadNodeResources() {
        try {
            const response = await fetch(`${API.baseUrl}/proxy/operation`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    password: this.adminPassword,
                    operation: 'list',
                    params: {}
                })
            });

            const result = await response.json();
            if (result.status === 'ok') {
                // 解析资源列表
                const resources = JSON.parse(result.message || '[]');
                this.renderResources(resources);
            }
        } catch (e) {
            console.error('加载资源失败:', e);
            App.showToast('加载资源失败');
        }
    },

    /**
     * 渲染资源列表
     */
    renderResources(resources) {
        const container = document.getElementById('node-resources-container');
        if (!container) return;

        if (!resources || resources.length === 0) {
            container.innerHTML = '<div class="empty-hint"><p>暂无资源</p></div>';
            return;
        }

        container.innerHTML = resources.map(item => `
            <div class="resource-item" data-id="${item.id}">
                <div class="resource-icon">
                    ${item.mimeType && item.mimeType.startsWith('video') ? '🎬' : 
                      item.mimeType && item.mimeType.startsWith('audio') ? '🎵' : '📄'}
                </div>
                <div class="resource-info">
                    <div class="resource-name">${this.escapeHtml(item.title)}</div>
                    <div class="resource-meta">
                        ${this.formatSize(item.size)} · ${item.mimeType || '未知类型'}
                    </div>
                </div>
                <div class="resource-actions">
                    <button class="btn-secondary btn-play-resource" data-id="${item.id}" title="播放">▶</button>
                    <button class="btn-secondary btn-publish-resource" data-id="${item.id}" title="发布">📤</button>
                    <button class="btn-danger btn-delete-resource" data-id="${item.id}" title="删除">&times;</button>
                </div>
            </div>
        `).join('');

        // 绑定事件
        container.querySelectorAll('.btn-play-resource').forEach(btn => {
            btn.addEventListener('click', (e) => {
                const id = e.target.dataset.id;
                this.playResource(id);
            });
        });

        container.querySelectorAll('.btn-publish-resource').forEach(btn => {
            btn.addEventListener('click', (e) => {
                const id = e.target.dataset.id;
                this.publishResource(id);
            });
        });

        container.querySelectorAll('.btn-delete-resource').forEach(btn => {
            btn.addEventListener('click', (e) => {
                const id = e.target.dataset.id;
                this.deleteResource(id);
            });
        });
    },

    /**
     * 播放资源
     */
    playResource(id) {
        const mediaUrl = `${API.baseUrl}/media/${id}`;
        Player.playFromUrl(mediaUrl, '远程资源');
    },

    /**
     * 发布资源
     */
    async publishResource(id) {
        try {
            const response = await fetch(`${API.baseUrl}/proxy/operation`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    password: this.adminPassword,
                    operation: 'publish',
                    params: { mediaId: id }
                })
            });

            const result = await response.json();
            if (result.status === 'ok') {
                App.showToast('资源已发布');
            } else {
                App.showToast('发布失败: ' + (result.message || '未知错误'));
            }
        } catch (e) {
            App.showToast('发布失败: ' + e.message);
        }
    },

    /**
     * 删除资源
     */
    async deleteResource(id) {
        if (!confirm('确定要删除此资源吗？')) return;

        try {
            const response = await fetch(`${API.baseUrl}/proxy/operation`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    password: this.adminPassword,
                    operation: 'delete',
                    params: { mediaId: id }
                })
            });

            const result = await response.json();
            if (result.status === 'ok') {
                App.showToast('资源已删除');
                this.loadNodeResources();
            } else {
                App.showToast('删除失败: ' + (result.message || '未知错误'));
            }
        } catch (e) {
            App.showToast('删除失败: ' + e.message);
        }
    },

    /**
     * 上传文件到节点
     */
    async uploadFile() {
        const input = document.getElementById('node-file-input');
        if (!input || !input.files || input.files.length === 0) {
            App.showToast('请选择文件');
            return;
        }

        const file = input.files[0];
        const formData = new FormData();
        formData.append('file', file);
        formData.append('password', this.adminPassword);

        try {
            const response = await fetch(`${API.baseUrl}/proxy/operation`, {
                method: 'POST',
                body: formData
            });

            const result = await response.json();
            if (result.status === 'ok') {
                App.showToast('文件已上传');
                input.value = '';
                this.loadNodeResources();
            } else {
                App.showToast('上传失败: ' + (result.message || '未知错误'));
            }
        } catch (e) {
            App.showToast('上传失败: ' + e.message);
        }
    },

    /**
     * 在节点创建文件夹
     */
    async createFolder() {
        const name = prompt('请输入文件夹名称');
        if (!name) return;

        try {
            const response = await fetch(`${API.baseUrl}/proxy/operation`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    password: this.adminPassword,
                    operation: 'createFolder',
                    params: { name }
                })
            });

            const result = await response.json();
            if (result.status === 'ok') {
                App.showToast('文件夹已创建');
                this.loadNodeResources();
            } else {
                App.showToast('创建失败: ' + (result.message || '未知错误'));
            }
        } catch (e) {
            App.showToast('创建失败: ' + e.message);
        }
    },

    /**
     * 重新扫描节点
     */
    async rescanNode() {
        try {
            App.showToast('正在重新扫描...');
            const response = await fetch(`${API.baseUrl}/proxy/operation`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    password: this.adminPassword,
                    operation: 'rescan',
                    params: {}
                })
            });

            const result = await response.json();
            if (result.status === 'ok') {
                App.showToast('扫描完成');
                this.loadNodeResources();
            } else {
                App.showToast('扫描失败: ' + (result.message || '未知错误'));
            }
        } catch (e) {
            App.showToast('扫描失败: ' + e.message);
        }
    },

    /**
     * 移除节点
     */
    removeNode(index) {
        if (!confirm('确定要移除此节点吗？')) return;
        
        this.nodes.splice(index, 1);
        this.saveNodes();
        this.renderNodes();
        App.showToast('节点已移除');
    },

    /**
     * 格式化时间
     */
    formatTime(timestamp) {
        const date = new Date(timestamp);
        const now = new Date();
        const diff = now - date;
        
        if (diff < 60000) return '刚刚';
        if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`;
        if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`;
        return `${Math.floor(diff / 86400000)}天前`;
    },

    /**
     * 格式化文件大小
     */
    formatSize(bytes) {
        if (!bytes) return '';
        if (bytes < 1024) return `${bytes} B`;
        if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
        if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
        return `${(bytes / (1024 * 1024 * 1024)).toFixed(1)} GB`;
    },

    /**
     * HTML 转义
     */
    escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }
};

// 导出
window.NodeManager = NodeManager;
