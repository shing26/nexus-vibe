import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { FileText, Pencil, Trash2 } from 'lucide-react';
import { useToastStore } from '../stores/toastStore';

interface Draft {
  id: number;
  title: string;
  categoryId?: number | null;
  content: string;
  tags: string;
  postType: string;
  updatedAt: string;
}

const DRAFT_KEY = 'nexus_vibe_drafts';

export default function DraftsPage() {
  const navigate = useNavigate();
  const addToast = useToastStore((s) => s.addToast);
  const [drafts, setDrafts] = useState<Draft[]>([]);

  const loadDrafts = () => {
    try {
      const stored = JSON.parse(localStorage.getItem(DRAFT_KEY) || '[]');
      setDrafts(
        (stored as Draft[]).sort(
          (a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime()
        )
      );
    } catch {
      setDrafts([]);
    }
  };

  useEffect(loadDrafts, []);

  const handleDelete = (id: number) => {
    if (!window.confirm('Delete this draft?')) return;
    const next = drafts.filter((d) => d.id !== id);
    localStorage.setItem(DRAFT_KEY, JSON.stringify(next));
    setDrafts(next);
    addToast('Draft deleted', 'success');
  };

  return (
    <div className="max-w-4xl mx-auto px-4 py-8">
      <div className="flex items-center gap-3 mb-6">
        <div className="w-9 h-9 rounded-lg bg-vibe-cyan/20 border border-vibe-cyan/30 flex items-center justify-center">
          <FileText className="w-4 h-4 text-vibe-cyan" />
        </div>
        <div>
          <h1 className="text-base font-semibold font-mono text-slate-100">My Drafts</h1>
          <p className="text-[11px] font-mono text-slate-500">Local drafts saved in this browser</p>
        </div>
      </div>

      {drafts.length === 0 ? (
        <div className="bg-vibe-surface border border-vibe-border rounded-xl p-10 text-center">
          <p className="text-xs font-mono text-slate-500">// No drafts yet. Save one from the prompt studio.</p>
          <button
            onClick={() => navigate('/post/new')}
            className="mt-4 inline-flex items-center gap-1.5 px-4 py-2 rounded-lg bg-vibe-cyan/20 border border-vibe-cyan/30 text-vibe-cyan text-xs font-mono hover:bg-vibe-cyan/30 transition-colors"
          >
            <Pencil className="w-3.5 h-3.5" />
            New Vibe Post
          </button>
        </div>
      ) : (
        <div className="space-y-3">
          {drafts.map((draft) => (
            <div
              key={draft.id}
              className="bg-vibe-surface border border-vibe-border rounded-xl p-4 hover:border-vibe-cyan/40 transition-colors"
            >
              <div className="flex items-center justify-between gap-4">
                <div className="min-w-0 flex-1">
                  <h3 className="text-sm font-mono font-medium text-slate-200 truncate">
                    {draft.title || 'Untitled draft'}
                  </h3>
                  <p className="mt-1 text-[11px] font-mono text-slate-500 truncate">
                    {draft.content ? draft.content.slice(0, 120) : '// empty content'}
                  </p>
                  <div className="mt-2 flex items-center gap-3 text-[10px] font-mono text-slate-600">
                    <span className="text-vibe-cyan">{draft.postType === 'prompt' ? 'prompt' : 'post'}</span>
                    {draft.tags && <span>{draft.tags}</span>}
                    <span>{new Date(draft.updatedAt).toLocaleString()}</span>
                  </div>
                </div>
                <div className="flex items-center gap-2 shrink-0">
                  <button
                    onClick={() => navigate('/post/new?draft=' + draft.id)}
                    className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-vibe-cyan/15 border border-vibe-cyan/30 text-vibe-cyan text-[11px] font-mono hover:bg-vibe-cyan/25 transition-colors"
                  >
                    <Pencil className="w-3.5 h-3.5" />
                    Edit
                  </button>
                  <button
                    onClick={() => handleDelete(draft.id)}
                    className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-red-500/10 border border-red-500/30 text-red-400 text-[11px] font-mono hover:bg-red-500/20 transition-colors"
                  >
                    <Trash2 className="w-3.5 h-3.5" />
                    Delete
                  </button>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
