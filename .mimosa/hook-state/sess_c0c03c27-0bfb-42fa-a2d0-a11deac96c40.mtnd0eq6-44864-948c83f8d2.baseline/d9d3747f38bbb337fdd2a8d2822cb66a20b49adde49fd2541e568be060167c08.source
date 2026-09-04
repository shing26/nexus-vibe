 import { Link } from 'react-router-dom';
 import { Activity, FileQuestion, Sparkles, Search, MessageSquare } from 'lucide-react';
 
 const presets = {
   noPosts: {
     icon: Sparkles,
     title: 'No posts yet',
     desc: 'This space is waiting for its first contribution.',
     action: '/post/new',
     actionLabel: 'Create First Post',
   },
   noResults: {
     icon: Search,
     title: 'No results found',
     desc: 'Try different keywords or browse channels.',
     action: '/',
     actionLabel: 'Browse Channels',
   },
   noComments: {
     icon: MessageSquare,
     title: 'No comments',
     desc: 'Be the first to share your thoughts.',
     action: null,
     actionLabel: null,
   },
   noActivity: {
     icon: Activity,
     title: 'No recent activity',
     desc: 'No public activity has been recorded yet.',
     action: null,
     actionLabel: null,
   },
   notFound: {
     icon: FileQuestion,
     title: 'Nothing here',
     desc: "This page doesn't exist or has been moved.",
     action: '/',
     actionLabel: 'Back Home',
   },
 };
 
 type PresetKey = keyof typeof presets;
 
interface EmptyStateProps {
  preset: PresetKey;
  className?: string;
  title?: string;
  desc?: string;
  action?: string | null;
  actionLabel?: string | null;
}

export default function EmptyState({
  preset,
  className = '',
  title,
  desc,
  action,
  actionLabel,
}: EmptyStateProps) {
  const p = presets[preset];
  const Icon = p.icon;
  const finalTitle = title ?? p.title;
  const finalDesc = desc ?? p.desc;
  const finalAction = action !== undefined ? action : p.action;
  const finalActionLabel = actionLabel ?? p.actionLabel;
  return (
    <div className={'flex flex-col items-center justify-center py-16 px-4 text-center ' + className}>
      <div className="w-14 h-14 rounded-xl bg-vibe-surface border border-vibe-border flex items-center justify-center mb-4">
        <Icon className="w-6 h-6 text-slate-500" />
      </div>
      <h3 className="text-sm font-mono font-semibold text-slate-400 mb-1">{finalTitle}</h3>
      <p className="text-xs font-mono text-slate-600 max-w-xs mb-5">{finalDesc}</p>
      {finalAction && (
        <Link to={finalAction} className="inline-flex items-center gap-1.5 px-4 py-2 rounded-lg bg-vibe-cyan/20 border border-vibe-cyan/30 text-vibe-cyan text-xs font-mono hover:bg-vibe-cyan/30 active:scale-[0.97] transition-all">
          <Sparkles className="w-3.5 h-3.5" />
          {finalActionLabel}
        </Link>
      )}
     </div>
   );
 }
