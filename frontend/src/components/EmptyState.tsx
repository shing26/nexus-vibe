import { Link } from 'react-router-dom';
import { Activity, FileQuestion, Sparkles, Search, MessageSquare } from 'lucide-react';

/**
 * The shared empty-state copy. Each `desc` names the mechanism that fills the
 * surface rather than reporting absence: a reader who lands on a blank page
 * should learn what would make it non-blank and, where possible, do it now.
 *
 * <p>The keys are exposed on the component as a runtime-derived list so
 * `EmptyState.test.tsx` can iterate every preset: a preset added later without
 * copy fails the suite instead of rendering a blank line.</p>
 */
const presets = {
  noPosts: {
    icon: Sparkles,
    title: 'No posts yet',
    desc: 'Posts appear here once someone publishes one; start the first thread yourself.',
    action: '/post/new',
    actionLabel: 'Create First Post',
  },
  noResults: {
    icon: Search,
    title: 'No results found',
    desc: 'Nothing matched those filters; clear one or search a different keyword to widen the results.',
    action: '/',
    actionLabel: 'Browse Channels',
  },
  noComments: {
    icon: MessageSquare,
    title: 'No comments',
    desc: 'No one has replied yet; the first comment you write appears here and starts the thread.',
    action: null,
    actionLabel: null,
  },
  noActivity: {
    icon: Activity,
    title: 'No recent activity',
    desc: 'Activity appears here after this profile publishes posts, comments, versions or forks; it is a record, not a place to act.',
    action: null,
    actionLabel: null,
  },
  notFound: {
    icon: FileQuestion,
    title: 'Nothing here',
    desc: 'This page may have moved or never existed; follow a link from the feed to find it.',
    action: '/',
    actionLabel: 'Back Home',
  },
};

export type PresetKey = keyof typeof presets;

interface EmptyStateProps {
  preset: PresetKey;
  className?: string;
  title?: string;
  desc?: string;
  action?: string | null;
  actionLabel?: string | null;
}

function EmptyState({
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

/**
 * Exposed on the component so the copy test can iterate the map itself: a
 * preset added later without copy appears here automatically and fails the
 * suite. Kept off a named export so this file still exports only a component
 * (fast refresh), and derived rather than typed by hand so it cannot drift.
 */
Object.assign(EmptyState, { presetKeys: Object.keys(presets) as PresetKey[] });

export default EmptyState;
