import { useState, useRef, useCallback, useMemo, useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { apiClient } from '../api/client';
import { useAuthStore } from '../stores/authStore';
import { useChannels, type Channel } from '../api/useChannels';
import { mergeWorkedExample, readWorkedExample } from '../components/promptMetadata';
import { channelLabel } from '../lib/channels';
import { Code2, RotateCw } from 'lucide-react';

function estimateTokens(text: string): number {
  if (!text.trim()) return 0;
  const chineseChars = (text.match(/[\u4e00-\u9fff\u3400-\u4dbf\uf900-\ufaff]/g) || []).length;
  const asciiText = text.replace(/[\u4e00-\u9fff\u3400-\u4dbf\uf900-\ufaff]/g, ' ');
  const asciiWords = asciiText.split(/\s+/).filter(Boolean).length;
  return Math.round(chineseChars * 2 + asciiWords * 1.3);
}

export default function EditPostPage() {
  const { t } = useTranslation('editor');
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
  // Item 5: the worked example lives inside promptMetadata, so it is edited as
  // two fields and merged back on save, leaving every other key untouched.
  const [exampleInput, setExampleInput] = useState('');
  const [exampleOutput, setExampleOutput] = useState('');
  const [changeNote, setChangeNote] = useState('');
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const { data: channels, isLoading: channelsLoading } = useChannels();

  // Unsaved-changes guard: dirty once fields drift from the loaded baseline;
  // beforeunload warns on reload/close, Cancel asks before discarding.
  const baseline = useRef({ title: '', content: '', changeNote: '', exampleInput: '', exampleOutput: '' });
  const dirty = title !== baseline.current.title
    || content !== baseline.current.content
    || changeNote !== baseline.current.changeNote
    || exampleInput !== baseline.current.exampleInput
    || exampleOutput !== baseline.current.exampleOutput;
  useEffect(() => {
    if (!dirty) return;
    const handler = (e: BeforeUnloadEvent) => {
      e.preventDefault();
    };
    window.addEventListener('beforeunload', handler);
    return () => window.removeEventListener('beforeunload', handler);
  }, [dirty]);

  const handleCancel = () => {
    if (dirty && !window.confirm(t('discardConfirm'))) return;
    navigate(`/post/${id}`);
  };

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
        const storedExample = readWorkedExample(post.promptMetadata);
        setExampleInput(storedExample.exampleInput ?? '');
        setExampleOutput(storedExample.exampleOutput ?? '');
        baseline.current = {
          title: post.title || '',
          content: post.content || '',
          changeNote: '',
          exampleInput: storedExample.exampleInput ?? '',
          exampleOutput: storedExample.exampleOutput ?? '',
        };
      } catch {
        setError(t('errors.failedToLoad'));
      } finally {
        setLoading(false);
      }
    })();
  }, [id, t]);

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
      setError(t('errors.titleAndContent'));
      return;
    }
    if (title.trim().length > 150) {
      setError(t('errors.titleTooLong'));
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
        body.promptMetadata = mergeWorkedExample(promptMetadata, exampleInput, exampleOutput);
      }
      await apiClient.put(`/posts/${id}`, body);
      navigate(`/post/${id}`);
    } catch (err: any) {
      setError(err.response?.data?.message || t('errors.failedToUpdate'));
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) {
    return (
      <div className="mx-auto max-w-5xl px-4 py-8">
        <div className="animate-pulse motion-reduce:animate-none space-y-4">
          <div className="h-3 w-1/3 rounded bg-vibe-surface" />
          <div className="h-96 rounded bg-vibe-surface" />
        </div>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-5xl px-4 py-8">
      <h1 className="mb-8 text-xl font-semibold text-vibe-text">{t('editTitle')}</h1>

      <form onSubmit={handleSubmit} className="space-y-6">
      {error && (
          <div role="alert" className="rounded-md border border-vibe-danger/40 bg-vibe-danger/5 px-4 py-3 text-xs text-vibe-danger">
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
              placeholder={t('editTitlePlaceholder')}
              className="w-full rounded-md border border-vibe-border bg-vibe-bg px-4 py-3 text-sm text-vibe-text placeholder-vibe-faint transition-colors focus:border-vibe-accent/50 focus:outline-none focus:ring-1 focus:ring-vibe-accent/50"
            />
          </div>
          <select
            value={categoryId ?? ""}
            onChange={(e) => setCategoryId(Number(e.target.value))}
            className="rounded-md border border-vibe-border bg-vibe-surface px-4 py-3 text-sm text-vibe-text transition-colors focus:border-vibe-accent/50 focus:outline-none focus:ring-1 focus:ring-vibe-accent/50"
            disabled={channelsLoading}
          >
            {channelsLoading ? (
                <option value="">{t('loadingChannels')}</option>
              ) : (
                displayChannels.map((ch: Channel) => (
                  <option key={ch.id} value={ch.id}>
                    {channelLabel(ch.slug) || ch.name}
                  </option>
                ))
              )}
          </select>
        </div>

        <div className="flex items-center gap-2 rounded-t-lg border border-vibe-border bg-vibe-surface px-4 py-2">
           <button
            type="button"
            onClick={insertCodeBlock}
            className="inline-flex items-center gap-1.5 rounded-md border border-vibe-border bg-vibe-card px-3 py-1.5 text-xs text-vibe-muted transition-colors hover:bg-vibe-surface hover:text-vibe-text"
            title={t('insertCodeTitle')}
          >
            <Code2 className="h-3.5 w-3.5" />
            {t('codeBlock')}
          </button>
          <span className="ml-auto text-xs tabular-nums text-vibe-faint">
            {t('tokensLabel', { n: tokens })}
          </span>
        </div>

        <div className="grid grid-cols-1 gap-0 overflow-hidden rounded-b-lg border border-vibe-border border-t-0 md:grid-cols-2 md:border-x">
            <textarea
            ref={textareaRef}
            value={content}
            onChange={(e) => setContent(e.target.value)}
            placeholder={t('contentEditPlaceholder')}
            className="h-96 w-full resize-none border-0 bg-vibe-bg p-4 font-mono text-sm text-vibe-text placeholder-vibe-faint focus:outline-none focus:ring-0 md:border-r md:border-vibe-border"
          />
          <div className="h-96 overflow-y-auto bg-vibe-bg p-4 prose prose-sm max-w-none dark:prose-invert">
            {content ? (
              <ReactMarkdown remarkPlugins={[remarkGfm]}>
                {content}
              </ReactMarkdown>
            ) : (
              <p className="text-xs italic text-vibe-faint">{t('previewPlaceholder')}</p>
            )}
          </div>
        </div>

        {postType === 'prompt' && (
          <div className="space-y-3">
            <div className="flex items-center gap-3 rounded-lg border border-vibe-border bg-vibe-surface px-3 py-2.5">
              <span className="shrink-0 rounded-full border border-vibe-accent/40 bg-vibe-accent/10 px-1.5 py-0.5 text-xs text-vibe-accent">
                {t('newVersion')}
              </span>
              <input
                type="text"
                value={changeNote}
                onChange={(e) => setChangeNote(e.target.value)}
                placeholder={t('changeNotePlaceholder')}
                className="flex-1 bg-transparent text-sm text-vibe-text placeholder-vibe-faint focus:outline-none"
              />
            </div>
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              <div className="space-y-1.5">
                <label htmlFor="edit-example-input" className="block text-xs text-vibe-muted">{t('promptConfig.exampleInput')}</label>
                <textarea
                  id="edit-example-input"
                  value={exampleInput}
                  onChange={(e) => setExampleInput(e.target.value)}
                  placeholder={t('promptConfig.exampleInputPlaceholder')}
                  rows={3}
                  className="w-full resize-none rounded-md border border-vibe-border bg-vibe-bg px-3 py-2 text-sm text-vibe-text placeholder-vibe-faint transition-colors focus:border-vibe-accent/50 focus:outline-none focus:ring-1 focus:ring-vibe-accent/50"
                />
              </div>
              <div className="space-y-1.5">
                <label htmlFor="edit-example-output" className="block text-xs text-vibe-muted">{t('promptConfig.exampleOutput')}</label>
                <textarea
                  id="edit-example-output"
                  value={exampleOutput}
                  onChange={(e) => setExampleOutput(e.target.value)}
                  placeholder={t('promptConfig.exampleOutputPlaceholder')}
                  rows={3}
                  className="w-full resize-none rounded-md border border-vibe-border bg-vibe-bg px-3 py-2 text-sm text-vibe-text placeholder-vibe-faint transition-colors focus:border-vibe-accent/50 focus:outline-none focus:ring-1 focus:ring-vibe-accent/50"
                />
              </div>
            </div>
          </div>
        )}

        <div className="flex justify-end gap-3">
          <button
            type="button"
            onClick={handleCancel}
            className="rounded-md border border-vibe-border px-6 py-3 text-xs text-vibe-muted transition-colors hover:bg-vibe-surface hover:text-vibe-text"
          >
            {t('cancel')}
          </button>
          <button
            type="submit"
            disabled={submitting}
            className="inline-flex items-center gap-1.5 rounded-md bg-vibe-accent px-6 py-3 text-xs font-medium text-white transition-colors hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {submitting && <RotateCw className="h-3.5 w-3.5 animate-spin motion-reduce:animate-none" />}
            {submitting ? t('saving') : t('saveChanges')}
          </button>
        </div>
      </form>
    </div>
  );
}
