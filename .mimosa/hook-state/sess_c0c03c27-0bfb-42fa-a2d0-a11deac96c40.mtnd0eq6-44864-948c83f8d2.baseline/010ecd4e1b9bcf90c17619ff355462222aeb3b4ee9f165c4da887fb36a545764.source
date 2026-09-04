import { useState, useEffect } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Search, SlidersHorizontal, X } from 'lucide-react';
import { apiClient } from '../api/client';
import PostCard from '../components/PostCard';
import Pagination from '../components/Pagination';
import Sidebar from '../components/Sidebar';
import EmptyState from '../components/EmptyState';
import { useChannels } from '../api/useChannels';

const LANGUAGE_OPTIONS = [
  { value: '', label: 'All languages' },
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
  { value: '', label: 'Any AI score' },
  { value: '1', label: 'AI reviewed' },
  { value: '60', label: 'Score 60+' },
  { value: '80', label: 'Score 80+' },
];

const SORT_OPTIONS = [
  { value: 'latest', label: 'Latest' },
  { value: 'hot', label: 'Hot' },
  { value: 'ai', label: 'AI score' },
];

interface FilterSelectProps {
  label: string;
  value: string;
  options: { value: string; label: string }[];
  onChange: (value: string) => void;
}

function FilterSelect({ label, value, options, onChange }: FilterSelectProps) {
  return (
    <label className="flex items-center gap-1.5 min-w-0">
      <span className="text-[10px] font-mono text-slate-600 shrink-0">{label}</span>
      <select
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className="w-auto max-w-[130px] px-2 py-1.5 bg-vibe-bg border border-vibe-border rounded-md text-[11px] font-mono text-slate-300 focus:outline-none focus:ring-1 focus:ring-vibe-cyan/50 focus:border-vibe-cyan/50 transition-colors"
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

export default function SearchPage() {
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

  return (
    <div className="max-w-[1400px] mx-auto px-4 py-8">
      <div className="flex gap-4">
        <div className="w-12 shrink-0 hidden lg:block">
          <Sidebar />
        </div>
        <div className="flex-1 min-w-0">
          <h1 className="text-lg font-bold font-mono text-slate-100 pb-3 border-b border-vibe-border mb-5">
            {keyword ? 'Search: ' + keyword : hasFilters ? 'Filtered Search' : 'Search'}
          </h1>

          {/* Search input */}
          <form onSubmit={submitQuery} className="relative mb-4 max-w-xl">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-slate-500" />
            <input
              value={queryInput}
              onChange={(e) => setQueryInput(e.target.value)}
              placeholder="Search prompts, code, errors..."
              className="w-full pl-9 pr-3 py-2 bg-vibe-surface border border-vibe-border rounded-lg text-xs font-mono text-slate-200 placeholder-slate-600 focus:outline-none focus:ring-1 focus:ring-vibe-cyan/50 focus:border-vibe-cyan/50 transition-colors"
            />
          </form>

          {showNoKeyword ? (
            <p className="text-slate-500 text-center py-12 text-sm font-mono">Enter a keyword or use filters to search posts</p>
          ) : (
            <>
              {/* Filter bar */}
              <div className="flex flex-wrap items-center gap-x-4 gap-y-2 mb-5 rounded-xl border border-vibe-border bg-vibe-surface/40 p-2.5">
                <span className="flex items-center gap-1.5 text-[10px] font-mono text-slate-500">
                  <SlidersHorizontal className="w-3.5 h-3.5" />
                  Filters
                </span>
                <FilterSelect
                  label="Channel"
                  value={channelSlug}
                  onChange={(v) => updateParam('channel', v)}
                  options={[
                    { value: '', label: 'All channels' },
                    ...(channels ?? []).map((c: any) => ({ value: c.slug, label: c.name })),
                  ]}
                />
                <FilterSelect
                  label="Language"
                  value={language}
                  onChange={(v) => updateParam('language', v)}
                  options={LANGUAGE_OPTIONS}
                />
                <FilterSelect
                  label="AI"
                  value={score}
                  onChange={(v) => updateParam('score', v)}
                  options={SCORE_OPTIONS}
                />
                <FilterSelect
                  label="Sort"
                  value={sort}
                  onChange={(v) => updateParam('sort', v)}
                  options={SORT_OPTIONS}
                />
                {hasFilters && (
                  <button
                    onClick={clearFilters}
                    className="inline-flex items-center gap-1 ml-auto px-2 py-1 rounded-md text-[10px] font-mono text-slate-400 hover:text-red-400 border border-vibe-border hover:border-red-500/40 transition-colors active:scale-[0.97]"
                  >
                    <X className="w-3 h-3" />
                    Clear filters
                  </button>
                )}
              </div>

              {isLoading ? (
                <div className="space-y-3">
                  {[1, 2, 3].map((i) => (
                    <div key={i} className="bg-vibe-surface/50 border border-vibe-border rounded-xl p-4 relative overflow-hidden">
                      <div className="absolute inset-0 bg-gradient-to-r from-transparent via-vibe-cyan/5 to-transparent bg-[length:200%_100%] animate-shimmer" />
                      <div className="h-3 bg-vibe-card rounded w-3/4 mb-3 relative" />
                      <div className="h-2.5 bg-vibe-card/50 rounded w-1/2 mb-2 relative" />
                      <div className="h-2 bg-vibe-card/30 rounded w-1/4 relative" />
                    </div>
                  ))}
                </div>
              ) : results.length > 0 ? (
                <>
                  <div className="space-y-3">
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
                    title="没有匹配的结果"
                    desc="试试调整关键词、语言、AI 评分或频道筛选。"
                    action="/post/new?template=prompt"
                    actionLabel="发布一个 Prompt"
                  />
                  {hasFilters && (
                    <div className="text-center -mt-8">
                      <button
                        onClick={clearFilters}
                        className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-vibe-border text-[11px] font-mono text-slate-400 hover:text-vibe-cyan hover:border-vibe-cyan/40 transition-colors active:scale-[0.97]"
                      >
                        <X className="w-3 h-3" />
                        清除筛选
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
