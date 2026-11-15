import { useState, useEffect } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Search, SlidersHorizontal, X } from 'lucide-react';
import { apiClient } from '../api/client';
import PostCard from '../components/PostCard';
import Pagination from '../components/Pagination';
import Skeleton from '../components/Skeleton';
import EmptyState from '../components/EmptyState';
import { useChannels } from '../api/useChannels';
import { channelLabel } from '../lib/channels';

/**
 * Search over the topic-row list. The filter bar keeps its URL-parameter
 * wiring; every label resolves through the dictionary, and channel rows use
 * the shared channel constants so names cannot drift from the sidebar.
 */
export default function SearchPage() {
  const { t } = useTranslation('common');
  const [searchParams, setSearchParams] = useSearchParams();
  const keyword = searchParams.get('q') || '';
  const channelSlug = searchParams.get('channel') || '';
  const language = searchParams.get('language') || '';
  const score = searchParams.get('score') || '';
  const sort = searchParams.get('sort') || 'latest';
  const [page, setPage] = useState(1);
  const [queryInput, setQueryInput] = useState(keyword);

  const { data: channels } = useChannels();

  useEffect(() => {
    setPage(1);
  }, [keyword, channelSlug, language, score, sort]);

  useEffect(() => {
    setQueryInput(keyword);
  }, [keyword]);

  const updateParam = (key: string, value: string) => {
    const next = new URLSearchParams(searchParams);
    if (value) next.set(key, value);
    else next.delete(key);
    setSearchParams(next);
  };

  const clearFilters = () => {
    const next = new URLSearchParams(searchParams);
    next.delete('channel');
    next.delete('language');
    next.delete('score');
    next.delete('sort');
    setSearchParams(next);
  };

  const hasFilters = !!(channelSlug || language || score || (sort && sort !== 'latest'));
  const enabled = !!keyword || hasFilters;

  const { data, isLoading } = useQuery({
    queryKey: ['posts', 'search', keyword, channelSlug, language, score, sort, page],
    queryFn: async () => {
      const params: Record<string, any> = { page, size: 10 };
      if (keyword) params.keyword = keyword;
      if (channelSlug) params.channelSlug = channelSlug;
      if (language) params.language = language;
      if (score) params.aiScoreMin = Number(score);
      if (sort && sort !== 'latest') params.sort = sort;
      const res = await apiClient.get('/posts', { params });
      return res.data.data;
    },
    enabled,
    staleTime: 1000 * 60,
  });

  const submitQuery = (e: React.FormEvent) => {
    e.preventDefault();
    const next = new URLSearchParams(searchParams);
    if (queryInput.trim()) next.set('q', queryInput.trim());
    else next.delete('q');
    setSearchParams(next);
  };

  const results = data?.list ?? [];
  const showNoKeyword = !keyword && !hasFilters;

  const LANGUAGE_OPTIONS = [
    { value: '', label: t('search.page.filters.allLanguages') },
    { value: 'python', label: 'Python' },
    { value: 'javascript', label: 'JavaScript' },
    { value: 'typescript', label: 'TypeScript' },
    { value: 'java', label: 'Java' },
    { value: 'go', label: 'Go' },
    { value: 'rust', label: 'Rust' },
    { value: 'sql', label: 'SQL' },
    { value: 'bash', label: 'Bash' },
    { value: 'json', label: 'JSON' },
    { value: 'yaml', label: 'YAML' },
    { value: 'markdown', label: 'Markdown' },
  ];

  const SCORE_OPTIONS = [
    { value: '', label: t('search.page.filters.anyScore') },
    { value: '1', label: t('search.page.filters.aiReviewed') },
    { value: '60', label: t('search.page.filters.score60') },
    { value: '80', label: t('search.page.filters.score80') },
  ];

  const SORT_OPTIONS = [
    { value: 'latest', label: t('search.page.filters.sortLatest') },
    { value: 'hot', label: t('search.page.filters.sortHot') },
    { value: 'ai', label: t('search.page.filters.sortAi') },
  ];

  return (
    <div className="mx-auto max-w-[1400px] px-4 py-8">
      <div className="flex gap-6">
        {/* The channel rail lives in the app shell; the page owns only its search. */}
        <div className="min-w-0 flex-1">
          <h1 className="mb-5 border-b border-vibe-border pb-3 text-lg font-semibold text-vibe-text">
            {keyword
              ? t('search.page.titleWithKeyword', { keyword })
              : hasFilters
                ? t('search.page.titleFiltered')
                : t('search.page.title')}
          </h1>

          {/* Search input */}
          <form onSubmit={submitQuery} className="relative mb-4 max-w-xl">
            <Search className="absolute left-3 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-vibe-muted" />
            <input
              value={queryInput}
              onChange={(e) => setQueryInput(e.target.value)}
              placeholder={t('search.page.placeholder')}
              className="w-full rounded-lg border border-vibe-border bg-vibe-surface py-2 pl-9 pr-3 text-sm text-vibe-text placeholder-vibe-faint focus:border-vibe-accent/50 focus:outline-none focus:ring-1 focus:ring-vibe-accent/50"
            />
          </form>

          {showNoKeyword ? (
            <p className="py-12 text-center text-sm text-vibe-muted">{t('search.page.noKeyword')}</p>
          ) : (
            <>
              {/* Filter bar */}
              <div className="mb-5 flex flex-wrap items-center gap-x-4 gap-y-2 rounded-lg border border-vibe-border bg-vibe-surface p-2.5">
                <span className="flex items-center gap-1.5 text-xs text-vibe-muted">
                  <SlidersHorizontal className="h-3.5 w-3.5" />
                  {t('search.page.filters.label')}
                </span>
                <FilterSelect
                  label={t('search.page.filters.channel')}
                  value={channelSlug}
                  onChange={(v) => updateParam('channel', v)}
                  options={[
                    { value: '', label: t('search.page.filters.allChannels') },
                    ...(channels ?? []).map((c: any) => ({
                      value: c.slug,
                      label: channelLabel(c.slug) || c.name,
                    })),
                  ]}
                />
                <FilterSelect
                  label={t('search.page.filters.language')}
                  value={language}
                  onChange={(v) => updateParam('language', v)}
                  options={LANGUAGE_OPTIONS}
                />
                <FilterSelect
                  label={t('search.page.filters.ai')}
                  value={score}
                  onChange={(v) => updateParam('score', v)}
                  options={SCORE_OPTIONS}
                />
                <FilterSelect
                  label={t('search.page.filters.sort')}
                  value={sort}
                  onChange={(v) => updateParam('sort', v)}
                  options={SORT_OPTIONS}
                />
                {hasFilters && (
                  <button
                    type="button"
                    onClick={clearFilters}
                    className="ml-auto inline-flex items-center gap-1 rounded-md border border-vibe-border px-2 py-1 text-xs text-vibe-muted transition-colors hover:border-vibe-danger/40 hover:text-vibe-danger"
                  >
                    <X className="h-3 w-3" />
                    {t('search.page.filters.clear')}
                  </button>
                )}
              </div>

              {isLoading ? (
                <div className="space-y-4 px-3 py-2">
                  {[1, 2, 3].map((i) => (
                    <div key={i} className="space-y-2">
                      <Skeleton className="h-4 w-3/4" />
                      <Skeleton className="h-3 w-1/2" />
                    </div>
                  ))}
                </div>
              ) : results.length > 0 ? (
                <>
                  <div>
                    {results.map((post: any) => (
                      <PostCard key={post.id} post={post} />
                    ))}
                  </div>
                  <Pagination page={data.page} pages={data.pages} onPageChange={setPage} />
                </>
              ) : (
                <>
                  <EmptyState
                    preset="noResults"
                    title={t('search.page.empty.title')}
                    desc={t('search.page.empty.desc')}
                    action="/post/new?template=prompt"
                    actionLabel={t('search.page.emptyAction')}
                  />
                  {hasFilters && (
                    <div className="-mt-8 text-center">
                      <button
                        type="button"
                        onClick={clearFilters}
                        className="inline-flex items-center gap-1.5 rounded-lg border border-vibe-border px-3 py-1.5 text-xs text-vibe-muted transition-colors hover:border-vibe-accent/40 hover:text-vibe-accent"
                      >
                        <X className="h-3 w-3" />
                        {t('search.page.filters.clear')}
                      </button>
                    </div>
                  )}
                </>
              )}
            </>
          )}
        </div>
      </div>
    </div>
  );
}

interface FilterSelectProps {
  label: string;
  value: string;
  options: { value: string; label: string }[];
  onChange: (value: string) => void;
}

function FilterSelect({ label, value, options, onChange }: FilterSelectProps) {
  return (
    <label className="flex min-w-0 items-center gap-1.5">
      <span className="shrink-0 text-xs text-vibe-muted">{label}</span>
      <select
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className="w-auto max-w-[130px] rounded-md border border-vibe-border bg-vibe-bg px-2 py-1.5 text-xs text-vibe-text transition-colors focus:border-vibe-accent/50 focus:outline-none focus:ring-1 focus:ring-vibe-accent/50"
      >
        {options.map((o) => (
          <option key={o.value} value={o.value} className="bg-vibe-bg">
            {o.label}
          </option>
        ))}
      </select>
    </label>
  );
}
