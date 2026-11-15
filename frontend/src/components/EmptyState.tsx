import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Activity, FileQuestion, Sparkles, Search, MessageSquare } from 'lucide-react';

/**
 * The shared empty-state copy. Each `desc` names the mechanism that fills the
 * surface rather than reporting absence: a reader who lands on a blank page
 * should learn what would make it non-blank and, where possible, do it now.
 *
 * <p>The copy lives in the dictionary (`common:empty.<key>.*`) and the action
 * rows in `common:empty.actions.*`; the keys are exposed on the component as a
 * runtime-derived list so `EmptyState.test.tsx` can iterate every preset: a
 * preset added later without copy fails the suite instead of rendering a blank
 * line.</p>
 */
const presets = {
  noPosts: {
    icon: Sparkles,
    action: '/post/new',
    actionKey: 'actions.createFirstPost',
  },
  noResults: {
    icon: Search,
    action: '/',
    actionKey: 'actions.browseChannels',
  },
  noComments: {
    icon: MessageSquare,
    action: null,
    actionKey: null,
  },
  noActivity: {
    icon: Activity,
    action: null,
    actionKey: null,
  },
  notFound: {
    icon: FileQuestion,
    action: '/',
    actionKey: 'actions.backHome',
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
  const { t } = useTranslation('common');
  const p = presets[preset];
  const Icon = p.icon;
  const finalTitle = title ?? t('empty.' + preset + '.title');
  const finalDesc = desc ?? t('empty.' + preset + '.desc');
  const finalAction = action !== undefined ? action : p.action;
  const finalActionLabel = actionLabel ?? (p.actionKey ? t('empty.' + p.actionKey) : null);
  return (
    <div className={'flex flex-col items-center justify-center px-4 py-16 text-center ' + className}>
      <div className="mb-4 flex h-14 w-14 items-center justify-center rounded-lg border border-vibe-border bg-vibe-surface">
        <Icon className="h-6 w-6 text-vibe-faint" />
      </div>
      <h3 className="mb-1 text-sm font-semibold text-vibe-muted">{finalTitle}</h3>
      <p className="mb-5 max-w-xs text-xs text-vibe-faint">{finalDesc}</p>
      {finalAction && (
        <Link to={finalAction} className="inline-flex items-center gap-1.5 rounded-md bg-vibe-accent px-4 py-2 text-xs font-medium text-white transition-colors hover:opacity-90">
          <Sparkles className="h-3.5 w-3.5" />
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
