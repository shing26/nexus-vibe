import { Link, useNavigate } from 'react-router-dom';
import type { PostPageVo } from '../types/post';
import { Heart, MessageCircle, Eye, Copy, Check, GitFork, Link2 } from 'lucide-react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { apiClient } from '../api/client';
import { useAuthStore } from '../stores/authStore';
import { useToastStore } from '../stores/toastStore';
import { AI_REVIEW, POST_STATUS, aiReviewLabel, postStatusLabel } from '../lib/postStatus';
import { timeAgo } from '../lib/time';
import { readRole } from './promptMetadata';

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
 * The first fenced block (or first plain lines) of the body — used only to feed
 * the copy-prompt action now that rows are compact and carry no code preview.
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
 * and the AI score on the metadata line; counts right-aligned as tabular numerals.
 */
export default function PostCard({ post }: PostCardProps) {
  const { t } = useTranslation(['post', 'common']);
  const navigate = useNavigate();
  const addToast = useToastStore((s) => s.addToast);
  const user = useAuthStore((s) => s.user);
  const [copied, setCopied] = useState(false);
  const [forking, setForking] = useState(false);
  const preview = getPreview(post);

  const aiScore = Math.round((post.aiReviewScore ?? 0) * 10);
  const role = readRole(post.promptMetadata);

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
          <Link
            to={'/post/' + post.id}
            className="block truncate text-sm text-vibe-text hover:text-vibe-accent"
          >
            {post.title}
          </Link>
          <div className="mt-1 flex flex-wrap items-center gap-x-2.5 gap-y-1 text-xs text-vibe-muted">
            <span className="rounded-full border border-vibe-border bg-vibe-bg px-1.5 py-0.5 text-xs text-vibe-muted">
              {post.categoryName}
            </span>
            {post.postType === 'prompt' && (
              <span className="rounded-full border border-vibe-border px-1.5 py-0.5 text-xs text-vibe-accent">
                {t('post:template')}
              </span>
            )}
            {post.aiReviewed === AI_REVIEW.REVIEWED && aiScore > 0 && (
              <span className="rounded-full border border-vibe-border px-1.5 py-0.5 tabular-nums text-vibe-accent">
                {t('post:aiScore', { score: aiScore })}
              </span>
            )}
            <span>{post.authorName}</span>
            {role && (
              <span className="max-w-[120px] truncate" title={role}>
                {role}
              </span>
            )}
            <span className="tabular-nums">{timeAgo(post.createTime)}</span>
            <span className="ml-auto flex shrink-0 items-center gap-3 tabular-nums">
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
        </div>
        {/* Quick actions — hover reveal, on the right of the row */}
        {hasActions && (
          <div className="flex shrink-0 items-center gap-1 opacity-0 transition-opacity focus-within:opacity-100 group-hover:opacity-100 motion-reduce:transition-none">
            {preview && (
              <button
                type="button"
                onClick={handleCopyPrompt}
                title={t('post:actions.copyPromptTitle')}
                className="inline-flex items-center gap-1 rounded-md border border-vibe-border bg-vibe-card px-2 py-1 text-xs text-vibe-muted hover:border-vibe-accent/40 hover:text-vibe-accent"
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
                className="inline-flex items-center gap-1 rounded-md border border-vibe-border bg-vibe-card px-2 py-1 text-xs text-vibe-muted hover:border-vibe-accent/40 hover:text-vibe-accent disabled:opacity-50"
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
          className="shrink-0 text-vibe-faint opacity-0 transition-opacity hover:text-vibe-text focus-visible:opacity-100 group-hover:opacity-100 motion-reduce:transition-none"
        >
          <Link2 className="h-3.5 w-3.5" />
          <span className="sr-only">{t('post:actions.copyLinkTitle')}</span>
        </button>
      </div>
    </article>
  );
}
