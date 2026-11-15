import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { apiClient } from '../api/client';
import { serverErrorMessage } from '../api/serverErrorMessage';
import { useToastStore } from '../stores/toastStore';

interface PendingPost {
  id: number;
  title: string;
  summary: string;
  authorName: string;
  categoryName: string | null;
  createTime: string;
  status: number;
  safetySeverity: string | null;
  safetyClassification: string | null;
  safetyIsApproved: number | null;
}

interface PendingComment {
  id: number;
  postId: number;
  postTitle: string | null;
  authorName: string | null;
  content: string;
  createTime: string;
}

interface PendingPostsResponse {
  code: number;
  message: string;
  data: PendingPost[];
}

interface PendingCommentsResponse {
  code: number;
  message: string;
  data: PendingComment[];
}

/** The v2 severity map: safe is calm, injection and harm are loud. */
const safetyTagColors: Record<string, string> = {
  'Prompt injection': 'bg-vibe-danger/10 text-vibe-danger ring-vibe-danger/40',
  'Harmful content': 'bg-vibe-danger/10 text-vibe-danger ring-vibe-danger/40',
  Spam: 'bg-vibe-high/10 text-vibe-high ring-vibe-high/40',
  Safe: 'bg-vibe-success/10 text-vibe-success ring-vibe-success/30',
};

const safetyStatusMap: Record<string, { labelKey: string; dotColor: string } | null> = {
  'Prompt injection': { labelKey: 'safety.pendingReview', dotColor: 'bg-vibe-warning' },
  'Harmful content': { labelKey: 'safety.autoHidden', dotColor: 'bg-vibe-danger' },
  Spam: { labelKey: 'safety.autoHidden', dotColor: 'bg-vibe-danger' },
  Safe: { labelKey: 'safety.clean', dotColor: 'bg-vibe-success' },
};

function SafetyBadge({ classification }: { classification: string | null }) {
  const { t } = useTranslation('admin');
  if (!classification) return null;

  const tagColor =
    safetyTagColors[classification] || 'bg-vibe-surface text-vibe-muted ring-vibe-border';
  const statusInfo = safetyStatusMap[classification];

  return (
    <div className="flex items-center gap-3">
      <span
        className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ring-1 ring-inset ${tagColor}`}
      >
        {classification}
      </span>
      {statusInfo && (
        <span className="inline-flex items-center gap-1.5 text-xs text-vibe-muted">
          <span className={`inline-block h-2 w-2 rounded-full ${statusInfo.dotColor}`} />
          {t(statusInfo.labelKey)}
        </span>
      )}
    </div>
  );
}

export default function AuditPage() {
  const { t } = useTranslation('admin');
  const queryClient = useQueryClient();
  const addToast = useToastStore((s) => s.addToast);
  const [rejectId, setRejectId] = useState<number | null>(null);
  const [rejectReason, setRejectReason] = useState('');
  // Rejection here is irreversible (the backend has no un-reject), so the
  // comment button is a two-step confirm — posts get a reason field instead
  // because their audit endpoint records one.
  const [commentRejectId, setCommentRejectId] = useState<number | null>(null);

  const { data, isLoading, isError, error } = useQuery<PendingPostsResponse>({
    queryKey: ['admin', 'pending-posts'],
    queryFn: () =>
      apiClient.get('/admin/pending-posts').then((r) => r.data as PendingPostsResponse),
  });

  const { data: commentsData } = useQuery<PendingCommentsResponse>({
    queryKey: ['admin', 'pending-comments'],
    queryFn: () =>
      apiClient.get('/admin/audit/comments').then((r) => r.data as PendingCommentsResponse),
  });

  const commentAuditMutation = useMutation({
    mutationFn: ({ id, action }: { id: number; action: 'APPROVED' | 'REJECTED' }) =>
      apiClient.post(`/admin/audit/comments/${id}/${action === 'APPROVED' ? 'approve' : 'reject'}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'pending-comments'] });
    },
    onError: (err) => {
      addToast(serverErrorMessage(err, t('commentAuditFailed')), 'error');
    },
  });

  const handleCommentAudit = (id: number, action: 'APPROVED' | 'REJECTED') => {
    commentAuditMutation.mutate({ id, action }, {
      onSuccess: () => {
        setCommentRejectId(null);
        addToast(
          action === 'APPROVED' ? t('commentApproved') : t('commentRejected'),
          action === 'APPROVED' ? 'success' : 'info'
        );
      },
    });
  };

  const auditMutation = useMutation({
    mutationFn: ({
      id,
      action,
      reason,
    }: {
      id: number;
      action: 'APPROVED' | 'REJECTED';
      reason?: string;
    }) => apiClient.post(`/admin/audit/${id}`, { action, reason }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'pending-posts'] });
      setRejectId(null);
      setRejectReason('');
    },
    onError: (err) => {
      addToast(serverErrorMessage(err, t('auditFailed')), 'error');
    },
  });

  const handleApprove = (id: number) => {
    auditMutation.mutate({ id, action: 'APPROVED' }, {
      onSuccess: () => addToast(t('postApproved'), 'success'),
    });
  };

  const handleReject = (id: number) => {
    auditMutation.mutate({ id, action: 'REJECTED', reason: rejectReason }, {
      onSuccess: () => addToast(t('postRejected'), 'info'),
    });
  };

  if (isLoading) {
    return (
      <div>
        <h1 className="mb-8 text-lg font-semibold text-vibe-text">{t('auditTitle')}</h1>
        <div className="flex items-center justify-center py-20">
          <div className="h-8 w-8 animate-spin motion-reduce:animate-none rounded-full border-b-2 border-vibe-accent" />
        </div>
      </div>
    );
  }

  if (isError) {
    return (
      <div>
        <h1 className="mb-8 text-lg font-semibold text-vibe-text">{t('auditTitle')}</h1>
        <div role="alert" className="rounded-lg border border-vibe-danger/40 bg-vibe-danger/5 p-6 text-center">
          <p className="text-sm text-vibe-danger">{t('failedToLoadPosts')}</p>
          <p className="mt-1 text-xs text-vibe-danger">
            {serverErrorMessage(error, t('unexpectedError'))}
          </p>
        </div>
      </div>
    );
  }

  const posts = data?.data ?? [];
  const pendingComments = commentsData?.data ?? [];

  return (
    <div>
      <div className="mb-8 flex items-center justify-between">
        <div>
          <h1 className="text-lg font-semibold text-vibe-text">{t('auditTitle')}</h1>
          <p className="mt-1 text-xs text-vibe-muted">
            {t('pendingSummary', {
              posts: posts.length,
              comments: pendingComments.length > 0
                ? t('pendingCommentsSuffix', { n: pendingComments.length })
                : '',
            })}
          </p>
        </div>
      </div>

      {posts.length === 0 ? (
        <div className="rounded-lg border border-vibe-border bg-vibe-surface p-12 text-center">
          <p className="text-sm text-vibe-muted">{t('noPendingPosts')}</p>
          <p className="mt-1 text-xs text-vibe-faint">{t('allCaughtUp')}</p>
        </div>
      ) : (
      <div className="space-y-4">
        {posts.map((post) => (
          <div
            key={post.id}
            className="rounded-lg border border-vibe-border bg-vibe-surface p-6"
          >
            <div className="flex items-start justify-between gap-6">
              <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-center gap-3">
                  <h3 className="truncate text-sm font-semibold text-vibe-text">
                    {post.title}
                  </h3>
                  <SafetyBadge
                    classification={post.safetyClassification}

                  />
                </div>
                <div className="mt-1 flex items-center gap-4 text-xs text-vibe-muted">
                  <span>{t('byAuthor', { name: post.authorName })}</span>
                  {post.categoryName && (
                    <span className="inline-flex items-center gap-1">
                      <span className="h-1.5 w-1.5 rounded-full bg-vibe-faint" />
                      {post.categoryName}
                    </span>
                  )}
                  <span className="inline-flex items-center gap-1">
                    <span className="h-1.5 w-1.5 rounded-full bg-vibe-faint" />
                    <span className="tabular-nums">
                      {new Date(post.createTime).toLocaleDateString(undefined, {
                        year: 'numeric',
                        month: 'short',
                        day: 'numeric',
                        hour: '2-digit',
                        minute: '2-digit',
                      })}
                    </span>
                  </span>
                </div>
                <p className="mt-3 line-clamp-3 text-xs leading-relaxed text-vibe-muted">
                {post.summary || t('noContentPreview')}
                </p>
              </div>

              <div className="flex shrink-0 items-center gap-2">
                <button
                  type="button"
                  onClick={() => handleApprove(post.id)}
                  disabled={auditMutation.isPending}
                  className="rounded-md border border-vibe-success/40 bg-vibe-success/10 px-4 py-2 text-xs font-medium text-vibe-success transition-colors hover:bg-vibe-success/20 disabled:cursor-not-allowed disabled:opacity-50"
                >
                  {t('approve')}
                </button>
                <button
                  type="button"
                  onClick={() => setRejectId(rejectId === post.id ? null : post.id)}
                  className="rounded-md border border-vibe-danger/40 bg-vibe-danger/10 px-4 py-2 text-xs font-medium text-vibe-danger transition-colors hover:bg-vibe-danger/20"
                >
                  {t('reject')}
                </button>
              </div>
            </div>

            {rejectId === post.id && (
              <div className="mt-4 border-t border-vibe-border pt-4">
                <label className="mb-2 block text-xs text-vibe-muted">
                  {t('rejectReason')}
                </label>
                <div className="flex gap-3">
                  <input
                    type="text"
                    value={rejectReason}
                    onChange={(e) => setRejectReason(e.target.value)}
                    placeholder={t('reasonPlaceholder')}
                    className="flex-1 rounded-md border border-vibe-border bg-vibe-bg px-3 py-2 text-sm text-vibe-text placeholder-vibe-faint transition-colors focus:border-vibe-accent/50 focus:outline-none focus:ring-1 focus:ring-vibe-accent/50"
                    onKeyDown={(e) => {
                      if (e.key === 'Enter' && rejectReason.trim()) {
                        handleReject(post.id);
                      }
                    }}
                  />
                  <button
                    type="button"
                    onClick={() => handleReject(post.id)}
                    disabled={auditMutation.isPending || !rejectReason.trim()}
                    className="rounded-md border border-vibe-danger/40 bg-vibe-danger/10 px-4 py-2 text-xs font-medium text-vibe-danger transition-colors hover:bg-vibe-danger/20 disabled:cursor-not-allowed disabled:opacity-50"
                  >
                    {t('confirmReject')}
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      setRejectId(null);
                      setRejectReason('');
                    }}
                    className="rounded-md px-4 py-2 text-xs text-vibe-muted transition-colors hover:bg-vibe-card"
                  >
                    {t('cancel')}
                  </button>
                </div>
              </div>
            )}
          </div>
        ))}
      </div>
      )}

      {/* HELD COMMENTS — the status-2 queue shared by the DFA path and the comment-safety path */}
      <div className="mt-10">
        <h2 className="mb-4 text-sm font-semibold text-vibe-text">
          {t('heldComments')}
        </h2>
        {pendingComments.length === 0 ? (
          <p className="text-xs text-vibe-faint">
            {t('noHeldComments')}
          </p>
        ) : (
          <div className="space-y-3">
            {pendingComments.map((comment) => (
              <div
                key={comment.id}
                className="rounded-lg border border-vibe-border bg-vibe-surface p-4"
              >
                <div className="flex items-start justify-between gap-4">
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-center gap-3 text-xs text-vibe-muted">
                      <span>{t('byAuthor', { name: comment.authorName || t('unknownAuthor') })}</span>
                      <span className="inline-flex items-center gap-1">
                        <span className="h-1.5 w-1.5 rounded-full bg-vibe-faint" />
                        {t('on', { title: comment.postTitle || `#${comment.postId}` })}
                      </span>
                      <span className="inline-flex items-center gap-1">
                        <span className="h-1.5 w-1.5 rounded-full bg-vibe-faint" />
                        <span className="tabular-nums">{new Date(comment.createTime).toLocaleString()}</span>
                      </span>
                    </div>
                    <p className="mt-2 line-clamp-3 text-sm leading-relaxed text-vibe-text">
                      {comment.content}
                    </p>
                  </div>
                  <div className="flex shrink-0 items-center gap-2">
                    <button
                      type="button"
                      onClick={() => handleCommentAudit(comment.id, 'APPROVED')}
                      disabled={commentAuditMutation.isPending}
                      className="rounded-md border border-vibe-success/40 bg-vibe-success/10 px-3 py-1.5 text-xs font-medium text-vibe-success transition-colors hover:bg-vibe-success/20 disabled:cursor-not-allowed disabled:opacity-50"
                    >
                      {t('approve')}
                    </button>
                    <button
                      type="button"
                      onClick={() => setCommentRejectId(commentRejectId === comment.id ? null : comment.id)}
                      disabled={commentAuditMutation.isPending}
                      className="rounded-md border border-vibe-danger/40 bg-vibe-danger/10 px-3 py-1.5 text-xs font-medium text-vibe-danger transition-colors hover:bg-vibe-danger/20 disabled:cursor-not-allowed disabled:opacity-50"
                    >
                      {t('reject')}
                    </button>
                  </div>
                </div>
                {commentRejectId === comment.id && (
                  <div className="mt-3 flex flex-wrap items-center gap-3 border-t border-vibe-border pt-3">
                    <p className="min-w-[200px] flex-1 text-xs text-vibe-danger">
                      {t('rejectCommentWarning')}
                    </p>
                    <button
                      type="button"
                      onClick={() => handleCommentAudit(comment.id, 'REJECTED')}
                      disabled={commentAuditMutation.isPending}
                      className="rounded-md border border-vibe-danger/40 bg-vibe-danger/10 px-3 py-1.5 text-xs font-medium text-vibe-danger transition-colors hover:bg-vibe-danger/20 disabled:cursor-not-allowed disabled:opacity-50"
                    >
                      {t('confirmReject')}
                    </button>
                    <button
                      type="button"
                      onClick={() => setCommentRejectId(null)}
                      className="rounded-md px-3 py-1.5 text-xs text-vibe-muted transition-colors hover:bg-vibe-card"
                    >
                      {t('cancel')}
                    </button>
                  </div>
                )}
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
