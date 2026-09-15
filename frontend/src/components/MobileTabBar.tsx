import { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { Home, Mail, PenSquare, Search, Settings, ScrollText, User, LogOut } from 'lucide-react';
import { useAuthStore } from '../stores/authStore';

/**
 * The bottom bar for phones and tablets, below `lg`.
 *
 * <p>This is not a cosmetic port of the header. `Navbar` hides Messages and Settings
 * below `sm`, hides the username below `md`, and has no hamburger at any width, so a
 * logged-in visitor on a phone could reach the logo, search, theme switch, New Post
 * and Logout, and nothing else: messages, settings, drafts and their own profile had
 * no route onto them that a thumb could press. `Cmd+K` is not a mobile answer.</p>
 *
 * <p>Five tabs, and the fifth is a menu rather than a link, because a bottom bar holds
 * five destinations and there are seven worth reaching. Settings and logout are
 * therefore two taps from anywhere - the tab, then the row - which is stated rather
 * than smoothed over: the alternative was dropping one of them, and the acceptance
 * test for this component enumerates exactly which destinations exist and where they
 * point.</p>
 */

interface Tab {
  to: string;
  label: string;
  icon: typeof Home;
  /** Matched with startsWith so a detail page keeps its section tab lit. */
  activeWhen: (path: string) => boolean;
}

const baseActive = (prefix: string) => (path: string) =>
  prefix === '/' ? path === '/' : path.startsWith(prefix);

export default function MobileTabBar() {
  const { pathname } = useLocation();
  const navigate = useNavigate();
  const { user, logout } = useAuthStore();
  const [menuOpen, setMenuOpen] = useState(false);

  const tabs: Tab[] = [
    { to: '/', label: 'Home', icon: Home, activeWhen: baseActive('/') },
    { to: '/search', label: 'Search', icon: Search, activeWhen: baseActive('/search') },
    { to: '/post/new', label: 'Post', icon: PenSquare, activeWhen: baseActive('/post/new') },
    { to: '/user/messages', label: 'Messages', icon: Mail, activeWhen: baseActive('/user/messages') },
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
    'flex flex-1 flex-col items-center justify-center gap-0.5 py-2 min-h-[48px] font-mono text-[10px] transition-colors ' +
    (active ? 'text-vibe-cyan' : 'text-slate-500 hover:text-slate-300');

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
            aria-label="Account"
            className="fixed bottom-[calc(3.5rem+env(safe-area-inset-bottom))] left-0 right-0 z-50 mx-3 mb-2 overflow-hidden rounded-xl border border-vibe-border bg-vibe-surface shadow-xl"
          >
            <Link role="menuitem" to={profilePath} onClick={() => setMenuOpen(false)} className="flex items-center gap-3 px-4 py-3 text-xs font-mono text-slate-200 hover:bg-vibe-card">
              <User className="w-4 h-4 text-slate-400" /> My profile
            </Link>
            <Link role="menuitem" to="/drafts" onClick={() => setMenuOpen(false)} className="flex items-center gap-3 px-4 py-3 text-xs font-mono text-slate-200 hover:bg-vibe-card">
              <ScrollText className="w-4 h-4 text-slate-400" /> Drafts
            </Link>
            <Link role="menuitem" to="/user/settings" onClick={() => setMenuOpen(false)} className="flex items-center gap-3 px-4 py-3 text-xs font-mono text-slate-200 hover:bg-vibe-card">
              <Settings className="w-4 h-4 text-slate-400" /> Settings
            </Link>
            <button
              type="button"
              role="menuitem"
              onClick={handleLogout}
              className="flex w-full items-center gap-3 border-t border-vibe-border px-4 py-3 text-left text-xs font-mono text-red-400 hover:bg-vibe-card"
            >
              <LogOut className="w-4 h-4" /> Log out
            </button>
          </nav>
        </>
      )}
      <nav
        aria-label="Primary"
        className="fixed bottom-0 left-0 right-0 z-50 flex items-stretch border-t border-vibe-border bg-vibe-bg/95 backdrop-blur pb-[env(safe-area-inset-bottom)]"
      >
        {tabs.map((tab) => {
          const Icon = tab.icon;
          const active = tab.activeWhen(pathname);
          return (
            <Link
              key={tab.to}
              to={tab.to}
              aria-label={tab.label}
              aria-current={active ? 'page' : undefined}
              className={linkClass(active)}
            >
              <Icon className="w-[18px] h-[18px]" />
              <span>{tab.label}</span>
            </Link>
          );
        })}
        <button
          type="button"
          aria-label="Me"
          aria-haspopup="menu"
          aria-expanded={menuOpen}
          onClick={() => setMenuOpen((v) => !v)}
          className={linkClass(onProfile) + ' bg-transparent border-0'}
        >
          <User className="w-[18px] h-[18px]" />
          <span>Me</span>
        </button>
      </nav>
    </div>
  );
}
