import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { apiClient } from '../api/client';
import { useAuthStore } from '../stores/authStore';
import PostCard from '../components/PostCard';
import EmptyState from '../components/EmptyState';
import ShowcaseEntry from '../components/ShowcaseEntry';
import { TheatreEntry } from './TheatrePage';
import Pagination from '../components/Pagination';
import Skeleton from '../components/Skeleton';
import { ArrowRight, GitFork, Terminal, Bug, Palette } from 'lucide-react';

type Tab = 'hot' | 'latest' | 'ai-verified' | 'debug' | 'prompts';

/**
 * The home feed. Signed-in readers get one featured strip (a real, recorded
 * review), the tab row and the topic-row list — no channel grid, because the
 * sidebar is the single navigation surface for channels. Signed-out visitors
 * additionally see the Mission Control band.
 */
export default function HomePage() {
  const { t } = useTranslation(['home', 'common', 'theatre']);
  const [activeTab, setActiveTab] = useState<Tab>('hot');
  const [page, setPage] = useState(1);
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);

  const changeTab = (tab: Tab) => {
    setActiveTab(tab);
    setPage(1);
  };

  const { data: posts, isLoading } = useQuery({
    queryKey: ['posts', activeTab, page],
    queryFn: async () => {
      const params: Record<string, any> = { size: 10, page };
      if (activeTab === 'hot') params.hot = true;
      if (activeTab === 'ai-verified') {
        params.aiScoreMin = 1;
        params.sort = 'ai';
      }
      if (activeTab === 'debug') {
        params.channelSlug = 'debug';
        params.type = 'all';
      } else if (activeTab === 'prompts') {
        params.type = 'prompt';
      } else {
        params.type = 'post';
      }
      const res = await apiClient.get('/posts', { params });
      return res.data.data;
    },
    staleTime: 1000 * 60 * 2,
  });

  const postList = posts?.list ?? posts ?? [];
  const isEmpty = !isLoading && postList.length === 0;

  const emptyCopy: Record<Tab, { title: string; desc: string; action: string; actionLabel: string }> = {
    debug: {
      title: t('home:empty.debug.title'),
      desc: t('home:empty.debug.desc'),
      action: '/post/new?template=debug',
      actionLabel: t('home:empty.debug.actionLabel'),
    },
    prompts: {
      title: t('home:empty.prompts.title'),
      desc: t('home:empty.prompts.desc'),
      action: '/post/new?template=prompt',
      actionLabel: t('home:empty.prompts.actionLabel'),
    },
    'ai-verified': {
      title: t('home:empty.ai-verified.title'),
      desc: t('home:empty.ai-verified.desc'),
      action: '/post/new',
      actionLabel: t('home:empty.ai-verified.actionLabel'),
    },
    hot: {
      title: t('home:empty.hot.title'),
      desc: t('home:empty.hot.desc'),
      action: '/post/new',
      actionLabel: t('home:empty.hot.actionLabel'),
    },
    latest: {
      title: t('home:empty.latest.title'),
      desc: t('home:empty.latest.desc'),
      action: '/post/new',
      actionLabel: t('home:empty.latest.actionLabel'),
    },
  };

  const missions = [
    {
      to: '/post/new?template=debug',
      label: t('home:missionControl.missions.debug.label'),
      sub: t('home:missionControl.missions.debug.sub'),
      icon: Bug,
    },
    {
      to: '/post/new?template=prompt',
      label: t('home:missionControl.missions.prompt.label'),
      sub: t('home:missionControl.missions.prompt.sub'),
      icon: Terminal,
    },
    {
      to: '/post/new?template=showcase',
      label: t('home:missionControl.missions.showcase.label'),
      sub: t('home:missionControl.missions.showcase.sub'),
      icon: Palette,
    },
    {
      to: '/channel/prompts',
      label: t('home:missionControl.missions.fork.label'),
      sub: t('home:missionControl.missions.fork.sub'),
      icon: GitFork,
    },
  ];

  return (
    <div className="mx-auto max-w-[1200px] space-y-6 px-4 py-6">
      <h1 className="sr-only">{t('home:srTitle')}</h1>
      {/* Mission Control — shown to signed-out visitors only. Signed-in users
          already have the navbar's New Post button, and this band plus the
          channel grid below pushed the first post past the fold on every
          screen size. */}
      {!isAuthenticated && (
        <div className="overflow-hidden rounded-lg border border-vibe-border bg-vibe-surface">
          <div className="flex items-center justify-between border-b border-vibe-border px-4 py-2.5">
            <h2 className="text-xs font-semibold uppercase tracking-wide text-vibe-muted">
              {t('home:missionControl.title')}
            </h2>
            <span className="hidden text-xs text-vibe-faint sm:inline">
              {t('home:missionControl.hint')}
            </span>
          </div>
          <div className="grid grid-cols-2 gap-2 p-3 lg:grid-cols-4">
            {missions.map((m) => {
              const Icon = m.icon;
              return (
                <Link
                  key={m.to}
                  to={m.to}
                  className="group/mission rounded-lg border border-vibe-border bg-vibe-card p-3.5 transition-colors hover:border-vibe-accent/50 hover:bg-vibe-surface"
                >
                  <div className="flex items-start justify-between gap-2">
                    <div className="flex h-8 w-8 items-center justify-center rounded-md border border-vibe-border bg-vibe-bg text-vibe-accent">
                      <Icon className="h-4 w-4" />
                    </div>
                    <ArrowRight className="h-3.5 w-3.5 text-vibe-faint transition-colors group-hover/mission:text-vibe-accent" />
                  </div>
                  <p className="mt-3 text-sm font-medium text-vibe-text">{m.label}</p>
                  <p className="mt-0.5 text-xs leading-snug text-vibe-muted">{m.sub}</p>
                </Link>
              );
            })}
          </div>
        </div>
      )}

      {/* The featured strip: one real, recorded review — the product's claim,
          one click away. It sits above the tabs so both signed-in and signed-out
          readers meet it first; the channel grid that used to live here is gone
          because the sidebar owns channel navigation. */}
      <ShowcaseEntry />

      {/* The Chaos Theatre: the signed-out visitor can watch the review pipeline
          fail and recover without an account, so the thesis is playable in one
          click. Signed-in readers keep the navbar; the link stays here too. */}
      <TheatreEntry label={t('theatre:entry.label')} desc={t('theatre:entry.desc')} />

      {/* Underline tabs — GitHub style: a bottom border per tab, the active one
          carries the accent rail and the text darkens, nothing else moves. */}
      <div className="flex flex-wrap items-center gap-1 border-b border-vibe-border">
        {(['hot', 'latest', 'ai-verified', 'debug', 'prompts'] as Tab[]).map((key) => (
          <button
            key={key}
            type="button"
            onClick={() => changeTab(key)}
            // The feed's ranking terms are the product's own dialect; a tooltip
            // keeps them from being jargon with no way in.
            title={key === 'hot' ? t('home:tabs.hotHint') : undefined}
            className={
              '-mb-px border-b-2 px-3 py-2 text-sm transition-colors ' +
              (activeTab === key
                ? 'border-vibe-accent font-medium text-vibe-text'
                : 'border-transparent text-vibe-muted hover:border-vibe-border hover:text-vibe-text')
            }
          >
            {t('home:tabs.' + (key === 'ai-verified' ? 'aiVerified' : key))}
          </button>
        ))}
      </div>

      {/* One plain sentence explaining the order the reader is looking at.
          The product's ranking dialect stays honest without a hover; this is
          the forum's version of "show your work" (observability-forum round). */}
      <p data-testid="ranking-note" className="mt-2 text-xs text-vibe-muted">
        {t('home:tabNotes.' + activeTab)}
      </p>

      {/* Empty state (shown when no posts) */}
      {isEmpty && (
        <EmptyState
          preset="noPosts"
          title={emptyCopy[activeTab].title}
          desc={emptyCopy[activeTab].desc}
          action={emptyCopy[activeTab].action}
          actionLabel={emptyCopy[activeTab].actionLabel}
        />
      )}

      {/* Post Feed — topic rows directly on the page, no card wrappers */}
      {isLoading ? (
        <div className="space-y-3 px-3 py-2">
          {[1, 2, 3].map((i) => (
            <div key={i} className="space-y-2">
              <Skeleton className="h-4 w-3/4" />
              <Skeleton className="h-3 w-1/2" />
            </div>
          ))}
        </div>
      ) : !isEmpty ? (
        <>
          <div>
            {postList.map((post: any) => (
              <PostCard key={post.id} post={post} />
            ))}
          </div>
          {/* A feed that stops at 10 posts forever is a dead end; the search
              page already ships this paginator, so the feed uses it too. */}
          <Pagination
            page={posts?.page ?? page}
            pages={posts?.pages ?? 1}
            onPageChange={setPage}
          />
        </>
      ) : null}
    </div>
  );
}
