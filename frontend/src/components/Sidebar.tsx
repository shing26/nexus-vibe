import { Link, useLocation } from 'react-router-dom';
import { Activity, FileText, LayoutDashboard, Mail, Settings, ShieldCheck, Tag, User } from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useAuthStore } from '../stores/authStore';
import { CHANNELS } from '../lib/channels';
import SystemPulse from './SystemPulse';

interface SidebarProps {
  /** Drawer rows close the drawer on navigation. */
  onNavigate?: () => void;
}

/**
 * The single navigation surface for channels: the community group (seven channels plus
 * the tag directory) and, for a signed-in reader, the personal group. Signed-out
 * visitors see only the community group. The active channel gets the accent color and a left rail.
 *
 * <p>Three width regimes by CSS, not by JS: the drawer below `lg` keeps full labels;
 * `lg`–`xl` (1024–1279px) collapses to a 48px icon rail with a hover/focus tooltip;
 * `xl` and up restores the 240px sidebar. Labels stay in the accessibility tree at
 * every width (`sr-only` rather than `hidden`), so an icon-only rail is still
 * navigable by a screen reader.</p>
 */
export default function Sidebar({ onNavigate }: SidebarProps) {
  const { t } = useTranslation(['common', 'channel']);
  const location = useLocation();
  const user = useAuthStore((s) => s.user);
  const isAdmin = user?.role === 'ADMIN';

  const itemClass = (active: boolean) =>
    'press-feedback group relative flex items-center gap-2.5 rounded-md px-2 py-1.5 text-sm transition-colors ' +
    'lg:justify-center lg:gap-0 lg:px-0 xl:justify-start xl:gap-2.5 xl:px-2 ' +
    (active
      ? 'font-medium text-vibe-accent bg-vibe-accent/10'
      : 'text-vibe-text hover:bg-vibe-card hover:text-vibe-accent');

  const railClass = (active: boolean) =>
    'before:absolute before:left-0 before:top-1.5 before:bottom-1.5 before:w-0.5 before:rounded-full ' +
    (active ? 'before:bg-vibe-accent' : 'before:bg-transparent');

  const labelClass =
    'min-w-0 flex-1 truncate lg:sr-only xl:not-sr-only xl:min-w-0 xl:flex-1 xl:truncate';

  const sectionClass =
    'px-2 pb-1 pt-2 text-[11px] font-semibold uppercase tracking-wide text-vibe-faint lg:hidden xl:block';

  const handleClick = () => onNavigate?.();

  /** A tooltip for the rail only; the full sidebar and the drawer show the label itself. */
  const railTip = (label: string) => (
    <span
      aria-hidden="true"
      className="pointer-events-none absolute left-full z-50 ml-2 hidden whitespace-nowrap rounded-md border border-vibe-border bg-vibe-card px-2 py-1 text-xs text-vibe-text opacity-0 shadow-lg transition-opacity motion-reduce:transition-none group-hover:opacity-100 group-focus-visible:opacity-100 lg:block xl:hidden"
    >
      {label}
    </span>
  );

  const row = (to: string, icon: LucideIcon, label: string, active: boolean, title?: string) => {
    const Icon = icon;
    return (
      <Link
        key={to}
        to={to}
        onClick={handleClick}
        title={title ?? label}
        aria-label={label}
        className={itemClass(active) + ' ' + railClass(active)}
      >
        <Icon className="h-4 w-4 shrink-0" />
        <span className={labelClass}>{label}</span>
        {railTip(label)}
      </Link>
    );
  };

  return (
    <nav aria-label={t('common:nav.sidebarAria')} className="flex flex-col gap-1 py-2">
      <p className={sectionClass}>{t('common:nav.community')}</p>
      {CHANNELS.map((ch) => {
        const href = '/channel/' + ch.slug;
        // The channel's description is the hover text at full width; the rail
        // tooltip shows the display name, which is what an icon-only row lacks.
        return row(href, ch.icon, t(ch.labelKey), location.pathname === href, t(ch.descKey));
      })}
      {row('/tags', Tag, t('common:nav.tags'), location.pathname === '/tags')}

      {user && (
        <>
          <p className={sectionClass + ' pt-4'}>{t('common:nav.mine')}</p>
          {row('/user/' + user.id, User, t('common:nav.myPosts'), location.pathname === '/user/' + user.id)}
          {row('/drafts', FileText, t('common:nav.drafts'), location.pathname === '/drafts')}
          {row('/user/messages', Mail, t('common:nav.messages'), location.pathname.startsWith('/user/messages'))}
          {row('/user/settings', Settings, t('common:nav.settings'), location.pathname.startsWith('/user/settings'))}
        </>
      )}

      {isAdmin && (
        <>
          <p className={sectionClass + ' pt-4'}>{t('common:nav.admin')}</p>
          {row('/admin/audit', ShieldCheck, t('common:nav.auditQueue'), location.pathname === '/admin/audit')}
          {row('/admin/dashboard', LayoutDashboard, t('common:nav.dashboard'), location.pathname === '/admin/dashboard')}
          {row('/agent-logs', Activity, t('common:nav.agentLogs'), location.pathname.startsWith('/agent-logs'))}
        </>
      )}

      <SystemPulse />
    </nav>
  );
}
