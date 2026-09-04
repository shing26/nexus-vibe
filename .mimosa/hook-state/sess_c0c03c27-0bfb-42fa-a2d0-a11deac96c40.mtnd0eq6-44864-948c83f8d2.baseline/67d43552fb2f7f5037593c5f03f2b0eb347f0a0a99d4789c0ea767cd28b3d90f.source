/**
 * Nexus Campus - Posts module
 * Post listing, search, filter, create, detail, like
 */
(function() {
    'use strict';

    window.renderPostCard = function(post) {
        var tags = post.tags && post.tags.length > 0
            ? post.tags.map(function(t) { return '<span class="tag">' + window.escapeHtml(t) + '</span>'; }).join('')
            : '';
        return '<div class="card">' +
            '<div class="card-meta" style="margin-bottom:8px;"><span>' + window.escapeHtml(post.categoryName || 'Uncategorized') + '</span></div>' +
            '<h3 class="card-title"><a href="/post/detail?id=' + post.id + '">' + window.escapeHtml(post.title) + '</a></h3>' +
            '<div class="card-meta"><span>' + window.formatTime(post.createTime) + '</span><span>by ' + window.escapeHtml(post.authorName || 'Anonymous') + '</span></div>' +
            '<div class="card-body">' + window.escapeHtml(post.summary || '') + '</div>' +
            '<div class="mt-8 flex-between">' +
            '  <div>' + tags + '</div>' +
            '  <div class="stat-bar" style="gap:12px;padding:0;">' +
            '    <span class="stat-item">Views: <span class="value">' + (post.viewCount || 0) + '</span></span>' +
            '    <span class="stat-item">Likes: <span class="value">' + (post.likeCount || 0) + '</span></span>' +
            '    <span class="stat-item">Comments: <span class="value">' + (post.commentCount || 0) + '</span></span>' +
            '  </div>' +
            '</div>' +
            '</div>';
    };

    var SKELETON_COUNT = 6;
    function renderSkeletons() {
        var html = '<div class="post-grid">';
        for (var i = 0; i < SKELETON_COUNT; i++) {
            html += '<div class="card post-card-skeleton">' +
                '  <div class="skeleton-line skeleton-line-sm" style="width:40%;"></div>' +
                '  <div class="skeleton-line skeleton-line-md" style="width:75%;margin-top:12px;"></div>' +
                '  <div class="skeleton-line" style="width:50%;margin-top:8px;"></div>' +
                '  <div class="skeleton-line" style="width:90%;margin-top:8px;"></div>' +
                '  <div class="skeleton-line" style="width:65%;margin-top:8px;"></div>' +
                '  <div class="flex-between mt-8">' +
                '    <div class="skeleton-line skeleton-line-sm" style="width:30%;"></div>' +
                '    <div class="skeleton-line skeleton-line-sm" style="width:40%;"></div>' +
                '  </div>' +
                '</div>';
        }
        html += '</div>';
        return html;
    }

    var _page = 1;
    var _pageSize = 12;
    var _total = 0;
    var _pages = 0;
    var _currentParams = {};

    function renderPostPage() {
        var container = document.getElementById('post-container');
        if (!container) return;

        if (!_data || !_data.list || _data.list.length === 0) {
            container.innerHTML = '<div class="empty-state"><h3>No posts found</h3><p>The data stream is silent. Be the first to post.</p></div>';
            return;
        }

        var html = '<div class="post-grid">' + _data.list.map(window.renderPostCard).join('') + '</div>';

        if (_pages > 1) {
            html += '<div class="pagination" style="display:flex;align-items:center;justify-content:center;gap:8px;padding:24px 0;">';
            if (_page > 1) {
                html += '<button class="btn btn-secondary btn-sm" onclick="navigatePage(' + (_page - 1) + ')">Previous</button>';
            }
            html += '<span style="color:var(--text-secondary);font-size:0.85rem;">Page ' + _page + ' of ' + _pages + '</span>';
            if (_page < _pages) {
                html += '<button class="btn btn-secondary btn-sm" onclick="navigatePage(' + (_page + 1) + ')">Next</button>';
                html += '<span style="color:var(--text-muted);font-size:0.75rem;">(' + _total + ' total)</span>';
            }
            html += '</div>';
        } else if (_total > _pageSize) {
            html += '<div class="pagination" style="display:flex;align-items:center;justify-content:center;gap:8px;padding:24px 0;">' +
                '<span style="color:var(--text-muted);font-size:0.75rem;">' + _total + ' posts found</span>' +
                '</div>';
        }

        container.innerHTML = html;
    }

    var _data = null;

    function fetchPage(page, params) {
        params = params || {};
        params.page = page;
        params.size = _pageSize;
        var container = document.getElementById('post-container');
        if (!container) return;
        container.innerHTML = renderSkeletons();

        window.api.get('/posts', { params: params }).then(function(res) {
            if (res.data === null || !res.data.list) {
                container.innerHTML = '<div class="empty-state"><h3>No posts found</h3><p>The data stream is silent. Be the first to post.</p></div>';
                return;
            }
            _data = res.data;
            _page = res.data.page || 1;
            _pageSize = res.data.size || 12;
            _total = res.data.total || 0;
            _pages = res.data.pages || 0;
            renderPostPage();
        }).catch(function() {
            container.innerHTML = '<div class="empty-state"><h3>Connection lost</h3><p>Unable to reach the Nexus.</p></div>';
        });
    }

    window.navigatePage = function(page) {
        fetchPage(page, _currentParams);
    };

    window.loadPosts = function(params) {
        params = params || {};
        _currentParams = params;
        _page = 1;
        _data = null;
        fetchPage(1, params);
    };

    window.loadCategories = function() {
        var container = document.getElementById('category-filter');
        if (!container) return;
        window.api.get('/categories').then(function(res) {
            if (!res.data) return;
            var html = '<button class="active" data-category="" onclick="filterPosts(this)">All</button>';
            res.data.forEach(function(cat) {
                html += '<button data-category="' + cat.id + '" onclick="filterPosts(this,' + cat.id + ')">' + window.escapeHtml(cat.name) + '</button>';
            });
            container.innerHTML = html;
        });
    };

    window.filterPosts = function(btn, categoryId) {
        document.querySelectorAll('#category-filter button').forEach(function(b) { b.classList.remove('active'); });
        btn.classList.add('active');
        window.loadPosts(categoryId ? { categoryId: categoryId } : {});
    };

    window.searchPosts = function() {
        var input = document.getElementById('search-input');
        if (!input) return;
        var keyword = input.value.trim();
        window.loadPosts(keyword ? { keyword: keyword } : {});
    };

    window.submitPost = function() {
        var title = document.getElementById('post-title');
        var category = document.getElementById('post-category');
        var content = document.getElementById('post-content');
        var error = document.getElementById('post-error');
        var submitBtn = document.getElementById('post-submit');
        if (!category.value) {
            error.textContent = 'Please select a category.';
            error.classList.remove('hidden');
            return;
        }
        if (!title.value || !content.value) {
            error.textContent = 'Title and content are required.';
            error.classList.remove('hidden');
            return;
        }
        submitBtn.disabled = true;
        submitBtn.textContent = 'Transmitting...';
        window.api.post('/posts', { title: title.value, categoryId: parseInt(category.value), content: content.value }).then(function(res) {
            if (res.code === 200) {
                window.showToast(res.message || 'Post created!', 'success');
                setTimeout(function() { window.location.href = '/post/detail?id=' + res.data.postId; }, 800);
            } else {
                error.textContent = res.message || 'Failed to create post.';
                error.classList.remove('hidden');
                submitBtn.disabled = false;
                submitBtn.textContent = 'Submit';
            }
        }).catch(function() {
            error.textContent = 'Connection failed.';
            error.classList.remove('hidden');
            submitBtn.disabled = false;
            submitBtn.textContent = 'Submit';
        });
    };

    window.loadPostDetail = function() {
        var params = new URLSearchParams(window.location.search);
        var postId = params.get('id');
        if (!postId) {
            document.getElementById('post-detail-container').innerHTML = '<div class="empty-state"><h3>Post not found</h3></div>';
            return;
        }
        var container = document.getElementById('post-detail-container');
        container.innerHTML = '<div class="loading-container"><div class="spinner"></div><span>Decrypting neural packet...</span></div>';
        window.api.get('/posts/' + postId).then(function(res) {
            if (!res.data) {
                container.innerHTML = '<div class="empty-state"><h3>Post not found</h3></div>';
                return;
            }
            renderPostDetailView(res.data);
            if (typeof window.loadComments === 'function') {
                window.loadComments(postId);
            }
        }).catch(function() {
            container.innerHTML = '<div class="empty-state"><h3>Failed to load post</h3></div>';
        });
    };

    function renderPostDetailView(post) {
        var container = document.getElementById('post-detail-container');
        var tags = post.tags && post.tags.length > 0
            ? post.tags.map(function(t) { return '<span class="tag">' + window.escapeHtml(t) + '</span>'; }).join('')
            : '';
        container.innerHTML =
            '<div class="card" style="padding:32px;">' +
            '  <div class="card-meta mb-8"><span>' + window.escapeHtml(post.categoryName || 'Uncategorized') + '</span></div>' +
            '  <h1 style="font-family:Orbitron,monospace;font-size:1.6rem;margin-bottom:12px;">' + window.escapeHtml(post.title) + '</h1>' +
            '  <div class="card-meta mb-16"><span>by ' + window.escapeHtml(post.authorName || 'Anonymous') + '</span><span>' + window.formatTime(post.createTime) + '</span></div>' +
            '  <div style="margin-bottom:16px;">' + tags + '</div>' +
            '  <div class="stat-bar mb-16">' +
            '    <span class="stat-item">Views: <span class="value">' + (post.viewCount || 0) + '</span></span>' +
            '    <span class="stat-item">Likes: <span class="value" id="like-count">' + (post.likeCount || 0) + '</span></span>' +
            '    <span class="stat-item">Comments: <span class="value" id="comment-count">' + (post.commentCount || 0) + '</span></span>' +
            '  </div>' +
            '  <button onclick="likePost(' + post.id + ')" class="btn btn-secondary btn-sm">Like</button>' +
            '  <hr style="border-color:var(--border-color);margin:24px 0;">' +
            '  <div class="post-content">' + post.content + '</div>' +
            '</div>';
    }

    window.likePost = function(postId) {
        if (!window.isAuthenticated()) {
            window.showToast('Login required to like posts.', 'info');
            return;
        }
        window.api.post('/posts/' + postId + '/like').then(function(res) {
            if (res.data && res.data.currentLikes !== undefined) {
                document.getElementById('like-count').textContent = res.data.currentLikes;
            }
            window.showToast(res.message || 'Like registered.', 'success');
        });
    };

    window.loadPostCategories = function() {
        var select = document.getElementById('post-category');
        if (!select) return;
        window.api.get('/categories').then(function(res) {
            if (!res.data || !Array.isArray(res.data)) return;
            res.data.forEach(function(cat) {
                var opt = document.createElement('option');
                opt.value = cat.id;
                opt.textContent = cat.name;
                select.appendChild(opt);
            });
        });
    };

})();