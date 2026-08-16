import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { motion, AnimatePresence } from 'motion/react';
import { History, RotateCcw, X, Check } from 'lucide-react';
import { apiClient } from '../api/client';
import { useToastStore } from '../stores/toastStore';
import type { PromptVersion } from '../types/post';

interface PromptVersionPanelProps {
  postId: string;
  open: boolean;
  onClose: () => void;
  canRestore: boolean;
  onRestored?: () => void;
}

const timeAgo = (dateStr: string) => {
  const diff = Date.now() - new Date(dateStr).getTime();
  const m = Math.floor(diff / 60000);
  if (m < 1) return 'just now';
  if (m < 60) return m + 'm ago';
  const h = Math.floor(m / 60);
  if (h < 24) return h + 'h ago';
  const d = Math.floor(h / 24);
  if (d < 30) return d + 'd ago';
  return new Date(dateStr).toLocaleDateString();
};

export default function PromptVersionPanel({ postId, open, onClose, canRestore, onRestored }: PromptVersionPanelProps) {
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
      addToast('Version v' + variables.version + ' restored', 'success');
      queryClient.invalidateQueries({ queryKey: ['versions', postId] });
      queryClient.invalidateQueries({ queryKey: ['post', postId] });
      onRestored?.();
    },
    onError: () => addToast('Restore failed', 'error'),
  });

  const latestVersion = versions?.[0]?.version ?? 0;

  return (
    <AnimatePresence>
      {open && (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          className="fixed inset-0 z-[90] bg-black/70 backdrop-blur-sm flex items-center justify-center p-4"
          onClick={onClose}
        >
          <motion.div
            initial={{ opacity: 0, y: 16, scale: 0.98 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: 8, scale: 0.98 }}
            transition={{ type: 'spring', stiffness: 320, damping: 26 }}
            onClick={(e) => e.stopPropagation()}
            className="w-full max-w-2xl bg-vibe-surface border border-vibe-border rounded-xl overflow-hidden shadow-2xl"
          >
            <div className="flex items-center h-10 bg-vibe-card border-b border-vibe-border px-3">
              <span className="w-2.5 h-2.5 rounded-full bg-red-500/80" />
              <span className="w-2.5 h-2.5 rounded-full bg-yellow-500/80 ml-1.5" />
              <span className="w-2.5 h-2.5 rounded-full bg-green-500/80 ml-1.5" />
              <span className="flex-1 text-center text-[11px] font-mono text-slate-500 truncate">
                git log — prompt template versions
              </span>
              <button onClick={onClose} className="p-1 rounded-md text-slate-500 hover:text-white hover:bg-vibe-surface transition-colors">
                <X className="w-3.5 h-3.5" />
              </button>
            </div>

            <div className="max-h-[70vh] overflow-y-auto p-4 space-y-3">
              {isLoading && (
                <div className="space-y-3">
                  {[0, 1, 2].map((i) => (
                    <div key={i} className="h-16 bg-vibe-card/50 rounded-lg animate-pulse" />
                  ))}
                </div>
              )}

              {!isLoading && (!versions || versions.length === 0) && (
                <div className="py-12 text-center">
                  <History className="w-6 h-6 text-slate-600 mx-auto mb-2" />
                  <p className="text-xs font-mono text-slate-500">No versions recorded yet.</p>
                </div>
              )}

              {versions?.map((v) => {
                const isLatest = v.version === latestVersion;
                return (
                  <div key={v.id} className="bg-vibe-bg border border-vibe-border rounded-lg p-3">
                    <div className="flex items-center gap-2">
                      <span className="px-1.5 py-0.5 rounded bg-vibe-cyan/10 border border-vibe-cyan/30 text-vibe-cyan text-[10px] font-mono">
                        v{v.version}
                      </span>
                      <span className="text-[10px] font-mono text-slate-600">main</span>
                      {isLatest && (
                        <span className="px-1.5 py-0.5 rounded bg-vibe-emerald/10 border border-vibe-emerald/30 text-vibe-emerald text-[10px] font-mono">
                          HEAD
                        </span>
                      )}
                      <span className="ml-auto text-[10px] font-mono text-slate-600">
                        {v.authorName} · {timeAgo(v.createTime)}
                      </span>
                    </div>
                    <p className="mt-2 text-xs font-mono text-slate-300">{v.changeNote || 'No change note'}</p>
                    <p className="mt-1 text-[11px] font-mono text-slate-500 line-clamp-2">{v.title}</p>
                    {canRestore && !isLatest && (
                      <div className="mt-2.5 flex items-center gap-2">
                        <input
                          value={notes[v.version] ?? ''}
                          onChange={(e) => setNotes((prev) => ({ ...prev, [v.version]: e.target.value }))}
                          placeholder="Restore note (optional)"
                          className="flex-1 px-2.5 py-1.5 bg-vibe-card border border-vibe-border rounded-md text-[11px] font-mono text-slate-300 placeholder-slate-600 focus:outline-none focus:ring-1 focus:ring-vibe-purple/50"
                        />
                        <button
                          onClick={() => restoreMutation.mutate({ version: v.version, note: notes[v.version] ?? '' })}
                          disabled={restoreMutation.isPending}
                          className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md bg-vibe-purple/20 border border-vibe-purple/30 text-vibe-purple text-[11px] font-mono hover:bg-vibe-purple/30 transition-colors disabled:opacity-50"
                        >
                          {restoreMutation.isPending ? <RotateCcw className="w-3 h-3 animate-spin" /> : <Check className="w-3 h-3" />}
                          Restore
                        </button>
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
