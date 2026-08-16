import { useState, useRef, useCallback, useMemo, useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { apiClient } from '../api/client';
import { useAuthStore } from '../stores/authStore';
import { useChannels, type Channel } from '../api/useChannels';

function estimateTokens(text: string): number {
  if (!text.trim()) return 0;
  const chineseChars = (text.match(/[\u4e00-\u9fff\u3400-\u4dbf\uf900-\ufaff]/g) || []).length;
  const asciiText = text.replace(/[\u4e00-\u9fff\u3400-\u4dbf\uf900-\ufaff]/g, ' ');
  const asciiWords = asciiText.split(/\s+/).filter(Boolean).length;
  return Math.round(chineseChars * 2 + asciiWords * 1.3);
}

export default function EditPostPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const user = useAuthStore((s) => s.user);
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  const [title, setTitle] = useState('');
  const [categoryId, setCategoryId] = useState<number | null>(null);
  const [content, setContent] = useState('');
  const [postType, setPostType] = useState('post');
  const [promptMetadata, setPromptMetadata] = useState('');
  const [changeNote, setChangeNote] = useState('');
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const { data: channels, isLoading: channelsLoading } = useChannels();

  const tokens = useMemo(() => estimateTokens(content), [content]);

  const displayChannels = useMemo(() => {
    if (!channels) return [];
    if (user?.role === 'ADMIN') return channels;
    const filtered = channels.filter((ch: Channel) => ch.slug !== 'announcements');
    const currentIsAnnouncement = categoryId !== null &&
      channels.some((ch: Channel) => ch.id === categoryId && ch.slug === 'announcements');
    if (currentIsAnnouncement && !filtered.some((ch: Channel) => ch.id === categoryId)) {
      const announcement = channels.find((ch: Channel) => ch.id === categoryId);
      if (announcement) filtered.push(announcement);
    }
    return filtered;
  }, [channels, user?.role, categoryId]);

  useEffect(() => {
    if (!id) return;
    (async () => {
      try {
        const res = await apiClient.get(`/posts/${id}`);
        const post = res.data.data;
        setTitle(post.title || '');
        setCategoryId(post.categoryId ?? null);
        setContent(post.content || '');
        setPostType(post.postType || 'post');
        setPromptMetadata(post.promptMetadata || '');
      } catch {
        setError('Failed to load post.');
      } finally {
        setLoading(false);
      }
    })();
  }, [id]);

  const insertCodeBlock = useCallback(() => {
    const textarea = textareaRef.current;
    if (!textarea) return;
    const start = textarea.selectionStart;
    const end = textarea.selectionEnd;
    const selected = content.slice(start, end);
    const before = content.slice(0, start);
    const after = content.slice(end);
    let insertion: string;
    let cursorOffset: number;
    if (selected) {
      insertion = '```\n' + selected + '\n```';
      cursorOffset = start + insertion.length;
    } else {
      insertion = '```\n\n```';
      cursorOffset = start + 4;
    }
    setContent(before + insertion + after);
    requestAnimationFrame(() => {
      textarea.focus();
      textarea.setSelectionRange(cursorOffset, cursorOffset);
    });
  }, [content]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim() || !content.trim()) {
      setError('Title and content are required.');
      return;
    }
    if (title.trim().length > 150) {
      setError('Title must not exceed 150 characters.');
      return;
    }
    if (!isAuthenticated) {
      navigate('/login');
      return;
    }
    setSubmitting(true);
    setError('');
    try {
      const body: any = {
        title: title.trim(),
        categoryId,
        content,
        changeNote: changeNote.trim(),
      };
      if (postType === 'prompt') {
        body.promptMetadata = promptMetadata;
      }
      await apiClient.put(`/posts/${id}`, body);
      navigate(`/post/${id}`);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to update post.');
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) {
    return (
      <div className="max-w-5xl mx-auto px-4 py-8">
        <div className="animate-pulse space-y-4">
          <div className="h-3 bg-vibe-card rounded w-1/3" />
          <div className="h-96 bg-vibe-card/50 rounded" />
        </div>
      </div>
    );
  }

  return (
    <div className="max-w-5xl mx-auto px-4 py-8">
      <h1 className="text-3xl font-bold text-slate-200 mb-8"><span className="text-vibe-cyan">$</span> Edit Post</h1>

      <form onSubmit={handleSubmit} className="space-y-6">
      {error && (
          <div className="bg-red-900/30 border border-red-500/40 text-red-400 px-4 py-3 rounded-lg text-[11px] font-mono">
            {error}
          </div>
        )}

        <div className="flex gap-4">
          <div className="flex-1">
            <input
              type="text"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              maxLength={150}
              placeholder="Post title..."
              className="w-full px-4 py-3 border border-vibe-border rounded-lg text-sm font-mono text-slate-200 focus:outline-none focus:ring-2 focus:ring-vibe-cyan/50 focus:border-vibe-cyan/50 focus:border-transparent"
            />
          </div>
          <select
            value={categoryId ?? ""}
            onChange={(e) => setCategoryId(Number(e.target.value))}
            className="px-4 py-3 border border-vibe-border rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-vibe-cyan/50 focus:border-vibe-cyan/50 focus:border-transparent bg-vibe-surface"
            disabled={channelsLoading}
          >
            {channelsLoading ? (
                <option value="">Loading channels...</option>
              ) : (
                displayChannels.map((ch: Channel) => (
                  <option key={ch.id} value={ch.id}>
                    {ch.name}
                  </option>
                ))
              )}
          </select>
        </div>

        <div className="flex items-center gap-2 border border-vibe-border rounded-t-lg bg-vibe-surface px-4 py-2">
           <button
            type="button"
            onClick={insertCodeBlock}
            className="inline-flex items-center gap-1.5 px-3 py-1.5 text-[11px] font-mono text-slate-300 bg-vibe-surface border border-vibe-border rounded-md hover:bg-vibe-card/50 transition-colors"
            title="Insert code block"
          >
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 20l4-16m4 4l4 4-4 4M6 16l-4-4 4-4" />
            </svg>
            Code Block
          </button>
          <span className="text-[10px] font-mono text-slate-600 ml-auto">
            ~{tokens} tokens
          </span>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-0 border-x border-vibe-border rounded-b-lg overflow-hidden">
            <textarea
            ref={textareaRef}
            value={content}
            onChange={(e) => setContent(e.target.value)}
            placeholder="Write your post content in Markdown..."
            className="w-full h-96 p-4 bg-vibe-bg border-r border-vibe-border font-mono text-sm text-slate-200 resize-none focus:outline-none focus:ring-0 border-0"
          />
          <div className="h-96 overflow-y-auto p-4 bg-vibe-bg prose prose-invert prose-sm max-w-none">
            {content ? (
              <ReactMarkdown remarkPlugins={[remarkGfm]}>
                {content}
              </ReactMarkdown>
            ) : (
              <p className="text-slate-600 italic font-mono text-xs">// Preview will appear here...</p>
            )}
          </div>
        </div>

        {postType === 'prompt' && (
          <div className="flex items-center gap-3 border border-vibe-border rounded-lg bg-vibe-surface px-3 py-2.5">
            <span className="text-[10px] font-mono text-vibe-purple bg-vibe-purple/10 border border-vibe-purple/30 rounded px-1.5 py-0.5 shrink-0">
              New version
            </span>
            <input
              type="text"
              value={changeNote}
              onChange={(e) => setChangeNote(e.target.value)}
              placeholder="Change note, e.g. tighten role instructions (optional)"
              className="flex-1 bg-transparent text-xs font-mono text-slate-200 placeholder-slate-600 focus:outline-none"
            />
          </div>
        )}

        <div className="flex justify-end gap-3">
          <button
            type="button"
            onClick={() => navigate(`/post/${id}`)}
            className="px-6 py-3 border border-vibe-border text-slate-400 text-xs font-mono rounded-lg hover:bg-vibe-surface transition-colors"
          >
            Cancel
          </button>
          <button
            type="submit"
            disabled={submitting}
            className="px-6 py-3 rounded-lg bg-vibe-cyan/20 border border-vibe-cyan/30 text-vibe-cyan text-xs font-mono hover:bg-vibe-cyan/30 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
          >
            {submitting ? 'Saving...' : 'Save Changes'}
          </button>
        </div>
      </form>
    </div>
  );
}

