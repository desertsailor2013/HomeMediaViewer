/**
 * 收藏模块
 * 管理收藏设备
 */
const Favorites = {
    items: [],

    /**
     * 初始化收藏模块
     */
    init() {
        this.loadFromStorage();
        this.setupDialog();
    },

    /**
     * 设置弹窗
     */
    setupDialog() {
        const dialog = document.getElementById('favorite-dialog');
        const addBtn = document.getElementById('btn-add-favorite');
        const closeBtn = document.getElementById('btn-close-fav-dialog');
        const saveBtn = document.getElementById('btn-save-favorite');

        addBtn.addEventListener('click', () => {
            document.getElementById('fav-name').value = '';
            document.getElementById('fav-host').value = '';
            document.getElementById('fav-port').value = '8080';
            document.getElementById('fav-alias').value = '';
            dialog.classList.remove('hidden');
        });

        closeBtn.addEventListener('click', () => {
            dialog.classList.add('hidden');
        });

        dialog.addEventListener('click', (e) => {
            if (e.target === dialog) {
                dialog.classList.add('hidden');
            }
        });

        saveBtn.addEventListener('click', () => {
            const name = document.getElementById('fav-name').value.trim();
            const host = document.getElementById('fav-host').value.trim();
            const port = parseInt(document.getElementById('fav-port').value);
            const alias = document.getElementById('fav-alias').value.trim();

            if (!name || !host || !port) {
                App.showToast('请填写完整信息');
                return;
            }

            this.add({ name, host, port, alias });
            dialog.classList.add('hidden');
            App.showToast(I18n.t('toast.addedToFavorites'));
        });
    },

    /**
     * 添加收藏
     */
    add(device) {
        if (this.items.some(item => item.name === device.name)) return;
        this.items.push(device);
        this.saveToStorage();
        this.render();
    },

    /**
     * 移除收藏
     */
    remove(name) {
        this.items = this.items.filter(item => item.name !== name);
        this.saveToStorage();
        this.render();
    },

    /**
     * 检查是否已收藏
     */
    isFavorite(name) {
        return this.items.some(item => item.name === name);
    },

    /**
     * 切换收藏状态
     */
    toggle(device) {
        if (this.isFavorite(device.name)) {
            this.remove(device.name);
        } else {
            this.add(device);
        }
    },

    /**
     * 获取收藏列表
     */
    getAll() {
        return this.items;
    },

    /**
     * 渲染收藏列表
     */
    render() {
        const container = document.getElementById('favorite-list-container');
        if (!container) return;

        if (this.items.length === 0) {
            container.innerHTML = '<div class="empty-hint"><p>暂无收藏设备</p></div>';
            return;
        }

        container.innerHTML = '';
        this.items.forEach(item => {
            const el = document.createElement('div');
            el.className = 'device-item';
            el.innerHTML = `
                <span class="device-icon">&#9733;</span>
                <div class="device-info">
                    <div class="device-name">${item.alias || item.name}</div>
                    <div class="device-address">${item.host}:${item.port}</div>
                </div>
                <div class="device-actions">
                    <span class="btn-fav active" data-name="${item.name}" title="取消收藏">&#9733;</span>
                    <button class="btn-secondary btn-connect-fav" data-host="${item.host}" data-port="${item.port}">连接</button>
                </div>
            `;

            el.querySelector('.btn-fav').addEventListener('click', (e) => {
                this.remove(e.target.dataset.name);
            });

            el.querySelector('.btn-connect-fav').addEventListener('click', (e) => {
                document.getElementById('input-host').value = e.target.dataset.host;
                document.getElementById('input-port').value = e.target.dataset.port;
                Device.connect();
                App.navigateTo('media');
            });

            container.appendChild(el);
        });
    },

    /**
     * 保存到 localStorage
     */
    saveToStorage() {
        localStorage.setItem('hmv_favorites', JSON.stringify(this.items));
    },

    /**
     * 从 localStorage 加载
     */
    loadFromStorage() {
        try {
            const saved = localStorage.getItem('hmv_favorites');
            if (saved) {
                this.items = JSON.parse(saved);
            }
        } catch (e) {
            this.items = [];
        }
    }
};
