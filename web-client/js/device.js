/**
 * 设备模块
 * 管理设备连接和发现
 */
const Device = {
    currentHost: '',
    currentPort: 0,
    isConnected: false,
    statusEl: null,
    favorites: [],

    /**
     * 初始化设备模块
     */
    init() {
        this.statusEl = document.getElementById('connect-status');
        const connectBtn = document.getElementById('btn-connect');
        const hostInput = document.getElementById('input-host');
        const portInput = document.getElementById('input-port');

        connectBtn.addEventListener('click', () => this.connect());
        portInput.addEventListener('keypress', (e) => {
            if (e.key === 'Enter') this.connect();
        });

        // 加载收藏和上次连接
        this.loadFavorites();
        this.loadLastConnection();
        
        // 自动连接收藏设备
        this.autoConnectFavorites();
    },

    /**
     * 连接设备
     */
    async connect() {
        const host = document.getElementById('input-host').value.trim();
        const port = parseInt(document.getElementById('input-port').value);

        if (!host || !port) {
            this.setStatus('请输入 IP 和端口', 'error');
            return;
        }

        this.setStatus('连接中...', '');

        try {
            await API.testConnection(host, port);
            this.currentHost = host;
            this.currentPort = port;
            this.isConnected = true;
            API.setServer(host, port);
            this.setStatus(`已连接 ${host}:${port}`, 'connected');
            this.saveLastConnection();

            // 触发连接成功事件
            App.onDeviceConnected();
        } catch (e) {
            this.isConnected = false;
            this.setStatus('连接失败', 'error');
        }
    },

    /**
     * 自动连接收藏设备
     */
    async autoConnectFavorites() {
        for (const fav of this.favorites) {
            try {
                await API.testConnection(fav.host, fav.port);
                this.currentHost = fav.host;
                this.currentPort = fav.port;
                this.isConnected = true;
                API.setServer(fav.host, fav.port);
                this.setStatus(`已连接 ${fav.name || fav.host}:${fav.port}`, 'connected');
                this.saveLastConnection();
                App.onDeviceConnected();
                return;
            } catch (e) {
                // 连接失败，尝试下一个
            }
        }
    },

    /**
     * 设置状态文本
     */
    setStatus(text, className) {
        this.statusEl.textContent = text;
        this.statusEl.className = `status-text ${className}`;
    },

    /**
     * 保存上次连接
     */
    saveLastConnection() {
        localStorage.setItem('hmv_last_host', this.currentHost);
        localStorage.setItem('hmv_last_port', this.currentPort);
    },

    /**
     * 加载上次连接
     */
    loadLastConnection() {
        const host = localStorage.getItem('hmv_last_host');
        const port = localStorage.getItem('hmv_last_port');
        if (host) document.getElementById('input-host').value = host;
        if (port) document.getElementById('input-port').value = port;
    },

    /**
     * 加载收藏设备
     */
    loadFavorites() {
        try {
            const data = localStorage.getItem('hmv_favorites');
            this.favorites = data ? JSON.parse(data) : [];
        } catch (e) {
            this.favorites = [];
        }
    },

    /**
     * 保存收藏设备
     */
    saveFavorites() {
        localStorage.setItem('hmv_favorites', JSON.stringify(this.favorites));
    },

    /**
     * 添加收藏
     */
    addFavorite(name, host, port) {
        if (!this.isFavorite(host, port)) {
            this.favorites.push({ name, host, port });
            this.saveFavorites();
        }
    },

    /**
     * 移除收藏
     */
    removeFavorite(host, port) {
        this.favorites = this.favorites.filter(f => !(f.host === host && f.port === port));
        this.saveFavorites();
    },

    /**
     * 检查是否已收藏
     */
    isFavorite(host, port) {
        return this.favorites.some(f => f.host === host && f.port === port);
    }
};
