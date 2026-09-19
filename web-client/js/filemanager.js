/**
 * 文件管理模块
 * 功能：浏览文件、上传、删除、重命名、新建文件夹
 */
const FileManager = {
    currentPath: '/',
    allItems: [],
    filteredItems: [],

    init() {
        this.setupBreadcrumb();
        document.getElementById('btn-upload').addEventListener('click', () => this.showUploadDialog());
        document.getElementById('btn-new-folder').addEventListener('click', () => this.showNewFolderDialog());
        document.getElementById('fm-search').addEventListener('input', (e) => this.filterItems(e.target.value));
    },

    /**
     * 加载媒体列表
     */
    async loadFiles() {
        if (!Device.isConnected) {
            this.allItems = [];
            this.renderList();
            return;
        }
        try {
            this.allItems = await API.getMediaList();
            this.filterItems(document.getElementById('fm-search')?.value || '');
        } catch (e) {
            console.error('加载文件列表失败:', e);
            this.allItems = [];
            this.renderList();
        }
    },

    /**
     * 筛选文件
     */
    filterItems(query) {
        if (!query) {
            this.filteredItems = [...this.allItems];
        } else {
            const q = query.toLowerCase();
            this.filteredItems = this.allItems.filter(item =>
                (item.title && item.title.toLowerCase().includes(q)) ||
                (item.path && item.path.toLowerCase().includes(q)) ||
                (item.mimeType && item.mimeType.toLowerCase().includes(q))
            );
        }
        this.renderList();
    },

    /**
     * 按路径分组获取文件
     */
    getFilesByPath(path) {
        return this.filteredItems.filter(item => {
            const itemPath = item.path || item.title || '';
            const dir = this.getDir(itemPath);
            return dir === path;
        });
    },

    /**
     * 获取目录部分
     */
    getDir(filePath) {
        const parts = filePath.split('/');
        parts.pop();
        return parts.length > 0 ? parts.join('/') + '/' : '/';
    },

    /**
     * 设置面包屑
     */
    setupBreadcrumb() {
        document.getElementById('fm-breadcrumb').addEventListener('click', (e) => {
            const item = e.target.closest('.breadcrumb-item');
            if (item) {
                this.navigateTo(item.dataset.path);
            }
        });
    },

    /**
     * 导航到路径
     */
    navigateTo(path) {
        this.currentPath = path;
        this.renderBreadcrumb();
        this.renderList();
    },

    /**
     * 渲染面包屑
     */
    renderBreadcrumb() {
        const breadcrumb = document.getElementById('fm-breadcrumb');
        breadcrumb.innerHTML = '';

        const root = document.createElement('span');
        root.className = 'breadcrumb-item' + (this.currentPath === '/' ? ' active' : '');
        root.dataset.path = '/';
        root.textContent = '根目录';
        breadcrumb.appendChild(root);

        if (this.currentPath !== '/') {
            const parts = this.currentPath.split('/').filter(Boolean);
            let fullPath = '';
            parts.forEach(part => {
                fullPath += '/' + part;
                const sep = document.createElement('span');
                sep.className = 'breadcrumb-sep';
                sep.textContent = ' / ';
                breadcrumb.appendChild(sep);

                const item = document.createElement('span');
                item.className = 'breadcrumb-item';
                item.dataset.path = fullPath + '/';
                item.textContent = part;
                breadcrumb.appendChild(item);
            });
        }
    },

    /**
     * 渲染文件列表
     */
    renderList() {
        const container = document.getElementById('filemanager-container');
        if (!container) return;

        const files = this.getFilesByPath(this.currentPath);

        if (files.length === 0) {
            container.innerHTML = '<div class="empty-hint"><p>当前目录为空</p></div>';
            return;
        }

        container.innerHTML = '';
        files.forEach(item => {
            const el = document.createElement('div');
            el.className = 'fm-item';
            const isAudio = item.mimeType && item.mimeType.startsWith('audio');
            const isVideo = item.mimeType && item.mimeType.startsWith('video');
            const sizeStr = this.formatSize(item.size);

            el.innerHTML = `
                <div class="fm-icon">${isAudio ? '&#9835;' : isVideo ? '&#9654;' : '&#128196;'}</div>
                <div class="fm-info">
                    <div class="fm-name">${item.title || '未知文件'}</div>
                    <div class="fm-meta">${sizeStr} | ${item.mimeType || '未知类型'}</div>
                </div>
                <div class="fm-actions">
                    <button class="btn-secondary btn-play-fm" data-id="${item.id}" title="播放">&#9654; 播放</button>
                    <button class="btn-icon btn-rename-fm" data-id="${item.id}" data-title="${item.title || ''}" title="重命名">&#9998;</button>
                    <button class="btn-icon btn-delete-fm" data-id="${item.id}" data-title="${item.title || ''}" title="删除">&times;</button>
                </div>
            `;

            el.querySelector('.btn-play-fm').addEventListener('click', (e) => {
                e.stopPropagation();
                Player.play(item);
            });

            el.querySelector('.btn-rename-fm').addEventListener('click', (e) => {
                e.stopPropagation();
                this.showRenameDialog(item);
            });

            el.querySelector('.btn-delete-fm').addEventListener('click', (e) => {
                e.stopPropagation();
                this.deleteFile(item);
            });

            el.addEventListener('click', () => Player.play(item));

            container.appendChild(el);
        });
    },

    /**
     * 显示上传弹窗
     */
    showUploadDialog() {
        const input = document.createElement('input');
        input.type = 'file';
        input.multiple = true;
        input.accept = 'video/*,audio/*';
        input.onchange = async (e) => {
            const files = e.target.files;
            if (!files.length) return;

            for (const file of files) {
                try {
                    App.showToast(`正在上传: ${file.name}`);
                    await API.uploadFile(file, this.currentPath);
                } catch (err) {
                    App.showToast(`上传失败: ${file.name}`);
                }
            }
            App.showToast('上传完成');
            this.loadFiles();
        };
        input.click();
    },

    /**
     * 显示新建文件夹弹窗
     */
    showNewFolderDialog() {
        const name = prompt('请输入文件夹名称');
        if (!name) return;

        API.createFolder(name, this.currentPath)
            .then(() => {
                App.showToast('文件夹已创建');
                this.loadFiles();
            })
            .catch(err => {
                App.showToast('创建失败: ' + err.message);
            });
    },

    /**
     * 显示重命名弹窗
     */
    showRenameDialog(item) {
        const newName = prompt('请输入新名称', item.title || '');
        if (!newName || newName === item.title) return;

        API.renameFile(item.id, newName)
            .then(() => {
                App.showToast('重命名成功');
                this.loadFiles();
            })
            .catch(err => {
                App.showToast('重命名失败: ' + err.message);
            });
    },

    /**
     * 删除文件
     */
    deleteFile(item) {
        if (!confirm(`确定要删除 "${item.title}" 吗？`)) return;

        API.deleteFile(item.id)
            .then(() => {
                App.showToast('已删除');
                this.loadFiles();
            })
            .catch(err => {
                App.showToast('删除失败: ' + err.message);
            });
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
     * 渲染
     */
    render() {
        this.renderBreadcrumb();
        this.loadFiles();
    }
};
