import { Link, useNavigate } from 'react-router-dom';
import type { PostPageVo } from '../types/post';
import { Heart, MessageCircle, Eye, Copy, Check, GitFork, Link2, ChevronDown } from 'lucide-react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { apiClient } from '../api/client';
import { useAuthStore } from '../stores/authStore';
import { useToastStore } from '../stores/toastStore';
import { AI_REVIEW, POST_STATUS, aiReviewLabel, postStatusLabel } from '../lib/postStatus';
import { timeAgo } from '../lib/time';

interface PostCardProps {
  post: PostPageVo;
}

const POST_STATUS_TONE: Record<number, string> = {
  [POST_STATUS.ACTIVE]: 'border-vibe-success/40 bg-vibe-success/10 text-vibe-success',
  [POST_STATUS.PENDING_REVIEW]: 'border-vibe-warning/40 bg-vibe-warning/10 text-vibe-warning',
  [POST_STATUS.REJECTED]: 'border-vibe-danger/40 bg-vibe-danger/10 text-vibe-danger',
};

const AI_REVIEW_TONE: Record<number, string> = {
  [AI_REVIEW.NOT_REVIEWED]: 'text-vibe-faint',
  [AI_REVIEW.REVIEWED]: 'text-vibe-accent',
  [AI_REVIEW.REVIEWING]: 'text-vibe-warning',
  [AI_REVIEW.FAILED]: 'text-vibe-danger',
};

/**
 * The state of one row, as words. Shared by `PostCard` and `UserProfilePage` so the
 * codes are read through `lib/postStatus` in both places and an unrecognised code
 * still renders a visible label. The reason is plain text in the row — a held post
 * must not look like an ordinary post with its state hidden behind a hover.
 */
export function PostStateBadges({ post }: { post: PostPageVo }) {
  const { t } = useTranslation(['post', 'common']);
  const reason = post.status === POST_STATUS.REJECTED ? post.rejectionReason : null;
  const score = post.aiReviewScore > 0 ? Math.round(post.aiReviewScore * 10) : null;
  const aiText =
    post.aiReviewed === AI_REVIEW.REVIEWED && score != null
      ? `${aiReviewLabel(post.aiReviewed)} · ${score}`
      : aiReviewLabel(post.aiReviewed);
  return (
    <span className="inline-flex flex-wrap items-center gap-1.5">
      <span
        data-testid="post-status-badge"
        className={
          'rounded-full border px-2 py-0.5 text-xs ' +
          (POST_STATUS_TONE[post.status] ?? 'border-vibe-border bg-vibe-card text-vibe-muted')
        }
      >
        {postStatusLabel(post.status)}
      </span>
      <span
        data-testid="ai-review-badge"
        className={'text-xs ' + (AI_REVIEW_TONE[post.aiReviewed] ?? 'text-vibe-muted')}
      >
        {t('common:stateBadgePrefix')}
        {aiText}
      </span>
      {reason && (
        <span
          data-testid="rejection-reason"
          className="basis-full text-xs text-vibe-danger"
        >
          {t('post:rejectReasonPrefix')}
          {reason}
        </span>
      )}
    </span>
  );
}

function stripHtml(text: string): string {
  return text?.replace(/<[^>]*>/g, '') ?? '';
}

interface PreviewInfo {
  label: string;
  code: string;
}

/**
 * The first fenced block (or first plain lines) of the body. It feeds the copy
 * action and the row's collapsible preview; the row itself stays one line until
 * the reader asks for the code, so the list keeps its Discourse density.
 */
function getPreview(post: PostPageVo): PreviewInfo | null {
  const content = post.content || '';
  const fenced = content.match(/```([a-zA-Z0-9_+-]*)\n([\s\S]*?)```/);
  if (fenced) {
    return { label: fenced[1] || 'code', code: fenced[2].trim() };
  }
  const plain = stripHtml(content).split('\n').map((l) => l.trim()).filter(Boolean).slice(0, 2);
  if (plain.length === 0) return null;
  return { label: post.postType === 'prompt' ? 'prompt' : 'text', code: plain.join('\n') };
}

/**
 * One topic-row of the v2 list: full-width, separated by the row's own bottom
 * border, no card box. Title on the first line; channel, author, relative time
 * and the right-aligned counts on one metadata line. The role and template type
 * live on the detail page now — the row carries only what a reader scans for.
 * The first code block is one chevron away and collapsed by default.
 */
export default function PostCard({ post }: PostCardProps) {
  const { t } = useTranslation(['post', 'common']);
  const navigate = useNavigate();
  const addToast = useToastStore((s) => s.addToast);
  const user = useAuthStore((s) => s.user);
  const [copied, setCopied] = useState(false);
  const [forking, setForking] = useState(false);
  const [previewOpen, setPreviewOpen] = useState(false);
  const preview = getPreview(post);

  const aiScore = Math.round((post.aiReviewScore ?? 0) * 10);
  /**
   * The row's AI credential. Spike rule: only what the list payload really
   * carries — the reviewed state code and the score. Severity and retry counts
   * have no list field yet, so no invented color encodes them; the check mark
   * means exactly "this row's review completed and was recorded" (aiReviewed
   * REVIEWED), nothing more.
   */
  const credential =
    post.aiReviewed === AI_REVIEW.REVIEWED && aiScore > 0 ? (
      <span
        data-testid="ai-credential"
        title={t('post:credential.scoreTitle', { score: aiScore })}
        className="inline-flex items-center gap-1 rounded border border-vibe-border bg-vibe-bg px-1.5 py-0.5 text-[11px]"
      >
        <Check className="h-3 w-3 text-vibe-success" aria-hidden="true" />
        <span className="tabular-nums text-vibe-text">{aiScore}</span>
        <span className="text-vibe-faint">{t('post:credential.verified')}</span>
      </span>
    ) : post.aiReviewed === AI_REVIEW.REVIEWING ? (
      <span data-testid="ai-credential" className="inline-flex items-center gap-1 text-xs text-vibe-warning">
        <span aria-hidden="true" className="h-1.5 w-1.5 rounded-full bg-vibe-warning" />
        {t('post:credential.reviewing')}
      </span>
    ) : post.aiReviewed === AI_REVIEW.FAILED ? (
      <span data-testid="ai-credential" className="inline-flex items-center gap-1 text-xs text-vibe-danger">
        <span aria-hidden="true" className="h-1.5 w-1.5 rounded-full bg-vibe-danger" />
        {t('post:credential.failed')}
      </span>
    ) : null;

  const handleCopyLink = (e: React.MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();
    navigator.clipboard.writeText(window.location.origin + '/post/' + post.id);
    addToast(t('common:toast.linkCopied'), 'success');
  };

  const handleCopyPrompt = (e: React.MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();
    if (!preview) return;
    navigator.clipboard.writeText(preview.code);
    setCopied(true);
    addToast(t('common:toast.promptCopied'), 'success');
    setTimeout(() => setCopied(false), 2000);
  };

  const handleFork = async (e: React.MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();
    if (!user) {
      navigate('/login');
      return;
    }
    setForking(true);
    try {
      const res = await apiClient.post('/posts/' + post.id + '/fork');
      const postId = res.data?.data?.postId;
      // Checked rather than assumed. This used to read `res.data.data.postId`
      // straight, so an envelope with a null body reached its catch by throwing a
      // TypeError, which reported "Fork failed" for a response the server said was
      // fine and would have kept doing so if the shape ever changed again.
      if (!postId) {
        addToast(t('common:toast.forkFailed'), 'error');
        return;
      }
      addToast(t('common:toast.templateForked'), 'success');
      navigate('/post/' + postId);
    } catch {
      addToast(t('common:toast.forkFailed'), 'error');
    } finally {
      setForking(false);
    }
  };

  const hasActions = !!(preview || post.postType === 'prompt');

  return (
    <article className="group relative border-b border-vibe-border px-3 py-2.5 transition-colors hover:bg-vibe-surface">
      <div className="flex items-start gap-2">
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-1.5">
            <Link
              to={'/post/' + post.id}
              className="press-feedback block min-w-0 flex-1 truncate text-sm text-vibe-text hover:text-vibe-accent"
            >
              {post.title}
            </Link>
            {preview && (
              <button
                type="button"
                onClick={() => setPreviewOpen((v) => !v)}
                aria-expanded={previewOpen}
                aria-label={t('post:actions.togglePreview', { label: preview.label })}
                title={t('post:actions.togglePreview', { label: preview.label })}
                className="press-feedback shrink-0 rounded p-0.5 text-vibe-faint transition-colors hover:text-vibe-accent"
              >
                <ChevronDown
                  className={
                    'h-3.5 w-3.5 transition-transform motion-reduce:transition-none ' +
                    (previewOpen ? 'rotate-180' : '')
                  }
                />
              </button>
            )}
          </div>
          {/* One line at every width: the author truncates, nothing wraps, and the
              counts stay right-aligned. Role/template moved to the detail page. */}
          <div className="mt-1 flex items-center gap-2 overflow-hidden whitespace-nowrap text-xs text-vibe-muted">
            <span className="max-w-[40%] shrink-0 truncate rounded-full border border-vibe-border bg-vibe-bg px-1.5 py-0.5 text-xs text-vibe-muted">
              {post.categoryName}
            </span>
            <span className="min-w-0 truncate">{post.authorName}</span>
            <span className="shrink-0 tabular-nums">{timeAgo(post.createTime)}</span>
            <span className="ml-auto flex shrink-0 items-center gap-2.5 tabular-nums">
              {credential}
              <span className="flex items-center gap-1">
                <Heart className="h-3 w-3" />
                {post.likeCount}
              </span>
              <span className="flex items-center gap-1">
                <MessageCircle className="h-3 w-3" />
                {post.commentCount}
              </span>
              <span className="flex items-center gap-1">
                <Eye className="h-3 w-3" />
                {post.viewCount}
              </span>
            </span>
          </div>
          {preview && previewOpen && (
            <div className="mt-2 overflow-hidden rounded-md border border-vibe-border bg-vibe-bg">
              <div className="flex items-center justify-between border-b border-vibe-border px-2 py-1 text-[11px] text-vibe-faint">
                <span className="font-mono">{preview.label}</span>
                <span>{t('post:actions.preview')}</span>
              </div>
              {/* A plain pre, not CodeBlock: the list must stay light, and the
                  syntax-highlighted rendering belongs to the detail page. */}
              <pre className="max-h-48 overflow-auto p-2 font-mono text-xs leading-relaxed text-vibe-text">
                {preview.code}
              </pre>
            </div>
          )}
        </div>
        {/* Quick actions — hover reveal, on the right of the row. They return at
            `sm`: on a touch-width row they are unreachable anyway, and reserving
            their width would squeeze the single metadata line until the counts
            left the viewport. */}
        {hasActions && (
          <div className="hidden shrink-0 items-center gap-1 opacity-0 transition-opacity focus-within:opacity-100 group-hover:opacity-100 motion-reduce:transition-none sm:flex">
            {preview && (
              <button
                type="button"
                onClick={handleCopyPrompt}
                title={t('post:actions.copyPromptTitle')}
                className="press-feedback inline-flex items-center gap-1 rounded-md border border-vibe-border bg-vibe-card px-2 py-1 text-xs text-vibe-muted hover:border-vibe-accent/40 hover:text-vibe-accent"
              >
                {copied ? <Check className="h-3 w-3 text-vibe-success" /> : <Copy className="h-3 w-3" />}
                {copied ? t('post:actions.copied') : t('post:actions.copy')}
              </button>
            )}
            {post.postType === 'prompt' && (
              <button
                type="button"
                onClick={handleFork}
                disabled={forking}
                title={t('post:actions.forkTitle')}
                className="press-feedback inline-flex items-center gap-1 rounded-md border border-vibe-border bg-vibe-card px-2 py-1 text-xs text-vibe-muted hover:border-vibe-accent/40 hover:text-vibe-accent disabled:opacity-50"
              >
                <GitFork className="h-3 w-3" />
                {forking ? t('post:actions.forking') : t('post:actions.fork')}
              </button>
            )}
          </div>
        )}
        <button
          type="button"
          onClick={handleCopyLink}
          title={t('post:actions.copyLinkTitle')}
          className="press-feedback hidden shrink-0 text-vibe-faint opacity-0 transition-opacity hover:text-vibe-text focus-visible:opacity-100 group-hover:opacity-100 motion-reduce:transition-none sm:block"
        >
          <Link2 className="h-3.5 w-3.5" />
          <span className="sr-only">{t('post:actions.copyLinkTitle')}</span>
        </button>
      </div>
    </article>
  );
}
