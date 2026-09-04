/**
 * Nexus Campus - Admin module
 * Audit panel and dashboard
 */
(function() {
    'use strict';

    // ── Load audit posts ───────────────────────────────────
    window.loadAuditPosts = function() {
        var container = document.getElementById('audit-container');
        if (!container) return;
        container.innerHTML = '<div class="loading-container"><div class="spinner"></div><span>Scanning firewall queue...</span></div>';
        window.api.get('/admin/audit/posts').then(function(res) {
            if (!res.data || res.data.length === 0) {
                container.innerHTML = '<div class="empty-state"><h3>Queue is clear</h3></div>';
                return;
            }
            var html = '<table class="admin-table"><thead><tr><th>ID</th><th>Title</th><th>Author</th><th>Category</th><th>Date</th><th>Actions</th></tr></thead><tbody>';
            res.data.forEach(function(post) {
                html += '<tr>' +
                    '<td style="font-family:Share Tech Mono,monospace;">' + post.id + '</td>' +
                    '<td><a href="/post/detail?id=' + post.id + '" style="color:var(--text-primary);text-decoration:none;">' + window.escapeHtml(post.title) + '</a></td>' +
                    '<td>' + window.escapeHtml(post.authorName) + '</td>' +
                    '<td>' + window.escapeHtml(post.categoryName) + '</td>' +
                    '<td style="font-size:0.8rem;">' + window.formatTime(post.createTime) + '</td>' +
                    '<td><button onclick="approvePost(' + post.id + ')" class="btn btn-success btn-sm">Approve</button> <button onclick="rejectPost(' + post.id + ')" class="btn btn-danger btn-sm">Reject</button></td>' +
                    '</tr>';
            });
            html += '</tbody></table>';
            container.innerHTML = html;
        }).catch(function() {
            container.innerHTML = '<div class="empty-state"><h3>Access denied</h3></div>';
        });
    };

    window.approvePost = function(postId) {
        window.api.post('/admin/audit/posts/' + postId + '/approve').then(function() {
            window.showToast('Post approved.', 'success');
            window.loadAuditPosts();
        });
    };

    window.rejectPost = function(postId) {
        window.api.post('/admin/audit/posts/' + postId + '/reject').then(function() {
            window.showToast('Post rejected.', 'info');
            window.loadAuditPosts();
        });
    };

    // ── Load admin dashboard stats ─────────────────────────
    window.loadAdminStats = function() {
        var container = document.getElementById('stats-container');
        if (!container) return;
        container.innerHTML = '<div class="loading-container"><div class="spinner"></div><span>Scanning Nexus core...</span></div>';
        window.api.get('/admin/stats').then(function(res) {
            if (!res.data) {
                container.innerHTML = '<div class="empty-state"><h3>No data available</h3></div>';
                return;
            }
            var stats = res.data;
            container.innerHTML =
                '<div class="stats-grid">' +
                '  <div class="stat-card">' +
                '    <div class="stat-card-icon" style="color:var(--neon-cyan);">' +
                '      <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>' +
                '    </div>' +
                '    <div class="stat-card-value" id="stat-users">' + (stats.totalUsers || 0) + '</div>' +
                '    <div class="stat-card-label">Total Users</div>' +
                '  </div>' +
                '  <div class="stat-card">' +
                '    <div class="stat-card-icon" style="color:var(--neon-magenta);">' +
                '      <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/></svg>' +
                '    </div>' +
                '    <div class="stat-card-value">' + (stats.totalPosts || 0) + '</div>' +
                '    <div class="stat-card-label">Total Posts</div>' +
                '  </div>' +
                '  <div class="stat-card">' +
                '    <div class="stat-card-icon" style="color:var(--neon-green);">' +
                '      <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>' +
                '    </div>' +
                '    <div class="stat-card-value">' + (stats.totalComments || 0) + '</div>' +
                '    <div class="stat-card-label">Total Comments</div>' +
                '  </div>' +
                '  <div class="stat-card">' +
                '    <div class="stat-card-icon" style="color:var(--neon-yellow);">' +
                '      <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="18" height="18" rx="2" ry="2"/><line x1="3" y1="9" x2="21" y2="9"/><line x1="9" y1="21" x2="9" y2="9"/></svg>' +
                '    </div>' +
                '    <div class="stat-card-value">' + (stats.pendingAudit || 0) + '</div>' +
                '    <div class="stat-card-label">Pending Audit</div>' +
                '  </div>' +
                '</div>';
        }).catch(function() {
            container.innerHTML = '<div class="empty-state"><h3>Failed to load stats</h3></div>';
        });
    };

})();
