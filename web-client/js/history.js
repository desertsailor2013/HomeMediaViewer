/**
 * 播放历史模块
 * 功能：记录播放历史，支持清除、搜索、重新播放
 */
const History = {
    items: [],

    init() {
        this.loadFromStorage();
        document.getElementById('btn-clear-history').addEventListener('click', () => this.clear());
        document.getElementById('history-search').addEventListener('input', (e) => this.render(e.target.value));
    },

    /**
     * 记录播放
     */
    add(mediaItem) {
        const record = {
            id: mediaItem.id,
            title: mediaItem.title || '未知标题',
            mimeType: mediaItem.mimeType || '',
            size: mediaItem.size || 0,
            thumbnail: mediaItem.thumbnail || mediaItem.thumbnailUri || '',
            playedAt: Date.now(),
            playCount: 1,
            duration: 0,
            position: 0
        };
        const exist = this.items.find(i => i.id === mediaItem.id);
        if (exist) {
            exist.playCount++;
            exist.playedAt = record.playedAt;
        } else {
            this.items.unshift(record);
        }
        this.saveToStorage();
    },

    /**
     * 更新播放进度
     */
    updateProgress(mediaId, position, duration) {
        const item = this.items.find(i => i.id === mediaId);
        if (item) {
            item.position = position;
            item.duration = duration;
            this.saveToStorage();
        }
    },

    /**
     * 获取播放次数
     */
    getPlayCount(mediaId) {
        const item = this.items.find(i => i.id === mediaId);
        return item ? item.playCount : 0;
    },

    /**
     * 获取总播放次数
     */
    getTotalPlayCount() {
        return this.items.reduce((sum, item) => sum + (item.playCount || 0), 0);
    },

    /**
     * 获取总播放时长（秒）
     */
    getTotalDuration() {
        return this.items.reduce((sum, item) => sum + (item.duration || 0) * (item.playCount || 0), 0);
    },

    /**
     * 获取总播放大小
     */
    getTotalSize() {
        return this.items.reduce((sum, item) => sum + (item.size || 0) * (item.playCount || 0), 0);
    },

    /**
     * 获取最近播放（前 N 条）
     */
    getRecent(count = 5) {
        return this.items.slice(0, count);
    },

    /**
     * 删除单条历史
     */
    remove(mediaId) {
        this.items = this.items.filter(i => i.id !== mediaId);
        this.saveToStorage();
        this.render();
    },

    /**
     * 清空历史
     */
    clear() {
        if (this.items.length === 0) return;
        if (!confirm('确定要清空所有播放历史吗？')) return;
        this.items = [];
        this.saveToStorage();
        this.render();
    },

    /**
     * 渲染历史列表
     */
    render(searchQuery = '') {
        const container = document.getElementById('history-list-container');
        if (!container) return;

        let filtered = this.items;
        if (searchQuery) {
            const q = searchQuery.toLowerCase();
            filtered = this.items.filter(item =>
                (item.title && item.title.toLowerCase().includes(q)) ||
                (item.mimeType && item.mimeType.toLowerCase().includes(q))
            );
        }

        if (filtered.length === 0) {
            container.innerHTML = '<div class="empty-hint"><p>暂无播放记录</p></div>';
            return;
        }

        container.innerHTML = '';
        filtered.forEach(item => {
            const el = document.createElement('div');
            el.className = 'history-item';
            const time = new Date(item.playedAt).toLocaleString('zh-CN');
            const isAudio = item.mimeType && item.mimeType.startsWith('audio');
            const sizeStr = this.formatSize(item.size);
            const posStr = item.position > 0 ? ` | 播放至 ${this.formatTime(item.position)}` : '';

            el.innerHTML = `
                <div class="history-thumb">
                    ${item.thumbnail ?
                        `<img src="${item.thumbnail}" onerror="this.parentElement.innerHTML='${isAudio ? '&#9835;' : '&#9654;'}'">` :
                        (isAudio ? '&#9835;' : '&#9654;')
                    }
                </div>
                <div class="history-info">
                    <div class="history-title">${item.title}</div>
                    <div class="history-meta">${sizeStr} | ${item.mimeType || '未知'} | ${item.playCount} 次播放${posStr}</div>
                    <div class="history-time">${time}</div>
                </div>
                <div class="history-actions">
                    <button class="btn-secondary btn-play-history" data-id="${item.id}" title="重新播放">&#9654;</button>
                    <button class="btn-icon btn-remove-history" data-id="${item.id}" title="删除">&times;</button>
                </div>
            `;

            el.querySelector('.btn-play-history').addEventListener('click', (e) => {
                e.stopPropagation();
                this.replay(item);
            });

            el.querySelector('.btn-remove-history').addEventListener('click', (e) => {
                e.stopPropagation();
                this.remove(item.id);
            });

            el.addEventListener('click', () => this.replay(item));

            container.appendChild(el);
        });
    },

    /**
     * 重新播放
     */
    replay(item) {
        // 构造 MediaItem 对象传给 Player
        Player.play({
            id: item.id,
            title: item.title,
            mimeType: item.mimeType,
            size: item.size,
            thumbnailUri: item.thumbnail
        });
    },

    /**
     * 格式化时间
     */
    formatTime(seconds) {
        const h = Math.floor(seconds / 3600);
        const m = Math.floor((seconds % 3600) / 60);
        const s = Math.floor(seconds % 60);
        if (h > 0) return `${h}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
        return `${m}:${String(s).padStart(2, '0')}`;
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
     * 保存到 localStorage
     */
    saveToStorage() {
        localStorage.setItem('hmv_history', JSON.stringify(this.items));
    },

    /**
     * 从 localStorage 加载
     */
    loadFromStorage() {
        try {
            const saved = localStorage.getItem('hmv_history');
            if (saved) this.items = JSON.parse(saved);
        } catch (e) {
            this.items = [];
        }
    }
};
