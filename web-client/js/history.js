/**
 * 播放历史模块
 * 功能：记录播放历史，支持清除和重新播放
 * 状态：界面待完成
 */
const History = {
    items: [],

    init() {
        this.loadFromStorage();
        document.getElementById('btn-clear-history').addEventListener('click', () => this.clear());
    },

    /**
     * 记录播放
     */
    add(mediaItem) {
        const record = {
            ...mediaItem,
            playedAt: Date.now(),
            playCount: 1
        };
        // 更新或添加
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
     * 清空历史
     */
    clear() {
        this.items = [];
        this.saveToStorage();
        this.render();
    },

    /**
     * 渲染历史列表
     */
    render() {
        const container = document.getElementById('history-list-container');
        if (!container) return;

        if (this.items.length === 0) {
            container.innerHTML = '<div class="empty-hint"><p>暂无播放记录</p></div>';
            return;
        }

        container.innerHTML = '';
        this.items.forEach(item => {
            const el = document.createElement('div');
            el.className = 'queue-item';
            const time = new Date(item.playedAt).toLocaleString('zh-CN');
            el.innerHTML = `
                <div class="queue-info">
                    <div class="queue-title">${item.title || '未知标题'}</div>
                    <div class="queue-meta">${item.mimeType || ''} | ${time} | 播放 ${item.playCount} 次</div>
                </div>
            `;
            el.addEventListener('click', () => Player.play(item));
            container.appendChild(el);
        });
    },

    saveToStorage() {
        localStorage.setItem('hmv_history', JSON.stringify(this.items));
    },

    loadFromStorage() {
        try {
            const saved = localStorage.getItem('hmv_history');
            if (saved) this.items = JSON.parse(saved);
        } catch (e) {
            this.items = [];
        }
    }
};
