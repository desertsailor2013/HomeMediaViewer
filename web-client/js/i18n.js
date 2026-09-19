/**
 * 多语言模块
 * 功能：中/英/日/韩语言切换
 * 状态：界面待完成，实际翻译字典待补充
 */
const I18n = {
    currentLang: 'zh-CN',

    translations: {
        'zh-CN': {
            'nav.media': '媒体库',
            'nav.devices': '设备列表',
            'nav.favorites': '收藏设备',
            'nav.queue': '播放队列',
            'nav.history': '播放历史',
            'nav.filemanager': '文件管理',
            'nav.stats': '统计面板',
            'nav.network': '网络诊断',
            'nav.shortcuts': '快捷键',
            'nav.i18n': '多语言',
            'nav.export': '数据导出',
            'nav.settings': '设置',
            'nav.about': '关于',
            'common.connect': '连接',
            'common.search': '搜索',
            'common.clear': '清空',
            'common.save': '保存',
            'common.cancel': '取消',
            'common.delete': '删除',
            'common.upload': '上传',
            'common.export': '导出',
            'common.import': '导入',
            'player.play': '播放',
            'player.pause': '暂停',
            'player.close': '关闭',
            'player.speed': '速度',
            'player.cast': '投屏'
        },
        'en': {
            'nav.media': 'Media',
            'nav.devices': 'Devices',
            'nav.favorites': 'Favorites',
            'nav.queue': 'Queue',
            'nav.history': 'History',
            'nav.filemanager': 'Files',
            'nav.stats': 'Statistics',
            'nav.network': 'Network',
            'nav.shortcuts': 'Shortcuts',
            'nav.i18n': 'Language',
            'nav.export': 'Export',
            'nav.settings': 'Settings',
            'nav.about': 'About',
            'common.connect': 'Connect',
            'common.search': 'Search',
            'common.clear': 'Clear',
            'common.save': 'Save',
            'common.cancel': 'Cancel',
            'common.delete': 'Delete',
            'common.upload': 'Upload',
            'common.export': 'Export',
            'common.import': 'Import',
            'player.play': 'Play',
            'player.pause': 'Pause',
            'player.close': 'Close',
            'player.speed': 'Speed',
            'player.cast': 'Cast'
        },
        'ja': {
            'nav.media': 'メディア',
            'nav.devices': 'デバイス',
            'nav.favorites': 'お気に入り',
            'nav.queue': 'キュー',
            'nav.history': '履歴',
            'nav.filemanager': 'ファイル',
            'nav.stats': '統計',
            'nav.network': 'ネットワーク',
            'nav.shortcuts': 'ショートカット',
            'nav.i18n': '言語',
            'nav.export': 'エクスポート',
            'nav.settings': '設定',
            'nav.about': 'について'
        },
        'ko': {
            'nav.media': '미디어',
            'nav.devices': '기기',
            'nav.favorites': '즐겨찾기',
            'nav.queue': '큐',
            'nav.history': '기록',
            'nav.filemanager': '파일',
            'nav.stats': '통계',
            'nav.network': '네트워크',
            'nav.shortcuts': '단축키',
            'nav.i18n': '언어',
            'nav.export': '내보내기',
            'nav.settings': '설정',
            'nav.about': '정보'
        }
    },

    init() {
        this.currentLang = localStorage.getItem('hmv_lang') || 'zh-CN';
        this.setupLanguageSelector();
    },

    setupLanguageSelector() {
        document.querySelectorAll('.lang-option').forEach(el => {
            el.addEventListener('click', () => {
                const lang = el.dataset.lang;
                this.setLanguage(lang);
            });
        });
    },

    setLanguage(lang) {
        this.currentLang = lang;
        localStorage.setItem('hmv_lang', lang);

        // 更新 UI 选中状态
        document.querySelectorAll('.lang-option').forEach(el => {
            const isActive = el.dataset.lang === lang;
            el.classList.toggle('active', isActive);
            el.querySelector('.lang-check').textContent = isActive ? '\u2713' : '';
        });

        // 更新当前语言显示
        const names = { 'zh-CN': '简体中文', 'en': 'English', 'ja': '日本語', 'ko': '한국어' };
        document.getElementById('i18n-current-lang').textContent = names[lang] || lang;

        // TODO: 应用翻译到所有 UI 元素
        App.showToast(`语言已切换为 ${names[lang] || lang}`);
    },

    t(key) {
        const dict = this.translations[this.currentLang] || this.translations['zh-CN'];
        return dict[key] || key;
    },

    render() {
        // 更新当前语言显示
        const names = { 'zh-CN': '简体中文', 'en': 'English', 'ja': '日本語', 'ko': '한국어' };
        document.getElementById('i18n-current-lang').textContent = names[this.currentLang] || this.currentLang;

        document.querySelectorAll('.lang-option').forEach(el => {
            const isActive = el.dataset.lang === this.currentLang;
            el.classList.toggle('active', isActive);
            el.querySelector('.lang-check').textContent = isActive ? '\u2713' : '';
        });
    }
};
