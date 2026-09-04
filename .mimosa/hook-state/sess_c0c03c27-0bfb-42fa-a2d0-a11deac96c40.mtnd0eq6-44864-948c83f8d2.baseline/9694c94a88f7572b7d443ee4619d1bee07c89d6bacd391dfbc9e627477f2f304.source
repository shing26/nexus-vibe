/**
 * Nexus Campus - Comments module
 * Load and submit comments
 */
(function() {
    'use strict';

    // ── Load comments for a post ───────────────────────────
    window.loadComments = function(postId) {
        var container = document.getElementById('comments-container');
        if (!container) return;
        container.innerHTML = '<div class="loading-container"><div class="spinner"></div><span>Loading comments...</span></div>';
        window.api.get('/comments/post/' + postId).then(function(res) {
            if (!res.data || res.data.length === 0) {
                container.innerHTML = '<div class="empty-state" style="padding:40px;"><p>No comments yet.</p></div>';
                return;
            }
            var html = '<h3 style="font-family:Orbitron,monospace;margin-bottom:16px;">Comments (' + res.data.length + ')</h3>';
            res.data.forEach(function(c) {
                html += '<div class="comment">' +
                    '  <div class="comment-header">' +
                    '    <img src="https://ui-avatars.com/api/?name=' + encodeURIComponent(c.authorName) + '&background=1a1a3e&color=00f0ff&size=32" class="comment-avatar">' +
                    '    <span class="comment-author">' + window.escapeHtml(c.authorName) + '</span>' +
                    '    <span class="comment-time">' + window.formatTime(c.createTime) + '</span>' +
                    '  </div>' +
                    '  <div class="comment-body">' + window.escapeHtml(c.content) + '</div>' +
                    '</div>';
            });
            container.innerHTML = html;
        }).catch(function() {
            container.innerHTML = '<div class="empty-state" style="padding:40px;"><p>Failed to load comments.</p></div>';
        });
    };

    // ── Submit a comment ───────────────────────────────────
    window.submitComment = function() {
        var params = new URLSearchParams(window.location.search);
        var postId = params.get('id');
        var content = document.getElementById('comment-content');
        var error = document.getElementById('comment-error');
        if (!window.isAuthenticated()) {
            window.showToast('Login required to comment.', 'info');
            return;
        }
        if (!content.value.trim()) {
            error.textContent = 'Comment cannot be empty.';
            error.classList.remove('hidden');
            return;
        }
        window.api.post('/comments', { postId: parseInt(postId), content: content.value.trim() }).then(function(res) {
            if (res.code === 200) {
                content.value = '';
                window.showToast('Comment transmitted.', 'success');
                window.loadComments(postId);
                var countEl = document.getElementById('comment-count');
                if (countEl) countEl.textContent = parseInt(countEl.textContent) + 1;
            } else {
                error.textContent = res.message || 'Failed to post comment.';
                error.classList.remove('hidden');
            }
        });
    };

})();
