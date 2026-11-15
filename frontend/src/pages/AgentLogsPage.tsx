import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import {
  Terminal,
  CheckCircle2,
  Flag,
  AlertOctagon,
  AlertTriangle,
  Layers,
} from 'lucide-react';
import { apiClient } from '../api/client';
import { serverErrorMessage } from '../api/serverErrorMessage';
import Pagination from '../components/Pagination';
import EmptyState from '../components/EmptyState';
import Skeleton from '../components/Skeleton';
import type { AiLog, AiLogStats, PageResponse } from '../types/post';

interface AgentLogsResponse {
  code: number;
  data: PageResponse<AiLog>;
}

interface AgentLogStatsResponse {
  code: number;
  data: AiLogStats;
}

type ReviewerFilter = 'all' | 'code-review' | 'safety-check';

const statCards = [
  {
    key: 'totalReviews',
    labelKey: 'stats.totalReviews',
    valueClass: 'text-vibe-text',
    accentClass: 'bg-vibe-muted',
    icon: Layers,
  },
  {
    key: 'approved',
    labelKey: 'stats.approved',
    valueClass: 'text-vibe-success',
    accentClass: 'bg-vibe-success',
    icon: CheckCircle2,
  },
  {
    key: 'flagged',
    labelKey: 'stats.flagged',
    valueClass: 'text-vibe-warning',
    accentClass: 'bg-vibe-warning',
    icon: Flag,
  },
  {
    key: 'critical',
    labelKey: 'stats.critical',
    valueClass: 'text-vibe-danger',
    accentClass: 'bg-vibe-danger',
    icon: AlertOctagon,
  },
  {
    key: 'high',
    labelKey: 'stats.high',
    valueClass: 'text-vibe-high',
    accentClass: 'bg-vibe-high',
    icon: AlertTriangle,
  },
] as const;

/** The v2 severity map: low → success, medium → warning, high → vibe-high, critical → danger. */
const severityStyles: Record<string, string> = {
  critical: 'bg-vibe-danger/10 text-vibe-danger ring-vibe-danger/40',
  high: 'bg-vibe-high/10 text-vibe-high ring-vibe-high/40',
  medium: 'bg-vibe-warning/10 text-vibe-warning ring-vibe-warning/40',
  low: 'bg-vibe-success/10 text-vibe-success ring-vibe-success/30',
  unknown: 'bg-vibe-muted/10 text-vibe-muted ring-vibe-border',
  unavailable: 'bg-vibe-muted/5 text-vibe-faint ring-vibe-border',
};

function SeverityBadge({ severity }: { severity: string | null | undefined }) {
  const { t } = useTranslation('agentLogs');
  const normalized = (severity || 'unavailable').toLowerCase();
  const className = severityStyles[normalized] || severityStyles.unknown;

  return (
    <span className={'inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ring-1 ring-inset ' + className}>
      {t('severityNames.' + normalized, { defaultValue: normalized })}
    </span>
  );
}

function StatusBadge({ log }: { log: AiLog }) {
  const { t } = useTranslation(['agentLogs', 'common']);
  const approved = log.isApproved === 1;
  const unavailable = log.status === 'unavailable';
  const label = unavailable
    ? t('severityNames.unavailable')
    : approved
      ? t('common:status.approved')
      : t('common:status.flagged');

  return (
    <span
      className={
        'inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ring-1 ring-inset ' +
        (unavailable
          ? 'bg-vibe-muted/5 text-vibe-faint ring-vibe-border'
          : approved
          ? 'bg-vibe-success/10 text-vibe-success ring-vibe-success/30'
          : 'bg-vibe-warning/10 text-vibe-warning ring-vibe-warning/40')
      }
    >
      {label}
    </span>
  );
}

function formatTimestamp(value: string) {
  return value ? value.replace('T', ' ').slice(0, 19) : '--';
}

function LogRow({ log }: { log: AiLog }) {
  const { t } = useTranslation('agentLogs');
  return (
    <div className="overflow-hidden rounded-lg border border-vibe-border bg-vibe-surface">
      <div className="border-b border-vibe-border px-4 py-2 text-xs font-medium text-vibe-text">
        {t('row.fileTitle', { id: log.id })}
      </div>
      <div className="space-y-2 p-4 text-sm">
        <div className="flex flex-wrap items-center gap-x-2 gap-y-1 text-vibe-text">
          <span className="text-vibe-accent">$</span>
          <span>{log.reviewer}</span>
          <span className="text-vibe-faint">→</span>
          <span className="text-vibe-accent">{t('row.postRef', { id: log.postId })}</span>
        </div>
        <p className="truncate text-vibe-muted">"{log.postTitle}"</p>
        <div className="flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-vibe-muted">
          <span>{t('row.severity')}</span>
          <SeverityBadge severity={log.severity} />
          <span className="text-vibe-border">|</span>
          <span>{t('row.status')}</span>
          <StatusBadge log={log} />
        </div>
        <div className="text-xs tabular-nums text-vibe-muted">
          <span className="text-vibe-faint">{t('row.timestamp')}</span> {formatTimestamp(log.createdAt)}
        </div>
      </div>
    </div>
  );
}

function StatsSkeleton() {
  return (
    <div className="mb-8 grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
      {[1, 2, 3, 4, 5].map((i) => (
        <div key={i} className="rounded-lg border border-vibe-border bg-vibe-surface p-4">
          <Skeleton className="mb-3 h-3 w-20" />
          <Skeleton className="h-7 w-10" />
        </div>
      ))}
    </div>
  );
}

function LogsSkeleton() {
  return (
    <div className="space-y-4">
      {[1, 2, 3].map((i) => (
        <div key={i} className="overflow-hidden rounded-lg border border-vibe-border bg-vibe-surface">
          <div className="border-b border-vibe-border px-4 py-2">
            <Skeleton className="mx-auto h-3 w-44" />
          </div>
          <div className="space-y-2.5 p-4">
            <Skeleton className="h-3 w-3/5" />
            <Skeleton className="h-3 w-2/3" />
            <Skeleton className="h-2.5 w-1/3" />
          </div>
        </div>
      ))}
    </div>
  );
}

export default function AgentLogsPage() {
  const { t } = useTranslation('agentLogs');
  const [page, setPage] = useState(1);
  const [reviewer, setReviewer] = useState<ReviewerFilter>('all');

  const { data: statsData, isLoading: statsLoading } = useQuery<AgentLogStatsResponse>({
    queryKey: ['agent-logs', 'stats'],
    queryFn: async () => (await apiClient.get<AgentLogStatsResponse>('/agent-logs/stats')).data,
    staleTime: 1000 * 60,
  });

  const { data: logsData, isLoading: logsLoading, isError, error } = useQuery<AgentLogsResponse>({
    queryKey: ['agent-logs', 'list', page],
    queryFn: async () =>
      (await apiClient.get<AgentLogsResponse>('/agent-logs', { params: { page, size: 20 } })).data,
    staleTime: 1000 * 30,
  });

  const stats = statsData?.data;
  const logs = logsData?.data.list ?? [];
  const visibleLogs =
    reviewer === 'all'
      ? logs
      : logs.filter((log) => {
          const reviewerName = log.reviewer.toLowerCase();
          return reviewer === 'code-review'
            ? reviewerName.includes('code-review') ||
                (reviewerName.includes('code') && reviewerName.includes('review'))
            : reviewerName.includes('safety') || reviewerName.includes('safe');
        });

  const handleReviewerChange = (value: ReviewerFilter) => {
    setReviewer(value);
    setPage(1);
  };

  return (
    <div className="mx-auto max-w-5xl px-4 py-8">
      <div className="mb-6 flex items-center gap-3">
        <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-md border border-vibe-border bg-vibe-surface text-vibe-accent">
          <Terminal className="h-4 w-4" />
        </div>
        <div className="min-w-0">
          <h1 className="truncate text-lg font-semibold text-vibe-text">{t('title')}</h1>
          <p className="mt-0.5 text-xs text-vibe-muted">{t('subtitle')}</p>
        </div>
      </div>

      {stats ? (
        <div className="mb-8 grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
          {statCards.map((card) => {
            const Icon = card.icon;
            return (
              <div key={card.key} className="relative overflow-hidden rounded-lg border border-vibe-border bg-vibe-surface p-4">
                <div className={'absolute inset-x-0 top-0 h-0.5 ' + card.accentClass} />
                <div className="flex items-center justify-between">
                  <p className="text-xs uppercase tracking-wide text-vibe-muted">{t(card.labelKey)}</p>
                  <Icon className="h-3.5 w-3.5 text-vibe-faint" />
                </div>
                <p className={'mt-2 text-2xl font-semibold tabular-nums ' + card.valueClass}>
                  {stats[card.key].toLocaleString()}
                </p>
              </div>
            );
          })}
        </div>
      ) : statsLoading ? (
        <StatsSkeleton />
      ) : (
        <div className="mb-8 grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
          {statCards.map((card) => (
            <div key={card.key} className="rounded-lg border border-vibe-border bg-vibe-surface p-4">
              <p className="text-xs uppercase tracking-wide text-vibe-muted">{t(card.labelKey)}</p>
              <p className="mt-2 text-2xl font-semibold tabular-nums text-vibe-faint">--</p>
            </div>
          ))}
        </div>
      )}

      <div className="mb-4 flex items-center justify-between gap-3">
        <div className="min-w-0">
          <h2 className="text-xs font-semibold uppercase tracking-wide text-vibe-muted">{t('recentReviews')}</h2>
          {logsData?.data && (
            <p className="mt-0.5 text-xs tabular-nums text-vibe-faint">{t('entries', { n: logsData.data.total })}</p>
          )}
        </div>
        <select
          value={reviewer}
          onChange={(e) => handleReviewerChange(e.target.value as ReviewerFilter)}
          aria-label={t('recentReviews')}
          className="rounded-md border border-vibe-border bg-vibe-surface px-3 py-1.5 text-xs text-vibe-text transition-colors focus:border-vibe-accent/50 focus:outline-none focus:ring-1 focus:ring-vibe-accent/50"
        >
          <option value="all" className="bg-vibe-bg">{t('filters.all')}</option>
          <option value="code-review" className="bg-vibe-bg">{t('filters.codeReview')}</option>
          <option value="safety-check" className="bg-vibe-bg">{t('filters.safetyCheck')}</option>
        </select>
      </div>

      {logsLoading ? (
        <LogsSkeleton />
      ) : isError ? (
        <div role="alert" className="rounded-lg border border-vibe-danger/40 bg-vibe-danger/5 p-6 text-center">
          <p className="text-sm text-vibe-danger">{t('failedToLoad')}</p>
          <p className="mt-1 text-xs text-vibe-danger">
            {serverErrorMessage(error, t('unexpectedError'))}
          </p>
        </div>
      ) : visibleLogs.length === 0 ? (
        <div className="rounded-lg border border-vibe-border bg-vibe-surface">
          <EmptyState
            preset="noPosts"
            title={t('empty.title')}
            desc={t('empty.desc')}
          />
        </div>
      ) : (
        <>
          <div className="space-y-4">
            {visibleLogs.map((log) => (
              <LogRow key={log.id} log={log} />
            ))}
          </div>
          {logsData?.data && (
            <Pagination
              page={logsData.data.page}
              pages={logsData.data.pages}
              onPageChange={setPage}
            />
          )}
        </>
      )}
    </div>
  );
}
