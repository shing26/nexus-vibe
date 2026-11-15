import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { History, RotateCcw, X, Check } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { apiClient } from '../api/client';
import { useToastStore } from '../stores/toastStore';
import { timeAgo } from '../lib/time';
import Skeleton from './Skeleton';
import type { PromptVersion } from '../types/post';

interface PromptVersionPanelProps {
  postId: string;
  open: boolean;
  onClose: () => void;
  canRestore: boolean;
  onRestored?: () => void;
}

/**
 * The version history of a prompt post, as a modal.
 *
 * <p>Restoring writes a new version on top (the API owns that rule) and the
 * panel only relays it. The row marked `HEAD` is the one a restore would
 * duplicate, so its control is hidden rather than disabled: a disabled button
 * invites the question "why not", a missing one does not.</p>
 */
export default function PromptVersionPanel({ postId, open, onClose, canRestore, onRestored }: PromptVersionPanelProps) {
  const { t } = useTranslation(['post', 'common']);
  const queryClient = useQueryClient();
  const addToast = useToastStore((s) => s.addToast);
  const [notes, setNotes] = useState<Record<number, string>>({});

  const { data: versions, isLoading } = useQuery({
    queryKey: ['versions', postId],
    queryFn: async () => {
      const res = await apiClient.get('/posts/' + postId + '/versions');
      return res.data.data as PromptVersion[];
    },
    enabled: open && !!postId,
    staleTime: 1000 * 30,
  });

  const restoreMutation = useMutation({
    mutationFn: async ({ version, note }: { version: number; note: string }) => {
      await apiClient.post('/posts/' + postId + '/versions/' + version + '/restore', { changeNote: note });
    },
    onSuccess: (_data, variables) => {
      setNotes((prev) => {
        const next = { ...prev };
        delete next[variables.version];
        return next;
      });
      addToast(t('versions.restored', { version: variables.version }), 'success');
      queryClient.invalidateQueries({ queryKey: ['versions', postId] });
      queryClient.invalidateQueries({ queryKey: ['post', postId] });
      onRestored?.();
    },
    onError: () => addToast(t('versions.restoreFailed'), 'error'),
  });

  const latestVersion = versions?.[0]?.version ?? 0;

  if (!open) return null;

  return (
    <div
      className="fixed inset-0 z-[90] flex items-center justify-center bg-black/50 p-4"
      onClick={onClose}
    >
      <div
        onClick={(e) => e.stopPropagation()}
        className="w-full max-w-2xl overflow-hidden rounded-lg border border-vibe-border bg-vibe-card shadow-xl"
      >
        <div className="flex items-center gap-2 border-b border-vibe-border px-4 py-3">
          <History className="h-4 w-4 shrink-0 text-vibe-muted" aria-hidden="true" />
          <span className="min-w-0 flex-1 truncate text-sm font-medium text-vibe-text">
            {t('versions.title')}
          </span>
          <button
            onClick={onClose}
            aria-label={t('common:close')}
            className="rounded-md p-1 text-vibe-muted transition-colors hover:bg-vibe-surface hover:text-vibe-text focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-vibe-accent"
          >
            <X className="h-3.5 w-3.5" />
          </button>
        </div>

        <div className="max-h-[70vh] space-y-3 overflow-y-auto p-4">
          {isLoading && (
            <div className="space-y-3">
              {[0, 1, 2].map((i) => (
                <Skeleton key={i} className="h-16 w-full rounded-lg" />
              ))}
            </div>
          )}

          {!isLoading && (!versions || versions.length === 0) && (
            <div className="py-12 text-center">
              <History className="mx-auto mb-2 h-6 w-6 text-vibe-faint" aria-hidden="true" />
              <p className="text-xs text-vibe-muted">{t('versions.empty')}</p>
            </div>
          )}

          {versions?.map((v) => {
            const isLatest = v.version === latestVersion;
            return (
              <div key={v.id} className="rounded-lg border border-vibe-border bg-vibe-surface p-3">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="rounded-full border border-vibe-accent/40 bg-vibe-accent/10 px-2 py-0.5 text-xs font-medium tabular-nums text-vibe-accent">
                    v{v.version}
                  </span>
                  <span className="text-xs text-vibe-faint">{t('versions.branch')}</span>
                  {isLatest && (
                    <span className="rounded-full border border-vibe-success/40 bg-vibe-success/10 px-2 py-0.5 text-xs font-medium text-vibe-success">
                      {t('versions.head')}
                    </span>
                  )}
                  <span className="ml-auto text-xs tabular-nums text-vibe-faint">
                    {v.authorName} · {timeAgo(v.createTime)}
                  </span>
                </div>
                <p className="mt-2 text-sm text-vibe-text">{v.changeNote || t('versions.noChangeNote')}</p>
                <p className="mt-1 line-clamp-2 text-xs text-vibe-muted">{v.title}</p>
                {canRestore && !isLatest && (
                  <div className="mt-2.5 flex flex-wrap items-center gap-2">
                    <input
                      value={notes[v.version] ?? ''}
                      onChange={(e) => setNotes((prev) => ({ ...prev, [v.version]: e.target.value }))}
                      placeholder={t('versions.restoreNotePlaceholder')}
                      className="min-w-0 flex-1 rounded-md border border-vibe-border bg-vibe-card px-2.5 py-1.5 text-xs text-vibe-text placeholder:text-vibe-faint focus:border-vibe-accent/50 focus:outline-none focus:ring-1 focus:ring-vibe-accent/50"
                    />
                    <button
                      onClick={() => restoreMutation.mutate({ version: v.version, note: notes[v.version] ?? '' })}
                      disabled={restoreMutation.isPending}
                      className="inline-flex items-center gap-1.5 rounded-md bg-vibe-accent px-3 py-1.5 text-xs font-medium text-white transition-colors hover:opacity-90 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-vibe-accent disabled:opacity-50"
                    >
                      {restoreMutation.isPending ? <RotateCcw className="h-3 w-3 animate-spin motion-reduce:animate-none" /> : <Check className="h-3 w-3" />}
                      {t('versions.restore')}
                    </button>
                  </div>
                )}
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}
