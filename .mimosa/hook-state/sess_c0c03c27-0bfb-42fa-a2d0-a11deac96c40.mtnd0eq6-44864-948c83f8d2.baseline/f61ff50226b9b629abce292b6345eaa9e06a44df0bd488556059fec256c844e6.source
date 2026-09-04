import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../api/client';

interface PendingPost {
  id: number;
  title: string;
  summary: string;
  authorName: string;
  categoryName: string | null;
  createTime: string;
  status: number;
  safetySeverity: string | null;
  safetyClassification: string | null;
  safetyIsApproved: number | null;
}

interface PendingPostsResponse {
  code: number;
  message: string;
  data: PendingPost[];
}

const safetyTagColors: Record<string, string> = {
  'Prompt injection': 'bg-red-900/30 text-red-400 ring-red-500/40',
  'Harmful content': 'bg-red-900/30 text-red-400 ring-red-500/40',
  Spam: 'bg-orange-900/30 text-orange-400 ring-orange-500/40',
  Safe: 'bg-vibe-emerald/10 text-vibe-emerald ring-vibe-emerald/30',
};

const safetyStatusMap: Record<string, { label: string; dotColor: string } | null> = {
  'Prompt injection': { label: 'Pending review', dotColor: 'bg-amber-400' },
  'Harmful content': { label: 'Auto-hidden', dotColor: 'bg-red-500' },
  Spam: { label: 'Auto-hidden', dotColor: 'bg-red-500' },
  Safe: { label: 'Clean', dotColor: 'bg-green-500' },
};

function SafetyBadge({ classification }: { classification: string | null }) {
  if (!classification) return null;

  const tagColor =
    safetyTagColors[classification] || 'bg-gray-100 text-gray-800 ring-gray-600/20';
  const statusInfo = safetyStatusMap[classification];

  return (
    <div className="flex items-center gap-3">
      <span
        className={`inline-flex items-center rounded-md px-2 py-0.5 text-[10px] font-mono ring-1 ring-inset ${tagColor}`}
      >
        {classification}
      </span>
      {statusInfo && (
        <span className="inline-flex items-center gap-1.5 text-[10px] font-mono text-slate-500">
          <span className={`inline-block w-2 h-2 rounded-full ${statusInfo.dotColor}`} />
          {statusInfo.label}
        </span>
      )}
    </div>
  );
}

export default function AuditPage() {
  const queryClient = useQueryClient();
  const [rejectId, setRejectId] = useState<number | null>(null);
  const [rejectReason, setRejectReason] = useState('');

  const { data, isLoading, isError, error } = useQuery<PendingPostsResponse>({
    queryKey: ['admin', 'pending-posts'],
    queryFn: () =>
      apiClient.get('/admin/pending-posts').then((r) => r.data as PendingPostsResponse),
  });

  const auditMutation = useMutation({
    mutationFn: ({
      id,
      action,
      reason,
    }: {
      id: number;
      action: 'APPROVED' | 'REJECTED';
      reason?: string;
    }) => apiClient.post(`/admin/audit/${id}`, { action, reason }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'pending-posts'] });
      setRejectId(null);
      setRejectReason('');
    },
  });

  const handleApprove = (id: number) => {
    auditMutation.mutate({ id, action: 'APPROVED' });
  };

  const handleReject = (id: number) => {
    auditMutation.mutate({ id, action: 'REJECTED', reason: rejectReason });
  };

  if (isLoading) {
    return (
      <div>
        <h1 className="text-base font-semibold font-mono text-slate-100 mb-8"><span className="text-vibe-cyan">$</span> Audit Queue</h1>
        <div className="flex items-center justify-center py-20">
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-vibe-cyan" />
        </div>
      </div>
    );
  }

  if (isError) {
    return (
      <div>
        <h1 className="text-base font-semibold font-mono text-slate-100 mb-8"><span className="text-vibe-cyan">$</span> Audit Queue</h1>
        <div className="bg-red-900/30 border border-red-500/40 rounded-lg p-6 text-center">
          <p className="text-red-400 font-mono text-sm">Failed to load pending posts</p>
          <p className="text-red-500 text-xs font-mono mt-1">
            {(error as Error)?.message || 'An unexpected error occurred'}
          </p>
        </div>
      </div>
    );
  }

  const posts = data?.data ?? [];

  if (posts.length === 0) {
    return (
      <div>
        <h1 className="text-base font-semibold font-mono text-slate-100 mb-8"><span className="text-vibe-cyan">$</span> Audit Queue</h1>
        <div className="bg-vibe-surface border border-vibe-border rounded-lg p-12 text-center">
          <p className="text-slate-400 font-mono text-sm">No pending posts to review.</p>
          <p className="text-slate-600 text-[11px] font-mono mt-1">
            All caught up -- new posts will appear here when submitted.
          </p>
        </div>
      </div>
    );
  }

  return (
    <div>
      <div className="flex items-center justify-between mb-8">
        <div>
          <h1 className="text-base font-semibold font-mono text-slate-100"><span className="text-vibe-cyan">$</span> Audit Queue</h1>
          <p className="text-slate-500 text-xs font-mono mt-1">
            {posts.length} post{posts.length !== 1 ? 's' : ''} pending review
          </p>
        </div>
      </div>

      <div className="space-y-4">
        {posts.map((post) => (
          <div
            key={post.id}
            className="bg-vibe-surface border border-vibe-border rounded-lg p-6"
          >
            <div className="flex items-start justify-between gap-6">
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-3 flex-wrap">
                  <h3 className="text-sm font-semibold font-mono text-slate-200 truncate">
                    {post.title}
                  </h3>
                  <SafetyBadge
                    classification={post.safetyClassification}
                    
                  />
                </div>
                <div className="flex items-center gap-4 mt-1 text-xs font-mono text-slate-500">
                  <span>by {post.authorName}</span>
                  {post.categoryName && (
                    <span className="inline-flex items-center gap-1">
                      <span className="w-1.5 h-1.5 rounded-full bg-slate-600" />
                      {post.categoryName}
                    </span>
                  )}
                  <span className="inline-flex items-center gap-1">
                    <span className="w-1.5 h-1.5 rounded-full bg-slate-600" />
                    {new Date(post.createTime).toLocaleDateString('en-US', {
                      year: 'numeric',
                      month: 'short',
                      day: 'numeric',
                      hour: '2-digit',
                      minute: '2-digit',
                    })}
                  </span>
                </div>
                <p className="mt-3 text-slate-400 text-xs font-mono leading-relaxed line-clamp-3">
                {post.summary || 'No content preview available'}
                </p>
              </div>

              <div className="flex items-center gap-2 shrink-0">
                <button
                  onClick={() => handleApprove(post.id)}
                  disabled={auditMutation.isPending}
                  className="px-4 py-2 bg-vibe-emerald/20 border border-vibe-emerald/30 text-vibe-emerald text-xs font-mono rounded-lg hover:bg-vibe-emerald/30 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                >
                  Approve
                </button>
                <button
                  onClick={() => setRejectId(rejectId === post.id ? null : post.id)}
                  className="px-4 py-2 bg-red-900/30 border border-red-500/40 text-red-400 text-xs font-mono rounded-lg hover:bg-red-900/50 transition-colors"
                >
                  Reject
                </button>
              </div>
            </div>

            {rejectId === post.id && (
              <div className="mt-4 pt-4 border-t border-vibe-border">
                <label className="block text-xs font-mono text-slate-400 mb-2">
                  Reason for rejection
                </label>
                <div className="flex gap-3">
                  <input
                    type="text"
                    value={rejectReason}
                    onChange={(e) => setRejectReason(e.target.value)}
                    placeholder="Enter reason..."
                    className="flex-1 px-3 py-2 bg-vibe-bg border border-vibe-border rounded-lg text-xs font-mono text-slate-200 placeholder-slate-600 focus:outline-none focus:ring-1 focus:ring-vibe-cyan/50 focus:border-vibe-cyan/50 transition-colors"
                    onKeyDown={(e) => {
                      if (e.key === 'Enter' && rejectReason.trim()) {
                        handleReject(post.id);
                      }
                    }}
                  />
                  <button
                    onClick={() => handleReject(post.id)}
                    disabled={auditMutation.isPending || !rejectReason.trim()}
                    className="px-4 py-2 bg-red-900/30 border border-red-500/40 text-red-400 text-xs font-mono rounded-lg hover:bg-red-900/50 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                  >
                    Confirm
                  </button>
                  <button
                    onClick={() => {
                      setRejectId(null);
                      setRejectReason('');
                    }}
                    className="px-4 py-2 text-slate-500 text-xs font-mono rounded-lg hover:bg-vibe-card transition-colors"
                  >
                    Cancel
                  </button>
                </div>
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}
