/**
 * 数据导出/导入模块
 * 功能：导出/导入收藏、设置、队列、历史等偏好数据
 * 状态：界面待完成
 */
const ExportImport = {
    init() {
        document.getElementById('btn-export-data').addEventListener('click', () => this.exportData());
        document.getElementById('btn-import-data').addEventListener('click', () => this.importData());
        document.getElementById('btn-select-file').addEventListener('click', () => {
            document.getElementById('import-file').click();
        });

        const fileInput = document.getElementById('import-file');
        fileInput.addEventListener('change', (e) => this.handleFileSelect(e));

        // 拖拽上传
        const area = document.getElementById('import-area');
        area.addEventListener('dragover', (e) => {
            e.preventDefault();
            area.classList.add('drag-over');
        });
        area.addEventListener('dragleave', () => {
            area.classList.remove('drag-over');
        });
        area.addEventListener('drop', (e) => {
            e.preventDefault();
            area.classList.remove('drag-over');
            if (e.dataTransfer.files.length) {
                this.handleFile(e.dataTransfer.files[0]);
            }
        });
    },

    exportData() {
        const data = {};

        if (document.getElementById('export-favorites').checked) {
            data.favorites = Favorites.items || [];
        }
        if (document.getElementById('export-settings').checked) {
            data.settings = Settings.getAll ? Settings.getAll() : {};
        }
        if (document.getElementById('export-queue').checked) {
            data.queue = Queue.items || [];
        }
        if (document.getElementById('export-history').checked) {
            data.history = History.items || [];
        }
        if (document.getElementById('export-progress').checked) {
            data.progress = {};
            Object.keys(localStorage).forEach(key => {
                if (key.startsWith('hmv_progress_')) {
                    data.progress[key] = localStorage.getItem(key);
                }
            });
        }

        const json = JSON.stringify(data, null, 2);
        const blob = new Blob([json], { type: 'application/json' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `hmv-backup-${new Date().toISOString().slice(0, 10)}.json`;
        a.click();
        URL.revokeObjectURL(url);
        App.showToast('数据已导出');
    },

    handleFileSelect(e) {
        if (e.target.files.length) {
            this.handleFile(e.target.files[0]);
        }
    },

    handleFile(file) {
        if (!file.name.endsWith('.json')) {
            App.showToast('请选择 JSON 文件');
            return;
        }
        this.pendingFile = file;
        document.getElementById('btn-import-data').disabled = false;
        App.showToast(`已选择: ${file.name}`);
    },

    importData() {
        if (!this.pendingFile) return;

        const reader = new FileReader();
        reader.onload = (e) => {
            try {
                const data = JSON.parse(e.target.result);

                if (data.favorites) {
                    Favorites.items = data.favorites;
                    Favorites.saveToStorage();
                }
                if (data.settings) {
                    localStorage.setItem('hmv_settings', JSON.stringify(data.settings));
                }
                if (data.queue) {
                    Queue.items = data.queue;
                    Queue.saveToStorage();
                }
                if (data.history) {
                    History.items = data.history;
                    History.saveToStorage();
                }
                if (data.progress) {
                    Object.entries(data.progress).forEach(([key, value]) => {
                        localStorage.setItem(key, value);
                    });
                }

                App.showToast('数据已导入');
                this.pendingFile = null;
                document.getElementById('btn-import-data').disabled = true;
                document.getElementById('import-file').value = '';
            } catch (err) {
                App.showToast('导入失败: 文件格式错误');
            }
        };
        reader.readAsText(this.pendingFile);
    },

    render() {
        // 页面已静态渲染
    }
};
