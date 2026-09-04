import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
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
}

interface CommandPaletteProps {
  open: boolean;
  onClose: () => void;
}

export default function CommandPalette({ open, onClose }: CommandPaletteProps) {
  const navigate = useNavigate();
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const [query, setQuery] = useState('');
  const [selected, setSelected] = useState(0);
  const inputRef = useRef<HTMLInputElement>(null);

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
      setQuery('');
      setSelected(0);
      setTimeout(() => inputRef.current?.focus(), 10);
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

  const baseActions: PaletteItem[] = [
    {
      id: 'new-post',
      label: 'New Vibe Post',
      hint: isAuthenticated ? 'create' : 'login',
      icon: Plus,
      section: 'Actions',
      run: () => navigate(isAuthenticated ? '/post/new' : '/login'),
    },
    {
      id: 'drafts',
      label: 'My Drafts',
      icon: FileText,
      section: 'Actions',
      run: () => navigate('/drafts'),
    },
    {
      id: 'tags',
      label: 'Hot Tags',
      icon: Tag,
      section: 'Actions',
      run: () => navigate('/tags'),
    },
    {
      id: 'messages',
      label: 'Messages',
      icon: Mail,
      section: 'Actions',
      run: () => navigate('/user/messages'),
    },
    {
      id: 'agent-logs',
      label: 'Agent Logs',
      hint: 'console',
      icon: Activity,
      section: 'Actions',
      run: () => navigate('/agent-logs'),
    },
    {
      id: 'settings',
      label: 'Settings',
      icon: Settings,
      section: 'Actions',
      run: () => navigate('/user/settings'),
    },
  ];

  const channelItems: PaletteItem[] = channels.map((ch) => ({
    id: 'channel-' + ch.slug,
    label: ch.name,
    hint: '#' + ch.slug,
    icon: Terminal,
    section: 'Channels',
    run: () => navigate('/channel/' + ch.slug),
  }));

  const postItems: PaletteItem[] = postHits.map((p) => ({
    id: 'post-' + p.id,
    label: p.title,
    hint: p.categoryName || 'post',
    icon: FileText,
    section: 'Posts',
    run: () => navigate('/post/' + p.id),
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

  const runItem = (item: PaletteItem | undefined) => {
    if (!item) return;
    item.run();
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
      runItem(items[selected]);
    } else if (e.key === 'Escape') {
      close();
    }
  };

  if (!open) return null;

  let lastSection = '';

  return (
    <div
      className="fixed inset-0 z-[100] flex items-start justify-center pt-[14vh] px-4"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) close();
      }}
    >
      <div className="absolute inset-0 bg-black/70 backdrop-blur-sm" />
      <div className="relative w-full max-w-xl bg-vibe-surface border border-vibe-border rounded-xl shadow-2xl shadow-black/50 overflow-hidden">
        <div className="flex items-center gap-3 px-4 border-b border-vibe-border bg-vibe-card/60">
          <Search className="w-4 h-4 text-vibe-cyan shrink-0" />
          <input
            ref={inputRef}
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="Search posts, jump to channels, run commands..."
            className="flex-1 h-12 bg-transparent text-sm font-mono text-slate-200 placeholder-slate-600 focus:outline-none"
          />
          <kbd className="px-1.5 py-0.5 rounded bg-vibe-bg border border-vibe-border text-[10px] font-mono text-slate-500">esc</kbd>
        </div>

        <div className="max-h-[50vh] overflow-y-auto py-2">
          {searchEnabled && postsFetching && query.trim().length >= 2 && (
            <div className="px-4 py-3 text-xs font-mono text-slate-500">
              <span className="inline-block w-3 h-3 border border-vibe-cyan border-t-transparent rounded-full animate-spin align-[-2px] mr-2" />
              Searching posts...
            </div>
          )}

          {items.length === 0 && !postsFetching && (
            <div className="px-4 py-8 text-center">
              <p className="text-sm font-mono text-slate-500">No results found</p>
              <p className="text-[11px] font-mono text-slate-600 mt-1">Try a different keyword or command</p>
            </div>
          )}

          {items.map((item, i) => {
            const Icon = item.icon;
            const showSection = item.section !== lastSection;
            lastSection = item.section;
            return (
              <div key={item.id}>
                {showSection && (
                  <div className="px-4 pt-3 pb-1 text-[10px] font-mono uppercase tracking-widest text-slate-600">
                    {item.section}
                  </div>
                )}
                <button
                  onMouseEnter={() => setSelected(i)}
                  onClick={() => runItem(item)}
                  className={
                    'w-full flex items-center gap-3 px-4 py-2.5 text-left transition-colors ' +
                    (i === selected ? 'bg-vibe-cyan/10 text-slate-100' : 'text-slate-400')
                  }
                >
                  <span className={'w-7 h-7 rounded-lg border flex items-center justify-center shrink-0 ' + (i === selected ? 'bg-vibe-cyan/20 border-vibe-cyan/40 text-vibe-cyan' : 'bg-vibe-card border-vibe-border text-slate-500')}>
                    <Icon className="w-3.5 h-3.5" />
                  </span>
                  <span className="flex-1 min-w-0">
                    <span className="block text-xs font-mono truncate">{item.label}</span>
                  </span>
                  <span className="flex items-center gap-1.5 shrink-0">
                    {item.hint && <span className="text-[10px] font-mono text-slate-600">{item.hint}</span>}
                    {i === selected && <CornerDownLeft className="w-3 h-3 text-vibe-cyan/70" />}
                  </span>
                </button>
              </div>
            );
          })}
        </div>

        <div className="flex items-center gap-4 px-4 py-2.5 border-t border-vibe-border bg-vibe-card/40 text-[10px] font-mono text-slate-600">
          <span><kbd className="px-1 py-0.5 rounded bg-vibe-bg border border-vibe-border">↑↓</kbd> navigate</span>
          <span><kbd className="px-1 py-0.5 rounded bg-vibe-bg border border-vibe-border">↵</kbd> open</span>
          <span className="ml-auto text-slate-700">NEXUS.VIBE ⌘PALETTE</span>
        </div>
      </div>
    </div>
  );
}
