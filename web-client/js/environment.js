/**
 * 环境检测与扫描路径管理模块
 * 检测当前设备是 PC 还是手机/平板
 * PC 端支持本地媒体扫描路径设置
 */
const Environment = {
    isPC: false,
    scanPaths: [],
    defaultPath: '',

    /**
     * 初始化环境检测
     */
    init() {
        this.detectEnvironment();
        this.loadScanPaths();
        
        // 如果是 PC，显示扫描路径管理界面
        if (this.isPC) {
            this.showScanPathUI();
        }
    },

    /**
     * 检测运行环境
     */
    detectEnvironment() {
        const userAgent = navigator.userAgent.toLowerCase();
        const mobileKeywords = [
            'android', 'webos', 'iphone', 'ipad', 'ipod', 
            'blackberry', 'windows phone', 'opera mini', 'mobile'
        ];
        
        const isMobile = mobileKeywords.some(keyword => userAgent.includes(keyword));
        const isTablet = /ipad|android(?!.*mobile)|tablet/i.test(userAgent);
        
        this.isPC = !isMobile && !isTablet;
        
        // 设置默认扫描路径
        if (this.isPC) {
            // 尝试获取用户 home 目录
            this.defaultPath = this.detectDefaultPath();
        }
        
        console.log(`Environment: ${this.isPC ? 'PC' : 'Mobile/Tablet'}`);
    },

    /**
     * 检测默认扫描路径
     */
    detectDefaultPath() {
        // 尝试从本地存储获取
        const saved = localStorage.getItem('hmv_default_path');
        if (saved) return saved;

        // 根据操作系统推测默认路径
        const platform = navigator.platform.toLowerCase();
        if (platform.includes('win')) {
            // Windows: 用户目录
            return 'C:\\Users\\Public\\Videos';
        } else if (platform.includes('mac')) {
            // macOS: 用户目录
            return '~/Movies';
        } else {
            // Linux: 用户目录
            return '~/Videos';
        }
    },

    /**
     * 加载扫描路径
     */
    loadScanPaths() {
        try {
            const data = localStorage.getItem('hmv_scan_paths');
            this.scanPaths = data ? JSON.parse(data) : [this.defaultPath];
        } catch (e) {
            this.scanPaths = [this.defaultPath];
        }
    },

    /**
     * 保存扫描路径
     */
    saveScanPaths() {
        localStorage.setItem('hmv_scan_paths', JSON.stringify(this.scanPaths));
    },

    /**
     * 显示扫描路径管理界面
     */
    showScanPathUI() {
        // 在设置页面添加扫描路径管理区域
        const settingsPage = document.getElementById('page-settings');
        if (!settingsPage) return;

        const scanPathSection = document.createElement('div');
        scanPathSection.className = 'setting-group';
        scanPathSection.innerHTML = `
            <h4>媒体扫描路径</h4>
            <p class="setting-desc" style="color: var(--text-secondary); font-size: 12px; margin-bottom: 12px;">
                设置本地媒体文件扫描路径，支持多个路径
            </p>
            <div id="scan-paths-container" class="scan-paths-container">
                <!-- 扫描路径列表将在这里渲染 -->
            </div>
            <div class="scan-path-add">
                <input type="text" id="input-scan-path" placeholder="输入扫描路径，如 C:\\Users\\Public\\Videos" style="flex: 1;">
                <button id="btn-add-scan-path" class="btn-primary">添加</button>
            </div>
            <div class="scan-path-actions" style="margin-top: 8px;">
                <button id="btn-rescan" class="btn-secondary">重新扫描</button>
                <button id="btn-refresh-scan-paths" class="btn-secondary">刷新列表</button>
            </div>
        `;

        // 插入到设置页面
        const settingsContainer = settingsPage.querySelector('.settings-container');
        if (settingsContainer) {
            settingsContainer.insertBefore(scanPathSection, settingsContainer.firstChild);
        }

        // 绑定事件
        this.setupScanPathEvents();
        this.renderScanPaths();
    },

    /**
     * 设置扫描路径事件
     */
    setupScanPathEvents() {
        const addBtn = document.getElementById('btn-add-scan-path');
        const input = document.getElementById('input-scan-path');
        const rescanBtn = document.getElementById('btn-rescan');
        const refreshBtn = document.getElementById('btn-refresh-scan-paths');

        if (addBtn && input) {
            addBtn.addEventListener('click', () => {
                const path = input.value.trim();
                if (path) {
                    this.addScanPath(path);
                    input.value = '';
                }
            });

            input.addEventListener('keypress', (e) => {
                if (e.key === 'Enter') {
                    const path = input.value.trim();
                    if (path) {
                        this.addScanPath(path);
                        input.value = '';
                    }
                }
            });
        }

        if (rescanBtn) {
            rescanBtn.addEventListener('click', () => {
                this.rescanAll();
            });
        }

        if (refreshBtn) {
            refreshBtn.addEventListener('click', () => {
                this.fetchScanPaths();
            });
        }
    },

    /**
     * 渲染扫描路径列表
     */
    renderScanPaths() {
        const container = document.getElementById('scan-paths-container');
        if (!container) return;

        if (this.scanPaths.length === 0) {
            container.innerHTML = '<div class="empty-hint">暂无扫描路径</div>';
            return;
        }

        container.innerHTML = this.scanPaths.map((path, index) => `
            <div class="scan-path-item">
                <span class="scan-path-icon">&#128193;</span>
                <span class="scan-path-text" title="${this.escapeHtml(path)}">${this.escapeHtml(path)}</span>
                <button class="btn-remove-scan-path" data-index="${index}" title="移除">&times;</button>
            </div>
        `).join('');

        // 绑定移除按钮事件
        container.querySelectorAll('.btn-remove-scan-path').forEach(btn => {
            btn.addEventListener('click', (e) => {
                const index = parseInt(e.target.dataset.index);
                this.removeScanPath(index);
            });
        });
    },

    /**
     * 添加扫描路径
     */
    async addScanPath(path) {
        // 检查是否已存在
        if (this.scanPaths.includes(path)) {
            App.showToast('该路径已存在');
            return;
        }

        // 添加到本地列表
        this.scanPaths.push(path);
        this.saveScanPaths();
        this.renderScanPaths();

        // 如果已连接服务器，同步到服务器
        if (Device.isConnected) {
            try {
                const response = await fetch(`${API.baseUrl}/scanpaths`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ path })
                });

                if (response.ok) {
                    App.showToast('扫描路径已添加');
                } else {
                    const result = await response.json();
                    App.showToast(`添加失败: ${result.message || '未知错误'}`);
                }
            } catch (e) {
                console.error('添加扫描路径失败:', e);
                App.showToast('添加失败: 网络错误');
            }
        } else {
            App.showToast('扫描路径已添加（未连接服务器）');
        }
    },

    /**
     * 移除扫描路径
     */
    async removeScanPath(index) {
        if (index < 0 || index >= this.scanPaths.length) return;

        const path = this.scanPaths[index];
        this.scanPaths.splice(index, 1);
        this.saveScanPaths();
        this.renderScanPaths();

        // 如果已连接服务器，同步到服务器
        if (Device.isConnected) {
            try {
                const encodedPath = encodeURIComponent(path);
                const response = await fetch(`${API.baseUrl}/scanpaths/${encodedPath}`, {
                    method: 'DELETE'
                });

                if (response.ok) {
                    App.showToast('扫描路径已移除');
                } else {
                    App.showToast('移除失败');
                }
            } catch (e) {
                console.error('移除扫描路径失败:', e);
                App.showToast('移除失败: 网络错误');
            }
        } else {
            App.showToast('扫描路径已移除（未连接服务器）');
        }
    },

    /**
     * 从服务器获取扫描路径
     */
    async fetchScanPaths() {
        if (!Device.isConnected) {
            App.showToast('请先连接服务器');
            return;
        }

        try {
            const response = await fetch(`${API.baseUrl}/scanpaths`);
            if (response.ok) {
                const paths = await response.json();
                this.scanPaths = paths;
                this.saveScanPaths();
                this.renderScanPaths();
                App.showToast('扫描路径列表已刷新');
            }
        } catch (e) {
            console.error('获取扫描路径失败:', e);
            App.showToast('获取扫描路径失败');
        }
    },

    /**
     * 重新扫描所有路径
     */
    async rescanAll() {
        if (!Device.isConnected) {
            App.showToast('请先连接服务器');
            return;
        }

        try {
            App.showToast('正在重新扫描...');
            const response = await fetch(`${API.baseUrl}/scanpaths/rescan`, {
                method: 'POST'
            });

            if (response.ok) {
                App.showToast('重新扫描完成');
                // 刷新媒体列表
                App.onDeviceConnected();
            } else {
                const result = await response.json();
                App.showToast(`重新扫描失败: ${result.message || '未知错误'}`);
            }
        } catch (e) {
            console.error('重新扫描失败:', e);
            App.showToast('重新扫描失败: 网络错误');
        }
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

// 在应用初始化时加载环境检测
document.addEventListener('DOMContentLoaded', () => {
    Environment.init();
});
