import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { ArrowRight, ShieldCheck } from 'lucide-react';
import { apiClient } from '../api/client';

interface ShowcasePost {
  /** A string on the wire: Jackson serialises Long ids as strings so snowflake ids keep
   *  full precision in JavaScript. See backend JacksonConfig. */
  postId: string;
  title: string;
  aiReviewScore: number;
}

/**
 * Points a signed-out visitor at one already-reviewed post.
 *
 * Renders nothing when the backend has no showcase post configured, so a deployment
 * without the seed looks exactly as it did before this existed.
 */
export default function ShowcaseEntry() {
  const { data } = useQuery<ShowcasePost | null>({
    queryKey: ['showcase'],
    queryFn: async () => (await apiClient.get('/showcase')).data.data ?? null,
    staleTime: 1000 * 60 * 5,
  });

  if (!data?.postId) return null;

  return (
    <Link
      to={'/post/' + data.postId}
      className="group block border border-vibe-cyan/25 rounded-xl bg-vibe-cyan/5 hover:bg-vibe-cyan/10 hover:border-vibe-cyan/40 transition-colors"
    >
      <div className="flex items-start gap-3 px-4 py-3">
        <div className="w-8 h-8 shrink-0 rounded-lg border border-vibe-cyan/30 bg-vibe-cyan/10 flex items-center justify-center">
          <ShieldCheck className="w-4 h-4 text-vibe-cyan" />
        </div>
        <div className="min-w-0 flex-1">
          <p className="text-[10px] font-mono uppercase tracking-widest text-vibe-cyan/80">
            Read a real review — no account needed
          </p>
          <p className="mt-1 text-sm font-mono text-slate-200 truncate group-hover:text-white transition-colors">
            {data.title}
          </p>
          <p className="mt-0.5 text-[11px] font-mono text-slate-500">
            A recorded run of the review pipeline: it scored this snippet{' '}
            {data.aiReviewScore}/10 and wrote the findings on that page. Copied verbatim, not
            written by hand.
          </p>
        </div>
        <div className="flex items-center gap-2 shrink-0 self-center">
          <span className="text-xs font-mono text-vibe-cyan font-semibold">
            {data.aiReviewScore}/10
          </span>
          <ArrowRight className="w-3.5 h-3.5 text-slate-500 group-hover:text-vibe-cyan transition-colors" />
        </div>
      </div>
    </Link>
  );
}
