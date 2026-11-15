import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { Clock, Inbox, KeyRound, PieChart } from 'lucide-react';
import { apiClient } from '../api/client';
import { serverErrorMessage } from '../api/serverErrorMessage';
import { channelLabel } from '../lib/channels';
import Skeleton from '../components/Skeleton';
import type { ChannelStats, PostPageVo } from '../types/post';

interface DashboardData {
  totalPosts: number;
  pendingAudits: number;
  todayPosts: number;
}

interface DashboardResponse {
  code: number;
  data: DashboardData;
}

interface AuditPostsResponse {
  code: number;
  data: PostPageVo[];
}

interface ChannelStatsResponse {
  code: number;
  data: ChannelStats[];
}

const statCards = [
  { key: 'totalPosts', labelKey: 'totalPosts', color: 'bg-vibe-accent' },
  { key: 'pendingAudits', labelKey: 'pendingAudits', color: 'bg-vibe-warning' },
  { key: 'todayPosts', labelKey: 'todayPosts', color: 'bg-vibe-success' },
] as const;

export default function DashboardPage() {
  const { t } = useTranslation('admin');
  const { data, isLoading, isError, error } = useQuery<DashboardResponse>({
    queryKey: ['admin', 'dashboard'],
    queryFn: () =>
      apiClient.get('/admin/dashboard').then((r) => r.data),
  });
  const { data: auditData } = useQuery<AuditPostsResponse>({
    queryKey: ['admin', 'audit', 'dashboard'],
    queryFn: () => apiClient.get('/admin/audit/posts').then((r) => r.data),
  });
  const { data: channelStatsData } = useQuery<ChannelStatsResponse>({
    queryKey: ['channels', 'stats'],
    queryFn: () => apiClient.get('/channels/stats').then((r) => r.data),
  });

  // Admin password recovery stopgap: no mail delivery yet, so the generated
  // temporary password is shown here for the admin to hand over out-of-band.
  const [resetUsername, setResetUsername] = useState('');
  const [resetResult, setResetResult] = useState<{ username: string; tempPassword: string } | null>(null);
  const [resetError, setResetError] = useState('');
  const [resetting, setResetting] = useState(false);

  const handleResetPassword = async () => {
    setResetError('');
    setResetResult(null);
    if (!resetUsername.trim()) { setResetError(t('dashboard.enterUsername')); return; }
    setResetting(true);
    try {
      const res = await apiClient.post('/admin/users/reset-password', { username: resetUsername.trim() });
      setResetResult(res.data.data);
      setResetUsername('');
    } catch (err: any) {
      setResetError(err?.response?.data?.message || t('dashboard.resetFailed'));
    } finally { setResetting(false); }
  };

  if (isLoading) {
    return (
      <div>
        <h1 className="mb-8 text-xl font-semibold text-vibe-text">{t('dashboard.title')}</h1>
        <div className="grid grid-cols-1 gap-6 md:grid-cols-3">
          {[1, 2, 3].map((i) => (
            <div key={i} className="rounded-lg border border-vibe-border bg-vibe-surface p-6">
              <Skeleton className="mb-3 h-4 w-24" />
              <Skeleton className="h-8 w-16" />
            </div>
          ))}
        </div>
      </div>
    );
  }

  if (isError) {
    return (
      <div>
        <h1 className="mb-8 text-xl font-semibold text-vibe-text">{t('dashboard.title')}</h1>
        <div role="alert" className="rounded-lg border border-vibe-danger/40 bg-vibe-danger/5 p-6 text-center">
          <p className="text-sm text-vibe-danger">{t('dashboard.failedToLoad')}</p>
          <p className="mt-1 text-xs text-vibe-danger">
            {serverErrorMessage(error, t('unexpectedError'))}
          </p>
        </div>
      </div>
    );
  }

  const stats = data?.data;
  const pendingPosts = auditData?.data ?? [];
  const channelStats = channelStatsData?.data ?? [];
  const maxPosts = Math.max(1, ...channelStats.map((c) => Number(c.postCount) || 0));

  return (
    <div>
      <h1 className="mb-8 text-xl font-semibold text-vibe-text">{t('dashboard.title')}</h1>

      {stats && (
        <div className="grid grid-cols-1 gap-6 md:grid-cols-3">
          {statCards.map((card) => (
            <div key={card.key} className="overflow-hidden rounded-lg border border-vibe-border bg-vibe-surface">
              <div className={`${card.color} h-1.5`} />
              <div className="p-6">
                <p className="text-xs text-vibe-muted">{t('dashboard.' + card.labelKey)}</p>
                <p className="mt-2 text-3xl font-semibold tabular-nums text-vibe-text">
                  {stats[card.key].toLocaleString()}
                </p>
              </div>
            </div>
          ))}
        </div>
      )}

      <div className="mt-8 grid grid-cols-1 gap-6 lg:grid-cols-2">
        <section className="overflow-hidden rounded-lg border border-vibe-border bg-vibe-surface">
          <div className="flex items-center gap-2 border-b border-vibe-border bg-vibe-card/50 px-4 py-3">
            <Inbox className="h-4 w-4 text-vibe-warning" />
            <h2 className="text-xs font-semibold uppercase tracking-wide text-vibe-text">
              {t('dashboard.pendingQueue')}
            </h2>
            <span className="ml-auto text-xs tabular-nums text-vibe-muted">{t('dashboard.waiting', { n: pendingPosts.length })}</span>
          </div>
          <div className="p-3">
            {pendingPosts.length === 0 ? (
              <p className="px-2 py-6 text-center text-xs text-vibe-muted">{t('dashboard.queueEmpty')}</p>
            ) : (
              <ul className="space-y-2">
                {pendingPosts.slice(0, 5).map((post) => (
                  <li key={post.id}>
                    <Link
                      to="/admin/audit"
                      className="flex items-start gap-3 rounded-md px-2 py-2 transition-colors hover:bg-vibe-card"
                    >
                      <Clock className="mt-0.5 h-3.5 w-3.5 shrink-0 text-vibe-faint" />
                      <div className="min-w-0">
                        <p className="truncate text-xs text-vibe-text">{post.title}</p>
                        <p className="mt-0.5 text-xs text-vibe-muted">
                          {post.authorName} · {post.categoryName}
                        </p>
                      </div>
                    </Link>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </section>

        <section className="overflow-hidden rounded-lg border border-vibe-border bg-vibe-surface">
          <div className="flex items-center gap-2 border-b border-vibe-border bg-vibe-card/50 px-4 py-3">
            <PieChart className="h-4 w-4 text-vibe-accent" />
            <h2 className="text-xs font-semibold uppercase tracking-wide text-vibe-text">
              {t('dashboard.postsByChannel')}
            </h2>
            <span className="ml-auto text-xs tabular-nums text-vibe-muted">{t('dashboard.channelsCount', { n: channelStats.length })}</span>
          </div>
          <div className="space-y-2.5 p-4">
            {channelStats.map((channel) => {
              const count = Number(channel.postCount) || 0;
              return (
                <div key={channel.id}>
                  <div className="mb-1 flex items-center justify-between text-xs">
                    <span className="text-vibe-text">{channelLabel(channel.slug) || channel.slug}</span>
                    <span className="tabular-nums text-vibe-muted">{count}</span>
                  </div>
                  <div className="h-1.5 overflow-hidden rounded-full bg-vibe-card">
                    <div
                      className="h-full rounded-full bg-vibe-accent/60 transition-all motion-reduce:transition-none"
                      style={{ width: Math.max(4, (count / maxPosts) * 100) + '%' }}
                    />
                  </div>
                </div>
              );
            })}
          </div>
        </section>
      </div>

      <section className="mt-8 overflow-hidden rounded-lg border border-vibe-border bg-vibe-surface">
        <div className="flex items-center gap-2 border-b border-vibe-border bg-vibe-card/50 px-4 py-3">
          <KeyRound className="h-4 w-4 text-vibe-success" />
          <h2 className="text-xs font-semibold uppercase tracking-wide text-vibe-text">
            {t('dashboard.accountRecovery')}
          </h2>
          <span className="ml-auto text-xs text-vibe-faint">{t('dashboard.recoveryNote')}</span>
        </div>
        <div className="space-y-3 p-4">
          <p className="text-xs text-vibe-muted">
            {t('dashboard.recoveryDescription')}
          </p>
          <div className="flex gap-2">
            <input
              type="text"
              value={resetUsername}
              onChange={(e) => setResetUsername(e.target.value)}
              onKeyDown={(e) => { if (e.key === 'Enter') handleResetPassword(); }}
              placeholder={t('dashboard.usernamePlaceholder')}
              className="max-w-xs flex-1 rounded-md border border-vibe-border bg-vibe-bg px-3 py-2 text-sm text-vibe-text placeholder-vibe-faint transition-colors focus:border-vibe-success/50 focus:outline-none focus:ring-1 focus:ring-vibe-success/50"
            />
            <button
              type="button"
              onClick={handleResetPassword}
              disabled={resetting}
              className="rounded-md bg-vibe-accent px-4 py-2 text-xs font-medium text-white transition-colors hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-40"
            >
              {resetting ? t('dashboard.generating') : t('dashboard.generate')}
            </button>
          </div>
          {resetError && (
            <p role="alert" className="text-xs text-vibe-danger">! {resetError}</p>
          )}
          {resetResult && (
            <div className="rounded-md border border-vibe-border bg-vibe-bg px-3 py-2 text-sm">
              <span className="text-vibe-muted">{t('dashboard.tempPassword', { username: resetResult.username })}</span>{' '}
              <span className="select-all font-medium text-vibe-success">{resetResult.tempPassword}</span>
            </div>
          )}
        </div>
      </section>
    </div>
  );
}
