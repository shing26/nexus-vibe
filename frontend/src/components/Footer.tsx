import { useEffect, useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Bot, Radio } from 'lucide-react';
import { apiClient } from '../api/client';
import { ExternalLink } from './ui/ExternalLink';
import { ADR_INDEX_URL, DRILL_URL, LOADTEST_URL } from '../links';

interface AiLog {
  id: string;
  postId: string;
  postTitle: string;
  reviewer: string;
  severity: string;
  isApproved: number;
  status?: 'completed' | 'unavailable';
  createdAt: string;
}

function formatTime(value: string): string {
  if (!value) return '--:--:--';
  return value.replace('T', ' ').slice(11, 19);
}

function AgentTicker() {
  const { t } = useTranslation('common');
  const [index, setIndex] = useState(0);

  const { data: logsData } = useQuery({
    queryKey: ['agent-ticker'],
    queryFn: async () => (await apiClient.get('/agent-logs/ticker')).data.data,
    staleTime: 1000 * 60,
    refetchInterval: 1000 * 60,
  });

  const logs: AiLog[] = logsData ?? [];

  useEffect(() => {
    if (logs.length <= 1) return;
    const timer = setInterval(() => {
      setIndex((i) => (i + 1) % logs.length);
    }, 6000);
    return () => clearInterval(timer);
  }, [logs.length]);

  const active = logs[index];
  const unavailable = !!active && (active.status === 'unavailable' || (!active.status && !active.severity));
  const status = !active
    ? t('ticker.status.idle')
    : unavailable
      ? t('ticker.status.unavailable')
      : active.isApproved === 1
        ? t('ticker.status.approved')
        : t('ticker.status.flagged');
  const severityLabel = active?.severity || '--';

  const message = useMemo(() => {
    if (!active) return t('ticker.idle');
    return t('ticker.verified', {
      time: formatTime(active.createdAt),
      reviewer: active.reviewer,
      title: active.postTitle,
      id: active.postId,
      severity: severityLabel,
      status,
    });
  }, [active, severityLabel, status, t]);

  return (
    <div className="border-b border-vibe-border bg-vibe-bg">
      <div className="mx-auto max-w-[1400px] px-4 sm:px-6 lg:px-8">
        <div className="flex min-w-0 items-center gap-3 py-2">
          <span className="flex shrink-0 items-center gap-1.5 text-[11px] text-vibe-muted">
            <span className="relative flex h-1.5 w-1.5">
              <span className="absolute inline-flex h-full w-full rounded-full bg-vibe-success opacity-60 motion-reduce:animate-none animate-ping" />
              <span className="relative inline-flex h-1.5 w-1.5 rounded-full bg-vibe-success" />
            </span>
            {t('ticker.label')}
          </span>
          <span className="hidden shrink-0 text-[11px] text-vibe-faint sm:inline">
            <Bot className="mr-1 inline h-3 w-3 align-[-2px]" />
            {logs.length}
          </span>
          <div className="min-w-0 flex-1 truncate text-[11px] text-vibe-muted">
            <span className="mr-1.5 select-none text-vibe-accent">$</span>
            <span key={active?.id ?? 'idle'} className="inline-block max-w-full align-middle">
              {message}
            </span>
          </div>
          <span
            className={
              'shrink-0 rounded border px-1.5 py-0.5 text-[11px] font-medium ' +
              (unavailable
                ? 'border-vibe-border text-vibe-faint'
                : active?.isApproved === 1
                  ? 'border-vibe-success/40 text-vibe-success'
                  : active
                    ? 'border-vibe-warning/40 text-vibe-warning'
                    : 'border-vibe-border text-vibe-faint')
            }
          >
            {status}
          </span>
          <Radio className="hidden shrink-0 text-vibe-faint md:block" />
        </div>
      </div>
    </div>
  );
}

/**
 * The footer keeps the evidence links at every width: the repository's own
 * decisions and measurements are public on GitHub, and a visitor who wants to
 * check a claim should not have to hunt for them. Since the v2 shell the footer
 * is their only home — the top bar carries no navigation beyond search and the
 * user menu.
 */
export default function Footer() {
  const { t } = useTranslation('common');
  return (
    <footer className="mt-auto border-t border-vibe-border bg-vibe-surface">
      <AgentTicker />
      <div className="mx-auto max-w-[1400px] px-4 py-6 sm:px-6 lg:px-8">
        <div className="flex flex-col items-center justify-between gap-3 sm:flex-row">
          <p className="text-xs text-vibe-muted">{t('footer.rights', { year: new Date().getFullYear() })}</p>
          <div className="flex flex-wrap items-center justify-center gap-x-4 gap-y-1">
            <ExternalLink href={ADR_INDEX_URL} title={t('footer.adrTitle')}>
              {t('footer.adrs')}
            </ExternalLink>
            <ExternalLink href={LOADTEST_URL} title={t('footer.loadtestTitle')}>
              {t('footer.loadtest')}
            </ExternalLink>
            <ExternalLink href={DRILL_URL} title={t('footer.drillTitle')}>
              {t('footer.drill')}
            </ExternalLink>
          </div>
          <p className="text-[11px] text-vibe-faint">{t('footer.tagline')}</p>
        </div>
      </div>
    </footer>
  );
}
