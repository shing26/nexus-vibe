import { Link, useLocation } from 'react-router-dom';
import { Activity, FileText, LayoutDashboard, Mail, Settings, ShieldCheck, Tag, User } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useAuthStore } from '../stores/authStore';
import { CHANNELS } from '../lib/channels';

interface SidebarProps {
  /** Drawer rows close the drawer on navigation. */
  onNavigate?: () => void;
}

/**
 * The single navigation surface for channels: the 社区 group (seven channels plus
 * the tag directory) and, for a signed-in reader, the 我的 group. Signed-out
 * visitors see only 社区. The active channel gets the accent color and a left rail.
 */
export default function Sidebar({ onNavigate }: SidebarProps) {
  const { t } = useTranslation(['common', 'channel']);
  const location = useLocation();
  const user = useAuthStore((s) => s.user);
  const isAdmin = user?.role === 'ADMIN';

  const itemClass = (active: boolean) =>
    'relative flex items-center gap-2.5 rounded-md px-2 py-1.5 text-sm transition-colors ' +
    (active
      ? 'font-medium text-vibe-accent bg-vibe-accent/10'
      : 'text-vibe-text hover:bg-vibe-card hover:text-vibe-accent');

  const railClass = (active: boolean) =>
    'before:absolute before:left-0 before:top-1.5 before:bottom-1.5 before:w-0.5 before:rounded-full ' +
    (active ? 'before:bg-vibe-accent' : 'before:bg-transparent');

  const handleClick = () => onNavigate?.();

  return (
    <nav aria-label={t('common:nav.sidebarAria')} className="flex flex-col gap-1 py-2">
      <p className="px-2 pb-1 pt-2 text-[11px] font-semibold uppercase tracking-wide text-vibe-faint">
        {t('common:nav.community')}
      </p>
      {CHANNELS.map((ch) => {
        const Icon = ch.icon;
        const href = '/channel/' + ch.slug;
        const active = location.pathname === href;
        return (
          <Link key={ch.slug} to={href} onClick={handleClick} title={t(ch.descKey)} className={itemClass(active) + ' ' + railClass(active)}>
            <Icon className="h-4 w-4 shrink-0" />
            <span className="min-w-0 flex-1 truncate">{t(ch.labelKey)}</span>
          </Link>
        );
      })}
      <Link to="/tags" onClick={handleClick} className={itemClass(location.pathname === '/tags')}>
        <Tag className="h-4 w-4 shrink-0" />
        <span className="min-w-0 flex-1 truncate">{t('common:nav.tags')}</span>
      </Link>

      {user && (
        <>
          <p className="px-2 pb-1 pt-4 text-[11px] font-semibold uppercase tracking-wide text-vibe-faint">
            {t('common:nav.mine')}
          </p>
          <Link to={'/user/' + user.id} onClick={handleClick} className={itemClass(location.pathname === '/user/' + user.id)}>
            <User className="h-4 w-4 shrink-0" />
            <span className="min-w-0 flex-1 truncate">{t('common:nav.myPosts')}</span>
          </Link>
          <Link to="/drafts" onClick={handleClick} className={itemClass(location.pathname === '/drafts')}>
            <FileText className="h-4 w-4 shrink-0" />
            <span className="min-w-0 flex-1 truncate">{t('common:nav.drafts')}</span>
          </Link>
          <Link to="/user/messages" onClick={handleClick} className={itemClass(location.pathname.startsWith('/user/messages'))}>
            <Mail className="h-4 w-4 shrink-0" />
            <span className="min-w-0 flex-1 truncate">{t('common:nav.messages')}</span>
          </Link>
          <Link to="/user/settings" onClick={handleClick} className={itemClass(location.pathname.startsWith('/user/settings'))}>
            <Settings className="h-4 w-4 shrink-0" />
            <span className="min-w-0 flex-1 truncate">{t('common:nav.settings')}</span>
          </Link>
        </>
      )}

      {isAdmin && (
        <>
          <p className="px-2 pb-1 pt-4 text-[11px] font-semibold uppercase tracking-wide text-vibe-faint">
            {t('common:nav.admin')}
          </p>
          <Link to="/admin/audit" onClick={handleClick} className={itemClass(location.pathname === '/admin/audit')}>
            <ShieldCheck className="h-4 w-4 shrink-0" />
            <span className="min-w-0 flex-1 truncate">{t('common:nav.auditQueue')}</span>
          </Link>
          <Link to="/admin/dashboard" onClick={handleClick} className={itemClass(location.pathname === '/admin/dashboard')}>
            <LayoutDashboard className="h-4 w-4 shrink-0" />
            <span className="min-w-0 flex-1 truncate">{t('common:nav.dashboard')}</span>
          </Link>
          <Link to="/agent-logs" onClick={handleClick} className={itemClass(location.pathname.startsWith('/agent-logs'))}>
            <Activity className="h-4 w-4 shrink-0" />
            <span className="min-w-0 flex-1 truncate">{t('common:nav.agentLogs')}</span>
          </Link>
        </>
      )}
    </nav>
  );
}
