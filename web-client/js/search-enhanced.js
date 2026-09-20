/**
 * 搜索增强模块
 * 支持全局搜索、高级筛选、搜索历史
 */
const SearchEnhanced = {
    searchHistory: [],
    maxHistory: 20,

    init() {
        this.loadHistory();
        this.setupEventListeners();
    },

    setupEventListeners() {
        // 搜索输入框增强
        const searchInput = document.getElementById('search-input');
        if (searchInput) {
            searchInput.addEventListener('focus', () => this.showSearchPanel());
            searchInput.addEventListener('input', (e) => this.onSearchInput(e.target.value));
        }

        // 高级搜索按钮
        const advSearchBtn = document.getElementById('btn-advanced-search');
        if (advSearchBtn) {
            advSearchBtn.addEventListener('click', () => this.showAdvancedSearch());
        }

        // 搜索历史点击
        document.addEventListener('click', (e) => {
            if (e.target.classList.contains('search-history-item')) {
                const query = e.target.dataset.query;
                document.getElementById('search-input').value = query;
                this.performSearch(query);
            }
        });
    },

    loadHistory() {
        try {
            const data = localStorage.getItem('hmv_search_history');
            this.searchHistory = data ? JSON.parse(data) : [];
        } catch (e) {
            this.searchHistory = [];
        }
    },

    saveHistory() {
        localStorage.setItem('hmv_search_history', JSON.stringify(this.searchHistory));
    },

    addToHistory(query) {
        if (!query || query.length < 2) return;

        // 移除重复项
        this.searchHistory = this.searchHistory.filter(h => h !== query);
        
        // 添加到开头
        this.searchHistory.unshift(query);
        
        // 限制数量
        if (this.searchHistory.length > this.maxHistory) {
            this.searchHistory = this.searchHistory.slice(0, this.maxHistory);
        }
        
        this.saveHistory();
    },

    clearHistory() {
        this.searchHistory = [];
        this.saveHistory();
        this.hideSearchPanel();
    },

    showSearchPanel() {
        const panel = document.getElementById('search-panel');
        if (panel && this.searchHistory.length > 0) {
            panel.classList.remove('hidden');
            this.renderHistory();
        }
    },

    hideSearchPanel() {
        const panel = document.getElementById('search-panel');
        if (panel) {
            panel.classList.add('hidden');
        }
    },

    renderHistory() {
        const container = document.getElementById('search-history-list');
        if (!container) return;

        container.innerHTML = `
            <div class="search-history-header">
                <span>搜索历史</span>
                <button class="btn-clear-history" onclick="SearchEnhanced.clearHistory()">清空</button>
            </div>
            ${this.searchHistory.map(query => `
                <div class="search-history-item" data-query="${this.escapeHtml(query)}">
                    🕐 ${this.escapeHtml(query)}
                </div>
            `).join('')}
        `;
    },

    onSearchInput(value) {
        // 实时搜索建议
        if (value.length >= 2) {
            this.showSuggestions(value);
        } else {
            this.hideSuggestions();
        }
    },

    async showSuggestions(query) {
        try {
            const response = await fetch(`${API.baseUrl}/search?q=${encodeURIComponent(query)}&type=all`);
            if (response.ok) {
                const results = await response.json();
                this.renderSuggestions(results.slice(0, 5));
            }
        } catch (e) {
            console.error('搜索建议失败:', e);
        }
    },

    renderSuggestions(results) {
        const container = document.getElementById('search-suggestions');
        if (!container) return;

        if (results.length === 0) {
            container.classList.add('hidden');
            return;
        }

        container.classList.remove('hidden');
        container.innerHTML = results.map(item => `
            <div class="search-suggestion-item" onclick="SearchEnhanced.selectSuggestion('${item.id}', '${this.escapeHtml(item.title)}')">
                <span class="suggestion-icon">${item.mimeType && item.mimeType.startsWith('video') ? '🎬' : '🎵'}</span>
                <span class="suggestion-title">${this.escapeHtml(item.title)}</span>
                <span class="suggestion-folder">${this.escapeHtml(item.folderName || '')}</span>
            </div>
        `).join('');
    },

    hideSuggestions() {
        const container = document.getElementById('search-suggestions');
        if (container) {
            container.classList.add('hidden');
        }
    },

    selectSuggestion(id, title) {
        document.getElementById('search-input').value = title;
        this.hideSuggestions();
        this.hideSearchPanel();
        // 播放选中的媒体
        const item = App.allMediaItems.find(i => i.id === id);
        if (item) {
            Player.play(item);
        }
    },

    async performSearch(query, filters = {}) {
        if (!query && Object.keys(filters).length === 0) {
            App.applyFilter();
            return;
        }

        this.addToHistory(query);
        this.hideSearchPanel();

        try {
            const params = new URLSearchParams();
            if (query) params.set('q', query);
            if (filters.type) params.set('type', filters.type);
            if (filters.folder) params.set('folder', filters.folder);

            const response = await fetch(`${API.baseUrl}/search?${params.toString()}`);
            if (response.ok) {
                const results = await response.json();
                App.allMediaItems = results;
                App.applyFilter();
            }
        } catch (e) {
            console.error('搜索失败:', e);
        }
    },

    showAdvancedSearch() {
        const dialog = document.getElementById('advanced-search-dialog');
        if (dialog) {
            dialog.classList.remove('hidden');
        }
    },

    applyAdvancedSearch() {
        const query = document.getElementById('adv-search-query').value;
        const type = document.getElementById('adv-search-type').value;
        const folder = document.getElementById('adv-search-folder').value;
        const minSize = document.getElementById('adv-search-min-size').value;
        const maxSize = document.getElementById('adv-search-max-size').value;

        this.performSearch(query, { type, folder, minSize, maxSize });
        document.getElementById('advanced-search-dialog').classList.add('hidden');
    },

    escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }
};

window.SearchEnhanced = SearchEnhanced;
