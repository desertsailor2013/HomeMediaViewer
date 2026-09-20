/**
 * API 封装层
 * 与 core-server HTTP API 通信
 */
const API = {
    baseUrl: '',

    /**
     * 设置服务器地址
     */
    setServer(host, port) {
        this.baseUrl = `http://${host}:${port}`;
    },

    /**
     * 获取媒体列表
     */
    async getMediaList() {
        const response = await fetch(`${this.baseUrl}/media`);
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        return await response.json();
    },

    /**
     * 获取媒体 URL
     */
    getMediaUrl(mediaId) {
        return `${this.baseUrl}/media/${mediaId}`;
    },

    /**
     * 获取缩略图 URL
     */
    getThumbnailUrl(mediaId) {
        return `${this.baseUrl}/media/${mediaId}/thumbnail`;
    },

    /**
     * 发送投屏指令
     */
    async sendCastCommand(targetHost, targetPort, mediaId, title, position = 0) {
        const url = `http://${targetHost}:${targetPort}/play`;
        const body = JSON.stringify({ mediaId, title, position });
        const response = await fetch(url, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body
        });
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        return await response.json();
    },

    /**
     * 测试连接
     */
    async testConnection(host, port) {
        const url = `http://${host}:${port}/media`;
        const response = await fetch(url, { method: 'HEAD' });
        return response.ok;
    },

    // ========== 文件操作 API（需后端支持） ==========

    /**
     * 上传文件
     */
    async uploadFile(file, path = '/') {
        const formData = new FormData();
        formData.append('file', file);
        formData.append('path', path);
        const response = await fetch(`${this.baseUrl}/upload`, {
            method: 'POST',
            body: formData
        });
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        return await response.json();
    },

    /**
     * 删除文件
     */
    async deleteFile(mediaId) {
        const response = await fetch(`${this.baseUrl}/media/${mediaId}`, {
            method: 'DELETE'
        });
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        return await response.json();
    },

    /**
     * 重命名文件
     */
    async renameFile(mediaId, newName) {
        const response = await fetch(`${this.baseUrl}/media/${mediaId}/rename`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ name: newName })
        });
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        return await response.json();
    },

    /**
     * 新建文件夹
     */
    async createFolder(name, parentPath = '/') {
        const response = await fetch(`${this.baseUrl}/folder`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ name, path: parentPath })
        });
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        return await response.json();
    }
};
