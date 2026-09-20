/**
 * 队列模块
 * 管理播放队列
 */
const Queue = {
    items: [],
    currentIndex: -1,

    /**
     * 初始化队列
     */
    init() {
        this.loadFromStorage();
        document.getElementById('btn-clear-queue').addEventListener('click', () => this.clear());
        document.getElementById('btn-add-to-queue').addEventListener('click', () => this.addCurrent());
    },

    /**
     * 添加当前播放项到队列
     */
    addCurrent() {
        const media = Player.getCurrentMedia();
        if (!media) return;
        this.add(media);
        App.showToast(I18n.t('toast.addedToQueue'));
    },

    /**
     * 添加媒体到队列
     */
    add(mediaItem) {
        // 避免重复
        if (this.items.some(item => item.id === mediaItem.id)) return;
        this.items.push(mediaItem);
        this.saveToStorage();
        this.render();
    },

    /**
     * 移除队列项
     */
    remove(index) {
        if (index < 0 || index >= this.items.length) return;
        this.items.splice(index, 1);
        if (this.currentIndex >= this.items.length) {
            this.currentIndex = this.items.length - 1;
        }
        this.saveToStorage();
        this.render();
    },

    /**
     * 清空队列
     */
    clear() {
        this.items = [];
        this.currentIndex = -1;
        this.saveToStorage();
        this.render();
    },

    /**
     * 播放指定队列项
     */
    play(index) {
        if (index < 0 || index >= this.items.length) return;
        this.currentIndex = index;
        Player.play(this.items[index]);
        this.render();
    },

    /**
     * 获取队列信息
     */
    getInfo() {
        return {
            items: this.items,
            currentIndex: this.currentIndex,
            count: this.items.length
        };
    },

    /**
     * 渲染队列列表
     */
    render() {
        const container = document.getElementById('queue-list-container');
        if (!container) return;

        if (this.items.length === 0) {
            container.innerHTML = '<div class="empty-hint"><p>队列为空</p></div>';
            return;
        }

        container.innerHTML = '';
        this.items.forEach((item, index) => {
            const el = document.createElement('div');
            el.className = `queue-item ${index === this.currentIndex ? 'playing' : ''}`;
            el.innerHTML = `
                <span class="queue-index">${index + 1}</span>
                <div class="queue-info">
                    <div class="queue-title">${item.title || '未知标题'}</div>
                    <div class="queue-meta">${item.mimeType || ''}</div>
                </div>
                <span class="btn-remove" data-index="${index}">&times;</span>
            `;

            el.addEventListener('click', (e) => {
                if (!e.target.classList.contains('btn-remove')) {
                    this.play(index);
                }
            });

            el.querySelector('.btn-remove').addEventListener('click', (e) => {
                e.stopPropagation();
                this.remove(parseInt(e.target.dataset.index));
            });

            container.appendChild(el);
        });
    },

    /**
     * 保存到 localStorage
     */
    saveToStorage() {
        localStorage.setItem('hmv_queue', JSON.stringify(this.items));
        localStorage.setItem('hmv_queue_index', this.currentIndex);
    },

    /**
     * 从 localStorage 加载
     */
    loadFromStorage() {
        try {
            const saved = localStorage.getItem('hmv_queue');
            if (saved) {
                this.items = JSON.parse(saved);
                this.currentIndex = parseInt(localStorage.getItem('hmv_queue_index') || '-1');
            }
        } catch (e) {
            this.items = [];
            this.currentIndex = -1;
        }
    }
};
