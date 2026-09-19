/**
 * 统计面板模块
 * 功能：播放次数、时长、流量、设备使用统计
 * 状态：界面待完成
 */
const Stats = {
    init() {
        document.getElementById('btn-refresh-stats').addEventListener('click', () => this.refresh());
    },

    refresh() {
        // TODO: 从各模块获取统计数据
        this.updatePlayCount();
        this.updateDeviceCount();
        this.updateRecentList();
        this.updateTypeChart();
    },

    updatePlayCount() {
        const history = History.items || [];
        const count = history.reduce((sum, item) => sum + (item.playCount || 0), 0);
        document.getElementById('stat-play-count').textContent = count;
    },

    updateDeviceCount() {
        const favorites = Favorites.getAll ? Favorites.getAll() : [];
        document.getElementById('stat-device-count').textContent = favorites.length;
    },

    updateRecentList() {
        const container = document.getElementById('stats-recent-list');
        if (!container) return;

        const history = (History.items || []).slice(0, 5);
        if (history.length === 0) {
            container.innerHTML = '<div class="empty-hint"><p>暂无播放记录</p></div>';
            return;
        }

        container.innerHTML = '';
        history.forEach(item => {
            const el = document.createElement('div');
            el.className = 'queue-item';
            el.innerHTML = `
                <div class="queue-info">
                    <div class="queue-title">${item.title || '未知标题'}</div>
                    <div class="queue-meta">${item.mimeType || ''} | ${item.playCount || 0} 次</div>
                </div>
            `;
            container.appendChild(el);
        });
    },

    updateTypeChart() {
        const container = document.getElementById('stats-type-chart');
        if (!container) return;
        // TODO: 实现饼图/柱状图
        container.innerHTML = '<div class="empty-hint"><p>图表功能待实现</p></div>';
    },

    render() {
        this.refresh();
    }
};
