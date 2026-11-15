import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
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
  const { t } = useTranslation('editor');
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
    if (!window.confirm(t('drafts.deleteConfirm'))) return;
    const next = drafts.filter((d) => d.id !== id);
    localStorage.setItem(DRAFT_KEY, JSON.stringify(next));
    setDrafts(next);
    addToast(t('drafts.deleted'), 'success');
  };

  return (
    <div className="mx-auto max-w-4xl px-4 py-8">
      <div className="mb-6 flex items-center gap-3">
        <div className="flex h-9 w-9 items-center justify-center rounded-md border border-vibe-border bg-vibe-surface text-vibe-accent">
          <FileText className="h-4 w-4" />
        </div>
        <div>
          <h1 className="text-lg font-semibold text-vibe-text">{t('drafts.title')}</h1>
          <p className="text-xs text-vibe-muted">{t('drafts.subtitle')}</p>
        </div>
      </div>

      {drafts.length === 0 ? (
        <div className="rounded-lg border border-vibe-border bg-vibe-surface p-10 text-center">
          <p className="text-sm text-vibe-muted">{t('drafts.empty')}</p>
          <button
            type="button"
            onClick={() => navigate('/post/new')}
            className="mt-4 inline-flex items-center gap-1.5 rounded-md bg-vibe-accent px-4 py-2 text-xs font-medium text-white transition-colors hover:opacity-90"
          >
            <Pencil className="h-3.5 w-3.5" />
            {t('drafts.newPost')}
          </button>
        </div>
      ) : (
        <div className="space-y-3">
          {drafts.map((draft) => (
            <div
              key={draft.id}
              className="rounded-lg border border-vibe-border bg-vibe-surface p-4 transition-colors hover:border-vibe-accent/40"
            >
              <div className="flex items-center justify-between gap-4">
                <div className="min-w-0 flex-1">
                  <h3 className="truncate text-sm font-medium text-vibe-text">
                    {draft.title || t('drafts.untitled')}
                  </h3>
                  <p className="mt-1 truncate text-xs text-vibe-muted">
                    {draft.content ? draft.content.slice(0, 120) : t('drafts.emptyContent')}
                  </p>
                  <div className="mt-2 flex items-center gap-3 text-xs tabular-nums text-vibe-faint">
                    <span className="text-vibe-accent">{draft.postType === 'prompt' ? 'prompt' : 'post'}</span>
                    {draft.tags && <span>{draft.tags}</span>}
                    <span>{new Date(draft.updatedAt).toLocaleString()}</span>
                  </div>
                </div>
                <div className="flex shrink-0 items-center gap-2">
                  <button
                    type="button"
                    onClick={() => navigate('/post/new?draft=' + draft.id)}
                    className="inline-flex items-center gap-1.5 rounded-md border border-vibe-border bg-vibe-card px-3 py-1.5 text-xs text-vibe-text transition-colors hover:border-vibe-accent/40 hover:text-vibe-accent"
                  >
                    <Pencil className="h-3.5 w-3.5" />
                    {t('actions.edit')}
                  </button>
                  <button
                    type="button"
                    onClick={() => handleDelete(draft.id)}
                    className="inline-flex items-center gap-1.5 rounded-md border border-vibe-danger/40 bg-vibe-danger/10 px-3 py-1.5 text-xs text-vibe-danger transition-colors hover:bg-vibe-danger/20"
                  >
                    <Trash2 className="h-3.5 w-3.5" />
                    {t('actions.delete')}
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
