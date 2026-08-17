import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { Clock, Inbox, PieChart } from 'lucide-react';
import { apiClient } from '../api/client';
import type { PostPageVo } from '../types/post';

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

interface ChannelStat {
  id: number;
  slug: string;
  postCount: string;
}

interface ChannelStatsResponse {
  code: number;
  data: ChannelStat[];
}

const statCards = [
  { key: 'totalPosts', label: 'Total Posts', color: 'bg-vibe-cyan' },
  { key: 'pendingAudits', label: 'Pending Audits', color: 'bg-amber-500' },
  { key: 'todayPosts', label: "Today's Posts", color: 'bg-vibe-emerald' },
] as const;

const channelName: Record<string, string> = {
  announcements: '社区公告',
  prompts: 'Prompt 工坊',
  showcase: '作品展示',
  agents: 'Agent 实战',
  'vibe-coding': 'Vibe Coding',
  debug: '代码急诊室',
  resources: '资源聚合',
};

export default function DashboardPage() {
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

  if (isLoading) {
    return (
      <div>
        <h1 className="text-xl font-bold font-mono text-slate-100 mb-8">
          <span className="text-vibe-cyan">$</span> Dashboard
        </h1>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          {[1, 2, 3].map((i) => (
            <div key={i} className="bg-vibe-surface border border-vibe-border rounded-lg p-6 animate-pulse">
              <div className="h-4 bg-vibe-card rounded w-24 mb-3" />
              <div className="h-8 bg-vibe-card rounded w-16" />
            </div>
          ))}
        </div>
      </div>
    );
  }

  if (isError) {
    return (
      <div>
        <h1 className="text-xl font-bold font-mono text-slate-100 mb-8">
          <span className="text-vibe-cyan">$</span> Dashboard
        </h1>
        <div className="bg-red-900/30 border border-red-500/40 rounded-lg p-6 text-center">
          <p className="text-red-400 font-mono text-sm">Failed to load dashboard data</p>
          <p className="text-red-500 text-xs font-mono mt-1">
            {(error as Error)?.message || 'An unexpected error occurred'}
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
      <h1 className="text-xl font-bold font-mono text-slate-100 mb-8">
        <span className="text-vibe-cyan">$</span> Dashboard
      </h1>

      {stats && (
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          {statCards.map((card) => (
            <div key={card.key} className="bg-vibe-surface border border-vibe-border rounded-lg overflow-hidden">
              <div className={`${card.color} h-1.5`} />
              <div className="p-6">
                <p className="text-xs font-mono text-slate-500">{card.label}</p>
                <p className="mt-2 text-3xl font-bold font-mono text-slate-100">
                  {stats[card.key].toLocaleString()}
                </p>
              </div>
            </div>
          ))}
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 mt-8">
        <section className="bg-vibe-surface border border-vibe-border rounded-lg overflow-hidden">
          <div className="flex items-center gap-2 px-4 py-3 bg-vibe-card/50 border-b border-vibe-border">
            <Inbox className="w-4 h-4 text-amber-400" />
            <h2 className="text-xs font-mono font-semibold text-slate-300 uppercase tracking-wider">
              Pending Audit Queue
            </h2>
            <span className="ml-auto text-[11px] font-mono text-slate-500">{pendingPosts.length} waiting</span>
          </div>
          <div className="p-3">
            {pendingPosts.length === 0 ? (
              <p className="px-2 py-6 text-center text-xs font-mono text-slate-500">// Queue empty — all clear.</p>
            ) : (
              <ul className="space-y-2">
                {pendingPosts.slice(0, 5).map((post) => (
                  <li key={post.id}>
                    <Link
                      to="/admin/audit"
                      className="flex items-start gap-3 px-2 py-2 rounded-md hover:bg-vibe-card/60 transition-colors"
                    >
                      <Clock className="w-3.5 h-3.5 text-slate-500 mt-0.5 shrink-0" />
                      <div className="min-w-0">
                        <p className="text-xs font-mono text-slate-200 truncate">{post.title}</p>
                        <p className="text-[10px] font-mono text-slate-500 mt-0.5">
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

        <section className="bg-vibe-surface border border-vibe-border rounded-lg overflow-hidden">
          <div className="flex items-center gap-2 px-4 py-3 bg-vibe-card/50 border-b border-vibe-border">
            <PieChart className="w-4 h-4 text-vibe-cyan" />
            <h2 className="text-xs font-mono font-semibold text-slate-300 uppercase tracking-wider">
              Posts by Channel
            </h2>
            <span className="ml-auto text-[11px] font-mono text-slate-500">{channelStats.length} channels</span>
          </div>
          <div className="p-4 space-y-2.5">
            {channelStats.map((channel) => {
              const count = Number(channel.postCount) || 0;
              return (
                <div key={channel.id}>
                  <div className="flex items-center justify-between text-[11px] font-mono mb-1">
                    <span className="text-slate-300">{channelName[channel.slug] ?? channel.slug}</span>
                    <span className="text-slate-500">{count}</span>
                  </div>
                  <div className="h-1.5 bg-vibe-card rounded-full overflow-hidden">
                    <div
                      className="h-full bg-vibe-cyan/60 rounded-full transition-all"
                      style={{ width: Math.max(4, (count / maxPosts) * 100) + '%' }}
                    />
                  </div>
                </div>
              );
            })}
          </div>
        </section>
      </div>
    </div>
  );
}
