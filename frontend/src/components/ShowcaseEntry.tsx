import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { ArrowRight, ShieldCheck } from 'lucide-react';
import { useTranslation } from 'react-i18next';
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
  const { t } = useTranslation('home');
  const { data } = useQuery<ShowcasePost | null>({
    queryKey: ['showcase'],
    queryFn: async () => (await apiClient.get('/showcase')).data.data ?? null,
    staleTime: 1000 * 60 * 5,
  });

  if (!data?.postId) return null;

  return (
    <Link
      to={'/post/' + data.postId}
      className="group block rounded-lg border border-vibe-accent/30 bg-vibe-accent/5 transition-colors hover:border-vibe-accent/50 hover:bg-vibe-accent/10"
    >
      <div className="flex items-start gap-3 px-4 py-3">
        <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-md border border-vibe-accent/40 bg-vibe-accent/10">
          <ShieldCheck className="h-4 w-4 text-vibe-accent" />
        </div>
        <div className="min-w-0 flex-1">
          <p className="text-xs font-medium uppercase tracking-wide text-vibe-accent">
            {t('showcaseEntry.kicker')}
          </p>
          <p className="mt-1 truncate text-sm font-medium text-vibe-text">
            {data.title}
          </p>
          <p className="mt-0.5 text-xs text-vibe-muted">
            {t('showcaseEntry.desc', { score: data.aiReviewScore })}
          </p>
        </div>
        <div className="flex items-center gap-2 shrink-0 self-center">
          <span className="text-xs font-semibold tabular-nums text-vibe-accent">
            {data.aiReviewScore}/10
          </span>
          <ArrowRight className="h-3.5 w-3.5 text-vibe-faint transition-colors group-hover:text-vibe-accent" />
        </div>
      </div>
    </Link>
  );
}
