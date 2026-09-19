/**
 * 设置模块
 * 管理应用设置
 */
const Settings = {
    defaults: {
        autoReconnect: true,
        timeout: 5,
        autoNext: false,
        defaultSpeed: '1',
        darkMode: false,
        showThumbnails: true
    },

    /**
     * 初始化设置
     */
    init() {
        this.load();
        this.setupUI();
        this.setupClearButtons();
    },

    /**
     * 设置 UI 交互
     */
    setupUI() {
        const settings = this.getAll();

        // 自动重连
        const autoReconnect = document.getElementById('setting-auto-reconnect');
        autoReconnect.checked = settings.autoReconnect;
        autoReconnect.addEventListener('change', (e) => {
            this.set('autoReconnect', e.target.checked);
        });

        // 连接超时
        const timeout = document.getElementById('setting-timeout');
        timeout.value = settings.timeout;
        timeout.addEventListener('change', (e) => {
            this.set('timeout', parseInt(e.target.value));
        });

        // 自动播放下一个
        const autoNext = document.getElementById('setting-auto-next');
        autoNext.checked = settings.autoNext;
        autoNext.addEventListener('change', (e) => {
            this.set('autoNext', e.target.checked);
        });

        // 默认播放速度
        const defaultSpeed = document.getElementById('setting-default-speed');
        defaultSpeed.value = settings.defaultSpeed;
        defaultSpeed.addEventListener('change', (e) => {
            this.set('defaultSpeed', e.target.value);
        });

        // 深色模式
        const darkMode = document.getElementById('setting-dark-mode');
        darkMode.checked = settings.darkMode || document.documentElement.getAttribute('data-theme') === 'dark';
        darkMode.addEventListener('change', (e) => {
            this.set('darkMode', e.target.checked);
            if (e.target.checked) {
                document.documentElement.setAttribute('data-theme', 'dark');
                localStorage.setItem('hmv_theme', 'dark');
            } else {
                document.documentElement.removeAttribute('data-theme');
                localStorage.setItem('hmv_theme', 'light');
            }
        });

        // 显示缩略图
        const showThumbnails = document.getElementById('setting-show-thumbnails');
        showThumbnails.checked = settings.showThumbnails;
        showThumbnails.addEventListener('change', (e) => {
            this.set('showThumbnails', e.target.checked);
        });
    },

    /**
     * 设置清除按钮
     */
    setupClearButtons() {
        document.getElementById('btn-clear-progress').addEventListener('click', () => {
            if (confirm('确定要清除所有播放进度吗？')) {
                Object.keys(localStorage).forEach(key => {
                    if (key.startsWith('hmv_progress_')) {
                        localStorage.removeItem(key);
                    }
                });
                App.showToast('播放进度已清除');
            }
        });

        document.getElementById('btn-clear-favorites').addEventListener('click', () => {
            if (confirm('确定要清除所有收藏吗？')) {
                Favorites.items = [];
                Favorites.saveToStorage();
                Favorites.render();
                App.showToast('收藏已清除');
            }
        });
    },

    /**
     * 获取设置值
     */
    get(key) {
        const settings = this.getAll();
        return settings[key] !== undefined ? settings[key] : this.defaults[key];
    },

    /**
     * 获取所有设置
     */
    getAll() {
        try {
            const saved = localStorage.getItem('hmv_settings');
            return saved ? { ...this.defaults, ...JSON.parse(saved) } : { ...this.defaults };
        } catch (e) {
            return { ...this.defaults };
        }
    },

    /**
     * 设置值
     */
    set(key, value) {
        const settings = this.getAll();
        settings[key] = value;
        localStorage.setItem('hmv_settings', JSON.stringify(settings));
    }
};
