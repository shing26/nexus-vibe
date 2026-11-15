import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import {
  Search,
  FileText,
  Tag,
  Mail,
  Settings,
  Activity,
  Terminal,
  CornerDownLeft,
  Plus,
} from 'lucide-react';
import { apiClient } from '../api/client';
import { useAuthStore } from '../stores/authStore';
import { useToastStore } from '../stores/toastStore';
import { channelLabel } from '../lib/channels';

interface Channel {
  id: string;
  name: string;
  description: string;
  slug: string;
  sortOrder: number;
}

interface PostHit {
  id: number;
  title: string;
  authorName?: string;
  categoryName?: string;
}

interface PaletteItem {
  id: string;
  label: string;
  hint?: string;
  icon: React.ElementType;
  section: string;
  run: () => void;
  // A second destination for the same row, reached with the modifier. Rows that
  // have nothing else to offer (the action rows) leave it out, and the modifier
  // falls through to `run` rather than doing nothing.
  secondary?: {
    label: string;
    run: () => void;
  };
}

interface CommandPaletteProps {
  open: boolean;
  onClose: () => void;
}

export default function CommandPalette({ open, onClose }: CommandPaletteProps) {
  const { t } = useTranslation('common');
  const navigate = useNavigate();
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const addToast = useToastStore((s) => s.addToast);
  const [query, setQuery] = useState('');
  const [selected, setSelected] = useState(0);
  const inputRef = useRef<HTMLInputElement>(null);
  // The trigger that opened the palette gets focus back on close; without this
  // focus fell to <body> and keyboard users lost their place in the page.
  const restoreFocusRef = useRef<HTMLElement | null>(null);

  const { data: channels = [] } = useQuery<Channel[]>({
    queryKey: ['channels'],
    queryFn: () => apiClient.get('/channels').then((res) => res.data.data),
    staleTime: 5 * 60 * 1000,
    enabled: open,
  });

  const searchEnabled = open && query.trim().length >= 2;
  const { data: postsData, isFetching: postsFetching } = useQuery({
    queryKey: ['palette-search', query.trim()],
    queryFn: async () => {
      const res = await apiClient.get('/posts', {
        params: { keyword: query.trim(), size: 5 },
      });
      return res.data.data;
    },
    enabled: searchEnabled,
    staleTime: 1000 * 30,
  });

  const postHits: PostHit[] = postsData?.list ?? postsData ?? [];

  useEffect(() => {
    if (open) {
      restoreFocusRef.current = document.activeElement as HTMLElement | null;
      setQuery('');
      setSelected(0);
      const timer = setTimeout(() => inputRef.current?.focus(), 10);
      // Cleanup runs when `open` flips false: hand focus back to the trigger.
      return () => {
        clearTimeout(timer);
        restoreFocusRef.current?.focus();
      };
    }
  }, [open]);

  useEffect(() => {
    if (open) setSelected(0);
  }, [query, postHits.length, open]);

  const close = () => {
    onClose();
    setQuery('');
    setSelected(0);
  };

  // The address is the row's second destination. It is absolute because a
  // relative path pasted into a chat window is not a link.
  const copyLink = (route: string) => {
    const url = window.location.origin + route;
    const write = navigator.clipboard?.writeText(url);
    if (!write) {
      addToast(t('toast.couldNotCopy'), 'error');
      return;
    }
    write.then(
      () => addToast(t('toast.linkCopied'), 'success'),
      () => addToast(t('toast.couldNotCopy'), 'error'),
    );
  };

  const baseActions: PaletteItem[] = [
    {
      id: 'new-post',
      label: t('palette.items.newPost'),
      hint: isAuthenticated ? t('palette.hints.create') : t('palette.hints.login'),
      icon: Plus,
      section: t('palette.sections.actions'),
      run: () => navigate(isAuthenticated ? '/post/new' : '/login'),
    },
    {
      id: 'drafts',
      label: t('palette.items.drafts'),
      icon: FileText,
      section: t('palette.sections.actions'),
      run: () => navigate('/drafts'),
    },
    {
      id: 'tags',
      label: t('palette.items.tags'),
      icon: Tag,
      section: t('palette.sections.actions'),
      run: () => navigate('/tags'),
    },
    {
      id: 'messages',
      label: t('palette.items.messages'),
      icon: Mail,
      section: t('palette.sections.actions'),
      run: () => navigate('/user/messages'),
    },
    {
      id: 'agent-logs',
      label: t('palette.items.agentLogs'),
      hint: t('palette.hints.console'),
      icon: Activity,
      section: t('palette.sections.actions'),
      run: () => navigate('/agent-logs'),
    },
    {
      id: 'settings',
      label: t('palette.items.settings'),
      icon: Settings,
      section: t('palette.sections.actions'),
      run: () => navigate('/user/settings'),
    },
  ];

  const channelItems: PaletteItem[] = channels.map((ch) => ({
    id: 'channel-' + ch.slug,
    label: channelLabel(ch.slug) || ch.name,
    hint: '#' + ch.slug,
    icon: Terminal,
    section: t('palette.sections.channels'),
    run: () => navigate('/channel/' + ch.slug),
    secondary: {
      label: t('palette.items.copyLink'),
      run: () => copyLink('/channel/' + ch.slug),
    },
  }));

  const postItems: PaletteItem[] = postHits.map((p) => ({
    id: 'post-' + p.id,
    label: p.title,
    hint: p.categoryName || t('palette.hints.post'),
    icon: FileText,
    section: t('palette.sections.posts'),
    run: () => navigate('/post/' + p.id),
    secondary: {
      label: t('palette.items.copyLink'),
      run: () => copyLink('/post/' + p.id),
    },
  }));

  const visibleActions = baseActions.filter((a) => {
    const q = query.trim().toLowerCase();
    if (!q) return true;
    return a.label.toLowerCase().includes(q) || a.hint?.toLowerCase().includes(q);
  });

  const items: PaletteItem[] = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) {
      return [...channelItems, ...visibleActions];
    }
    return [...postItems, ...channelItems.filter((c) => c.label.toLowerCase().includes(q) || c.hint!.toLowerCase().includes(q)), ...visibleActions];
  }, [query, channelItems, postItems, visibleActions]);

  // `useSecondary` is the modifier state at the keystroke. A row without a
  // secondary still runs its primary action, so the modifier is never dead.
  const runItem = (item: PaletteItem | undefined, useSecondary = false) => {
    if (!item) return;
    if (useSecondary && item.secondary) {
      item.secondary.run();
    } else {
      item.run();
    }
    close();
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setSelected((s) => (items.length === 0 ? 0 : (s + 1) % items.length));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setSelected((s) => (items.length === 0 ? 0 : (s - 1 + items.length) % items.length));
    } else if (e.key === 'Enter') {
      e.preventDefault();
      runItem(items[selected], e.ctrlKey || e.metaKey);
    } else if (e.key === 'Escape') {
      close();
    } else if (e.key === 'Tab') {
      // Focus trap: the palette is a modal dialog, so Tab stays inside. The
      // listbox keeps keyboard focus on the input (roving via
      // aria-activedescendant), and Enter already runs the selected row.
      e.preventDefault();
    }
  };

  if (!open) return null;

  const selectedItem = items[selected];
  let lastSection = '';

  return (
    <div
      className="fixed inset-0 z-[100] flex items-start justify-center px-4 pt-[14vh]"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) close();
      }}
    >
      <div className="absolute inset-0 bg-black/50" />
      <div
        role="dialog"
        aria-modal="true"
        aria-label={t('palette.ariaLabel')}
        className="relative w-full max-w-xl overflow-hidden rounded-lg border border-vibe-border bg-vibe-card shadow-lg"
      >
        <div className="flex items-center gap-3 border-b border-vibe-border bg-vibe-surface px-4">
          <Search className="h-4 w-4 shrink-0 text-vibe-accent" />
          <input
            ref={inputRef}
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            onKeyDown={handleKeyDown}
            role="combobox"
            aria-expanded="true"
            aria-controls="command-palette-list"
            aria-activedescendant={items[selected] ? `palette-item-${items[selected].id}` : undefined}
            aria-label={t('palette.inputAria')}
            placeholder={t('palette.placeholder')}
            className="h-12 flex-1 bg-transparent text-sm text-vibe-text placeholder-vibe-faint focus:outline-none"
          />
          <kbd className="rounded border border-vibe-border bg-vibe-bg px-1.5 py-0.5 text-[11px] text-vibe-muted">{t('palette.esc')}</kbd>
        </div>

        <div id="command-palette-list" role="listbox" className="max-h-[50vh] overflow-y-auto py-2">
          {searchEnabled && postsFetching && query.trim().length >= 2 && (
            <div className="px-4 py-3 text-xs text-vibe-muted">
              <span className="mr-2 inline-block h-3 w-3 animate-spin motion-reduce:animate-none rounded-full border border-vibe-accent border-t-transparent align-[-2px]" />
              {t('palette.searching')}
            </div>
          )}

          {items.length === 0 && !postsFetching && (
            <div className="px-4 py-8 text-center">
              <p className="text-sm text-vibe-muted">{t('palette.noResults')}</p>
              <p className="mt-1 text-[11px] text-vibe-faint">{t('palette.noResultsHint')}</p>
            </div>
          )}

          {items.map((item, i) => {
            const Icon = item.icon;
            const showSection = item.section !== lastSection;
            lastSection = item.section;
            return (
              <div key={item.id}>
                {showSection && (
                  <div className="px-4 pb-1 pt-3 text-[11px] font-semibold uppercase tracking-wide text-vibe-faint">
                    {item.section}
                  </div>
                )}
                <button
                  id={`palette-item-${item.id}`}
                  role="option"
                  aria-selected={i === selected}
                  onMouseEnter={() => setSelected(i)}
                  onClick={() => runItem(item)}
                  className={
                    'flex w-full items-center gap-3 px-4 py-2.5 text-left transition-colors ' +
                    (i === selected ? 'bg-vibe-surface text-vibe-text' : 'text-vibe-muted')
                  }
                >
                  <span className={'flex h-7 w-7 shrink-0 items-center justify-center rounded-md border ' + (i === selected ? 'border-vibe-accent/40 bg-vibe-accent/10 text-vibe-accent' : 'border-vibe-border bg-vibe-surface text-vibe-muted')}>
                    <Icon className="h-3.5 w-3.5" />
                  </span>
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-xs">{item.label}</span>
                  </span>
                  <span className="flex shrink-0 items-center gap-1.5">
                    {item.hint && <span className="text-[11px] text-vibe-faint">{item.hint}</span>}
                    {i === selected && <CornerDownLeft className="h-3 w-3 text-vibe-accent/70" />}
                  </span>
                </button>
              </div>
            );
          })}
        </div>

        <div className="flex items-center gap-4 border-t border-vibe-border bg-vibe-surface px-4 py-2.5 text-[11px] text-vibe-faint">
          <span><kbd className="rounded border border-vibe-border bg-vibe-bg px-1 py-0.5">↑↓</kbd> {t('palette.footer.navigate')}</span>
          <span><kbd className="rounded border border-vibe-border bg-vibe-bg px-1 py-0.5">↵</kbd> {t('palette.footer.open')}</span>
          {/* Only rows with a second destination advertise the modifier; the
              label names the same Ctrl/Meta the handler listens for. */}
          {selectedItem?.secondary && (
            <span>
              <kbd className="rounded border border-vibe-border bg-vibe-bg px-1 py-0.5">{t('palette.footer.modifier')}</kbd> +{' '}
              <kbd className="rounded border border-vibe-border bg-vibe-bg px-1 py-0.5">↵</kbd>{' '}
              {selectedItem.secondary.label.toLowerCase()}
            </span>
          )}
          <span className="ml-auto">{t('palette.brandTag')}</span>
        </div>
      </div>
    </div>
  );
}
