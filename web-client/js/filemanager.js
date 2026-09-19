/**
 * 文件管理模块
 * 功能：上传、删除、重命名文件，浏览文件夹
 * 状态：界面待完成，需后端 API 支持
 */
const FileManager = {
    currentPath: '/',

    init() {
        this.setupBreadcrumb();
        document.getElementById('btn-upload').addEventListener('click', () => this.showUploadDialog());
        document.getElementById('btn-new-folder').addEventListener('click', () => this.showNewFolderDialog());
    },

    setupBreadcrumb() {
        const breadcrumb = document.getElementById('fm-breadcrumb');
        breadcrumb.addEventListener('click', (e) => {
            const item = e.target.closest('.breadcrumb-item');
            if (item) {
                this.navigateTo(item.dataset.path);
            }
        });
    },

    navigateTo(path) {
        this.currentPath = path;
        this.renderBreadcrumb();
        // TODO: 加载目录内容
    },

    renderBreadcrumb() {
        const breadcrumb = document.getElementById('fm-breadcrumb');
        breadcrumb.innerHTML = '';
        const parts = this.currentPath.split('/').filter(Boolean);
        let fullPath = '';

        const root = document.createElement('span');
        root.className = 'breadcrumb-item';
        root.dataset.path = '/';
        root.textContent = '根目录';
        breadcrumb.appendChild(root);

        parts.forEach(part => {
            fullPath += '/' + part;
            const sep = document.createElement('span');
            sep.className = 'breadcrumb-sep';
            sep.textContent = ' / ';
            breadcrumb.appendChild(sep);

            const item = document.createElement('span');
            item.className = 'breadcrumb-item';
            item.dataset.path = fullPath;
            item.textContent = part;
            breadcrumb.appendChild(item);
        });
    },

    showUploadDialog() {
        // TODO: 实现上传弹窗
        App.showToast('上传功能待实现');
    },

    showNewFolderDialog() {
        // TODO: 实现新建文件夹弹窗
        App.showToast('新建文件夹功能待实现');
    },

    render() {
        const container = document.getElementById('filemanager-container');
        if (!container) return;
        // TODO: 渲染文件列表
        container.innerHTML = '<div class="empty-hint"><p>文件管理功能待实现</p></div>';
    }
};
