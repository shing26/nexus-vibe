import { Link, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import {
  Activity,
  AlertCircle,
  FileText,
  Gauge,
  GitBranch,
  GitFork,
  Heart,
  MessageCircle,
  MessageSquare,
  RotateCw,
} from 'lucide-react';
import { apiClient } from '../api/client';
import type { ApiResponse } from '../api/client';
import { useAuthStore } from '../stores/authStore';
import EmptyState from '../components/EmptyState';
import type { PageResponse, PostPageVo, UserProfileSummary } from '../types/post';

function relativeTime(dateStr: string) {
  if (!dateStr) return '--';
  const diff = Date.now() - new Date(dateStr).getTime();
  const minutes = Math.floor(diff / 60000);
  if (minutes < 1) return 'just now';
  if (minutes < 60) return minutes + 'm ago';
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return hours + 'h ago';
  const days = Math.floor(hours / 24);
  if (days < 30) return days + 'd ago';
  return new Date(dateStr).toLocaleDateString();
}

function formatCount(value: number | null | undefined) {
  const count = Number(value ?? 0);
  return Number.isFinite(count) ? count.toLocaleString() : '--';
}

function ErrorPanel({ label, onRetry }: { label: string; onRetry: () => void }) {
  return (
    <div role="alert" className="rounded-lg border border-red-500/40 bg-red-950/40 p-4 font-mono text-xs text-red-400">
      <div className="flex items-center justify-between gap-3">
        <span className="flex min-w-0 items-center gap-2">
          <AlertCircle className="h-3.5 w-3.5 shrink-0" />
          Failed to load {label}.
        </span>
        <button
          type="button"
          onClick={onRetry}
          aria-label={'Retry ' + label}
          className="inline-flex shrink-0 items-center rounded-md border border-red-500/30 bg-red-500/10 p-1.5 text-red-400 hover:bg-red-500/20 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-vibe-neon"
        >
          <RotateCw className="h-3.5 w-3.5" />
        </button>
      </div>
    </div>
  );
}

function ProfileSkeleton() {
  return (
    <div className="max-w-4xl mx-auto px-4 py-8">
      <div className="mb-8 animate-pulse motion-reduce:animate-none rounded-lg border border-vibe-border bg-vibe-surface p-6">
        <div className="flex items-center gap-6">
          <div className="h-20 w-20 shrink-0 animate-pulse motion-reduce:animate-none rounded-full bg-vibe-card" />
          <div className="flex-1 space-y-2">
            <div className="h-6 w-1/4 animate-pulse motion-reduce:animate-none rounded bg-vibe-card" />
            <div className="h-4 w-1/3 animate-pulse motion-reduce:animate-none rounded bg-vibe-card/70" />
          </div>
        </div>
      </div>
      <div className="mb-8 grid grid-cols-2 gap-2 sm:grid-cols-3 lg:grid-cols-6">
        {[0, 1, 2, 3, 4, 5].map((item) => (
          <div key={item} className="h-16 animate-pulse motion-reduce:animate-none rounded-lg bg-vibe-card" />
        ))}
      </div>
      <div className="h-40 animate-pulse motion-reduce:animate-none rounded-lg bg-vibe-card" />
    </div>
  );
}

function SummarySkeleton() {
  return (
    <>
      <div className="mb-8 animate-pulse motion-reduce:animate-none rounded-lg border border-vibe-border bg-vibe-surface p-6">
        <div className="flex items-center gap-6">
          <div className="h-20 w-20 shrink-0 animate-pulse motion-reduce:animate-none rounded-full bg-vibe-card" />
          <div className="flex-1 space-y-2">
            <div className="h-6 w-1/4 animate-pulse motion-reduce:animate-none rounded bg-vibe-card" />
            <div className="h-4 w-1/3 animate-pulse motion-reduce:animate-none rounded bg-vibe-card/70" />
          </div>
        </div>
      </div>
      <div className="mb-8 grid grid-cols-2 gap-2 sm:grid-cols-3 lg:grid-cols-6">
        {[0, 1, 2, 3, 4, 5].map((item) => (
          <div key={item} className="h-16 animate-pulse motion-reduce:animate-none rounded-lg bg-vibe-card" />
        ))}
      </div>
    </>
  );
}

function PostsSkeleton() {
  return <div className="h-40 animate-pulse motion-reduce:animate-none rounded-lg bg-vibe-card" />;
}

export default function UserProfilePage() {
  const { id } = useParams<{ id: string }>();
  const currentUser = useAuthStore((state) => state.user);
  const isOwnProfile = currentUser?.id != null && String(currentUser.id) === String(id);

  const summaryQuery = useQuery<UserProfileSummary | null>({
    queryKey: ['user', id, 'summary'],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<UserProfileSummary | null>>(`/users/${id}/summary`);
      return res.data.data ?? null;
    },
    enabled: !!id,
    staleTime: 1000 * 60,
  });

  const postsQuery = useQuery<PostPageVo[]>({
    queryKey: ['posts', 'user', id],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<PageResponse<PostPageVo>>>('/posts', {
        params: { userId: id },
      });
      return res.data.data?.list ?? [];
    },
    enabled: !!id,
    staleTime: 1000 * 60,
  });

  if (summaryQuery.isLoading && postsQuery.isLoading) {
    return <ProfileSkeleton />;
  }

  const summary = summaryQuery.data;
  const posts = postsQuery.data ?? [];
  const avgScore = summary?.stats?.avgAiScore ?? null;
  const summaryUnavailable = !summaryQuery.isLoading && !summaryQuery.isError && !summary;

  return (
    <div className="max-w-4xl mx-auto px-4 py-8">
      {summaryQuery.isLoading ? (
        <SummarySkeleton />
      ) : summaryQuery.isError || summaryUnavailable ? (
        <div className="mb-8">
          <ErrorPanel label="profile summary" onRetry={() => summaryQuery.refetch()} />
        </div>
      ) : summary ? (
        <>
          <div className="mb-8 rounded-lg border border-vibe-border bg-vibe-surface p-6">
            <div className="flex items-center gap-6">
              <div className="flex h-20 w-20 shrink-0 items-center justify-center overflow-hidden rounded-full bg-vibe-cyan/20">
                {summary.avatar && summary.avatar !== 'default_avatar.png' ? (
                  <img src={summary.avatar} alt={summary.username} className="h-full w-full object-cover" />
                ) : (
                  <span className="text-2xl font-bold text-vibe-cyan">
                    {summary.username.charAt(0).toUpperCase()}
                  </span>
                )}
              </div>
              <div className="min-w-0 flex-1">
                <h1 className="truncate text-2xl font-bold text-slate-100">
                  {summary.nickname || summary.username}
                </h1>
                <p className="font-mono text-sm text-slate-500">@{summary.username}</p>
                {summary.bio && <p className="mt-1 text-sm text-slate-400">{summary.bio}</p>}
                {summary.createTime && (
                  <p className="mt-1 font-mono text-xs text-slate-500">
                    Joined{' '}
                    {new Date(summary.createTime).toLocaleDateString('en-US', {
                      year: 'numeric',
                      month: 'long',
                      day: 'numeric',
                    })}
                  </p>
                )}
              </div>
            </div>
          </div>

          <section aria-label="Profile stats" className="mb-8">
            <div className="grid grid-cols-2 gap-2 sm:grid-cols-3 lg:grid-cols-6">
              {[
                {
                  key: 'posts',
                  icon: FileText,
                  label: 'Posts',
                  value: formatCount(summary.stats?.posts),
                  title: 'Published post count',
                },
                {
                  key: 'comments',
                  icon: MessageSquare,
                  label: 'Comments',
                  value: formatCount(summary.stats?.comments),
                  title: 'Comment count',
                },
                {
                  key: 'likes',
                  icon: Heart,
                  label: 'Likes Received',
                  value: formatCount(summary.stats?.likesReceived),
                  title: 'Likes received on public posts',
                },
                {
                  key: 'avgAi',
                  icon: Gauge,
                  label: 'Avg AI Score',
                  value:
                    avgScore == null || avgScore === 0
                      ? '--'
                      : avgScore <= 10
                        ? Number((avgScore * 10).toFixed(1)).toLocaleString()
                        : Number(avgScore).toFixed(1),
                  title: 'Avg AI score excludes unreviewed posts',
                },
                {
                  key: 'forks',
                  icon: GitFork,
                  label: 'Forks',
                  value: formatCount(summary.stats?.forks),
                  title: 'Prompt fork count',
                },
                {
                  key: 'versions',
                  icon: GitBranch,
                  label: 'Versions',
                  value: formatCount(summary.stats?.versions),
                  title: 'Prompt version count',
                },
              ].map((stat) => {
                const Icon = stat.icon;
                return (
                  <div
                    key={stat.key}
                    title={stat.title}
                    className="min-w-0 rounded-lg border border-vibe-border bg-vibe-card/70 p-3"
                  >
                    <div className="flex items-center gap-1.5">
                      <Icon className="h-3.5 w-3.5 text-vibe-neon" />
                      <span className="truncate font-mono text-[10px] text-slate-400">{stat.label}</span>
                    </div>
                    <p className="mt-1 truncate text-xl font-semibold text-slate-100 tabular-nums">
                      {stat.value}
                    </p>
                  </div>
                );
              })}
            </div>
          </section>

          <section aria-label="Recent activity" className="mb-8">
            <h2 className="mb-3 font-mono text-sm font-semibold text-slate-400">// Recent Activity</h2>
            {summary.recentActivity.length === 0 ? (
              <div className="rounded-lg border border-vibe-border bg-vibe-surface/70">
                <EmptyState preset="noActivity" />
              </div>
            ) : (
              <ol>
                {summary.recentActivity.slice(0, 10).map((item) => {
                  const activityConfig: Record<
                    string,
                    { icon: typeof FileText; className: string; label: string }
                  > = {
                    post: { icon: FileText, className: 'text-vibe-neon', label: 'post' },
                    comment: { icon: MessageSquare, className: 'text-vibe-purple', label: 'comment' },
                    version: { icon: GitBranch, className: 'text-vibe-emerald', label: 'prompt version' },
                    fork: { icon: GitFork, className: 'text-vibe-neon', label: 'fork' },
                  };
                  const activity =
                    activityConfig[String(item.type).toLowerCase()] ?? {
                      icon: Activity,
                      className: 'text-slate-400',
                      label: String(item.type).toLowerCase() || 'activity',
                    };
                  const Icon = activity.icon;
                  return (
                    <li
                      key={String(item.id)}
                      className="relative pb-4 pl-6 last:pb-0 before:absolute before:left-[3px] before:top-2 before:bottom-0 before:w-px before:bg-vibe-border last:before:hidden"
                    >
                      <Link
                        to={'/post/' + item.postId}
                        className="group block rounded-md px-2 py-1.5 hover:bg-vibe-card/70 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-vibe-neon"
                      >
                        <span className="absolute left-0 top-1.5 h-1.5 w-1.5 rounded-full bg-vibe-neon shadow-[0_0_8px_rgba(6,182,212,0.45)]" />
                        <div className="flex items-center gap-2">
                          <Icon className={'h-3.5 w-3.5 shrink-0 ' + activity.className} />
                          <span className="min-w-0 flex-1 truncate font-mono text-xs text-slate-300 group-hover:text-vibe-neon">
                            {item.title}
                          </span>
                          <span className="shrink-0 text-[10px] text-slate-400">
                            {relativeTime(item.createdAt)}
                          </span>
                        </div>
                        <p className="mt-0.5 pl-[22px] text-[10px] text-slate-500">// {activity.label}</p>
                      </Link>
                    </li>
                  );
                })}
              </ol>
            )}
          </section>
        </>
      ) : null}

      <section aria-label="Published posts" className="mb-8">
        <h2 className="mb-3 font-mono text-sm font-semibold text-slate-400">// Published Posts</h2>
        {postsQuery.isLoading ? (
          <PostsSkeleton />
        ) : postsQuery.isError ? (
          <ErrorPanel label="published posts" onRetry={() => postsQuery.refetch()} />
        ) : posts.length === 0 ? (
          <div className="rounded-lg border border-vibe-border bg-vibe-surface/70">
            <EmptyState
              preset="noPosts"
              action={isOwnProfile ? '/post/new' : null}
              actionLabel={isOwnProfile ? 'Create First Post' : null}
            />
          </div>
        ) : (
          <div className="space-y-2">
            {posts.map((post) => (
              <Link
                key={post.id}
                to={'/post/' + post.id}
                className="group block rounded-lg border border-vibe-border bg-vibe-card/70 p-3 hover:border-vibe-neon/40 hover:bg-vibe-card focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-vibe-neon"
              >
                <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
                  <span className="rounded-md border border-vibe-cyan/30 bg-vibe-cyan/10 px-2 py-0.5 font-mono text-[10px] text-vibe-cyan">
                    {post.categoryName}
                  </span>
                  <span className="min-w-0 flex-1 basis-48 truncate font-mono text-xs text-slate-200 group-hover:text-vibe-neon">
                    {post.title}
                  </span>
                  {post.aiReviewScore > 0 && (
                    <span className="font-mono text-[10px] text-vibe-neon tabular-nums">
                      AI {Math.round(post.aiReviewScore * 10)}
                    </span>
                  )}
                  <span className="shrink-0 font-mono text-[10px] text-slate-400">
                    {relativeTime(post.createTime)}
                  </span>
                </div>
                <div className="mt-1.5 flex flex-wrap items-center gap-3 font-mono text-[10px] text-slate-400">
                  <span className="flex items-center gap-1">
                    <Heart className="h-3 w-3" />
                    {post.likeCount}
                  </span>
                  <span className="flex items-center gap-1">
                    <MessageCircle className="h-3 w-3" />
                    {post.commentCount}
                  </span>
                </div>
              </Link>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}
