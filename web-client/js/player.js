/**
 * 播放器模块
 * 管理 HTML5 video/audio 播放器
 */
const Player = {
    videoEl: null,
    audioEl: null,
    panel: null,
    titleEl: null,
    speedLabel: null,
    currentMedia: null,
    speeds: [0.5, 0.75, 1.0, 1.25, 1.5, 2.0],
    speedIndex: 2,

    /**
     * 初始化播放器
     */
    init() {
        this.videoEl = document.getElementById('video-player');
        this.audioEl = document.getElementById('audio-player');
        this.panel = document.getElementById('player-panel');
        this.titleEl = document.getElementById('player-title');
        this.speedLabel = document.getElementById('player-speed');

        // 关闭播放器
        document.getElementById('btn-close-player').addEventListener('click', () => {
            this.hide();
        });

        // 速度切换
        this.speedLabel.addEventListener('click', () => this.cycleSpeed());

        // 播放结束时保存进度
        this.videoEl.addEventListener('ended', () => this.clearProgress());
        this.audioEl.addEventListener('ended', () => this.clearProgress());

        // 播放时记录进度
        this.videoEl.addEventListener('timeupdate', () => {
            this.saveProgress();
            this.updateHistoryProgress();
        });
        this.audioEl.addEventListener('timeupdate', () => {
            this.saveProgress();
            this.updateHistoryProgress();
        });

        // 加载保存的速度
        this.loadSpeed();
    },

    /**
     * 播放媒体
     */
    play(mediaItem) {
        this.currentMedia = mediaItem;
        this.titleEl.textContent = mediaItem.title || '未知标题';

        const url = API.getMediaUrl(mediaItem.id);
        const isAudio = mediaItem.mimeType && mediaItem.mimeType.startsWith('audio');

        if (isAudio) {
            this.videoEl.classList.remove('active');
            this.audioEl.classList.add('active');
            this.audioEl.src = url;
            this.audioEl.play();
        } else {
            this.audioEl.classList.remove('active');
            this.videoEl.classList.add('active');
            this.videoEl.src = url;
            this.videoEl.play();
        }

        this.show();

        // 恢复播放进度
        this.restoreProgress();

        // 记录播放历史
        if (typeof History !== 'undefined') {
            History.add(mediaItem);
        }
    },

    /**
     * 显示播放器
     */
    show() {
        this.panel.classList.remove('hidden');
    },

    /**
     * 隐藏播放器
     */
    hide() {
        this.panel.classList.add('hidden');
        this.videoEl.pause();
        this.audioEl.pause();
    },

    /**
     * 循环切换速度
     */
    cycleSpeed() {
        this.speedIndex = (this.speedIndex + 1) % this.speeds.length;
        const speed = this.speeds[this.speedIndex];
        this.videoEl.playbackRate = speed;
        this.audioEl.playbackRate = speed;
        this.speedLabel.textContent = `${speed}x`;
        this.saveSpeed();
    },

    /**
     * 保存速度偏好
     */
    saveSpeed() {
        localStorage.setItem('hmv_speed', this.speeds[this.speedIndex]);
    },

    /**
     * 加载速度偏好
     */
    loadSpeed() {
        const saved = parseFloat(localStorage.getItem('hmv_speed'));
        if (!isNaN(saved)) {
            this.speedIndex = this.speeds.indexOf(saved);
            if (this.speedIndex < 0) this.speedIndex = 2;
            const speed = this.speeds[this.speedIndex];
            this.videoEl.playbackRate = speed;
            this.audioEl.playbackRate = speed;
            this.speedLabel.textContent = `${speed}x`;
        }
    },

    /**
     * 保存播放进度
     */
    saveProgress() {
        if (!this.currentMedia) return;
        const player = this.videoEl.classList.contains('active') ? this.videoEl : this.audioEl;
        if (player.duration > 0) {
            const progress = {
                position: player.currentTime,
                duration: player.duration
            };
            localStorage.setItem(`hmv_progress_${this.currentMedia.id}`, JSON.stringify(progress));
        }
    },

    /**
     * 恢复播放进度
     */
    restoreProgress() {
        if (!this.currentMedia) return;
        const saved = localStorage.getItem(`hmv_progress_${this.currentMedia.id}`);
        if (saved) {
            try {
                const progress = JSON.parse(saved);
                const player = this.videoEl.classList.contains('active') ? this.videoEl : this.audioEl;
                if (progress.position && progress.duration) {
                    // 如果播放到 95% 以上，不恢复
                    if (progress.position / progress.duration < 0.95) {
                        player.currentTime = progress.position;
                    }
                }
            } catch (e) {}
        }
    },

    /**
     * 清除播放进度
     */
    clearProgress() {
        if (this.currentMedia) {
            localStorage.removeItem(`hmv_progress_${this.currentMedia.id}`);
        }
    },

    /**
     * 获取当前播放媒体
     */
    getCurrentMedia() {
        return this.currentMedia;
    },

    /**
     * 更新播放历史进度
     */
    updateHistoryProgress() {
        if (!this.currentMedia || typeof History === 'undefined') return;
        const player = this.videoEl.classList.contains('active') ? this.videoEl : this.audioEl;
        if (player.duration > 0) {
            History.updateProgress(this.currentMedia.id, player.currentTime, player.duration);
        }
    }
};
