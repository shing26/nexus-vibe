import { Link, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
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
import Skeleton from '../components/Skeleton';
import { PostStateBadges } from '../components/PostCard';
import { timeAgo } from '../lib/time';
import type { PageResponse, PostPageVo, UserProfileSummary } from '../types/post';

function formatCount(value: number | null | undefined) {
  const count = Number(value ?? 0);
  return Number.isFinite(count) ? count.toLocaleString() : '--';
}

function ErrorPanel({ label, onRetry }: { label: string; onRetry: () => void }) {
  const { t } = useTranslation('profile');
  return (
    <div role="alert" className="rounded-lg border border-vibe-danger/40 bg-vibe-danger/5 p-4 text-sm text-vibe-danger">
      <div className="flex items-center justify-between gap-3">
        <span className="flex min-w-0 items-center gap-2">
          <AlertCircle className="h-3.5 w-3.5 shrink-0" />
          {t('failedToLoad', { label })}
        </span>
        <button
          type="button"
          onClick={onRetry}
          aria-label={t('retryAria', { label })}
          className="inline-flex shrink-0 items-center rounded-md border border-vibe-danger/30 bg-vibe-danger/10 p-1.5 text-vibe-danger hover:bg-vibe-danger/20 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-vibe-accent"
        >
          <RotateCw className="h-3.5 w-3.5" />
        </button>
      </div>
    </div>
  );
}

function ProfileSkeleton() {
  return (
    <div className="mx-auto max-w-4xl px-4 py-8">
      <div className="mb-8 rounded-lg border border-vibe-border bg-vibe-surface p-6">
        <div className="flex items-center gap-6">
          <Skeleton className="h-20 w-20 shrink-0 rounded-full" />
          <div className="flex-1 space-y-2">
            <Skeleton className="h-6 w-1/4" />
            <Skeleton className="h-4 w-1/3" />
          </div>
        </div>
      </div>
      <div className="mb-8 grid grid-cols-2 gap-2 sm:grid-cols-3 lg:grid-cols-6">
        {[0, 1, 2, 3, 4, 5].map((item) => (
          <Skeleton key={item} className="h-16 rounded-lg" />
        ))}
      </div>
      <Skeleton className="h-40 rounded-lg" />
    </div>
  );
}

function SummarySkeleton() {
  return (
    <>
      <div className="mb-8 rounded-lg border border-vibe-border bg-vibe-surface p-6">
        <div className="flex items-center gap-6">
          <Skeleton className="h-20 w-20 shrink-0 rounded-full" />
          <div className="flex-1 space-y-2">
            <Skeleton className="h-6 w-1/4" />
            <Skeleton className="h-4 w-1/3" />
          </div>
        </div>
      </div>
      <div className="mb-8 grid grid-cols-2 gap-2 sm:grid-cols-3 lg:grid-cols-6">
        {[0, 1, 2, 3, 4, 5].map((item) => (
          <Skeleton key={item} className="h-16 rounded-lg" />
        ))}
      </div>
    </>
  );
}

function PostsSkeleton() {
  return <Skeleton className="h-40 rounded-lg" />;
}

export default function UserProfilePage() {
  const { t } = useTranslation(['profile', 'post', 'common']);
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
    // The viewer is part of the key: the owner's page and the public page are two
    // different responses for one URL, and this browser keeps its cache across a
    // logout. Without the viewer, a second account could be served the first
    // account's held rows for the remaining staleTime.
    queryKey: ['posts', 'user', id, isOwnProfile ? 'owner' : 'public'],
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
    <div className="mx-auto max-w-4xl px-4 py-8">
      {summaryQuery.isLoading ? (
        <SummarySkeleton />
      ) : summaryQuery.isError || summaryUnavailable ? (
        <div className="mb-8">
          <ErrorPanel label={t('failedLabels.summary')} onRetry={() => summaryQuery.refetch()} />
        </div>
      ) : summary ? (
        <>
          <div className="mb-8 rounded-lg border border-vibe-border bg-vibe-surface p-6">
            <div className="flex items-center gap-6">
              <div className="flex h-20 w-20 shrink-0 items-center justify-center overflow-hidden rounded-full bg-vibe-accent/10">
                {summary.avatar && summary.avatar !== 'default_avatar.png' ? (
                  <img src={summary.avatar} alt={summary.username} className="h-full w-full object-cover" />
                ) : (
                  <span className="text-2xl font-semibold text-vibe-accent">
                    {summary.username.charAt(0).toUpperCase()}
                  </span>
                )}
              </div>
              <div className="min-w-0 flex-1">
                <h1 className="truncate text-2xl font-semibold text-vibe-text">
                  {summary.nickname || summary.username}
                </h1>
                <p className="text-sm text-vibe-muted">@{summary.username}</p>
                {summary.bio && <p className="mt-1 text-sm text-vibe-muted">{summary.bio}</p>}
                {summary.createTime && (
                  <p className="mt-1 text-xs text-vibe-muted">
                    {t('joined')}{' '}
                    {new Date(summary.createTime).toLocaleDateString(undefined, {
                      year: 'numeric',
                      month: 'long',
                      day: 'numeric',
                    })}
                  </p>
                )}
              </div>
            </div>
          </div>

          <section aria-label={t('statsAria')} className="mb-8">
            <div className="grid grid-cols-2 gap-2 sm:grid-cols-3 lg:grid-cols-6">
              {[
                {
                  key: 'posts',
                  icon: FileText,
                  label: t('stats.posts'),
                  value: formatCount(summary.stats?.posts),
                  title: t('statTitles.posts'),
                },
                {
                  key: 'comments',
                  icon: MessageSquare,
                  label: t('stats.comments'),
                  value: formatCount(summary.stats?.comments),
                  title: t('statTitles.comments'),
                },
                {
                  key: 'likes',
                  icon: Heart,
                  label: t('stats.likesReceived'),
                  value: formatCount(summary.stats?.likesReceived),
                  title: t('statTitles.likesReceived'),
                },
                {
                  key: 'avgAi',
                  icon: Gauge,
                  label: t('stats.avgAi'),
                  value:
                    avgScore == null || avgScore === 0
                      ? '--'
                      : avgScore <= 10
                        ? Number((avgScore * 10).toFixed(1)).toLocaleString()
                        : Number(avgScore).toFixed(1),
                  title: t('statTitles.avgAi'),
                },
                {
                  key: 'forks',
                  icon: GitFork,
                  label: t('stats.forks'),
                  value: formatCount(summary.stats?.forks),
                  title: t('statTitles.forks'),
                },
                {
                  key: 'versions',
                  icon: GitBranch,
                  label: t('stats.versions'),
                  value: formatCount(summary.stats?.versions),
                  title: t('statTitles.versions'),
                },
              ].map((stat) => {
                const Icon = stat.icon;
                return (
                  <div
                    key={stat.key}
                    title={stat.title}
                    className="min-w-0 rounded-lg border border-vibe-border bg-vibe-card p-3"
                  >
                    <div className="flex items-center gap-1.5">
                      <Icon className="h-3.5 w-3.5 text-vibe-accent" />
                      <span className="truncate text-xs text-vibe-muted">{stat.label}</span>
                    </div>
                    <p className="mt-1 truncate text-xl font-semibold tabular-nums text-vibe-text">
                      {stat.value}
                    </p>
                  </div>
                );
              })}
            </div>
          </section>

          <section aria-label={t('activityAria')} className="mb-8">
            <h2 className="mb-3 text-sm font-semibold text-vibe-text">{t('recentActivity')}</h2>
            {summary.recentActivity.length === 0 ? (
              <div className="rounded-lg border border-vibe-border bg-vibe-surface">
                <EmptyState preset="noActivity" />
              </div>
            ) : (
              <ol>
                {summary.recentActivity.slice(0, 10).map((item) => {
                  const activityConfig: Record<
                    string,
                    { icon: typeof FileText; className: string; labelKey: string }
                  > = {
                    post: { icon: FileText, className: 'text-vibe-accent', labelKey: 'activity.post' },
                    comment: { icon: MessageSquare, className: 'text-vibe-muted', labelKey: 'activity.comment' },
                    version: { icon: GitBranch, className: 'text-vibe-success', labelKey: 'activity.version' },
                    fork: { icon: GitFork, className: 'text-vibe-accent', labelKey: 'activity.fork' },
                  };
                  const activity =
                    activityConfig[String(item.type).toLowerCase()] ?? {
                      icon: Activity,
                      className: 'text-vibe-muted',
                      labelKey: 'activity.fallback',
                    };
                  const Icon = activity.icon;
                  const label = String(item.type).toLowerCase()
                    ? t(activity.labelKey)
                    : t('activity.fallback');
                  return (
                    <li
                      key={String(item.id)}
                      className="relative pb-4 pl-6 last:pb-0 before:absolute before:left-[3px] before:top-2 before:bottom-0 before:w-px before:bg-vibe-border last:before:hidden"
                    >
                      <Link
                        to={'/post/' + item.postId}
                        className="group block rounded-md px-2 py-1.5 transition-colors hover:bg-vibe-card focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-vibe-accent"
                      >
                        <span className="absolute left-0 top-1.5 h-1.5 w-1.5 rounded-full bg-vibe-accent" />
                        <div className="flex items-center gap-2">
                          <Icon className={'h-3.5 w-3.5 shrink-0 ' + activity.className} />
                          <span className="min-w-0 flex-1 truncate text-xs text-vibe-text">
                            {item.title}
                          </span>
                          <span className="shrink-0 text-xs tabular-nums text-vibe-muted">
                            {timeAgo(item.createdAt)}
                          </span>
                        </div>
                        <p className="mt-0.5 pl-[22px] text-xs text-vibe-faint">{label}</p>
                      </Link>
                    </li>
                  );
                })}
              </ol>
            )}
          </section>
        </>
      ) : null}

      <section aria-label={t('postsAria')} className="mb-8">
        <h2 className="mb-3 text-sm font-semibold text-vibe-text">
          {isOwnProfile ? t('yourPosts') : t('publishedPosts')}
        </h2>
        {postsQuery.isLoading ? (
          <PostsSkeleton />
        ) : postsQuery.isError ? (
          <ErrorPanel label={t('failedLabels.posts')} onRetry={() => postsQuery.refetch()} />
        ) : posts.length === 0 ? (
          <div className="rounded-lg border border-vibe-border bg-vibe-surface">
            <EmptyState
              preset="noPosts"
              desc={isOwnProfile ? t('emptyPostsOwn') : t('emptyPostsPublic')}
              action={isOwnProfile ? '/post/new' : null}
              actionLabel={isOwnProfile ? t('common:empty.actions.createFirstPost') : null}
            />
          </div>
        ) : (
          <div>
            {posts.map((post) => (
              <Link
                key={post.id}
                to={'/post/' + post.id}
                className="group block border-b border-vibe-border px-3 py-2.5 transition-colors last:border-0 hover:bg-vibe-card focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-vibe-accent"
              >
                <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
                  <span className="rounded-full border border-vibe-border bg-vibe-bg px-2 py-0.5 text-xs text-vibe-muted">
                    {post.categoryName}
                  </span>
                  <span className="min-w-0 flex-1 basis-48 truncate text-sm text-vibe-text group-hover:text-vibe-accent">
                    {post.title}
                  </span>
                  {isOwnProfile && <PostStateBadges post={post} />}
                  {!isOwnProfile && post.aiReviewScore > 0 && (
                    <span className="text-xs tabular-nums text-vibe-accent">
                      {t('post:aiScore', { score: Math.round(post.aiReviewScore * 10) })}
                    </span>
                  )}
                  <span className="shrink-0 text-xs tabular-nums text-vibe-muted">
                    {timeAgo(post.createTime)}
                  </span>
                </div>
                <div className="mt-1.5 flex flex-wrap items-center gap-3 text-xs tabular-nums text-vibe-muted">
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
