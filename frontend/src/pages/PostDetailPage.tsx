import { useState, useMemo, useEffect } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { motion } from 'motion/react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { Heart, Share2, MessageCircle, Eye, Copy, Check, ChevronDown, ChevronUp, GitFork, History, Pencil, Trash2, Loader2 } from 'lucide-react';
import { apiClient } from '../api/client';
import type { ApiResponse } from '../api/client';
import { useAuthStore } from '../stores/authStore';
import { useToastStore } from '../stores/toastStore';
import Avatar from '../components/Avatar';
import CodeBlock from '../components/CodeBlock';
import { AiReviewTerminal } from '../components/AiReviewTerminal';
 import { DecryptedText } from '../components/ui/DecryptedText';
 import EmptyState from '../components/EmptyState';
 import PromptVersionPanel from '../components/PromptVersionPanel';
 
 import type { AiReviewDetail, PostPageVo } from '../types/post';

const AI_USER_ID = 999;

function MacDots() {
  return (
    <div className="flex items-center gap-1.5 px-3">
      <span className="w-2.5 h-2.5 rounded-full bg-red-500/80" />
      <span className="w-2.5 h-2.5 rounded-full bg-yellow-500/80" />
      <span className="w-2.5 h-2.5 rounded-full bg-green-500/80" />
    </div>
  );
}

function TerminalWindow({ title, children, className = '' }: { title: string; children: React.ReactNode; className?: string }) {
  return (
    <div className={'bg-vibe-surface border border-vibe-border rounded-xl overflow-hidden ' + className}>
      <div className="flex items-center h-9 bg-vibe-card border-b border-vibe-border select-none">
        <MacDots />
        <span className="flex-1 text-center text-[11px] font-mono text-slate-500 truncate px-2">{title}</span>
        <div className="w-16" />
      </div>
      {children}
    </div>
  );
}

function CopyButton({ code }: { code: string }) {
  const [copied, setCopied] = useState(false);
  const handleCopy = async () => {
    await navigator.clipboard.writeText(code);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };
  return (
     <button onClick={handleCopy} className="absolute top-2 right-2 px-2 py-1 text-xs font-mono bg-vibe-card/80 text-slate-400 hover:text-white hover:bg-vibe-card transition-colors rounded-md border border-vibe-border active:scale-[0.97]">
      {copied ? <><Check className="w-3 h-3 inline" /> Copied</> : <><Copy className="w-3 h-3 inline" /> Copy</>}
    </button>
  );
}

const timeAgo = (dateStr: string) => {
  const diff = Date.now() - new Date(dateStr).getTime();
  const m = Math.floor(diff / 60000);
  if (m < 1) return 'just now';
  if (m < 60) return m + 'm ago';
  const h = Math.floor(m / 60);
  if (h < 24) return h + 'h ago';
  const d = Math.floor(h / 24);
  if (d < 30) return d + 'd ago';
  return new Date(dateStr).toLocaleDateString();
};

function estimateTokens(text: string): number {
  if (!text.trim()) return 0;
  const chineseChars = (text.match(/[\u4e00-\u9fff\u3400-\u4dbf\uf900-\ufaff]/g) || []).length;
  const asciiText = text.replace(/[\u4e00-\u9fff\u3400-\u4dbf\uf900-\ufaff]/g, ' ');
  const asciiWords = asciiText.split(/\s+/).filter(Boolean).length;
  return Math.round(chineseChars * 2 + asciiWords * 1.3);
}

export default function PostDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const user = useAuthStore((s) => s.user);
  const addToast = useToastStore((s) => s.addToast);
  
  const [commentText, setCommentText] = useState('');
  const [copiedLink, setCopiedLink] = useState(false);
  const [liked, setLiked] = useState(false);
  const [likeCount, setLikeCount] = useState(0);

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
      return data && data.aiReviewed !== 1 ? 5000 : false;
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
    enabled: !!id && post?.aiReviewed === 1 && hasCodeBlock,
    staleTime: 1000 * 60 * 5,
    retry: false,
  });

  const commentMutation = useMutation({
    mutationFn: async (content: string) => {
      await apiClient.post('/comments', { postId: id, content });
    },
    onSuccess: () => {
      setCommentText('');
      queryClient.invalidateQueries({ queryKey: ['comments', id] });
      addToast('Comment posted', 'success');
      if (post) {
        queryClient.setQueryData<PostPageVo>(['post', id], { ...post, commentCount: post.commentCount + 1 });
      }
    },
  });

  const handleLike = async () => {
    try {
      const res = await apiClient.post('/posts/' + id + '/like');
      setLiked(!liked);
      setLikeCount(res.data.data?.currentLikes ?? likeCount + (liked ? -1 : 1));
    } catch { /* ignore */ }
  };

  const handleCopyLink = () => {
    navigator.clipboard.writeText(window.location.href);
    setCopiedLink(true);
    addToast('Link copied', 'success');
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
    if (!window.confirm('Delete this post? This action cannot be undone.')) return;
    try {
      await apiClient.delete('/posts/' + post.id);
      addToast('Post deleted', 'success');
      queryClient.invalidateQueries({ queryKey: ['posts'] });
      navigate('/');
    } catch (err: any) {
      addToast(err.response?.data?.message || 'Delete failed', 'error');
    }
  };

  // Prompt Playground state
  const promptMeta = useMemo(() => {
    if (!post?.promptMetadata) return null;
    try { return JSON.parse(post.promptMetadata); } catch { return null; }
  }, [post]);

  const variables: string[] = promptMeta?.variables ?? [];

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
      addToast('Template forked', 'success');
      queryClient.invalidateQueries({ queryKey: ['posts'] });
      navigate('/post/' + res.data.data.postId);
    } catch {
      addToast('Fork failed', 'error');
    } finally {
      setForking(false);
    }
  };

  if (isLoading) {
   return (
     <div className="max-w-[1400px] mx-auto px-4 py-16">
       <div className="animate-pulse space-y-4">
          <div className="h-6 bg-vibe-card rounded w-3/4" />
          <div className="h-4 bg-vibe-card/50 rounded w-1/2" />
          <div className="h-64 bg-vibe-card/30 rounded" />
        </div>
      </div>
    );
  }

   if (!post) {
     return (
       <div className="max-w-[1400px] mx-auto px-4 py-16 text-center">
        <h2 className="text-lg font-bold font-mono text-slate-100 mb-2">404 — Post Not Found</h2>
        <p className="text-sm font-mono text-slate-500">This post may have been deleted or never existed.</p>
      </div>
    );
  }

  const comments = commentsData ?? [];
  const totalComments = comments.length > 0 ? comments.length : post.commentCount;
  const aiPending = hasCodeBlock && post.aiReviewed !== 1;

   return (
     <div className="max-w-[1400px] mx-auto px-4 py-8">
      {/* HEADER ROW */}
      <div className="flex items-start gap-3 mb-6">
        <Avatar name={post.authorName} size="md" />
        <div className="flex-1 min-w-0">
          <h1 className="text-xl font-bold font-mono text-slate-100 leading-snug">{post.title}</h1>
          <div className="flex items-center gap-2 text-[11px] font-mono text-slate-500 mt-0.5">
            <span>{post.authorName}</span>
            <span className="text-slate-700">·</span>
            <span className="bg-vibe-cyan/10 border border-vibe-cyan/30 text-vibe-cyan rounded-md px-1.5 py-0.5">{post.categoryName}</span>
            {post.postType === 'prompt' && (
              <span className="bg-vibe-purple/10 border border-vibe-purple/30 text-vibe-purple rounded-md px-1.5 py-0.5">🤖 Template</span>
            )}
            {post.postType === 'prompt' && post.versionCount > 0 && (
              <span className="bg-vibe-cyan/10 border border-vibe-cyan/30 text-vibe-cyan rounded-md px-1.5 py-0.5">v{post.versionCount}</span>
            )}
            <span className="text-slate-700">·</span>
            <span>{timeAgo(post.createTime)}</span>
          </div>
          {/* Stats badges */}
          <div className="flex items-center gap-3 mt-1.5 text-[11px] font-mono text-slate-500">
            <span className="flex items-center gap-1"><Eye className="w-3 h-3" /> {post.viewCount}</span>
            <span className="flex items-center gap-1"><Heart className="w-3 h-3" /> {post.likeCount}</span>
            <span className="flex items-center gap-1"><MessageCircle className="w-3 h-3" /> {totalComments}</span>
          </div>
          {post.forkedFromId && (
            <Link
              to={'/post/' + post.forkedFromId}
              className="inline-flex items-center gap-1 mt-1.5 text-[10px] font-mono text-vibe-purple hover:text-vibe-purple/80 transition-colors"
            >
              <GitFork className="w-3 h-3" />
              Forked from post #{post.forkedFromId}
            </Link>
          )}
        </div>
        {post.postType === 'prompt' && (
          <div className="flex items-center gap-2 shrink-0">
            <button
              onClick={handleFork}
              disabled={forking}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-vibe-purple/20 border border-vibe-purple/30 text-vibe-purple text-[11px] font-mono hover:bg-vibe-purple/30 transition-colors disabled:opacity-50"
              title="Fork this template"
            >
              <GitFork className="w-3.5 h-3.5" />
              {forking ? 'Forking...' : 'Fork'}
            </button>
            <button
              onClick={() => setShowVersions(true)}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-vibe-card border border-vibe-border text-slate-300 text-[11px] font-mono hover:border-vibe-cyan/40 hover:text-vibe-cyan transition-colors"
              title="View version history"
            >
              <History className="w-3.5 h-3.5" />
              History
            </button>
          </div>
        )}
        {canManage && (
          <div className="flex items-center gap-2 shrink-0">
            <button
              onClick={() => navigate('/post/' + post.id + '/edit')}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-vibe-card border border-vibe-border text-slate-300 text-[11px] font-mono hover:border-vibe-cyan/40 hover:text-vibe-cyan transition-colors"
            >
              <Pencil className="w-3.5 h-3.5" />
              Edit
            </button>
            <button
              onClick={handleDelete}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-red-500/10 border border-red-500/30 text-red-400 text-[11px] font-mono hover:bg-red-500/20 transition-colors"
            >
              <Trash2 className="w-3.5 h-3.5" />
              Delete
            </button>
          </div>
        )}
      </div>

      {/* POST CONTENT — macOS Terminal Wrapper */}
      <TerminalWindow title={post.title.slice(0, 30) + (post.title.length > 30 ? '...' : '') + '.md'} className="mb-6">
        <div className="p-4 bg-vibe-bg prose prose-invert prose-sm max-w-none prose-headings:text-slate-100 prose-a:text-vibe-cyan prose-code:text-vibe-cyan prose-code:bg-vibe-card prose-code:px-1 prose-code:py-0.5 prose-code:text-xs prose-pre:bg-transparent prose-pre:p-0">
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
      </TerminalWindow>

      {/* INTERACTIVE DOCK */}
      <div className="max-w-3xl mx-auto flex items-center justify-between mb-8 px-2">
        <div className="flex items-center gap-4">
         <motion.button
           onClick={handleLike}
           whileTap={{ scale: 0.8 }}
           className="inline-flex items-center gap-1.5 text-xs font-mono text-slate-400 hover:text-red-400 transition-colors active:scale-[0.97]"
         >
           <motion.span
             key={liked ? 'liked' : 'not-liked'}
             initial={{ scale: 0.5 }}
             animate={{ scale: 1 }}
             transition={{ type: 'spring', stiffness: 500, damping: 15 }}
           >
             <Heart className={'w-4 h-4 ' + (liked ? 'fill-red-500 text-red-500' : '')} />
           </motion.span>
           {likeCount || post.likeCount}
         </motion.button>
         <button onClick={handleCopyLink} className="inline-flex items-center gap-1.5 text-xs font-mono text-slate-400 hover:text-vibe-cyan transition-colors active:scale-[0.97]">
            {copiedLink ? <Check className="w-4 h-4 text-vibe-cyan" /> : <Share2 className="w-4 h-4" />}
            {copiedLink ? 'Copied!' : 'Share'}
          </button>
        </div>
        {post.aiReviewed === 1 && post.aiReviewScore > 0 && (
          <span className="text-[11px] font-mono text-vibe-emerald">AI Score: {Math.round(post.aiReviewScore * 10)}/100</span>
        )}
        {aiPending && (
          <span className="flex items-center gap-1.5 text-[11px] font-mono text-vibe-cyan animate-pulse motion-reduce:animate-none">
            <Loader2 className="w-3 h-3 animate-spin motion-reduce:animate-none" />
            AI Agent reviewing...
          </span>
        )}
      </div>

      {/* PROMPT PLAYGROUND */}
      {post.postType === 'prompt' && promptMeta && variables.length > 0 && (
        <div className="max-w-3xl mx-auto mb-8">
          <TerminalWindow title="prompt_playground — Template Variables">
            <button
              onClick={() => setPlaygroundOpen((v) => !v)}
              className="w-full flex items-center gap-2 px-4 py-2.5 bg-vibe-card/50 border-b border-vibe-border text-left hover:bg-vibe-card transition-colors"
              aria-expanded={playgroundOpen}
            >
              <span className="text-[11px] font-mono font-semibold text-slate-300 uppercase tracking-wider">
                Template Variables
              </span>
              <span className="text-[10px] font-mono text-slate-500">~{playgroundTokens} tokens</span>
              <span className="ml-auto text-vibe-cyan">
                {playgroundOpen ? <ChevronUp className="w-4 h-4" /> : <ChevronDown className="w-4 h-4" />}
              </span>
            </button>
            {playgroundOpen && (
              <div className="p-4 space-y-4">
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                  {variables.map((v) => (
                    <div key={v} className="space-y-1.5">
                      <label className="text-xs font-mono text-slate-400">{v}</label>
                      <input
                        value={varValues[v] ?? ''}
                        onChange={(e) => setVarValues((prev) => ({ ...prev, [v]: e.target.value }))}
                        placeholder={'Enter ' + v + '...'}
                        className="w-full px-3 py-2 bg-vibe-bg border border-vibe-border rounded-lg text-xs font-mono text-slate-200 placeholder-slate-600 focus:outline-none focus:ring-1 focus:ring-vibe-cyan/50 focus:border-vibe-cyan/50 transition-colors"
                      />
                    </div>
                  ))}
                </div>

                <div>
                  <div className="flex items-center justify-between mb-2">
                    <span className="text-[11px] font-mono text-slate-500">// Live Preview</span>
                    <span className="text-[10px] font-mono text-slate-600">~{playgroundTokens} tokens</span>
                  </div>
                  <pre className="w-full max-h-48 overflow-y-auto p-3 bg-vibe-bg border border-vibe-border rounded-lg text-xs font-mono text-slate-200 leading-relaxed whitespace-pre-wrap">
                    {renderedPrompt || <span className="text-slate-600">// Fill in variables above to preview...</span>}
                  </pre>
                </div>

                <div className="flex justify-end">
                  <button
                    onClick={handleCopyRendered}
                    className="inline-flex items-center gap-1.5 px-4 py-2 rounded-lg bg-vibe-cyan/20 border border-vibe-cyan/30 text-vibe-cyan text-xs font-mono hover:bg-vibe-cyan/30 transition-colors active:scale-[0.97]"
                  >
                    {playgroundCopied ? <><Check className="w-3.5 h-3.5" /> Copied!</> : <><Copy className="w-3.5 h-3.5" /> Copy Prompt</>}
                  </button>
                </div>
              </div>
            )}
          </TerminalWindow>
        </div>
      )}

      {/* AI REVIEW STATUS + TERMINAL */}
      {aiPending && (
        <div className="max-w-3xl mx-auto mb-8">
          <div className="relative rounded-xl border border-vibe-cyan/30 bg-vibe-card/70 p-4 font-mono text-xs">
            <div className="flex items-center gap-2 text-vibe-cyan">
              <Loader2 className="w-3.5 h-3.5 animate-spin motion-reduce:animate-none" />
              <DecryptedText text="AI Co-Pilot is reviewing this post..." speed={25} />
            </div>
            <p className="mt-1.5 text-slate-500">Review will auto-post a comment with a score when it finishes.</p>
          </div>
        </div>
      )}
      {hasCodeBlock && post.aiReviewed === 1 && (
        <div className="max-w-3xl mx-auto mb-8">
          {aiReviewLoading ? (
            <AiReviewTerminal state="loading" />
          ) : aiReviewError ? (
            <AiReviewTerminal state="error" onRetry={() => refetchAiReview()} />
          ) : aiReviewDetail ? (
            <AiReviewTerminal state="data" detail={aiReviewDetail} />
          ) : (
            <AiReviewTerminal state="unavailable" />
          )}
        </div>
      )}

      {/* COMMENTS SECTION */}
      <section className="max-w-3xl mx-auto border-t border-vibe-border pt-6 pb-12">
        <h2 className="text-xs font-mono font-semibold text-slate-400 mb-5">
          // Comments ({totalComments})
        </h2>

        {/* Comment Form — Terminal style */}
        <div className="bg-vibe-surface border border-vibe-border rounded-xl overflow-hidden mb-6">
          <div className="flex items-center h-8 bg-vibe-card border-b border-vibe-border px-3">
            <MacDots />
            <span className="text-[10px] font-mono text-slate-600 ml-3">new_comment.md</span>
          </div>
          <textarea
            value={commentText}
            onChange={(e) => setCommentText(e.target.value)}
            placeholder="// Write your comment..."
            rows={3}
            className="w-full px-4 py-3 text-sm font-mono bg-vibe-bg text-slate-200 resize-none focus:outline-none border-0 placeholder-slate-600 leading-relaxed"
          />
          <div className="flex items-center justify-between px-3 py-2 bg-vibe-card/50 border-t border-vibe-border">
            <span className="text-[10px] font-mono text-slate-600">{commentText.length > 0 ? 'Ready' : 'Type to comment'}</span>
            <button
              onClick={handleCommentSubmit}
              disabled={!commentText.trim() || commentMutation.isPending}
             className="px-4 py-1.5 rounded-lg bg-vibe-cyan/20 border border-vibe-cyan/30 text-vibe-cyan text-xs font-mono hover:bg-vibe-cyan/30 transition-colors disabled:opacity-40 disabled:cursor-not-allowed active:scale-[0.97]"
            >
              {commentMutation.isPending ? 'Posting...' : 'Post Comment'}
            </button>
          </div>
        </div>

        {/* Comment List */}
        {comments.length > 0 ? (
          <div className="space-y-4">
            {comments.map((comment: any, i: number) => {
              const isAi = Number(comment.userId) === AI_USER_ID;
              return (
                <motion.div
                  key={comment.id}
                  initial={{ opacity: 0, y: 8 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ duration: 0.15, delay: i * 0.02 }}
                  className="border-b border-vibe-border pb-3 last:border-0"
                >
                  <div className="flex items-start gap-3">
                    <Avatar name={comment.authorName} size="sm" />
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2 mb-0.5">
                        <span className="text-xs font-mono font-medium text-slate-200">{comment.authorName}</span>
                        {isAi && <span className="text-[10px] font-mono text-vibe-purple bg-vibe-purple/10 border border-vibe-purple/30 rounded px-1">AI</span>}
                        <span className="text-[10px] font-mono text-slate-600">{timeAgo(comment.createTime)}</span>
                      </div>
                      <p className="text-xs font-mono text-slate-400 leading-relaxed">{comment.content}</p>
                    </div>
                  </div>
                </motion.div>
              );
            })}
          </div>
        ) : (
         <EmptyState preset="noComments" className="py-8" />
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


