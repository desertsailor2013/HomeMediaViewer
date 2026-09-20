/**
 * 移动端APP检测与深链接模块
 * 检测移动端APP是否已安装，提示安装或打开
 */
const MobileApp = {
    // URL Scheme 配置
    scheme: 'homemedia',
    
    // 应用信息
    appInfo: {
        name: 'HomeMediaViewer',
        packageName: 'com.hmv.app',
        iosBundleId: 'com.hmv.app',
        // 应用商店链接
        playStoreUrl: 'https://play.google.com/store/apps/details?id=com.hmv.app',
        appStoreUrl: 'https://apps.apple.com/app/homemediaviewer/id1234567890',
        // 官网下载页
        downloadUrl: 'https://github.com/desertsailor2013/HomeMediaViewer/releases'
    },

    /**
     * 初始化
     */
    init() {
        // 只在移动端显示
        if (Environment.isPC) return;

        this.showAppBanner();
    },

    /**
     * 显示APP安装横幅
     */
    showAppBanner() {
        // 检查是否已关闭过
        if (localStorage.getItem('hmv_app_banner_closed') === 'true') return;

        const banner = document.createElement('div');
        banner.className = 'mobile-app-banner';
        banner.innerHTML = `
            <div class="app-banner-content">
                <div class="app-banner-icon">&#128241;</div>
                <div class="app-banner-info">
                    <div class="app-banner-title">使用原生APP获得更好体验</div>
                    <div class="app-banner-desc">支持后台播放、离线缓存、投屏控制</div>
                </div>
                <div class="app-banner-actions">
                    <button class="btn-open-app" id="btn-open-app">打开APP</button>
                    <button class="btn-install-app" id="btn-install-app">安装</button>
                    <button class="btn-close-banner" id="btn-close-banner">&times;</button>
                </div>
            </div>
        `;

        document.body.appendChild(banner);
        this.setupBannerEvents(banner);
    },

    /**
     * 设置横幅事件
     */
    setupBannerEvents(banner) {
        const openBtn = banner.querySelector('#btn-open-app');
        const installBtn = banner.querySelector('#btn-install-app');
        const closeBtn = banner.querySelector('#btn-close-banner');

        openBtn.addEventListener('click', () => {
            this.openApp();
        });

        installBtn.addEventListener('click', () => {
            this.installApp();
        });

        closeBtn.addEventListener('click', () => {
            banner.remove();
            localStorage.setItem('hmv_app_banner_closed', 'true');
        });
    },

    /**
     * 打开APP
     */
    openApp() {
        const deepLink = this.buildDeepLink();
        
        // 尝试打开APP
        window.location.href = deepLink;

        // 监听页面可见性变化，判断APP是否成功打开
        const startTime = Date.now();
        const handler = () => {
            if (document.hidden || Date.now() - startTime > 3000) {
                document.removeEventListener('visibilitychange', handler);
                return;
            }
        };
        document.addEventListener('visibilitychange', handler);

        // 如果超过2秒APP没有打开，可能未安装
        setTimeout(() => {
            if (!document.hidden) {
                this.showInstallPrompt();
            }
        }, 2500);
    },

    /**
     * 构建深链接URL
     */
    buildDeepLink() {
        const params = new URLSearchParams();
        
        // 传递当前连接的服务器信息
        if (Device.isConnected) {
            params.set('host', Device.currentHost);
            params.set('port', Device.currentPort);
        }

        // 传递当前页面
        params.set('page', App.currentPage || 'media');

        return `${this.scheme}://open?${params.toString()}`;
    },

    /**
     * 安装APP
     */
    installApp() {
        const platform = this.detectPlatform();
        
        if (platform === 'android') {
            window.open(this.appInfo.playStoreUrl, '_blank');
        } else if (platform === 'ios') {
            window.open(this.appInfo.appStoreUrl, '_blank');
        } else {
            window.open(this.appInfo.downloadUrl, '_blank');
        }
    },

    /**
     * 显示安装提示弹窗
     */
    showInstallPrompt() {
        const modal = document.createElement('div');
        modal.className = 'modal-overlay';
        modal.innerHTML = `
            <div class="modal-content app-install-modal">
                <div class="modal-header">
                    <h3>APP未安装</h3>
                    <button class="modal-close">&times;</button>
                </div>
                <div class="modal-body">
                    <div class="app-install-icon">&#128241;</div>
                    <p>检测到您正在使用移动设备访问</p>
                    <p>安装原生APP可获得更好的体验：</p>
                    <ul>
                        <li>后台播放 - 切换应用不中断</li>
                        <li>离线缓存 - 随时随地观看</li>
                        <li>投屏控制 - 大屏观看</li>
                        <li>推送通知 - 新媒体提醒</li>
                    </ul>
                </div>
                <div class="modal-footer">
                    <button class="btn-secondary btn-cancel">稍后再说</button>
                    <button class="btn-primary btn-install">立即安装</button>
                </div>
            </div>
        `;

        document.body.appendChild(modal);

        // 绑定事件
        modal.querySelector('.modal-close').addEventListener('click', () => {
            modal.remove();
        });

        modal.querySelector('.btn-cancel').addEventListener('click', () => {
            modal.remove();
        });

        modal.querySelector('.btn-install').addEventListener('click', () => {
            this.installApp();
            modal.remove();
        });

        // 点击遮罩关闭
        modal.addEventListener('click', (e) => {
            if (e.target === modal) {
                modal.remove();
            }
        });
    },

    /**
     * 检测移动平台
     */
    detectPlatform() {
        const userAgent = navigator.userAgent.toLowerCase();
        
        if (userAgent.includes('android')) {
            return 'android';
        } else if (userAgent.includes('iphone') || userAgent.includes('ipad') || userAgent.includes('ipod')) {
            return 'ios';
        }
        return 'unknown';
    },

    /**
     * 通过Universal Links检测APP是否安装
     * 注意：这需要服务器配置apple-app-site-association文件
     */
    async checkAppInstalled() {
        return new Promise((resolve) => {
            const timeout = 2000;
            const startTime = Date.now();

            // 创建隐藏的iframe
            const iframe = document.createElement('iframe');
            iframe.style.display = 'none';
            iframe.src = `${this.appInfo.universalLink || 'https://homemedia.app'}/app-check`;

            document.body.appendChild(iframe);

            // 监听加载失败（可能表示APP未安装）
            iframe.onload = () => {
                // 如果能加载，可能APP已安装
                clearTimeout(timer);
                document.body.removeChild(iframe);
                resolve(true);
            };

            iframe.onerror = () => {
                // 加载失败，APP可能未安装
                clearTimeout(timer);
                document.body.removeChild(iframe);
                resolve(false);
            };

            const timer = setTimeout(() => {
                // 超时，APP可能未安装
                try {
                    document.body.removeChild(iframe);
                } catch (e) {}
                resolve(false);
            }, timeout);
        });
    },

    /**
     * 分享功能
     */
    share(data) {
        if (navigator.share) {
            navigator.share({
                title: data.title || 'HomeMediaViewer',
                text: data.text || '查看媒体内容',
                url: data.url || window.location.href
            }).catch(console.error);
        } else {
            // 复制链接到剪贴板
            this.copyToClipboard(data.url || window.location.href);
            App.showToast('链接已复制到剪贴板');
        }
    },

    /**
     * 复制到剪贴板
     */
    async copyToClipboard(text) {
        try {
            await navigator.clipboard.writeText(text);
        } catch (e) {
            // 降级方案
            const textarea = document.createElement('textarea');
            textarea.value = text;
            textarea.style.position = 'fixed';
            textarea.style.left = '-9999px';
            document.body.appendChild(textarea);
            textarea.select();
            try {
                document.execCommand('copy');
            } catch (err) {
                console.error('复制失败:', err);
            }
            document.body.removeChild(textarea);
        }
    }
};

// 在环境检测后初始化
document.addEventListener('DOMContentLoaded', () => {
    // 延迟初始化，等待Environment模块
    setTimeout(() => {
        MobileApp.init();
    }, 100);
});
