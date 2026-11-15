import { useEffect, useState } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { apiClient } from '../api/client';
import PostCard from '../components/PostCard';
import Pagination from '../components/Pagination';
import Skeleton from '../components/Skeleton';
import EmptyState from '../components/EmptyState';
import { channelLabel } from '../lib/channels';
import type { Channel, PostPageVo } from '../types/post';

const DESKTOP_QUERY = '(min-width: 768px)';

/**
 * A4-4: the unanswered panel is not mounted below `md`, rather than mounted and
 * hidden with CSS. At phone widths it would push the feed below the fold, and
 * "not rendered" is a stronger promise than `display: none`.
 */
function useIsDesktop(): boolean {
  const [matches, setMatches] = useState(() => {
    if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') return false;
    return window.matchMedia(DESKTOP_QUERY).matches;
  });

  useEffect(() => {
    if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') return;
    const mql = window.matchMedia(DESKTOP_QUERY);
    const onChange = () => setMatches(mql.matches);
    onChange();
    mql.addEventListener('change', onChange);
    return () => mql.removeEventListener('change', onChange);
  }, []);

  return matches;
}

export default function ChannelPage() {
  const { t } = useTranslation('channel');
  const { slug } = useParams<{ slug: string }>();
  const [searchParams, setSearchParams] = useSearchParams();
  const [page, setPage] = useState(1);
  const isDesktop = useIsDesktop();
  // A6-4: the ordering lives in the URL so a refresh keeps it and a link shares it.
  const sort = searchParams.get('sort') === 'liked' ? 'liked' : 'latest';

  const changeSort = (next: 'latest' | 'liked') => {
    const nextParams = new URLSearchParams(searchParams);
    if (next === 'liked') {
      nextParams.set('sort', 'liked');
    } else {
      nextParams.delete('sort');
    }
    setSearchParams(nextParams, { replace: true });
    setPage(1);
  };

  const { data: channels } = useQuery({
    queryKey: ['channels'],
    queryFn: async () => {
      const res = await apiClient.get('/channels');
      return res.data.data as Channel[];
    },
    staleTime: 1000 * 60 * 10,
  });

  const channel = channels?.find((c: any) => c.slug === slug);

  const emptyGuides: Record<string, { title: string; desc: string; action: string; actionLabel: string }> = {
    prompts: {
      title: t('emptyGuides.prompts.title'),
      desc: t('emptyGuides.prompts.desc'),
      action: '/post/new?template=prompt',
      actionLabel: t('emptyGuides.prompts.actionLabel'),
    },
    debug: {
      title: t('emptyGuides.debug.title'),
      desc: t('emptyGuides.debug.desc'),
      action: '/post/new?template=debug',
      actionLabel: t('emptyGuides.debug.actionLabel'),
    },
    showcase: {
      title: t('emptyGuides.showcase.title'),
      desc: t('emptyGuides.showcase.desc'),
      action: '/post/new?template=showcase',
      actionLabel: t('emptyGuides.showcase.actionLabel'),
    },
    agents: {
      title: t('emptyGuides.agents.title'),
      desc: t('emptyGuides.agents.desc'),
      action: '/post/new?template=agents',
      actionLabel: t('emptyGuides.agents.actionLabel'),
    },
    'vibe-coding': {
      title: t('emptyGuides.vibe-coding.title'),
      desc: t('emptyGuides.vibe-coding.desc'),
      action: '/post/new',
      actionLabel: t('emptyGuides.vibe-coding.actionLabel'),
    },
    resources: {
      title: t('emptyGuides.resources.title'),
      desc: t('emptyGuides.resources.desc'),
      action: '/post/new',
      actionLabel: t('emptyGuides.resources.actionLabel'),
    },
  };
  const emptyGuide = channel ? emptyGuides[channel.slug] : undefined;

  const { data: postsData, isLoading } = useQuery({
    queryKey: ['posts', 'channel', slug, page, sort, slug === 'prompts' ? 'prompt' : 'all'],
    queryFn: async () => {
      const res = await apiClient.get('/posts', {
        params: {
          channelSlug: slug,
          page,
          size: 10,
          type: slug === 'prompts' ? 'prompt' : 'all',
          ...(sort === 'liked' ? { sort: 'liked' } : {}),
        },
      });
      return res.data.data;
    },
    enabled: !!slug && !!channel,
    staleTime: 1000 * 60 * 2,
  });

  const { data: unanswered } = useQuery<PostPageVo[]>({
    // Channel-aware key and query: the waiting panel belongs to the channel you
    // are in, so the cache must not hand one channel's rows to another.
    queryKey: ['posts', 'unanswered', channel?.id],
    queryFn: async () => {
      const res = await apiClient.get('/posts/unanswered', {
        params: { size: 5, ...(channel ? { channelId: channel.id } : {}) },
      });
      return (res.data.data ?? []) as PostPageVo[];
    },
    enabled: !!channel,
    staleTime: 1000 * 60 * 2,
  });

  if (!slug) {
    return (
      <div className="mx-auto max-w-[1400px] px-4 py-16 text-center">
        <p className="text-sm text-vibe-muted">{t('noChannelSpecified')}</p>
      </div>
    );
  }

  if (channels && !channel) {
    return (
      <div className="mx-auto max-w-[1400px] px-4 py-16 text-center">
        <h2 className="mb-2 text-lg font-semibold text-vibe-text">{t('notFoundTitle')}</h2>
        <p className="text-sm text-vibe-muted">{t('notFoundDesc', { slug })}</p>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-[1400px] px-4 py-8">
      <div className="flex gap-6">
        {/* The channel rail lives in the app shell; the page owns only its feed. */}
        <div className="min-w-0 flex-1">
          {channel && (
            <h1 className="mb-6 border-b border-vibe-border pb-3 text-xl font-semibold text-vibe-text">
              # {channelLabel(channel.slug) || channel.name}
            </h1>
          )}

          {channel && (
            <div className="mb-4 flex items-center gap-2 text-xs">
              <span className="text-vibe-muted">{t('orderLabel')}</span>
              {(['latest', 'liked'] as const).map((option) => (
                <button
                  key={option}
                  type="button"
                  onClick={() => changeSort(option)}
                  aria-pressed={sort === option}
                  className={
                    'rounded-full border px-2.5 py-1 transition-colors ' +
                    (sort === option
                      ? 'border-vibe-accent/60 bg-vibe-accent/10 text-vibe-accent'
                      : 'border-vibe-border text-vibe-muted hover:border-vibe-accent/40 hover:text-vibe-text')
                  }
                >
                  {option === 'latest' ? t('sortLatest') : t('sortLiked')}
                </button>
              ))}
            </div>
          )}

          {isLoading ? (
            <div className="space-y-4 px-3 py-2">
              {[1, 2, 3].map((i) => (
                <div key={i} className="space-y-2">
                  <Skeleton className="h-4 w-3/4" />
                  <Skeleton className="h-3 w-1/2" />
                </div>
              ))}
            </div>
          ) : postsData && postsData.list.length > 0 ? (
            <>
              <div>
                {postsData.list.map((post: any) => (
                  <PostCard key={post.id} post={post} />
                ))}
              </div>
              <Pagination
                page={postsData.page}
                pages={postsData.pages}
                onPageChange={setPage}
              />
            </>
          ) : (
            <EmptyState preset="noPosts" {...emptyGuide} />
          )}
        </div>

        {isDesktop && channel && (
          <aside aria-label={t('waiting.ariaLabel')} className="w-72 shrink-0">
            <div className="rounded-lg border border-vibe-border bg-vibe-surface p-4">
              <h2 className="mb-1 text-xs font-semibold text-vibe-text">{t('waiting.title')}</h2>
              <p className="mb-3 text-xs text-vibe-muted">{t('waiting.desc')}</p>
              {unanswered === undefined ? null : unanswered.length > 0 ? (
                <ul className="space-y-1">
                  {unanswered.map((post) => (
                    <li key={post.id}>
                      <Link
                        to={'/post/' + post.id}
                        className="group block rounded-md p-2 transition-colors hover:bg-vibe-card"
                      >
                        <p className="line-clamp-2 text-xs leading-snug text-vibe-text">{post.title}</p>
                        <p className="mt-1 text-xs tabular-nums text-vibe-muted">
                          {t('waiting.rowMeta', { author: post.authorName, likes: post.likeCount })}
                        </p>
                      </Link>
                    </li>
                  ))}
                </ul>
              ) : (
                <EmptyState
                  preset="noPosts"
                  title={t('waiting.emptyTitle')}
                  desc={t('waiting.emptyDesc')}
                  action={null}
                  actionLabel={null}
                  className="py-8"
                />
              )}
            </div>
          </aside>
        )}
      </div>
    </div>
  );
}
