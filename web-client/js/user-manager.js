/**
 * 用户权限管理模块
 */
const UserManager = {
    currentUser: null,
    users: [],

    init() {
        this.loadCurrentUser();
        this.setupEventListeners();
    },

    setupEventListeners() {
        // 用户管理按钮
        const manageBtn = document.getElementById('btn-manage-users');
        if (manageBtn) {
            manageBtn.addEventListener('click', () => this.showUserDialog());
        }

        // 登录按钮
        const loginBtn = document.getElementById('btn-user-login');
        if (loginBtn) {
            loginBtn.addEventListener('click', () => this.showLoginDialog());
        }

        // 添加用户
        const addBtn = document.getElementById('btn-add-user');
        if (addBtn) {
            addBtn.addEventListener('click', () => this.addUser());
        }

        // 登录提交
        const submitLoginBtn = document.getElementById('btn-submit-login');
        if (submitLoginBtn) {
            submitLoginBtn.addEventListener('click', () => this.login());
        }
    },

    loadCurrentUser() {
        try {
            const data = localStorage.getItem('hmv_current_user');
            this.currentUser = data ? JSON.parse(data) : null;
        } catch (e) {
            this.currentUser = null;
        }
        this.updateUI();
    },

    saveCurrentUser() {
        if (this.currentUser) {
            localStorage.setItem('hmv_current_user', JSON.stringify(this.currentUser));
        } else {
            localStorage.removeItem('hmv_current_user');
        }
    },

    updateUI() {
        const userBtn = document.getElementById('btn-user-login');
        if (userBtn) {
            if (this.currentUser) {
                userBtn.textContent = `${this.currentUser.username} (${this.getRoleName(this.currentUser.role)})`;
            } else {
                userBtn.textContent = '游客';
            }
        }
    },

    getRoleName(role) {
        const names = {
            'admin': '管理员',
            'editor': '编辑者',
            'viewer': '访客'
        };
        return names[role] || role;
    },

    showLoginDialog() {
        const dialog = document.getElementById('login-dialog');
        if (dialog) {
            dialog.classList.remove('hidden');
        }
    },

    showUserDialog() {
        if (!this.isAdmin()) {
            App.showToast('需要管理员权限');
            return;
        }
        
        const dialog = document.getElementById('user-dialog');
        if (dialog) {
            dialog.classList.remove('hidden');
            this.loadUsers();
        }
    },

    async loadUsers() {
        try {
            const response = await fetch(`${API.baseUrl}/users`);
            if (response.ok) {
                this.users = await response.json();
                this.renderUsers();
            }
        } catch (e) {
            console.error('加载用户列表失败:', e);
        }
    },

    renderUsers() {
        const container = document.getElementById('user-list-container');
        if (!container) return;

        container.innerHTML = this.users.map(user => `
            <div class="user-item">
                <div class="user-info">
                    <span class="user-name">${this.escapeHtml(user.username)}</span>
                    <span class="user-role badge-${user.role}">${this.getRoleName(user.role)}</span>
                </div>
                <div class="user-actions">
                    <select class="role-select" data-username="${user.username}">
                        <option value="admin" ${user.role === 'admin' ? 'selected' : ''}>管理员</option>
                        <option value="editor" ${user.role === 'editor' ? 'selected' : ''}>编辑者</option>
                        <option value="viewer" ${user.role === 'viewer' ? 'selected' : ''}>访客</option>
                    </select>
                    <button class="btn-danger btn-delete-user" data-username="${user.username}">&times;</button>
                </div>
            </div>
        `).join('');

        // 绑定事件
        container.querySelectorAll('.role-select').forEach(select => {
            select.addEventListener('change', (e) => {
                this.updateUserRole(e.target.dataset.username, e.target.value);
            });
        });

        container.querySelectorAll('.btn-delete-user').forEach(btn => {
            btn.addEventListener('click', (e) => {
                this.deleteUser(e.target.dataset.username);
            });
        });
    },

    async login() {
        const username = document.getElementById('login-username').value;
        const password = document.getElementById('login-password').value;

        if (!username || !password) {
            App.showToast('请输入用户名和密码');
            return;
        }

        try {
            const response = await fetch(`${API.baseUrl}/users/login`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ username, password })
            });

            const result = await response.json();

            if (result.status === 'ok') {
                this.currentUser = {
                    username: result.username,
                    role: result.role
                };
                this.saveCurrentUser();
                this.updateUI();
                document.getElementById('login-dialog').classList.add('hidden');
                App.showToast('登录成功');
            } else {
                App.showToast('登录失败: ' + (result.message || '用户名或密码错误'));
            }
        } catch (e) {
            App.showToast('登录失败');
        }
    },

    logout() {
        this.currentUser = null;
        this.saveCurrentUser();
        this.updateUI();
        App.showToast('已退出登录');
    },

    async addUser() {
        const username = document.getElementById('new-username').value;
        const password = document.getElementById('new-password').value;
        const role = document.getElementById('new-role').value;

        if (!username || !password) {
            App.showToast('请输入用户名和密码');
            return;
        }

        try {
            const response = await fetch(`${API.baseUrl}/users`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ username, password, role })
            });

            const result = await response.json();

            if (result.status === 'ok') {
                App.showToast('用户已添加');
                this.loadUsers();
                document.getElementById('new-username').value = '';
                document.getElementById('new-password').value = '';
            } else {
                App.showToast('添加失败: ' + (result.message || '未知错误'));
            }
        } catch (e) {
            App.showToast('添加失败');
        }
    },

    async updateUserRole(username, role) {
        try {
            const response = await fetch(`${API.baseUrl}/users/${username}`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ role })
            });

            if (response.ok) {
                App.showToast('角色已更新');
            }
        } catch (e) {
            App.showToast('更新失败');
        }
    },

    async deleteUser(username) {
        if (!confirm(`确定要删除用户 ${username} 吗？`)) return;

        try {
            const response = await fetch(`${API.baseUrl}/users/${username}`, {
                method: 'DELETE'
            });

            if (response.ok) {
                App.showToast('用户已删除');
                this.loadUsers();
            }
        } catch (e) {
            App.showToast('删除失败');
        }
    },

    isAdmin() {
        return this.currentUser && this.currentUser.role === 'admin';
    },

    canEdit() {
        return this.currentUser && (this.currentUser.role === 'admin' || this.currentUser.role === 'editor');
    },

    escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }
};

window.UserManager = UserManager;
