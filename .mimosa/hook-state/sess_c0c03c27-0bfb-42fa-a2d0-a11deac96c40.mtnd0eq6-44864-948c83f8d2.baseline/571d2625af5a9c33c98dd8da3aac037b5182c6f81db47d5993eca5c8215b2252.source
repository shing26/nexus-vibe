import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Tag } from 'lucide-react';
import { apiClient } from '../api/client';

interface VibeTag {
  id: number;
  name: string;
  status: number;
  createTime: string;
}

export default function TagsPage() {
  const { data: tags, isLoading } = useQuery<VibeTag[]>({
    queryKey: ['tags'],
    queryFn: () => apiClient.get('/tags').then((r) => r.data.data),
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
      ) : (
        <div className="flex flex-wrap gap-2">
          {tags?.map((tag) => (
            <Link
              key={tag.id}
              to={'/search?q=' + encodeURIComponent(tag.name)}
              className="inline-flex items-center gap-1.5 px-4 py-2 rounded-lg bg-vibe-surface border border-vibe-border text-xs font-mono text-slate-300 hover:border-vibe-cyan/40 hover:text-vibe-cyan transition-colors"
            >
              <Tag className="w-3.5 h-3.5" />
              {tag.name}
            </Link>
          ))}
          {tags?.length === 0 && (
            <p className="text-xs font-mono text-slate-600">// No tags yet.</p>
          )}
        </div>
      )}
    </div>
  );
}
