/**
 * Nexus Campus - Profile module
 * User profile display, editing, password change, image upload
 */
(function() {
    'use strict';

    function fetchAndRenderProfile() {
        if (!window.isAuthenticated()) {
            window.location.href = '/login';
            return;
        }
        var container = document.getElementById('profile-container');
        if (!container) return;
        container.innerHTML = '<div class="loading-container"><div class="spinner"></div><span>Loading profile...</span></div>';

        window.api.get('/users/profile').then(function(res) {
            if (!res.data) {
                renderFromCache();
                return;
            }
            var u = res.data;
            var avatarUrl = (u.avatar && u.avatar !== 'default_avatar.png')
                ? '/uploads/' + u.avatar
                : 'https://ui-avatars.com/api/?name=' + encodeURIComponent(u.nickname) + '&background=1a1a3e&color=00f0ff&size=80';
            container.innerHTML =
                '<div class="card text-center" style="padding:40px;">' +
                '  <img src="' + avatarUrl + '" class="avatar avatar-lg" style="margin:0 auto 16px;">' +
                '  <h2 style="font-family:Orbitron,monospace;">' + window.escapeHtml(u.nickname) + '</h2>' +
                '  <div style="color:var(--text-muted);font-size:0.85rem;margin-bottom:8px;">@' + window.escapeHtml(u.username) + '</div>' +
                '  <div class="flex-center gap-16 mt-8 mb-24">' +
                '    <span class="badge badge-level">LVL ' + (u.level || 1) + '</span>' +
                '    <span class="badge badge-role">' + (u.role || 'USER') + '</span>' +
                '  </div>' +
                '  <div class="stat-bar flex-center" style="gap:32px;">' +
                '    <div class="text-center"><div class="text-cyan" style="font-size:1.5rem;font-weight:700;">' + (u.corePower || 0) + '</div><div class="text-muted" style="font-size:0.8rem;">Core Power</div></div>' +
                '    <div class="text-center"><div class="text-cyan" style="font-size:1.5rem;font-weight:700;">' + (u.level || 1) + '</div><div class="text-muted" style="font-size:0.8rem;">Level</div></div>' +
                '  </div>' +
                '</div>';
        }).catch(function() {
            renderFromCache();
        });
    }

    function renderFromCache() {
        if (!window.isAuthenticated()) { window.location.href = '/login'; return; }
        var user = window.getUser();
        var container = document.getElementById('profile-container');
        if (!container) return;
        container.innerHTML =
            '<div class="card text-center" style="padding:40px;">' +
            '  <img src="https://ui-avatars.com/api/?name=' + encodeURIComponent(user.nickname) + '&background=1a1a3e&color=00f0ff&size=80" class="avatar avatar-lg" style="margin:0 auto 16px;">' +
            '  <h2 style="font-family:Orbitron,monospace;">' + window.escapeHtml(user.nickname) + '</h2>' +
            '  <div style="color:var(--text-muted);font-size:0.85rem;margin-bottom:8px;">@' + window.escapeHtml(user.username) + '</div>' +
            '  <div class="flex-center gap-16 mt-8 mb-24">' +
            '    <span class="badge badge-level">LVL ' + (user.level || 1) + '</span>' +
            '    <span class="badge badge-role">' + (user.role || 'USER') + '</span>' +
            '  </div>' +
            '  <div class="stat-bar flex-center" style="gap:32px;">' +
            '    <div class="text-center"><div class="text-cyan" style="font-size:1.5rem;font-weight:700;">' + (user.corePower || 0) + '</div><div class="text-muted" style="font-size:0.8rem;">Core Power</div></div>' +
            '    <div class="text-center"><div class="text-cyan" style="font-size:1.5rem;font-weight:700;">' + (user.level || 1) + '</div><div class="text-muted" style="font-size:0.8rem;">Level</div></div>' +
            '  </div>' +
            '</div>';
    }

    window.loadProfile = fetchAndRenderProfile;

    window.toggleEditMode = function() {
        var card = document.getElementById('profile-edit-card');
        if (!card) return;
        var btn = document.getElementById('edit-toggle-btn');
        var isVisible = card.style.display !== 'none';
        card.style.display = isVisible ? 'none' : 'block';
        if (btn) btn.textContent = isVisible ? 'Edit Profile' : 'Hide Edit';
    };

    window.togglePasswordCard = function() {
        var card = document.getElementById('password-change-card');
        if (!card) return;
        card.style.display = card.style.display === 'none' ? 'block' : 'none';
    };

    window.submitProfileUpdate = function() {
        var nickname = document.getElementById('edit-nickname');
        var avatar = document.getElementById('edit-avatar');
        if (!nickname || !nickname.value.trim()) {
            window.showToast('Nickname cannot be empty.', 'error');
            return;
        }
        var data = { nickname: nickname.value.trim() };
        if (avatar && avatar.value.trim()) {
            data.avatar = avatar.value.trim();
        }
        window.api.put('/users/profile', data).then(function(res) {
            if (res.code === 200) {
                window.showToast(res.message || 'Profile updated.', 'success');
                var user = window.getUser();
                if (user && res.data) {
                    user.nickname = res.data.nickname;
                    window.setAuth(window.getToken(), user);
                }
                window.toggleEditMode();
                window.loadProfile();
            } else {
                window.showToast(res.message || 'Update failed.', 'error');
            }
        }).catch(function() {
            window.showToast('Connection failed.', 'error');
        });
    };

    window.submitPasswordChange = function() {
        var oldPwd = document.getElementById('change-old-password');
        var newPwd = document.getElementById('change-new-password');
        if (!oldPwd || !newPwd || !oldPwd.value || !newPwd.value) {
            window.showToast('Both password fields are required.', 'error');
            return;
        }
        window.api.put('/users/password', {
            oldPassword: oldPwd.value,
            newPassword: newPwd.value
        }).then(function(res) {
            if (res.code === 200) {
                window.showToast(res.message || 'Password changed.', 'success');
                oldPwd.value = '';
                newPwd.value = '';
                window.togglePasswordCard();
            } else {
                window.showToast(res.message || 'Password change failed.', 'error');
            }
        }).catch(function() {
            window.showToast('Connection failed.', 'error');
        });
    };

    window.uploadImage = function(file, onSuccess, onError) {
        if (!window.isAuthenticated()) {
            onError('Login required.');
            return;
        }
        var formData = new FormData();
        formData.append('file', file);
        window.api.post('/upload/image', formData, {
            headers: { 'Content-Type': 'multipart/form-data' }
        }).then(function(res) {
            if (res.code === 200 && res.data) {
                onSuccess(res.data);
            } else {
                onError(res.message || 'Upload failed.');
            }
        }).catch(function() {
            onError('Upload failed.');
        });
    };

    window.loadMessages = function() {
        var container = document.getElementById('messages-container');
        if (!container) return;
        container.innerHTML = '<div class="loading-container"><div class="spinner"></div><span>Decrypting message stream...</span></div>';
        window.api.get('/messages').then(function(res) {
            if (!res.data || res.data.length === 0) {
                container.innerHTML = '<div class="empty-state"><h3>No messages</h3><p>Your inbox is empty. The Nexus is quiet.</p></div>';
                return;
            }
            var html = '<div class="messages-list">';
            res.data.forEach(function(msg) {
                var isUnread = msg.isRead === 0;
                html += '<div class="message-item' + (isUnread ? ' message-unread' : '') + '" data-id="' + msg.id + '" onclick="markMessageRead(' + msg.id + ')">' +
                    '  <div class="message-dot' + (isUnread ? ' message-dot-active' : '') + '"></div>' +
                    '  <div class="message-content">' +
                    '    <div class="message-header">' +
                    '      <span class="message-sender">' + window.escapeHtml(msg.fromUserName || 'System') + '</span>' +
                    '      <span class="message-time">' + window.formatTime(msg.createTime) + '</span>' +
                    '    </div>' +
                    '    <div class="message-body">' + window.escapeHtml(msg.content) + '</div>' +
                    '  </div>' +
                    '</div>';
            });
            html += '</div>';
            container.innerHTML = html;
        }).catch(function() {
            container.innerHTML = '<div class="empty-state"><h3>Failed to load messages</h3></div>';
        });
    };

    window.markMessageRead = function(messageId) {
        window.api.post('/messages/' + messageId + '/read').then(function() {
            var el = document.querySelector('.message-item[data-id="' + messageId + '"]');
            if (el) {
                el.classList.remove('message-unread');
                el.querySelector('.message-dot').classList.remove('message-dot-active');
            }
        });
    };

})();
