import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Tag } from 'lucide-react';
import { apiClient } from '../api/client';
import EmptyState from '../components/EmptyState';
import Skeleton from '../components/Skeleton';

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
  const { t } = useTranslation('home');
  const { data: tags, isLoading } = useQuery<TagUsage[]>({
    queryKey: ['tags', 'usage'],
    queryFn: () => apiClient.get('/tags/usage').then((r) => r.data.data),
  });

  return (
    <div className="mx-auto max-w-4xl px-4 py-8">
      <div className="mb-6 flex items-center gap-3">
        <div className="flex h-9 w-9 items-center justify-center rounded-md border border-vibe-border bg-vibe-surface text-vibe-accent">
          <Tag className="h-4 w-4" />
        </div>
        <div>
          <h1 className="text-lg font-semibold text-vibe-text">{t('tags.title')}</h1>
          <p className="text-xs text-vibe-muted">{t('tags.subtitle')}</p>
        </div>
      </div>

      {isLoading ? (
        <div className="flex flex-wrap gap-2">
          {Array.from({ length: 8 }).map((_, i) => (
            <Skeleton key={i} className="h-8 w-24 rounded-lg" />
          ))}
        </div>
      ) : tags && tags.length > 0 ? (
        <div className="flex flex-wrap gap-2">
          {tags.map((tag) => (
            <Link
              key={tag.tagId}
              to={'/search?q=' + encodeURIComponent(tag.name)}
              className="inline-flex items-center gap-1.5 rounded-full border border-vibe-border bg-vibe-surface px-4 py-2 text-xs text-vibe-text transition-colors hover:border-vibe-accent/40 hover:text-vibe-accent"
            >
              <Tag className="h-3.5 w-3.5" />
              {tag.name}
              <span className="rounded-full border border-vibe-border bg-vibe-card px-1.5 text-xs tabular-nums text-vibe-muted">
                {tag.postCount}
              </span>
            </Link>
          ))}
        </div>
      ) : (
        <EmptyState
          preset="noResults"
          title={t('tags.emptyTitle')}
          desc={t('tags.emptyDesc')}
          action="/post/new?template=prompt"
          actionLabel={t('tags.emptyAction')}
        />
      )}
    </div>
  );
}
