import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { apiClient } from '../api/client';

interface TickerRow {
  id: string;
  postId: string;
  postTitle: string;
  reviewer: string;
  severity: string;
  isApproved: number;
  createdAt: string;
}

function formatTime(value: string): string {
  if (!value) return '--:--';
  return value.replace('T', ' ').slice(11, 16);
}

/**
 * The rail's live review feed. It reads the same ticker endpoint as the footer and
 * shares its query key, so the sidebar adds no request of its own. Rows are shown
 * exactly as the server returned them; an empty list or a failed request gets its
 * own sentence instead of a fabricated row.
 */
export default function SidebarReviews() {
  const { t } = useTranslation('common');
  const { data, isLoading, isError } = useQuery({
    queryKey: ['agent-ticker'],
    queryFn: async () => (await apiClient.get('/agent-logs/ticker')).data.data as TickerRow[],
    staleTime: 1000 * 60,
    refetchInterval: 1000 * 60,
  });

  const rows = (data ?? []).slice(0, 5);

  return (
    <section
      aria-label={t('sidebarReviews.title')}
      data-testid="sidebar-reviews"
      className="mx-2 mb-2 mt-3 hidden rounded-md border border-vibe-border bg-vibe-card p-2 xl:block"
    >
      <div className="flex items-center justify-between pb-1">
        <p className="text-xs text-vibe-faint">{t('sidebarReviews.title')}</p>
        <span className="text-[9px] tracking-wide text-vibe-success">{t('sidebarReviews.live')}</span>
      </div>
      {isLoading ? (
        <p className="border-t border-vibe-border pt-1.5 text-[10px] text-vibe-faint">
          {t('sidebarReviews.loading')}
        </p>
      ) : isError ? (
        <p className="border-t border-vibe-border pt-1.5 text-[10px] text-vibe-warning">
          {t('sidebarReviews.error')}
        </p>
      ) : rows.length === 0 ? (
        <p className="border-t border-vibe-border pt-1.5 text-[10px] text-vibe-faint">
          {t('sidebarReviews.empty')}
        </p>
      ) : (
        <ul>
          {rows.map((row) => {
            const approved = row.isApproved === 1;
            return (
              <li
                key={row.id}
                title={row.reviewer}
                className="grid grid-cols-[34px_minmax(0,1fr)_auto] items-center gap-1.5 border-t border-vibe-border py-1 text-[10px]"
              >
                <span className="tabular-nums text-vibe-faint">{formatTime(row.createdAt)}</span>
                <span className="truncate text-vibe-muted">{row.postTitle || '#' + row.postId}</span>
                <span className={approved ? 'text-vibe-success' : 'text-vibe-warning'}>
                  {t(approved ? 'sidebarReviews.approved' : 'sidebarReviews.flagged')}
                </span>
              </li>
            );
          })}
        </ul>
      )}
      <p className="mt-1 border-t border-vibe-border pt-1 text-[9px] text-vibe-faint">
        {t('sidebarReviews.source')}
      </p>
    </section>
  );
}
