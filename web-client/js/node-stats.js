/**
 * 节点统计与日志模块
 * 查看各设备节点的运行时统计和日志
 */
const NodeStats = {
    currentTab: 'stats',
    refreshInterval: null,

    /**
     * 初始化
     */
    init() {
        this.setupEventListeners();
    },

    /**
     * 设置事件监听
     */
    setupEventListeners() {
        // 刷新统计
        const refreshBtn = document.getElementById('btn-refresh-node-stats');
        if (refreshBtn) {
            refreshBtn.addEventListener('click', () => this.refreshStats());
        }

        // 切换标签页
        document.querySelectorAll('.stats-tab').forEach(tab => {
            tab.addEventListener('click', (e) => {
                const tabName = e.target.dataset.tab;
                this.switchTab(tabName);
            });
        });

        // 清空日志
        const clearLogBtn = document.getElementById('btn-clear-logs');
        if (clearLogBtn) {
            clearLogBtn.addEventListener('click', () => this.clearLogs());
        }

        // 导出日志
        const exportLogBtn = document.getElementById('btn-export-logs');
        if (exportLogBtn) {
            exportLogBtn.addEventListener('click', () => this.exportLogs());
        }
    },

    /**
     * 切换标签页
     */
    switchTab(tabName) {
        this.currentTab = tabName;
        
        // 更新标签样式
        document.querySelectorAll('.stats-tab').forEach(tab => {
            tab.classList.toggle('active', tab.dataset.tab === tabName);
        });

        // 显示对应内容
        document.querySelectorAll('.stats-content').forEach(content => {
            content.classList.toggle('hidden', content.dataset.tab !== tabName);
        });

        // 加载数据
        if (tabName === 'stats') {
            this.loadRuntimeStats();
        } else if (tabName === 'traffic') {
            this.loadTrafficStats();
        } else if (tabName === 'logs') {
            this.loadLogs();
        }
    },

    /**
     * 刷新统计
     */
    async refreshStats() {
        if (!Device.isConnected) {
            App.showToast('请先连接设备');
            return;
        }

        if (this.currentTab === 'stats') {
            await this.loadRuntimeStats();
        } else if (this.currentTab === 'traffic') {
            await this.loadTrafficStats();
        } else if (this.currentTab === 'logs') {
            await this.loadLogs();
        }

        App.showToast('统计已刷新');
    },

    /**
     * 加载运行时统计
     */
    async loadRuntimeStats() {
        try {
            const response = await fetch(`${API.baseUrl}/stats/runtime`);
            if (response.ok) {
                const stats = await response.json();
                this.renderRuntimeStats(stats);
            }
        } catch (e) {
            console.error('加载运行时统计失败:', e);
        }
    },

    /**
     * 渲染运行时统计
     */
    renderRuntimeStats(stats) {
        const container = document.getElementById('runtime-stats-container');
        if (!container) return;

        const formatUptime = (ms) => {
            const seconds = Math.floor(ms / 1000);
            const minutes = Math.floor(seconds / 60);
            const hours = Math.floor(minutes / 60);
            const days = Math.floor(hours / 24);
            
            if (days > 0) return `${days}天 ${hours % 24}小时`;
            if (hours > 0) return `${hours}小时 ${minutes % 60}分钟`;
            return `${minutes}分钟`;
        };

        const formatBytes = (bytes) => {
            if (bytes < 1024) return `${bytes} B`;
            if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
            if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
            return `${(bytes / (1024 * 1024 * 1024)).toFixed(2)} GB`;
        };

        container.innerHTML = `
            <div class="stats-grid">
                <div class="stat-card">
                    <div class="stat-icon">⏱️</div>
                    <div class="stat-value">${formatUptime(stats.uptime)}</div>
                    <div class="stat-label">运行时间</div>
                </div>
                <div class="stat-card">
                    <div class="stat-icon">⚡</div>
                    <div class="stat-value">${(stats.cpuUsage * 100).toFixed(1)}%</div>
                    <div class="stat-label">CPU 使用率</div>
                </div>
                <div class="stat-card">
                    <div class="stat-icon">💾</div>
                    <div class="stat-value">${formatBytes(stats.memoryUsed)}</div>
                    <div class="stat-label">内存使用 / ${formatBytes(stats.memoryTotal)}</div>
                </div>
                <div class="stat-card">
                    <div class="stat-icon">💿</div>
                    <div class="stat-value">${formatBytes(stats.storageUsed)}</div>
                    <div class="stat-label">存储使用 / ${formatBytes(stats.storageTotal)}</div>
                </div>
                <div class="stat-card">
                    <div class="stat-icon">🔋</div>
                    <div class="stat-value">${stats.batteryLevel >= 0 ? stats.batteryLevel + '%' : 'N/A'}</div>
                    <div class="stat-label">电池 ${stats.batteryCharging ? '充电中' : ''}</div>
                </div>
                <div class="stat-card">
                    <div class="stat-icon">📤</div>
                    <div class="stat-value">${formatBytes(stats.networkUpload)}</div>
                    <div class="stat-label">网络上传</div>
                </div>
                <div class="stat-card">
                    <div class="stat-icon">📥</div>
                    <div class="stat-value">${formatBytes(stats.networkDownload)}</div>
                    <div class="stat-label">网络下载</div>
                </div>
                <div class="stat-card">
                    <div class="stat-icon">🔗</div>
                    <div class="stat-value">${stats.activeConnections}</div>
                    <div class="stat-label">活跃连接</div>
                </div>
                <div class="stat-card">
                    <div class="stat-icon">📊</div>
                    <div class="stat-value">${stats.totalRequests}</div>
                    <div class="stat-label">总请求数</div>
                </div>
            </div>
        `;
    },

    /**
     * 加载流量统计
     */
    async loadTrafficStats() {
        try {
            const response = await fetch(`${API.baseUrl}/stats/traffic`);
            if (response.ok) {
                const stats = await response.json();
                this.renderTrafficStats(stats);
            }
        } catch (e) {
            console.error('加载流量统计失败:', e);
        }
    },

    /**
     * 渲染流量统计
     */
    renderTrafficStats(stats) {
        const container = document.getElementById('traffic-stats-container');
        if (!container) return;

        const formatBytes = (bytes) => {
            if (bytes < 1024) return `${bytes} B`;
            if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
            if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
            return `${(bytes / (1024 * 1024 * 1024)).toFixed(2)} GB`;
        };

        const renderMap = (map, title) => {
            const entries = Object.entries(map || {});
            if (entries.length === 0) return '<div class="empty-hint"><p>暂无数据</p></div>';
            
            return `
                <div class="traffic-list">
                    ${entries.map(([key, value]) => `
                        <div class="traffic-item">
                            <span class="traffic-key">${this.escapeHtml(key)}</span>
                            <span class="traffic-value">${formatBytes(value)}</span>
                        </div>
                    `).join('')}
                </div>
            `;
        };

        container.innerHTML = `
            <div class="traffic-summary">
                <div class="traffic-total">
                    <span class="traffic-label">总出站流量:</span>
                    <span class="traffic-value highlight">${formatBytes(stats.totalOutbound)}</span>
                </div>
                <div class="traffic-total">
                    <span class="traffic-label">总入站流量:</span>
                    <span class="traffic-value highlight">${formatBytes(stats.totalInbound)}</span>
                </div>
            </div>
            
            <div class="traffic-section">
                <h4>按设备统计 - 出站（被点播）</h4>
                ${renderMap(stats.outboundByDevice)}
            </div>
            
            <div class="traffic-section">
                <h4>按设备统计 - 入站（点播他人）</h4>
                ${renderMap(stats.inboundByDevice)}
            </div>
            
            <div class="traffic-section">
                <h4>按媒体统计 - 出站</h4>
                ${renderMap(stats.outboundByMedia)}
            </div>
            
            <div class="traffic-section">
                <h4>按媒体统计 - 入站</h4>
                ${renderMap(stats.inboundByMedia)}
            </div>
        `;
    },

    /**
     * 加载日志
     */
    async loadLogs() {
        try {
            const response = await fetch(`${API.baseUrl}/logs?limit=200`);
            if (response.ok) {
                const logs = await response.json();
                this.renderLogs(logs);
            }
        } catch (e) {
            console.error('加载日志失败:', e);
        }
    },

    /**
     * 渲染日志
     */
    renderLogs(logs) {
        const container = document.getElementById('logs-container');
        if (!container) return;

        if (!logs || logs.length === 0) {
            container.innerHTML = '<div class="empty-hint"><p>暂无日志</p></div>';
            return;
        }

        const formatTime = (ts) => {
            const date = new Date(ts);
            return date.toLocaleString('zh-CN');
        };

        const getLevelClass = (level) => {
            switch (level.toLowerCase()) {
                case 'error': return 'log-error';
                case 'warn': case 'warning': return 'log-warn';
                case 'info': return 'log-info';
                case 'debug': return 'log-debug';
                default: return '';
            }
        };

        container.innerHTML = `
            <div class="log-list">
                ${logs.map(log => `
                    <div class="log-item ${getLevelClass(log.level)}">
                        <span class="log-time">${formatTime(log.timestamp)}</span>
                        <span class="log-level">[${log.level.toUpperCase()}]</span>
                        <span class="log-source">[${this.escapeHtml(log.source)}]</span>
                        <span class="log-message">${this.escapeHtml(log.message)}</span>
                    </div>
                `).join('')}
            </div>
        `;
    },

    /**
     * 清空日志
     */
    async clearLogs() {
        if (!confirm('确定要清空所有日志吗？')) return;

        try {
            const response = await fetch(`${API.baseUrl}/logs/clear`, {
                method: 'POST'
            });

            if (response.ok) {
                App.showToast('日志已清空');
                this.loadLogs();
            }
        } catch (e) {
            App.showToast('清空日志失败');
        }
    },

    /**
     * 导出日志
     */
    async exportLogs() {
        try {
            const response = await fetch(`${API.baseUrl}/logs?limit=1000`);
            if (response.ok) {
                const logs = await response.json();
                const blob = new Blob([JSON.stringify(logs, null, 2)], { type: 'application/json' });
                const url = URL.createObjectURL(blob);
                const a = document.createElement('a');
                a.href = url;
                a.download = `logs-${new Date().toISOString().slice(0, 10)}.json`;
                a.click();
                URL.revokeObjectURL(url);
                App.showToast('日志已导出');
            }
        } catch (e) {
            App.showToast('导出日志失败');
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

// 导出
window.NodeStats = NodeStats;
