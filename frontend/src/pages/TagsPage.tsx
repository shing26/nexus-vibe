import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Tag } from 'lucide-react';
import { apiClient } from '../api/client';
import EmptyState from '../components/EmptyState';

interface TagUsage {
  tagId: number;
  name: string;
  postCount: number;
}

/**
 * A directory, not a stub: each chip carries the number of publicly visible
 * posts that use it, busiest first (the order is the backend's — the page
 * does not re-sort). Counts ride the same cache TTL as the tag list itself,
 * so a just-created post appears here no sooner than its tag does.
 */
export default function TagsPage() {
  const { data: tags, isLoading } = useQuery<TagUsage[]>({
    queryKey: ['tags', 'usage'],
    queryFn: () => apiClient.get('/tags/usage').then((r) => r.data.data),
  });

  return (
    <div className="max-w-4xl mx-auto px-4 py-8">
      <div className="flex items-center gap-3 mb-6">
        <div className="w-9 h-9 rounded-lg bg-vibe-purple/20 border border-vibe-purple/30 flex items-center justify-center">
          <Tag className="w-4 h-4 text-vibe-purple" />
        </div>
        <div>
          <h1 className="text-base font-semibold font-mono text-slate-100">Hot Tags</h1>
          <p className="text-[11px] font-mono text-slate-500">Browse the community by topic</p>
        </div>
      </div>

      {isLoading ? (
        <div className="flex flex-wrap gap-2">
          {Array.from({ length: 8 }).map((_, i) => (
            <div key={i} className="w-24 h-8 bg-vibe-card rounded-lg animate-pulse" />
          ))}
        </div>
      ) : tags && tags.length > 0 ? (
        <div className="flex flex-wrap gap-2">
          {tags.map((tag) => (
            <Link
              key={tag.tagId}
              to={'/search?q=' + encodeURIComponent(tag.name)}
              className="inline-flex items-center gap-1.5 px-4 py-2 rounded-lg bg-vibe-surface border border-vibe-border text-xs font-mono text-slate-300 hover:border-vibe-cyan/40 hover:text-vibe-cyan transition-colors"
            >
              <Tag className="w-3.5 h-3.5" />
              {tag.name}
              <span className="px-1.5 rounded bg-vibe-card border border-vibe-border text-[10px] text-slate-500">
                {tag.postCount}
              </span>
            </Link>
          ))}
        </div>
      ) : (
        <EmptyState
          preset="noResults"
          title="还没有任何标签"
          desc="标签由 AI 评审从帖子中自动提取，发布 Prompt 后这里就会长出来。"
          action="/post/new?template=prompt"
          actionLabel="发布一个 Prompt"
        />
      )}
    </div>
  );
}
