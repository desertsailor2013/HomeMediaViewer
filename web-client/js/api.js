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
    }
};
