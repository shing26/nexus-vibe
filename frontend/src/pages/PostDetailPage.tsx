import { useState, useMemo, useEffect, useRef } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { Heart, Share2, MessageCircle, Eye, Copy, Check, ChevronDown, ChevronUp, GitFork, History, Pencil, Trash2, Loader2 } from 'lucide-react';
import { apiClient } from '../api/client';
import type { ApiResponse } from '../api/client';
import { useAuthStore } from '../stores/authStore';
import { useToastStore } from '../stores/toastStore';
import { AI_REVIEW, POST_STATUS, postStatusLabel } from '../lib/postStatus';
import { timeAgo } from '../lib/time';
import { readRole, readVariables } from '../components/promptMetadata';
import Avatar from '../components/Avatar';
import CommentBody from '../components/CommentBody';
import CodeBlock from '../components/CodeBlock';
import { AiReviewTerminal } from '../components/AiReviewTerminal';
import { ReviewStepStrip } from '../components/ui/ReviewStepStrip';
import EmptyState from '../components/EmptyState';
import PromptVersionPanel from '../components/PromptVersionPanel';
import PromptWorkedExample from '../components/PromptWorkedExample';
import CommentSentimentFilter from '../components/CommentSentimentFilter';

import type { AiReviewDetail, PostPageVo } from '../types/post';

const AI_USER_ID = 999;

function CopyButton({ code }: { code: string }) {
  const { t } = useTranslation('post');
  const [copied, setCopied] = useState(false);
  const handleCopy = async () => {
    await navigator.clipboard.writeText(code);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };
  return (
    <button
      type="button"
      onClick={handleCopy}
      className="absolute right-2 top-2 rounded-md border border-vibe-border bg-vibe-card px-2 py-1 text-xs text-vibe-muted transition-colors hover:bg-vibe-surface hover:text-vibe-text"
    >
      {copied ? <><Check className="inline h-3 w-3" /> {t('actions.copied')}</> : <><Copy className="inline h-3 w-3" /> {t('actions.copy')}</>}
    </button>
  );
}

function estimateTokens(text: string): number {
  if (!text.trim()) return 0;
  const chineseChars = (text.match(/[\u4e00-\u9fff\u3400-\u4dbf\uf900-\ufaff]/g) || []).length;
  const asciiText = text.replace(/[\u4e00-\u9fff\u3400-\u4dbf\uf900-\ufaff]/g, ' ');
  const asciiWords = asciiText.split(/\s+/).filter(Boolean).length;
  return Math.round(chineseChars * 2 + asciiWords * 1.3);
}

/**
 * The post detail page, v2 anatomy: one centered column at `max-w-3xl` — title
 * row, metadata line with the AI score as a bordered pill, rendered body, the
 * review boxes, then the comment stream. The macOS terminal chrome is gone;
 * every box is a plain bordered card.
 */
export default function PostDetailPage() {
  const { t } = useTranslation(['post', 'common']);
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const user = useAuthStore((s) => s.user);
  const addToast = useToastStore((s) => s.addToast);

  const [commentText, setCommentText] = useState('');
  const [copiedLink, setCopiedLink] = useState(false);
  const [liked, setLiked] = useState(false);
  const [likeCount, setLikeCount] = useState(0);
  const likeInFlight = useRef(false);
  const [showHistory, setShowHistory] = useState(false);

  const { data: post, isLoading } = useQuery({
    queryKey: ['post', id],
    queryFn: async () => {
      const res = await apiClient.get('/posts/' + id);
      return res.data.data;
    },
    enabled: !!id,
    staleTime: 1000 * 60,
    refetchInterval: (query) => {
      const data = query.state.data as PostPageVo | undefined;
      // Stop polling once reviewed (1) or failed (3, backend retries it)
      return data && data.aiReviewed !== 1 && data.aiReviewed !== 3 ? 5000 : false;
    },
  });

  const { data: commentsData } = useQuery({
    queryKey: ['comments', id],
    queryFn: async () => {
      const res = await apiClient.get('/comments/post/' + id);
      return res.data.data;
    },
    enabled: !!id,
    staleTime: 1000 * 30,
  });

  const hasCodeBlock = /```[\s\S]*```/.test(post?.content || '');
  const { data: aiReviewDetail, isLoading: aiReviewLoading, isError: aiReviewError, refetch: refetchAiReview } = useQuery<AiReviewDetail | null>({
    queryKey: ['agent-logs', 'post', id, 'latest'],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<AiReviewDetail | null>>(`/agent-logs/post/${id}/latest`);
      return res.data.data ?? null;
    },
    enabled: !!id && post?.aiReviewed === AI_REVIEW.REVIEWED && hasCodeBlock,
    staleTime: 1000 * 60 * 5,
    retry: false,
  });

  // The post cache updates quickly (polling) but the review cache holds for
  // 5 minutes — without this, a re-review would leave the terminal showing
  // the old score while the header already shows the new one.
  useEffect(() => {
    if (post?.aiReviewed === AI_REVIEW.REVIEWED && id) {
      queryClient.invalidateQueries({ queryKey: ['agent-logs', 'post', id, 'latest'] });
    }
  }, [post?.aiReviewed, id, queryClient]);

  // Seed like state from the server (likedByMe survives reloads; anonymous
  // viewers get null → false). Re-syncs when a refetch brings new values.
  const serverPostId = post?.id;
  const serverLikedByMe = post?.likedByMe;
  const serverLikeCount = post?.likeCount;
  useEffect(() => {
    if (serverPostId == null) return;
    setLiked(serverLikedByMe ?? false);
    setLikeCount(serverLikeCount ?? 0);
  }, [serverPostId, serverLikedByMe, serverLikeCount]);

  // Always fetched for a reviewed post: the voucher's call count is the number
  // of review-log rows this endpoint already serves, so the count and the history
  // panel cannot disagree. The panel below just reveals the same data.
  const { data: reviewHistory } = useQuery<AiReviewDetail[]>({
    queryKey: ['agent-logs', 'post', id, 'history'],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<AiReviewDetail[]>>(`/agent-logs/post/${id}`);
      return res.data.data ?? [];
    },
    enabled: !!id && post?.aiReviewed === AI_REVIEW.REVIEWED,
  });

  const commentMutation = useMutation({
    mutationFn: async (content: string) => {
      await apiClient.post('/comments', { postId: id, content });
    },
    onSuccess: () => {
      setCommentText('');
      queryClient.invalidateQueries({ queryKey: ['comments', id] });
      addToast(t('post:comments.form.posted'), 'success');
      if (post) {
        queryClient.setQueryData<PostPageVo>(['post', id], { ...post, commentCount: post.commentCount + 1 });
      }
    },
  });

  const handleLike = async () => {
    // Without a token the backend answers 401 and the click would vanish
    // silently — route to login instead, like the comment/fork actions.
    if (!user) {
      addToast(t('common:toast.loginToLike'), 'error');
      navigate('/login');
      return;
    }
    // One toggle per click: the backend Lua script toggles for real on every
    // request, so a double-fire would add and remove in the same burst.
    if (likeInFlight.current) return;
    likeInFlight.current = true;
    const nextLiked = !liked;
    try {
      const res = await apiClient.post('/posts/' + id + '/like');
      // The server count (Redis SCARD after the toggle) is the single truth
      setLiked(nextLiked);
      setLikeCount(res.data.data?.currentLikes ?? likeCount + (nextLiked ? 1 : -1));
    } catch {
      // A swallowed failure read as "the like went through"; say otherwise.
      addToast(t('common:toast.likeFailed'), 'error');
    } finally {
      likeInFlight.current = false;
    }
  };

  const handleCopyLink = () => {
    navigator.clipboard.writeText(window.location.href);
    setCopiedLink(true);
    addToast(t('common:toast.linkCopied'), 'success');
    setTimeout(() => setCopiedLink(false), 2000);
  };

  const handleCommentSubmit = () => {
    if (!commentText.trim()) return;
    if (!user) { window.location.href = '/login'; return; }
    commentMutation.mutate(commentText.trim());
  };

  const canManage = !!user && (user.role === 'ADMIN' || user.id === post?.userId);

  const handleDelete = async () => {
    if (!post) return;
    if (!window.confirm(t('post:deleteConfirm'))) return;
    try {
      await apiClient.delete('/posts/' + post.id);
      addToast(t('common:toast.postDeleted'), 'success');
      queryClient.invalidateQueries({ queryKey: ['posts'] });
      navigate('/');
    } catch (err: any) {
      addToast(err.response?.data?.message || t('common:toast.deleteFailed'), 'error');
    }
  };

  // Prompt Playground state
  const variables = useMemo(() => readVariables(post?.promptMetadata), [post]);
  const promptRole = readRole(post?.promptMetadata);

  const [varValues, setVarValues] = useState<Record<string, string>>({});


  const varsKey = variables.join(',');
  useEffect(() => {
    setVarValues((prev) => {
      const next: Record<string, string> = {};
      varsKey.split(',').filter(Boolean).forEach((v) => { next[v] = prev[v] ?? ''; });
      return next;
    });
  }, [varsKey]);

  const renderedPrompt = useMemo(() => {
    let text = post?.content ?? '';
    const vars = varsKey.split(',').filter(Boolean);
    vars.forEach((v) => {
      text = text.replace(new RegExp('\\{\\{' + v + '\\}\\}', 'g'), varValues[v] || '{{' + v + '}}');
    });
    return text;
  }, [post?.content, varsKey, varValues]);

  const playgroundTokens = useMemo(() => estimateTokens(renderedPrompt), [renderedPrompt]);

  const [playgroundCopied, setPlaygroundCopied] = useState(false);
  const [showVersions, setShowVersions] = useState(false);
  const [playgroundOpen, setPlaygroundOpen] = useState(true);
  const [forking, setForking] = useState(false);
  const handleCopyRendered = async () => {
    await navigator.clipboard.writeText(renderedPrompt);
    setPlaygroundCopied(true);
    setTimeout(() => setPlaygroundCopied(false), 2000);
  };

  const handleFork = async () => {
    if (!user) {
      navigate('/login');
      return;
    }
    setForking(true);
    try {
      const res = await apiClient.post('/posts/' + id + '/fork');
      addToast(t('common:toast.templateForked'), 'success');
      queryClient.invalidateQueries({ queryKey: ['posts'] });
      navigate('/post/' + res.data.data.postId);
    } catch {
      addToast(t('common:toast.forkFailed'), 'error');
    } finally {
      setForking(false);
    }
  };

  if (isLoading) {
   return (
     <div className="mx-auto max-w-3xl px-4 py-16">
       <div className="animate-pulse motion-reduce:animate-none space-y-4">
          <div className="h-6 w-3/4 rounded bg-vibe-surface" />
          <div className="h-4 w-1/2 rounded bg-vibe-surface" />
          <div className="h-64 rounded bg-vibe-surface" />
        </div>
      </div>
    );
  }

   if (!post) {
     return (
       <div className="mx-auto max-w-3xl px-4 py-16 text-center">
        <h2 className="mb-2 text-lg font-semibold text-vibe-text">{t('post:notFoundTitle')}</h2>
        <p className="text-sm text-vibe-muted">{t('post:notFoundDesc')}</p>
      </div>
    );
  }

  const comments = commentsData ?? [];
  const totalComments = comments.length > 0 ? comments.length : post.commentCount;
  // 2 = REVIEWING; 3 = FAILED gets its own quiet terminal notice (backend retries)
  const aiPending = hasCodeBlock && post.aiReviewed === AI_REVIEW.REVIEWING;
  const aiScorePill = post.aiReviewed === AI_REVIEW.REVIEWED && post.aiReviewScore > 0
    ? Math.round(post.aiReviewScore * 10)
    : null;

   return (
     <div className="mx-auto max-w-3xl px-4 py-8">
      {/* HEADER ROW — wraps so the action buttons drop below the title on
          narrow screens instead of squeezing it to one word per line */}
      <div className="mb-8 flex flex-wrap items-start gap-3">
        <Avatar name={post.authorName} size="md" />
        <div className="min-w-0 flex-1">
          <h1 className="text-2xl font-medium leading-snug text-vibe-text">{post.title}</h1>
          {/* Identity line: who wrote it, where, when. Mono, one row, no pills. */}
          <div className="mt-1.5 flex flex-wrap items-center gap-x-2 font-mono text-xs text-vibe-faint">
            <span>{post.authorName}</span>
            <span aria-hidden="true">·</span>
            <span>{post.categoryName}</span>
            <span aria-hidden="true">·</span>
            <span className="tabular-nums">{timeAgo(post.createTime)}</span>
          </div>
          {/* State stamps: only what is actually true of this post. An absent
              value renders nothing, so the line never fills with empty pills. */}
          <div className="mt-2 flex flex-wrap items-center gap-1.5 text-xs">
            {/* The author's own held post reaches this page only through the
                author-scoped detail read; a pending label turns "it 404'd" into
                "it exists and is waiting for review". Nobody else can see it. */}
            {post.status === POST_STATUS.PENDING_REVIEW && (
              <span className="rounded border border-vibe-warning/40 bg-vibe-warning/10 px-1.5 py-0.5 text-vibe-warning">
                {postStatusLabel(post.status)}
              </span>
            )}
            {post.postType === 'prompt' && (
              <span className="rounded border border-vibe-border px-1.5 py-0.5 text-vibe-muted">{t('post:template')}</span>
            )}
            {/* The row used to carry the prompt's role; it lives here now, where
                there is room to read it. */}
            {promptRole && (
              <span className="max-w-[220px] truncate rounded border border-vibe-border px-1.5 py-0.5 font-mono text-vibe-muted" title={promptRole}>
                {promptRole}
              </span>
            )}
            {post.postType === 'prompt' && post.versionCount > 0 && (
              <span className="rounded border border-vibe-border px-1.5 py-0.5 font-mono tabular-nums text-vibe-muted">v{post.versionCount}</span>
            )}
            {/* The score is the audit stamp: mono digits in the accent, the same
                language as the feed's credential. */}
            {aiScorePill != null && (
              <span
                className="cursor-help rounded border border-vibe-success/45 bg-vibe-success/5 px-1.5 py-0.5 font-mono tabular-nums text-vibe-success"
                title={t('post:scoreRubric')}
              >
                {t('post:scoreDisplay', { score: aiScorePill })}
              </span>
            )}
          </div>
          {post.forkedFromId && (
            <Link
              to={'/post/' + post.forkedFromId}
              className="mt-1.5 inline-flex items-center gap-1 text-xs text-vibe-muted transition-colors hover:text-vibe-accent"
            >
              <GitFork className="h-3 w-3" />
              {t('post:forkedFrom', { id: post.forkedFromId })}
            </Link>
          )}
        </div>
        {post.postType === 'prompt' && (
          <div className="flex shrink-0 items-center gap-2">
            <button
              type="button"
              onClick={handleFork}
              disabled={forking}
              className="inline-flex items-center gap-1.5 rounded-md bg-vibe-accent px-3 py-1.5 text-xs font-medium text-vibe-on-accent transition-colors hover:bg-vibe-accent/90 disabled:opacity-50"
              title={t('post:actions.forkTitle')}
            >
              <GitFork className="h-3.5 w-3.5" />
              {forking ? t('post:actions.forking') : t('post:actions.fork')}
            </button>
            <button
              type="button"
              onClick={() => setShowVersions(true)}
              className="inline-flex items-center gap-1.5 rounded-md border border-vibe-border bg-vibe-card px-3 py-1.5 text-xs text-vibe-text transition-colors hover:border-vibe-accent/40 hover:text-vibe-accent"
              title={t('post:actions.history')}
            >
              <History className="h-3.5 w-3.5" />
              {t('post:actions.history')}
            </button>
          </div>
        )}
        {canManage && (
          <div className="flex shrink-0 items-center gap-2">
            <button
              type="button"
              onClick={() => navigate('/post/' + post.id + '/edit')}
              className="inline-flex items-center gap-1.5 rounded-md border border-vibe-border bg-vibe-card px-3 py-1.5 text-xs text-vibe-text transition-colors hover:border-vibe-accent/40 hover:text-vibe-accent"
            >
              <Pencil className="h-3.5 w-3.5" />
              {t('post:actions.edit')}
            </button>
            <button
              type="button"
              onClick={handleDelete}
              className="inline-flex items-center gap-1.5 rounded-md border border-vibe-danger/40 bg-vibe-danger/10 px-3 py-1.5 text-xs text-vibe-danger transition-colors hover:bg-vibe-danger/20"
            >
              <Trash2 className="h-3.5 w-3.5" />
              {t('post:actions.delete')}
            </button>
          </div>
        )}
      </div>

      {/* POST CONTENT — plain bordered card, the v2 replacement for the
          terminal window */}
      <div className="prose prose-sm mb-6 max-w-none text-vibe-text dark:prose-invert prose-headings:text-vibe-text prose-a:text-vibe-accent prose-code:text-vibe-accent prose-code:bg-vibe-card prose-code:px-1 prose-code:py-0.5 prose-code:text-xs prose-pre:bg-transparent prose-pre:p-0">
        <ReactMarkdown
          remarkPlugins={[remarkGfm]}
          components={{
            code({ className, children, ...props }) {
              const match = /language-(\w+)/.exec(className || '');
              const codeStr = String(children).replace(/\n$/, '');
              if (match) {
                return (
                  <div className="relative group my-4 -mx-4 sm:-mx-6">
                    <CopyButton code={codeStr} />
                    <CodeBlock language={match[1]} code={codeStr} />
                  </div>
                );
              }
              return <code className={className} {...props}>{children}</code>;
            },
          }}
        >
          {post.content}
        </ReactMarkdown>
      </div>

      {/* INTERACTIVE DOCK */}
      {/* The only place the three counts appear: a hairline-separated action row.
          The header used to repeat them, so a reader saw the same numbers twice
          on one screen. */}
      <div className="mb-8 flex items-center justify-between border-t border-vibe-border pt-3">
        <div className="flex items-center gap-4 font-mono text-xs tabular-nums text-vibe-muted">
          <span className="flex items-center gap-1" aria-label={t('post:stats.views', { count: post.viewCount })}>
            <Eye className="h-3.5 w-3.5 text-vibe-faint" aria-hidden="true" />
            <span aria-hidden="true">{post.viewCount}</span>
          </span>
         <button
           type="button"
           onClick={handleLike}
           aria-label={t('post:actions.like')}
           className={'inline-flex items-center gap-1.5 transition-colors ' + (liked ? 'text-vibe-danger' : 'text-vibe-muted hover:text-vibe-danger')}
         >
           <Heart className={'h-3.5 w-3.5 ' + (liked ? 'fill-vibe-danger' : '')} aria-hidden="true" />
           <span className="tabular-nums">{likeCount}</span>
         </button>
         <span className="flex items-center gap-1" aria-label={t('post:stats.comments', { count: totalComments })}>
           <MessageCircle className="h-3.5 w-3.5 text-vibe-faint" aria-hidden="true" />
           <span aria-hidden="true">{totalComments}</span>
         </span>
         <button
           type="button"
           onClick={handleCopyLink}
           className="inline-flex items-center gap-1.5 text-vibe-muted transition-colors hover:text-vibe-text"
         >
            {copiedLink ? <Check className="h-3.5 w-3.5 text-vibe-success" /> : <Share2 className="h-3.5 w-3.5" />}
            {copiedLink ? t('post:actions.copied') : t('post:actions.share')}
          </button>
        </div>
        {aiPending && (
          <span className="flex items-center gap-1.5 text-xs text-vibe-accent motion-reduce:animate-none animate-pulse">
            <Loader2 className="h-3 w-3 animate-spin motion-reduce:animate-none" />
            {t('post:aiPending')}
          </span>
        )}
      </div>

      {/* PROMPT PLAYGROUND */}
      {post.postType === 'prompt' && variables.length > 0 && (
        <div className="mb-8">
          <div className="overflow-hidden rounded-lg border border-vibe-border bg-vibe-surface">
            <button
              type="button"
              onClick={() => setPlaygroundOpen((v) => !v)}
              className="flex w-full items-center gap-2 border-b border-vibe-border bg-vibe-card/50 px-4 py-2.5 text-left transition-colors hover:bg-vibe-card"
              aria-expanded={playgroundOpen}
            >
              <span className="text-xs font-medium text-vibe-faint">
                {t('post:playground.sectionTitle')}
              </span>
              <span className="text-xs tabular-nums text-vibe-muted">{t('post:playground.tokens', { n: playgroundTokens })}</span>
              <span className="ml-auto text-vibe-accent">
                {playgroundOpen ? <ChevronUp className="h-4 w-4" /> : <ChevronDown className="h-4 w-4" />}
              </span>
            </button>
            {playgroundOpen && (
              <div className="space-y-4 p-4">
                <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                  {variables.map((v) => (
                    <div key={v} className="space-y-1.5">
                      <label className="text-xs text-vibe-muted">{v}</label>
                      <input
                        value={varValues[v] ?? ''}
                        onChange={(e) => setVarValues((prev) => ({ ...prev, [v]: e.target.value }))}
                        placeholder={t('post:playground.inputPlaceholder', { name: v })}
                        className="w-full rounded-md border border-vibe-border bg-vibe-bg px-3 py-2 text-sm text-vibe-text placeholder-vibe-faint transition-colors focus:border-vibe-accent/50 focus:outline-none focus:ring-1 focus:ring-vibe-accent/50"
                      />
                    </div>
                  ))}
                </div>

                <div>
                  <div className="mb-2 flex items-center justify-between">
                    <span className="text-xs text-vibe-muted">{t('post:playground.livePreview')}</span>
                    <span className="text-xs tabular-nums text-vibe-faint">{t('post:playground.tokens', { n: playgroundTokens })}</span>
                  </div>
                  <pre className="w-full max-h-48 overflow-y-auto rounded-md border border-vibe-border bg-vibe-bg p-3 font-mono text-xs leading-relaxed text-vibe-text whitespace-pre-wrap">
                    {renderedPrompt || <span className="text-vibe-faint">{t('post:playground.previewPlaceholder')}</span>}
                  </pre>
                </div>

                <div className="flex justify-end">
                  <button
                    type="button"
                    onClick={handleCopyRendered}
                    className="inline-flex items-center gap-1.5 rounded-md bg-vibe-accent px-4 py-2 text-xs font-medium text-vibe-on-accent transition-colors hover:bg-vibe-accent/90"
                  >
                    {playgroundCopied ? <><Check className="h-3.5 w-3.5" /> {t('post:playground.copied')}</> : <><Copy className="h-3.5 w-3.5" /> {t('post:playground.copyPrompt')}</>}
                  </button>
                </div>
              </div>
            )}
          </div>
        </div>
      )}

      {/* WORKED EXAMPLE — sibling of the playground, filled from the two
          optional keys inside the stored promptMetadata. Renders nothing when
          the post is not a prompt or has no example. */}
      <PromptWorkedExample
        postType={post.postType}
        promptMetadata={post.promptMetadata}
        prompt={renderedPrompt}
      />

      {/* AI REVIEW STATUS + TERMINAL */}
      {aiPending && (
        <div className="mb-8">
          <div role="status" aria-live="polite" className="rounded-lg border border-vibe-border bg-vibe-surface p-4 text-sm">
            <div className="flex items-center gap-2 text-vibe-accent">
              <Loader2 className="h-3.5 w-3.5 animate-spin motion-reduce:animate-none" />
              {t('post:aiPendingBox.title')}
            </div>
            {/* The strip cycles to show that something is happening, so it is hidden from
                assistive technology on purpose: a live region that re-announces four step
                labels every 1.4s is worse than the line it would talk over. The steps are
                read for real once the review lands and the panel replays them. */}
            <div aria-hidden="true" className="mt-2.5">
              <ReviewStepStrip mode="running" />
            </div>
            <p className="mt-2 text-vibe-muted">{t('post:aiPendingBox.pollNote')}</p>
          </div>
        </div>
      )}
      {hasCodeBlock && post.aiReviewed === AI_REVIEW.REVIEWED && (
        <div className="mb-8">
          {aiReviewLoading ? (
            <AiReviewTerminal state="loading" />
          ) : aiReviewError ? (
            <AiReviewTerminal state="error" onRetry={() => refetchAiReview()} />
          ) : aiReviewDetail ? (
            <AiReviewTerminal
              state="data"
              detail={aiReviewDetail}
              reviewCount={reviewHistory?.length}
            />
          ) : (
            <AiReviewTerminal state="unavailable" />
          )}
          <button
            type="button"
            onClick={() => setShowHistory((v) => !v)}
            className="mt-2 inline-flex items-center gap-1.5 rounded-md border border-vibe-border bg-vibe-card px-3 py-1.5 text-xs text-vibe-muted transition-colors hover:border-vibe-accent/40 hover:text-vibe-accent"
            aria-expanded={showHistory}
          >
            <History className="h-3.5 w-3.5" />
            {showHistory ? t('post:review.history.hide') : t('post:review.history.show')}
          </button>
          {showHistory && (
            <div className="mt-2 rounded-lg border border-vibe-border bg-vibe-surface text-xs">
              <div className="border-b border-vibe-border px-3 py-2 text-vibe-muted">
                {t('post:review.history.title')}
              </div>
              {(reviewHistory ?? []).length === 0 ? (
                <p className="p-3 text-vibe-muted">{t('post:review.history.empty')}</p>
              ) : (
                <ul>
                  {(reviewHistory ?? []).map((entry, i) => (
                    <li key={i} className="flex flex-wrap items-center gap-2 border-b border-vibe-border px-3 py-2 last:border-0">
                      <span className="tabular-nums text-vibe-muted">{entry.reviewedAt ? timeAgo(entry.reviewedAt) : '--'}</span>
                      <span className="tabular-nums text-vibe-accent">{entry.score == null ? '--' : entry.score <= 10 ? Math.round(entry.score * 10) : Math.round(entry.score)}/100</span>
                      <span className="uppercase text-vibe-muted">{entry.severity || 'unknown'}</span>
                      <span className={entry.isApproved ? 'text-vibe-success' : 'text-vibe-warning'}>
                        {entry.isApproved ? t('post:review.history.approved') : t('post:review.history.needsReview')}
                      </span>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          )}
        </div>
      )}
      {hasCodeBlock && post.aiReviewed === AI_REVIEW.FAILED && (
        <div className="mb-8">
          <AiReviewTerminal state="failed" />
        </div>
      )}

      {/* COMMENTS SECTION */}
      <section className="border-t border-vibe-border pb-12 pt-6">
        <h2 className="mb-5 text-sm font-semibold text-vibe-text">
          {t('post:comments.heading', { n: totalComments })}
        </h2>

        {/* Comment Form */}
        <div className="mb-6 overflow-hidden rounded-lg border border-vibe-border bg-vibe-surface">
          <div className="border-b border-vibe-border px-3 py-2 text-xs text-vibe-faint">
            {t('post:comments.form.newCommentFile')}
          </div>
          <textarea
            value={commentText}
            onChange={(e) => setCommentText(e.target.value)}
            placeholder={t('post:comments.form.placeholder')}
            rows={3}
            className="w-full resize-none bg-vibe-bg px-4 py-3 text-sm leading-relaxed text-vibe-text placeholder-vibe-faint focus:outline-none"
          />
          <div className="flex items-center justify-between border-t border-vibe-border bg-vibe-card/50 px-3 py-2">
            <span className="text-xs text-vibe-faint">
              {commentText.length > 0 ? t('post:comments.form.ready') : t('post:comments.form.typeToComment')}
            </span>
            <button
              type="button"
              onClick={handleCommentSubmit}
              disabled={!commentText.trim() || commentMutation.isPending}
              className="rounded-md bg-vibe-accent px-4 py-1.5 text-xs font-medium text-vibe-on-accent transition-colors hover:bg-vibe-accent/90 disabled:cursor-not-allowed disabled:opacity-40"
            >
              {commentMutation.isPending ? t('post:comments.form.posting') : t('post:comments.form.post')}
            </button>
          </div>
        </div>

        {/* Comment List — grouped client-side by the stored sentiment label.
            The component owns the filter and the grouping; the rows come from
            the already-fetched list, so switching groups sends no request. */}
        {comments.length > 0 ? (
          <CommentSentimentFilter
            comments={comments}
            renderComment={(comment: any) => {
              const isAi = Number(comment.userId) === AI_USER_ID;
              return (
                <div className="border-b border-vibe-border pb-3 last:border-0">
                  <div className="flex items-start gap-3">
                    <Avatar name={comment.authorName} size="sm" />
                    <div className="min-w-0 flex-1">
                      <div className="mb-0.5 flex items-center gap-2">
                        <span className="text-xs font-medium text-vibe-text">{comment.authorName}</span>
                        {isAi && <span className="rounded border border-vibe-accent/40 bg-vibe-accent/10 px-1 text-xs text-vibe-accent">{t('post:comments.aiBadge')}</span>}
                        <span className="text-xs tabular-nums text-vibe-faint">{timeAgo(comment.createTime)}</span>
                      </div>
                      <CommentBody content={comment.content} />
                    </div>
                  </div>
                </div>
              );
            }}
          />
        ) : (
          <EmptyState
            preset="noComments"
            className="py-8"
            title={t('common:empty.noComments.title')}
            desc={t('common:empty.noComments.desc')}
          />
        )}

      </section>

      <PromptVersionPanel
        postId={post.id}
        open={showVersions}
        onClose={() => setShowVersions(false)}
        canRestore={!!user && (user.role === 'ADMIN' || user.id === post.userId)}
      />
    </div>
  );
}
