/**
 * 主应用模块
 * 协调各模块工作
 */
const App = {
    allMediaItems: [],
    searchQuery: '',
    typeFilter: 'all',
    groupByFolder: false,
    collapsedFolders: new Set(),
    currentPage: 'media',

    /**
     * 初始化应用
     */
    init() {
        Device.init();
        Player.init();
        Queue.init();
        Favorites.init();
        Settings.init();
        History.init();
        FileManager.init();
        Stats.init();
        Network.init();
        Shortcuts.init();
        I18n.init();
        ExportImport.init();
        this.setupNavigation();
        this.setupToolbar();
        this.setupTheme();
        this.setupCastDialog();
        this.setupMobileMenu();

        // 初始渲染
        Favorites.render();
        Queue.render();
    },

    /**
     * 设备连接成功回调
     */
    async onDeviceConnected() {
        try {
            this.allMediaItems = await API.getMediaList();
            this.applyFilter();
        } catch (e) {
            console.error('获取媒体列表失败:', e);
        }
    },

    /**
     * 设置导航
     */
    setupNavigation() {
        document.querySelectorAll('.nav-item').forEach(item => {
            item.addEventListener('click', (e) => {
                e.preventDefault();
                const page = item.dataset.page;
                this.navigateTo(page);
            });
        });
    },

    /**
     * 导航到页面
     */
    navigateTo(page) {
        this.currentPage = page;

        // 更新导航高亮
        document.querySelectorAll('.nav-item').forEach(item => {
            item.classList.toggle('active', item.dataset.page === page);
        });

        // 切换页面
        document.querySelectorAll('.page').forEach(p => {
            p.classList.toggle('active', p.id === `page-${page}`);
        });

        // 更新标题
        const titles = {
            media: '媒体库',
            devices: '设备列表',
            favorites: '收藏设备',
            queue: '播放队列',
            history: '播放历史',
            filemanager: '文件管理',
            stats: '统计面板',
            network: '网络诊断',
            shortcuts: '快捷键帮助',
            i18n: '多语言',
            export: '数据导出',
            settings: '设置',
            about: '关于'
        };
        document.getElementById('page-title').textContent = titles[page] || page;

        // 刷新对应页面
        if (page === 'favorites') Favorites.render();
        if (page === 'queue') Queue.render();
        if (page === 'devices') this.renderDeviceList();
        if (page === 'history') History.render();
        if (page === 'filemanager') FileManager.render();
        if (page === 'stats') Stats.render();
        if (page === 'network') Network.render();
        if (page === 'shortcuts') Shortcuts.render();
        if (page === 'i18n') I18n.render();
        if (page === 'export') ExportImport.render();

        // 关闭移动端菜单
        document.getElementById('sidebar').classList.remove('open');
    },

    /**
     * 渲染设备列表
     */
    renderDeviceList() {
        const container = document.getElementById('device-list-container');
        if (!container) return;

        // 获取所有收藏设备
        const favDevices = Favorites.getAll();

        if (favDevices.length === 0 && !Device.isConnected) {
            container.innerHTML = '<div class="empty-hint"><p>暂无设备</p></div>';
            return;
        }

        container.innerHTML = '';

        // 当前连接的设备
        if (Device.isConnected) {
            const el = document.createElement('div');
            el.className = 'device-item';
            el.innerHTML = `
                <span class="device-icon">&#9742;</span>
                <div class="device-info">
                    <div class="device-name">${Device.currentHost}:${Device.currentPort}</div>
                    <div class="device-address">当前连接</div>
                </div>
                <div class="device-actions">
                    <span class="btn-fav ${Favorites.isFavorite(Device.currentHost) ? 'active' : ''}"
                          data-name="${Device.currentHost}" title="收藏">&#9733;</span>
                </div>
            `;
            container.appendChild(el);
        }

        // 收藏设备
        favDevices.forEach(item => {
            const el = document.createElement('div');
            el.className = 'device-item';
            el.innerHTML = `
                <span class="device-icon">&#9733;</span>
                <div class="device-info">
                    <div class="device-name">${item.alias || item.name}</div>
                    <div class="device-address">${item.host}:${item.port}</div>
                </div>
                <div class="device-actions">
                    <button class="btn-secondary btn-connect-device" data-host="${item.host}" data-port="${item.port}">连接</button>
                </div>
            `;

            el.querySelector('.btn-connect-device').addEventListener('click', (e) => {
                document.getElementById('input-host').value = e.target.dataset.host;
                document.getElementById('input-port').value = e.target.dataset.port;
                Device.connect();
                this.navigateTo('media');
            });

            container.appendChild(el);
        });
    },

    /**
     * 设置工具栏
     */
    setupToolbar() {
        // 搜索
        const searchInput = document.getElementById('search-input');
        searchInput.addEventListener('input', (e) => {
            this.searchQuery = e.target.value.trim();
            this.applyFilter();
        });

        // 筛选按钮
        document.querySelectorAll('.filter-btn').forEach(btn => {
            btn.addEventListener('click', (e) => {
                document.querySelectorAll('.filter-btn').forEach(b => b.classList.remove('active'));
                e.target.classList.add('active');
                this.typeFilter = e.target.dataset.filter;
                this.applyFilter();
            });
        });

        // 分组按钮
        document.getElementById('btn-group').addEventListener('click', (e) => {
            this.groupByFolder = !this.groupByFolder;
            e.target.classList.toggle('active', this.groupByFolder);
            this.applyFilter();
        });
    },

    /**
     * 应用过滤和渲染
     */
    applyFilter() {
        // 过滤
        let filtered = this.allMediaItems.filter(item => {
            const matchesQuery = !this.searchQuery ||
                (item.title && item.title.toLowerCase().includes(this.searchQuery.toLowerCase()));
            const matchesType = this.typeFilter === 'all' ||
                (this.typeFilter === 'video' && item.mimeType && item.mimeType.startsWith('video')) ||
                (this.typeFilter === 'audio' && item.mimeType && item.mimeType.startsWith('audio'));
            return matchesQuery && matchesType;
        });

        // 渲染
        const container = document.getElementById('media-container');
        const emptyHint = document.getElementById('empty-hint');

        if (filtered.length === 0) {
            container.innerHTML = '';
            container.appendChild(emptyHint);
            emptyHint.style.display = 'block';
            emptyHint.querySelector('p').textContent = this.allMediaItems.length === 0 ?
                '暂无媒体文件' : '无匹配结果';
            return;
        }

        emptyHint.style.display = 'none';

        if (this.groupByFolder) {
            this.renderGrouped(container, filtered);
        } else {
            this.renderFlat(container, filtered);
        }
    },

    /**
     * 渲染平铺视图
     */
    renderFlat(container, items) {
        container.innerHTML = '';
        const grid = this.createGrid(items);
        container.appendChild(grid);
    },

    /**
     * 渲染分组视图
     */
    renderGrouped(container, items) {
        container.innerHTML = '';

        // 按文件夹分组
        const grouped = {};
        items.forEach(item => {
            const folder = item.folderName || '未分类';
            if (!grouped[folder]) grouped[folder] = [];
            grouped[folder].push(item);
        });

        // 排序
        const sortedFolders = Object.keys(grouped).sort((a, b) => b.localeCompare(a));

        sortedFolders.forEach(folder => {
            const folderItems = grouped[folder];
            const collapsed = this.collapsedFolders.has(folder);

            // Header
            const header = document.createElement('div');
            header.className = `folder-header ${collapsed ? 'collapsed' : ''}`;
            header.innerHTML = `
                <span class="arrow">&#9660;</span>
                <span class="folder-name">${folder}</span>
                <span class="folder-count">(${folderItems.length})</span>
            `;
            header.addEventListener('click', () => {
                this.toggleFolder(folder);
            });
            container.appendChild(header);

            // 内容
            if (!collapsed) {
                const grid = this.createGrid(folderItems);
                container.appendChild(grid);
            }
        });
    },

    /**
     * 创建媒体网格
     */
    createGrid(items) {
        const grid = document.createElement('div');
        grid.className = 'media-grid';

        items.forEach(item => {
            const card = document.createElement('div');
            card.className = 'media-card';

            const thumbnailUrl = item.thumbnailUri || '';
            const isAudio = item.mimeType && item.mimeType.startsWith('audio');
            const showThumbs = Settings.get('showThumbnails');

            card.innerHTML = `
                <div class="thumbnail">
                    ${showThumbs && thumbnailUrl ?
                        `<img src="${thumbnailUrl}" alt="" onerror="this.parentElement.innerHTML='${isAudio ? '&#9835;' : '&#9654;'}'">` :
                        (isAudio ? '&#9835;' : '&#9654;')
                    }
                </div>
                <div class="card-info">
                    <div class="card-title" title="${item.title || ''}">${item.title || '未知标题'}</div>
                    <div class="card-meta">${this.formatSize(item.size)}</div>
                </div>
            `;

            card.addEventListener('click', () => {
                Player.play(item);
            });

            grid.appendChild(card);
        });

        return grid;
    },

    /**
     * 切换分组折叠
     */
    toggleFolder(folder) {
        if (this.collapsedFolders.has(folder)) {
            this.collapsedFolders.delete(folder);
        } else {
            this.collapsedFolders.add(folder);
        }
        this.applyFilter();
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
     * 设置深色模式
     */
    setupTheme() {
        const saved = localStorage.getItem('hmv_theme');
        if (saved === 'dark') {
            document.documentElement.setAttribute('data-theme', 'dark');
        }

        // 侧边栏主题按钮
        document.getElementById('btn-theme').addEventListener('click', () => {
            this.toggleTheme();
        });

        // 移动端主题按钮
        document.getElementById('btn-theme-mobile').addEventListener('click', () => {
            this.toggleTheme();
        });
    },

    /**
     * 切换主题
     */
    toggleTheme() {
        const isDark = document.documentElement.getAttribute('data-theme') === 'dark';
        if (isDark) {
            document.documentElement.removeAttribute('data-theme');
            localStorage.setItem('hmv_theme', 'light');
        } else {
            document.documentElement.setAttribute('data-theme', 'dark');
            localStorage.setItem('hmv_theme', 'dark');
        }
    },

    /**
     * 设置投屏弹窗
     */
    setupCastDialog() {
        const dialog = document.getElementById('cast-dialog');
        const castBtn = document.getElementById('btn-cast');
        const closeBtn = document.getElementById('btn-close-dialog');
        const sendBtn = document.getElementById('btn-send-cast');
        const statusEl = document.getElementById('cast-status');

        castBtn.addEventListener('click', () => {
            dialog.classList.remove('hidden');
        });

        closeBtn.addEventListener('click', () => {
            dialog.classList.add('hidden');
            statusEl.textContent = '';
            statusEl.className = 'cast-status';
        });

        dialog.addEventListener('click', (e) => {
            if (e.target === dialog) {
                dialog.classList.add('hidden');
            }
        });

        sendBtn.addEventListener('click', async () => {
            const host = document.getElementById('cast-host').value.trim();
            const port = parseInt(document.getElementById('cast-port').value);
            const media = Player.getCurrentMedia();

            if (!host || !port) {
                statusEl.textContent = '请输入目标设备 IP 和端口';
                statusEl.className = 'cast-status error';
                return;
            }

            if (!media) {
                statusEl.textContent = '请先播放一个媒体文件';
                statusEl.className = 'cast-status error';
                return;
            }

            try {
                const player = document.getElementById('video-player').classList.contains('active') ?
                    document.getElementById('video-player') :
                    document.getElementById('audio-player');

                await API.sendCastCommand(host, port, media.id, media.title || '', player.currentTime * 1000);
                statusEl.textContent = '投屏成功';
                statusEl.className = 'cast-status success';
                setTimeout(() => {
                    dialog.classList.add('hidden');
                    statusEl.textContent = '';
                }, 1500);
            } catch (e) {
                statusEl.textContent = '投屏失败: ' + e.message;
                statusEl.className = 'cast-status error';
            }
        });
    },

    /**
     * 设置移动端菜单
     */
    setupMobileMenu() {
        const menuBtn = document.getElementById('btn-menu');
        const sidebar = document.getElementById('sidebar');

        menuBtn.addEventListener('click', () => {
            sidebar.classList.toggle('open');
        });

        // 点击内容区关闭菜单
        document.querySelector('.main-content').addEventListener('click', () => {
            sidebar.classList.remove('open');
        });
    },

    /**
     * 显示 Toast 提示
     */
    showToast(message, duration = 2000) {
        const toast = document.createElement('div');
        toast.style.cssText = `
            position: fixed;
            bottom: 80px;
            left: 50%;
            transform: translateX(-50%);
            background: var(--bg-primary);
            color: var(--text-primary);
            padding: 10px 20px;
            border-radius: var(--radius);
            box-shadow: 0 4px 12px var(--shadow);
            z-index: 1000;
            font-size: 14px;
        `;
        toast.textContent = message;
        document.body.appendChild(toast);
        setTimeout(() => toast.remove(), duration);
    }
};

// 启动应用
document.addEventListener('DOMContentLoaded', () => App.init());
