/**
 * Nexus Campus - Main entry point
 * Shared utilities, API client, loading overlay
 */
(function() {
    "use strict";

    const API_BASE = "/api/v1";
    const TOKEN_KEY = "nexus_token";
    const USER_KEY = "nexus_user";

    // ── Auth primitives ──────────────────────────────────────────
    window.getToken = function() { return localStorage.getItem(TOKEN_KEY); };

    window.getUser = function() {
        const data = localStorage.getItem(USER_KEY);
        return data ? JSON.parse(data) : null;
    };

    window.setAuth = function(token, user) {
        localStorage.setItem(TOKEN_KEY, token);
        localStorage.setItem(USER_KEY, JSON.stringify(user));
        updateNavbar();
    };

    window.logout = function() {
    if (typeof window.showToast === 'function') window.showToast('Disconnected from Nexus.', 'info');
    localStorage.removeItem('nexus_token');
    localStorage.removeItem('nexus_user');
    updateNavbar();
    window.location.href = '/login';
};

    window.clearAuth = function() {
        localStorage.removeItem(TOKEN_KEY);
        localStorage.removeItem(USER_KEY);
        updateNavbar();
        window.location.href = "/login";
    };

    window.isAuthenticated = function() { return !!window.getToken(); };

    // ── Utilities ───────────────────────────────────────────────
    window.formatTime = function(dateStr) {
        const d = new Date(dateStr);
        const now = new Date();
        const diff = now - d;
        const mins = Math.floor(diff / 60000);
        const hours = Math.floor(diff / 3600000);
        const days = Math.floor(diff / 86400000);
        if (mins < 1) return "just now";
        if (mins < 60) return mins + "m ago";
        if (hours < 24) return hours + "h ago";
        if (days < 7) return days + "d ago";
        return d.toLocaleDateString("en-US", { month: "short", day: "numeric", year: "numeric" });
    };

    window.escapeHtml = function(text) {
        const div = document.createElement("div");
        div.textContent = text;
        return div.innerHTML;
    };

    // ── Toast notifications ─────────────────────────────────────
    window.showToast = function(message, type) {
        type = type || "info";
        const container = document.getElementById("toast-container");
        if (!container) return;
        const toast = document.createElement("div");
        toast.className = "toast toast-" + type;
        toast.textContent = message;
        container.appendChild(toast);
        setTimeout(function() {
            toast.style.opacity = "0";
            toast.style.transform = "translateX(100%)";
            toast.style.transition = "opacity 0.4s ease, transform 0.4s ease";
            setTimeout(function() { toast.remove(); }, 400);
        }, 3000);
    };

    // ── Global loading overlay ──────────────────────────────────
    var loadingCounter = 0;

    window.showLoading = function() {
        loadingCounter++;
        var overlay = document.getElementById("loading-overlay");
        if (overlay) {
            overlay.classList.remove("hidden");
        }
    };

    window.hideLoading = function() {
        loadingCounter = Math.max(0, loadingCounter - 1);
        if (loadingCounter === 0) {
            var overlay = document.getElementById("loading-overlay");
            if (overlay) {
                overlay.classList.add("hidden");
            }
        }
    };

    // ── API client ──────────────────────────────────────────────
    window.apiClient = function() {
        const instance = axios.create({
            baseURL: API_BASE,
            headers: { "Content-Type": "application/json" }
        });
        instance.interceptors.request.use(function(config) {
            const token = window.getToken();
            if (token) { config.headers.Authorization = "Bearer " + token; }
            window.showLoading();
            return config;
        });
        instance.interceptors.response.use(
            function(response) {
                window.hideLoading();
                return response.data;
            },
            function(error) {
                window.hideLoading();
                if (error.response && error.response.status === 401) { window.clearAuth(); }
                const msg = error.response && error.response.data ? error.response.data.message : "Connection lost.";
                window.showToast(msg, "error");
                return Promise.reject(error);
            }
        );
        return instance;
    };

    window.api = window.apiClient();

    // ── Navbar update ───────────────────────────────────────────
    window.updateNavbar = function() {
        const nav = document.getElementById("navbar-user-area");
        if (!nav) return;
        const user = window.getUser();
        if (user) {
            nav.innerHTML =
                '<a href="/user/messages" class="nav-icon-link" title="Messages">' +
                '  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">' +
                '    <path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z"/>' +
                '    <polyline points="22,6 12,13 2,6"/>' +
                '  </svg>' +
                "</a>" +
                '<a href="/post/create" class="btn btn-primary btn-sm">+ New Post</a>' +
                (user.role === "ADMIN"
                    ? '<a href="/admin/audit" style="color:var(--text-secondary);padding:8px 16px;text-decoration:none;">Audit</a>' +
                      '<a href="/admin/dashboard" style="color:var(--text-secondary);padding:8px 16px;text-decoration:none;">Dashboard</a>'
                    : "") +
                '<a href="/user/profile" style="display:inline-flex;align-items:center;gap:8px;text-decoration:none;color:var(--text-secondary);">' +
                '  <img src="https://ui-avatars.com/api/?name=' + encodeURIComponent(user.nickname) + "&background=1a1a3e&color=00f0ff&size=32" + ' style="width:24px;height:24px;border-radius:50%;border:1px solid var(--border-color);">' +
                '  <span>' + window.escapeHtml(user.nickname) + "</span>" +
                "</a>" +
                '<button onclick="logout()" class="btn btn-secondary btn-sm">Disconnect</button>';
        } else {
            nav.innerHTML =
                '<a href="/login" class="btn btn-login" style="padding:8px 16px;border-radius:4px;border:1px solid var(--neon-cyan);color:var(--neon-cyan);text-decoration:none;">Login</a>' +
                '<a href="/register" class="btn btn-register" style="padding:8px 16px;border-radius:4px;background:linear-gradient(135deg,var(--neon-magenta),var(--neon-purple));color:#fff;text-decoration:none;">Register</a>';
        }
    };

    // ── Hamburger toggle ────────────────────────────────────────
    document.addEventListener("DOMContentLoaded", function() {
        updateNavbar();

        var toggle = document.getElementById("navbar-toggle");
        var nav = document.getElementById("navbar-user-area");
        if (toggle && nav) {
            toggle.addEventListener("click", function() {
                nav.classList.toggle("navbar-nav-open");
                toggle.classList.toggle("navbar-toggle-active");
            });
        }
    });

})();