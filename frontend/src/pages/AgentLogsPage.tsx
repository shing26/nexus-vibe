import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  Terminal,
  CheckCircle2,
  Flag,
  AlertOctagon,
  AlertTriangle,
  Layers,
} from 'lucide-react';
import { apiClient } from '../api/client';
import Pagination from '../components/Pagination';
import EmptyState from '../components/EmptyState';
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
    label: 'Total Reviews',
    valueClass: 'text-slate-100',
    accentClass: 'bg-slate-400',
    icon: Layers,
  },
  {
    key: 'approved',
    label: 'Approved',
    valueClass: 'text-vibe-emerald',
    accentClass: 'bg-vibe-emerald',
    icon: CheckCircle2,
  },
  {
    key: 'flagged',
    label: 'Flagged',
    valueClass: 'text-amber-400',
    accentClass: 'bg-amber-500',
    icon: Flag,
  },
  {
    key: 'critical',
    label: 'Critical',
    valueClass: 'text-red-400',
    accentClass: 'bg-red-500',
    icon: AlertOctagon,
  },
  {
    key: 'high',
    label: 'High Severity',
    valueClass: 'text-orange-400',
    accentClass: 'bg-orange-500',
    icon: AlertTriangle,
  },
] as const;

const severityStyles: Record<string, string> = {
  critical: 'bg-red-900/30 text-red-400 ring-red-500/40',
  high: 'bg-orange-900/30 text-orange-400 ring-orange-500/40',
  medium: 'bg-yellow-900/30 text-yellow-400 ring-yellow-500/40',
  low: 'bg-vibe-emerald/10 text-vibe-emerald ring-vibe-emerald/30',
  unknown: 'bg-slate-800/60 text-slate-400 ring-slate-600/50',
  unavailable: 'bg-slate-900/40 text-slate-500 ring-slate-700/40',
};

function MacDots() {
  return (
    <div className="flex items-center gap-1.5 px-3">
      <span className="w-2.5 h-2.5 rounded-full bg-red-500/80" />
      <span className="w-2.5 h-2.5 rounded-full bg-yellow-500/80" />
      <span className="w-2.5 h-2.5 rounded-full bg-green-500/80" />
    </div>
  );
}

function SeverityBadge({ severity }: { severity: string | null | undefined }) {
  const normalized = (severity || 'unavailable').toLowerCase();
  const className = severityStyles[normalized] || severityStyles.unknown;

  return (
    <span className={'inline-flex items-center rounded-md px-2 py-0.5 text-[10px] font-mono uppercase ring-1 ring-inset ' + className}>
      {normalized}
    </span>
  );
}

function StatusBadge({ log }: { log: AiLog }) {
  const approved = log.isApproved === 1;
  const unavailable = log.status === 'unavailable';
  const label = unavailable ? 'UNAVAILABLE' : approved ? 'APPROVED' : 'FLAGGED';

  return (
    <span
      className={
        'inline-flex items-center rounded-md px-2 py-0.5 text-[10px] font-mono font-semibold ring-1 ring-inset ' +
        (unavailable
          ? 'bg-slate-900/40 text-slate-500 ring-slate-700/40'
          : approved
          ? 'bg-vibe-emerald/10 text-vibe-emerald ring-vibe-emerald/30'
          : 'bg-amber-900/30 text-amber-400 ring-amber-500/40')
      }
    >
      {label}
    </span>
  );
}

function formatTimestamp(value: string) {
  return value ? value.replace('T', ' ').slice(0, 19) : '--';
}

function TerminalLogRow({ log }: { log: AiLog }) {
  return (
    <div className="bg-vibe-surface border border-vibe-border rounded-xl overflow-hidden">
      <div className="flex items-center h-9 bg-vibe-card border-b border-vibe-border select-none">
        <MacDots />
        <span className="flex-1 text-center text-[11px] font-mono text-slate-500 truncate px-2">
          ai_review_log — Log Entry #{log.id}
        </span>
        <div className="w-16 shrink-0" />
      </div>
      <div className="p-4 font-mono text-xs space-y-2">
        <div className="flex flex-wrap items-center gap-x-2 gap-y-1 text-slate-300">
          <span className="text-vibe-cyan">$</span>
          <span>{log.reviewer}</span>
          <span className="text-slate-600">→</span>
          <span className="text-vibe-cyan">Post #{log.postId}</span>
        </div>
        <p className="text-slate-400 truncate">"{log.postTitle}"</p>
        <div className="flex flex-wrap items-center gap-x-2 gap-y-1 text-slate-500">
          <span>Severity:</span>
          <SeverityBadge severity={log.severity} />
          <span className="text-slate-700">|</span>
          <span>Status:</span>
          <StatusBadge log={log} />
        </div>
        <div className="text-slate-500">
          <span className="text-slate-600">Timestamp:</span> {formatTimestamp(log.createdAt)}
        </div>
      </div>
    </div>
  );
}

function StatsSkeleton() {
  return (
    <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-3 mb-8">
      {[1, 2, 3, 4, 5].map((i) => (
        <div key={i} className="bg-vibe-surface border border-vibe-border rounded-xl p-4 animate-pulse">
          <div className="h-3 bg-vibe-card rounded w-20 mb-3" />
          <div className="h-7 bg-vibe-card/60 rounded w-10" />
        </div>
      ))}
    </div>
  );
}

function LogsSkeleton() {
  return (
    <div className="space-y-4">
      {[1, 2, 3].map((i) => (
        <div key={i} className="bg-vibe-surface border border-vibe-border rounded-xl overflow-hidden relative">
          <div className="absolute inset-0 bg-gradient-to-r from-transparent via-vibe-cyan/5 to-transparent bg-[length:200%_100%] animate-shimmer" />
          <div className="flex items-center h-9 bg-vibe-card border-b border-vibe-border px-3 relative">
            <div className="flex gap-1.5">
              <span className="w-2.5 h-2.5 rounded-full bg-slate-700" />
              <span className="w-2.5 h-2.5 rounded-full bg-slate-700" />
              <span className="w-2.5 h-2.5 rounded-full bg-slate-700" />
            </div>
            <div className="h-2.5 bg-vibe-card rounded w-44 mx-auto" />
          </div>
          <div className="p-4 space-y-2.5 relative">
            <div className="h-2.5 bg-vibe-card rounded w-3/5" />
            <div className="h-2.5 bg-vibe-card/70 rounded w-2/3" />
            <div className="h-2 bg-vibe-card/50 rounded w-1/3" />
          </div>
        </div>
      ))}
    </div>
  );
}

export default function AgentLogsPage() {
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
    <div className="max-w-5xl mx-auto px-4 py-8">
      <div className="flex items-center gap-3 mb-6">
        <div className="w-9 h-9 rounded-lg bg-vibe-cyan/20 border border-vibe-cyan/30 flex items-center justify-center shrink-0">
          <Terminal className="w-4 h-4 text-vibe-cyan" />
        </div>
        <div className="min-w-0">
          <h1 className="text-xl font-bold font-mono text-slate-100">
            <span className="text-vibe-cyan">$</span> agent_logs — AI Agent Operations Console
          </h1>
          <p className="text-[11px] font-mono text-slate-500 mt-0.5">
            Review trail from autonomous safety &amp; code agents
          </p>
        </div>
      </div>

      {stats ? (
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-3 mb-8">
          {statCards.map((card) => {
            const Icon = card.icon;
            return (
              <div key={card.key} className="bg-vibe-surface border border-vibe-border rounded-xl p-4 relative overflow-hidden">
                <div className={'absolute inset-x-0 top-0 h-0.5 ' + card.accentClass} />
                <div className="flex items-center justify-between">
                  <p className="text-[10px] font-mono text-slate-500 uppercase tracking-wider">{card.label}</p>
                  <Icon className="w-3.5 h-3.5 text-slate-600" />
                </div>
                <p className={'mt-2 text-2xl font-bold font-mono ' + card.valueClass}>
                  {stats[card.key].toLocaleString()}
                </p>
              </div>
            );
          })}
        </div>
      ) : statsLoading ? (
        <StatsSkeleton />
      ) : (
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-3 mb-8">
          {statCards.map((card) => (
            <div key={card.key} className="bg-vibe-surface border border-vibe-border rounded-xl p-4">
              <p className="text-[10px] font-mono text-slate-500 uppercase tracking-wider">{card.label}</p>
              <p className="mt-2 text-2xl font-bold font-mono text-slate-500">--</p>
            </div>
          ))}
        </div>
      )}

      <div className="flex items-center justify-between gap-3 mb-4">
        <div className="min-w-0">
          <h2 className="text-[11px] font-mono font-semibold text-slate-500 uppercase tracking-widest">Recent Reviews</h2>
          {logsData?.data && (
            <p className="text-[10px] font-mono text-slate-600 mt-0.5">{logsData.data.total} entries</p>
          )}
        </div>
        <select
          value={reviewer}
          onChange={(e) => handleReviewerChange(e.target.value as ReviewerFilter)}
          className="px-3 py-1.5 bg-vibe-surface border border-vibe-border rounded-lg text-[11px] font-mono text-slate-300 focus:outline-none focus:ring-1 focus:ring-vibe-cyan/50 focus:border-vibe-cyan/50 transition-colors"
        >
          <option value="all" className="bg-vibe-bg">All</option>
          <option value="code-review" className="bg-vibe-bg">Code Review</option>
          <option value="safety-check" className="bg-vibe-bg">Safety Check</option>
        </select>
      </div>

      {logsLoading ? (
        <LogsSkeleton />
      ) : isError ? (
        <div className="bg-red-900/30 border border-red-500/40 rounded-xl p-6 text-center">
          <p className="text-red-400 font-mono text-sm">Failed to load agent logs</p>
          <p className="text-red-500 text-xs font-mono mt-1">
            {(error as Error)?.message || 'An unexpected error occurred'}
          </p>
        </div>
      ) : visibleLogs.length === 0 ? (
        <div className="bg-vibe-surface border border-vibe-border rounded-xl">
          <EmptyState preset="noPosts" />
        </div>
      ) : (
        <>
          <div className="space-y-4">
            {visibleLogs.map((log) => (
              <TerminalLogRow key={log.id} log={log} />
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
