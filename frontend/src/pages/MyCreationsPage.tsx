import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { AlertCircle, FileText, GitFork, PenLine, RotateCw, Sparkles } from 'lucide-react';
import { apiClient } from '../api/client';
import type { ApiResponse } from '../api/client';
import { useAuthStore } from '../stores/authStore';
import { AI_REVIEW } from '../lib/postStatus';
import { timeAgo } from '../lib/time';
import Skeleton from '../components/Skeleton';
import type { PageResponse, PostPageVo } from '../types/post';

const DRAFT_KEY = 'nexus_vibe_drafts';
const PAGE_SIZE = 100;

type Kind = 'post' | 'prompt' | 'fork' | 'draft';
type Tab = 'all' | Kind;

interface Draft {
  id: number;
  title: string;
  content: string;
  postType: string;
  updatedAt: string;
}

interface Creation {
  key: string;
  kind: Kind;
  title: string;
  preview: string;
  to: string;
  time: string;
  score: number | null;
  deviceOnly: boolean;
}

function readDrafts(): Draft[] {
  try {
    const stored = JSON.parse(localStorage.getItem(DRAFT_KEY) || '[]');
    return Array.isArray(stored) ? (stored as Draft[]) : [];
  } catch {
    return [];
  }
}

function toCreation(post: PostPageVo): Creation {
  const isPrompt = post.postType === 'prompt';
  const kind: Kind = isPrompt ? (post.forkedFromId != null ? 'fork' : 'prompt') : 'post';
  return {
    key: post.id,
    kind,
    title: post.title,
    preview: (post.summary || post.content || '').replace(/<[^>]*>/g, '').slice(0, 140),
    to: '/post/' + post.id,
    time: post.createTime,
    score: post.aiReviewed === AI_REVIEW.REVIEWED && post.aiReviewScore > 0
      ? Math.round(post.aiReviewScore * 10)
      : null,
    deviceOnly: false,
  };
}

function draftToCreation(draft: Draft): Creation {
  return {
    key: 'draft-' + draft.id,
    kind: 'draft',
    title: draft.title,
    preview: (draft.content || '').replace(/<[^>]*>/g, '').slice(0, 140),
    to: '/post/new?draft=' + draft.id,
    time: draft.updatedAt,
    score: null,
    deviceOnly: true,
  };
}

const KIND_ICON: Record<Kind, typeof FileText> = {
  post: FileText,
  prompt: Sparkles,
  fork: GitFork,
  draft: PenLine,
};

export default function MyCreationsPage() {
  const { t } = useTranslation('creations');
  const user = useAuthStore((s) => s.user);
  const [tab, setTab] = useState<Tab>('all');

  const drafts = useMemo(readDrafts, []);
  const { data, isLoading, isError, refetch } = useQuery<PostPageVo[]>({
    queryKey: ['creations', user?.id],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<PageResponse<PostPageVo>>>('/posts', {
        params: { userId: user?.id, page: 1, size: PAGE_SIZE },
      });
      return res.data.data?.list ?? [];
    },
    enabled: !!user?.id,
    staleTime: 1000 * 60,
  });

  const serverCreations = (data ?? []).map(toCreation);
  const allCreations = [...serverCreations, ...drafts.map(draftToCreation)];
  const counts: Record<Tab, number> = {
    all: allCreations.length,
    post: serverCreations.filter((c) => c.kind === 'post').length,
    prompt: serverCreations.filter((c) => c.kind === 'prompt').length,
    fork: serverCreations.filter((c) => c.kind === 'fork').length,
    draft: drafts.length,
  };
  const visible = tab === 'all' ? allCreations : allCreations.filter((c) => c.kind === tab);
  const tabs: Tab[] = ['all', 'post', 'prompt', 'fork', 'draft'];

  return (
    <div className="mx-auto max-w-4xl px-4 py-8">
      <header className="mb-6 flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold text-vibe-text">{t('title')}</h1>
          <p className="mt-1 text-sm text-vibe-muted">{t('subtitle')}</p>
        </div>
        <Link
          to="/post/new"
          className="inline-flex items-center gap-1.5 rounded-md bg-vibe-accent px-3 py-2 text-xs font-medium text-white transition-colors hover:opacity-90"
        >
          <PenLine className="h-3.5 w-3.5" />
          {t('newPost')}
        </Link>
      </header>

      {isError ? (
        <div role="alert" className="flex items-center justify-between gap-3 rounded-lg border border-vibe-danger/40 bg-vibe-danger/5 p-4 text-sm text-vibe-danger">
          <span className="flex items-center gap-2">
            <AlertCircle className="h-4 w-4 shrink-0" />
            {t('error')}
          </span>
          <button
            type="button"
            onClick={() => refetch()}
            className="inline-flex shrink-0 items-center gap-1 rounded-md border border-vibe-danger/30 bg-vibe-danger/10 px-2 py-1.5 text-xs hover:bg-vibe-danger/20"
          >
            <RotateCw className="h-3.5 w-3.5" />
            {t('retry')}
          </button>
        </div>
      ) : isLoading ? (
        <div aria-label={t('loadingAria')} className="space-y-2">
          <Skeleton className="h-10 rounded-md" />
          <Skeleton className="h-20 rounded-lg" />
          <Skeleton className="h-20 rounded-lg" />
        </div>
      ) : (
        <>
          <div className="flex flex-wrap items-center gap-1 border-b border-vibe-border" role="tablist" aria-label={t('listAria')}>
            {tabs.map((key) => (
              <button
                key={key}
                type="button"
                role="tab"
                aria-selected={tab === key}
                onClick={() => setTab(key)}
                className={
                  '-mb-px flex items-center gap-1.5 border-b-2 px-3 py-2 text-sm transition-colors ' +
                  (tab === key
                    ? 'border-vibe-accent font-medium text-vibe-text'
                    : 'border-transparent text-vibe-muted hover:border-vibe-border hover:text-vibe-text')
                }
              >
                {t('tabs.' + key)}
                <span className="rounded-full border border-vibe-border bg-vibe-card px-1.5 text-[11px] tabular-nums text-vibe-muted">
                  {counts[key]}
                </span>
              </button>
            ))}
          </div>

          {data != null && data.length >= PAGE_SIZE && (
            <p className="mt-2 text-xs text-vibe-faint">{t('windowHint', { count: PAGE_SIZE })}</p>
          )}

          {visible.length === 0 ? (
            <div className="mt-4 rounded-lg border border-vibe-border bg-vibe-surface p-10 text-center">
              <p className="text-sm text-vibe-muted">{t('empty.' + tab)}</p>
            </div>
          ) : (
            <ul aria-label={t('listAria')} className="mt-4 space-y-2">
              {visible.map((creation) => {
                const Icon = KIND_ICON[creation.kind];
                return (
                  <li key={creation.key}>
                    <Link
                      to={creation.to}
                      className="flex items-start gap-3 rounded-lg border border-vibe-border bg-vibe-surface p-3 transition-colors hover:border-vibe-accent/50"
                    >
                      <span className="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-md border border-vibe-border bg-vibe-card text-vibe-accent">
                        <Icon className="h-4 w-4" aria-hidden="true" />
                      </span>
                      <span className="min-w-0 flex-1">
                        <span className="flex flex-wrap items-center gap-2">
                          <span className="truncate text-sm font-medium text-vibe-text">{creation.title}</span>
                          <span className="rounded-full border border-vibe-border bg-vibe-card px-1.5 py-0.5 text-[11px] text-vibe-muted">
                            {t('kind.' + creation.kind)}
                          </span>
                          {creation.deviceOnly && (
                            <span className="text-[11px] text-vibe-faint">{t('deviceOnly')}</span>
                          )}
                        </span>
                        {creation.preview && (
                          <span className="mt-1 block truncate text-xs text-vibe-muted">{creation.preview}</span>
                        )}
                        <span className="mt-1 block text-xs tabular-nums text-vibe-faint">
                          {creation.score != null ? t('score', { score: creation.score }) + ' · ' : ''}
                          {timeAgo(creation.time)}
                        </span>
                      </span>
                    </Link>
                  </li>
                );
              })}
            </ul>
          )}
        </>
      )}
    </div>
  );
}
