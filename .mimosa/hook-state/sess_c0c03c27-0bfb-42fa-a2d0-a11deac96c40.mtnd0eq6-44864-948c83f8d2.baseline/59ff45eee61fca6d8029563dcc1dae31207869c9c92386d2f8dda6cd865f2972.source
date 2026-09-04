/**
 * Nexus Campus - Auth module
 * Login, register, logout
 */
window.logout = function() {
    if (typeof window.showToast === 'function') window.showToast('Disconnected from Nexus.', 'info');
    if (typeof window.clearAuth === 'function') window.clearAuth();
};

function handleAuthResponse(res, errorEl, successMsg, redirectUrl) {
    if (res && res.code === 200 && res.data) {
        if (typeof window.setAuth === 'function') {
            window.setAuth(res.data.token, {
                userId: res.data.userId,
                username: res.data.username,
                nickname: res.data.nickname,
                role: res.data.role,
                avatar: res.data.avatar,
                corePower: res.data.corePower,
                level: res.data.level
            });
        }
        if (typeof window.showToast === 'function') window.showToast(successMsg, 'success');
        setTimeout(function() { window.location.href = redirectUrl || '/'; }, 500);
        return true;
    }
    if (errorEl) {
        errorEl.textContent = res && res.message ? res.message : 'Operation failed.';
        errorEl.classList.remove('hidden');
    }
    return false;
}

window.submitLogin = function() {
    var usernameEl = document.getElementById('login-username');
    var passwordEl = document.getElementById('login-password');
    var errorEl = document.getElementById('login-error');
    if (!usernameEl || !passwordEl) return;

    var username = usernameEl.value.trim();
    var password = passwordEl.value;

    if (!username || !password) {
        if (errorEl) {
            errorEl.textContent = 'Username and password are required.';
            errorEl.classList.remove('hidden');
        }
        return;
    }
    if (errorEl) errorEl.classList.add('hidden');

    var btn = document.querySelector('#login-submit');
    if (btn) { btn.disabled = true; btn.textContent = 'Connecting...'; }

    var api = typeof window.api !== 'undefined' ? window.api : null;
    if (!api) {
        if (errorEl) {
            errorEl.textContent = 'API client not available. Check network connection.';
            errorEl.classList.remove('hidden');
        }
        if (btn) { btn.disabled = false; btn.textContent = 'Connect'; }
        return;
    }

    api.post('/auth/login', { username: username, password: password })
        .then(function(res) {
            if (btn) { btn.disabled = false; btn.textContent = 'Connect'; }
            handleAuthResponse(res, errorEl, 'Login successful.', '/');
        })
        .catch(function(err) {
            if (btn) { btn.disabled = false; btn.textContent = 'Connect'; }
            var msg = 'Connection failed. Check server status.';
            if (err && err.response && err.response.data && err.response.data.message) {
                msg = err.response.data.message;
            }
            if (errorEl) {
                errorEl.textContent = msg;
                errorEl.classList.remove('hidden');
            }
        });
};

window.submitRegister = function() {
    var usernameEl = document.getElementById('reg-username');
    var nicknameEl = document.getElementById('reg-nickname');
    var passwordEl = document.getElementById('reg-password');
    var errorEl = document.getElementById('reg-error');
    if (!usernameEl || !nicknameEl || !passwordEl) return;

    var username = usernameEl.value.trim();
    var nickname = nicknameEl.value.trim();
    var password = passwordEl.value;

    if (!username || !nickname || !password) {
        if (errorEl) {
            errorEl.textContent = 'All fields are required.';
            errorEl.classList.remove('hidden');
        }
        return;
    }
    if (password.length < 6) {
        if (errorEl) {
            errorEl.textContent = 'Password must be at least 6 characters.';
            errorEl.classList.remove('hidden');
        }
        return;
    }
    if (errorEl) errorEl.classList.add('hidden');

    var btn = document.querySelector('#register-submit');
    if (btn) { btn.disabled = true; btn.textContent = 'Initializing...'; }

    var api = typeof window.api !== 'undefined' ? window.api : null;
    if (!api) {
        if (errorEl) {
            errorEl.textContent = 'API client not available. Check network connection.';
            errorEl.classList.remove('hidden');
        }
        if (btn) { btn.disabled = false; btn.textContent = 'Initialize'; }
        return;
    }

    api.post('/auth/register', { username: username, password: password, nickname: nickname })
        .then(function(res) {
            if (btn) { btn.disabled = false; btn.textContent = 'Initialize'; }
            handleAuthResponse(res, errorEl, 'Registration successful.', '/');
        })
        .catch(function(err) {
            if (btn) { btn.disabled = false; btn.textContent = 'Initialize'; }
            var msg = 'Connection failed. Check server status.';
            if (err && err.response && err.response.data && err.response.data.message) {
                msg = err.response.data.message;
            }
            if (errorEl) {
                errorEl.textContent = msg;
                errorEl.classList.remove('hidden');
            }
        });
};