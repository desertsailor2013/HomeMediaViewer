/**
 * 媒体元数据模块
 * 显示媒体详细信息（分辨率/码率/时长等）
 */
const MediaMetadata = {
    cache: {},

    init() {
        this.setupEventListeners();
    },

    setupEventListeners() {
        // 媒体详情按钮
        document.addEventListener('click', (e) => {
            if (e.target.classList.contains('btn-media-info')) {
                const mediaId = e.target.dataset.id;
                this.showMediaInfo(mediaId);
            }
        });
    },

    async loadMetadata(mediaId) {
        if (this.cache[mediaId]) {
            return this.cache[mediaId];
        }

        try {
            const response = await fetch(`${API.baseUrl}/media/${mediaId}/metadata`);
            if (response.ok) {
                const metadata = await response.json();
                this.cache[mediaId] = metadata;
                return metadata;
            }
        } catch (e) {
            console.error('加载元数据失败:', e);
        }
        return null;
    },

    async showMediaInfo(mediaId) {
        const metadata = await this.loadMetadata(mediaId);
        if (!metadata) {
            App.showToast('无法加载媒体信息');
            return;
        }

        this.renderMediaInfoDialog(metadata);
    },

    renderMediaInfoDialog(metadata) {
        const dialog = document.getElementById('media-info-dialog');
        if (!dialog) return;

        const formatDuration = (ms) => {
            const seconds = Math.floor(ms / 1000);
            const minutes = Math.floor(seconds / 60);
            const hours = Math.floor(minutes / 60);
            
            if (hours > 0) {
                return `${hours}:${(minutes % 60).toString().padStart(2, '0')}:${(seconds % 60).toString().padStart(2, '0')}`;
            }
            return `${minutes}:${(seconds % 60).toString().padStart(2, '0')}`;
        };

        const formatBitrate = (bps) => {
            if (bps >= 1000000) return `${(bps / 1000000).toFixed(1)} Mbps`;
            if (bps >= 1000) return `${(bps / 1000).toFixed(1)} Kbps`;
            return `${bps} bps`;
        };

        const formatDate = (ts) => {
            if (!ts) return '-';
            return new Date(ts).toLocaleString('zh-CN');
        };

        dialog.innerHTML = `
            <div class="dialog-content">
                <div class="dialog-header">
                    <h3>媒体信息</h3>
                    <button class="btn-close" onclick="document.getElementById('media-info-dialog').classList.add('hidden')">&times;</button>
                </div>
                <div class="dialog-body">
                    ${metadata.posterUrl ? `
                        <div class="media-poster">
                            <img src="${metadata.posterUrl}" alt="${this.escapeHtml(metadata.title)}">
                        </div>
                    ` : ''}
                    
                    <div class="info-grid">
                        <div class="info-item">
                            <label>标题</label>
                            <span>${this.escapeHtml(metadata.title || '-')}</span>
                        </div>
                        ${metadata.artist ? `
                            <div class="info-item">
                                <label>艺术家</label>
                                <span>${this.escapeHtml(metadata.artist)}</span>
                            </div>
                        ` : ''}
                        ${metadata.album ? `
                            <div class="info-item">
                                <label>专辑</label>
                                <span>${this.escapeHtml(metadata.album)}</span>
                            </div>
                        ` : ''}
                        ${metadata.duration > 0 ? `
                            <div class="info-item">
                                <label>时长</label>
                                <span>${formatDuration(metadata.duration)}</span>
                            </div>
                        ` : ''}
                        ${metadata.width > 0 ? `
                            <div class="info-item">
                                <label>分辨率</label>
                                <span>${metadata.width} × ${metadata.height}</span>
                            </div>
                        ` : ''}
                        ${metadata.bitrate > 0 ? `
                            <div class="info-item">
                                <label>码率</label>
                                <span>${formatBitrate(metadata.bitrate)}</span>
                            </div>
                        ` : ''}
                        ${metadata.codec ? `
                            <div class="info-item">
                                <label>编码</label>
                                <span>${this.escapeHtml(metadata.codec)}</span>
                            </div>
                        ` : ''}
                        ${metadata.format ? `
                            <div class="info-item">
                                <label>格式</label>
                                <span>${this.escapeHtml(metadata.format)}</span>
                            </div>
                        ` : ''}
                        <div class="info-item">
                            <label>添加时间</label>
                            <span>${formatDate(metadata.addedAt)}</span>
                        </div>
                        <div class="info-item">
                            <label>最后播放</label>
                            <span>${formatDate(metadata.lastPlayed)}</span>
                        </div>
                        <div class="info-item">
                            <label>播放次数</label>
                            <span>${metadata.playCount}</span>
                        </div>
                    </div>

                    ${metadata.tags && metadata.tags.length > 0 ? `
                        <div class="info-tags">
                            <label>标签</label>
                            <div class="tags-list">
                                ${metadata.tags.map(tag => `
                                    <span class="tag">${this.escapeHtml(tag)}</span>
                                `).join('')}
                            </div>
                        </div>
                    ` : ''}

                    ${metadata.subtitleUrls && metadata.subtitleUrls.length > 0 ? `
                        <div class="info-subtitles">
                            <label>字幕</label>
                            <div class="subtitle-list">
                                ${metadata.subtitleUrls.map(url => `
                                    <div class="subtitle-item">
                                        📄 ${this.escapeHtml(url.split('/').pop())}
                                    </div>
                                `).join('')}
                            </div>
                        </div>
                    ` : ''}
                </div>
            </div>
        `;

        dialog.classList.remove('hidden');
    },

    escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }
};

window.MediaMetadata = MediaMetadata;
