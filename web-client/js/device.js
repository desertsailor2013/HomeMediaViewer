/**
 * 设备模块
 * 管理设备连接和发现
 */
const Device = {
    currentHost: '',
    currentPort: 0,
    isConnected: false,
    statusEl: null,

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

        // 加载上次连接
        this.loadLastConnection();
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
    }
};
