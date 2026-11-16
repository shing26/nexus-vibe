import { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { Home, Mail, PenSquare, Search, Settings, ScrollText, User, LogOut } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useAuthStore } from '../stores/authStore';

/**
 * The bottom bar for phones and tablets, below `lg`.
 *
 * <p>This is not a cosmetic port of the header. The top bar hides Messages and
 * Settings behind the user menu, and a thumb on a phone reaches destinations
 * faster from a bottom bar than from a dropdown. Five tabs, and the fifth is a
 * menu rather than a link, because a bottom bar holds five destinations and
 * there are seven worth reaching. Settings and logout are therefore two taps
 * from anywhere - the tab, then the row - which is stated rather than smoothed
 * over: the alternative was dropping one of them, and the acceptance test for
 * this component enumerates exactly which destinations exist and where they
 * point.</p>
 */

interface Tab {
  to: string;
  labelKey: string;
  icon: typeof Home;
  /** Matched with startsWith so a detail page keeps its section tab lit. */
  activeWhen: (path: string) => boolean;
}

const baseActive = (prefix: string) => (path: string) =>
  prefix === '/' ? path === '/' : path.startsWith(prefix);

export default function MobileTabBar() {
  const { t } = useTranslation(['common', 'post']);
  const { pathname } = useLocation();
  const navigate = useNavigate();
  const { user, logout } = useAuthStore();
  const [menuOpen, setMenuOpen] = useState(false);

  const tabs: Tab[] = [
    { to: '/', labelKey: 'common:nav.home', icon: Home, activeWhen: baseActive('/') },
    { to: '/search', labelKey: 'common:search.go', icon: Search, activeWhen: baseActive('/search') },
    { to: '/post/new', labelKey: 'post:tabBar.post', icon: PenSquare, activeWhen: baseActive('/post/new') },
    { to: '/user/messages', labelKey: 'common:nav.messages', icon: Mail, activeWhen: baseActive('/user/messages') },
  ];

  const profilePath = user?.id ? '/user/' + user.id : '/login';
  const onProfile = pathname === profilePath || pathname.startsWith('/user/settings')
    || pathname.startsWith('/drafts');

  const handleLogout = () => {
    setMenuOpen(false);
    logout();
    navigate('/', { replace: true });
  };

  const linkClass = (active: boolean) =>
    'press-feedback flex flex-1 flex-col items-center justify-center gap-0.5 py-2 min-h-[48px] text-[11px] transition-colors ' +
    (active ? 'text-vibe-accent font-medium' : 'text-vibe-muted hover:text-vibe-text');

  return (
    <div className="lg:hidden">
      {menuOpen && (
        <>
          <div
            className="fixed inset-0 z-40 bg-black/50"
            onClick={() => setMenuOpen(false)}
            aria-hidden="true"
          />
          <nav
            role="menu"
            aria-label={t('common:user.menu')}
            className="fixed bottom-[calc(3.5rem+env(safe-area-inset-bottom))] left-0 right-0 z-50 mx-3 mb-2 overflow-hidden rounded-lg border border-vibe-border bg-vibe-card shadow-lg"
          >
            <Link role="menuitem" to={profilePath} onClick={() => setMenuOpen(false)} className="flex items-center gap-3 px-4 py-3 text-sm text-vibe-text hover:bg-vibe-surface">
              <User className="h-4 w-4 text-vibe-muted" /> {t('common:user.profile')}
            </Link>
            <Link role="menuitem" to="/drafts" onClick={() => setMenuOpen(false)} className="flex items-center gap-3 px-4 py-3 text-sm text-vibe-text hover:bg-vibe-surface">
              <ScrollText className="h-4 w-4 text-vibe-muted" /> {t('common:nav.drafts')}
            </Link>
            <Link role="menuitem" to="/user/settings" onClick={() => setMenuOpen(false)} className="flex items-center gap-3 px-4 py-3 text-sm text-vibe-text hover:bg-vibe-surface">
              <Settings className="h-4 w-4 text-vibe-muted" /> {t('common:nav.settings')}
            </Link>
            <button
              type="button"
              role="menuitem"
              onClick={handleLogout}
              className="flex w-full items-center gap-3 border-t border-vibe-border px-4 py-3 text-left text-sm text-vibe-danger hover:bg-vibe-surface"
            >
              <LogOut className="h-4 w-4" /> {t('common:user.logout')}
            </button>
          </nav>
        </>
      )}
      <nav
        aria-label={t('common:nav.primaryAria')}
        className="fixed bottom-0 left-0 right-0 z-50 flex items-stretch border-t border-vibe-border bg-vibe-surface pb-[env(safe-area-inset-bottom)]"
      >
        {tabs.map((tab) => {
          const Icon = tab.icon;
          const active = tab.activeWhen(pathname);
          return (
            <Link
              key={tab.to}
              to={tab.to}
              aria-label={t(tab.labelKey)}
              aria-current={active ? 'page' : undefined}
              className={linkClass(active)}
            >
              <Icon className="h-[18px] w-[18px]" />
              <span>{t(tab.labelKey)}</span>
            </Link>
          );
        })}
        <button
          type="button"
          aria-label={t('post:tabBar.me')}
          aria-haspopup="menu"
          aria-expanded={menuOpen}
          onClick={() => setMenuOpen((v) => !v)}
          className={linkClass(onProfile) + ' bg-transparent border-0'}
        >
          <User className="h-[18px] w-[18px]" />
          <span>{t('post:tabBar.me')}</span>
        </button>
      </nav>
    </div>
  );
}
