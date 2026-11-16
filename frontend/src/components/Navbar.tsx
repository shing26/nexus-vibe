import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { FileText, LogIn, LogOut, Mail, Menu, Moon, PenSquare, Search, Settings, Sun, User, UserPlus } from 'lucide-react';
import { useAuthStore } from '../stores/authStore';
import { useThemeStore } from '../stores/themeStore';
import CommandPalette from './CommandPalette';
import LanguageToggle from './LanguageToggle';

interface NavbarProps {
  onOpenDrawer: () => void;
}

// The palette hint names the modifier the reader actually holds: ⌘ on a Mac,
// Ctrl everywhere else. Both wordings travel in the dictionary so they stay
// translatable; the component only picks between them by platform.
function isApplePlatform(): boolean {
  if (typeof navigator === 'undefined') return false;
  return /Mac|iPhone|iPad/i.test(navigator.platform || navigator.userAgent);
}

/**
 * The v2 top bar: logo + site name left, the site search in the middle, the
 * language toggle and the user menu on the right. It carries no channel
 * navigation — channels live in the sidebar (≥lg) and the drawer (<lg).
 * CommandPalette stays on Ctrl+K, wired from here.
 */
export default function Navbar({ onOpenDrawer }: NavbarProps) {
  const { t } = useTranslation('common');
  const navigate = useNavigate();
  const { isAuthenticated, user, logout } = useAuthStore();
  const { dark, toggle } = useThemeStore();
  const [paletteOpen, setPaletteOpen] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const onKeyDown = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault();
        setPaletteOpen((v) => !v);
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, []);

  // Close the user menu on Escape or on any click outside of it.
  useEffect(() => {
    if (!menuOpen) return;
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setMenuOpen(false);
    };
    const onMouseDown = (e: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) setMenuOpen(false);
    };
    document.addEventListener('keydown', onKeyDown);
    document.addEventListener('mousedown', onMouseDown);
    return () => {
      document.removeEventListener('keydown', onKeyDown);
      document.removeEventListener('mousedown', onMouseDown);
    };
  }, [menuOpen]);

  const submitSearch = (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const input = e.currentTarget.elements.namedItem('q') as HTMLInputElement | null;
    const q = input?.value.trim();
    navigate(q ? '/search?q=' + encodeURIComponent(q) : '/search');
  };

  const handleLogout = () => {
    setMenuOpen(false);
    logout();
    navigate('/', { replace: true });
  };

  const menuItemClass =
    'flex w-full items-center gap-2.5 px-3 py-2 text-left text-sm text-vibe-text hover:bg-vibe-card';

  return (
    <nav className="sticky top-0 z-50 border-b border-vibe-border bg-vibe-surface">
      <div className="flex h-12 items-center gap-2 px-3 sm:px-4 lg:px-6">
        {/* Drawer trigger — below lg the sidebar is a drawer, not a rail */}
        <button
          type="button"
          onClick={onOpenDrawer}
          aria-label={t('common:nav.openDrawer')}
          aria-haspopup="dialog"
          className="rounded-md p-2 text-vibe-muted hover:bg-vibe-card hover:text-vibe-text lg:hidden"
        >
          <Menu className="h-5 w-5" />
        </button>

        {/* Logo */}
        <Link to="/" className="flex shrink-0 items-center gap-2">
          <span className="flex h-6 w-6 items-center justify-center rounded-md bg-vibe-accent text-xs font-bold text-white">N</span>
          <span className="text-sm font-semibold text-vibe-text">Nexus-Vibe</span>
        </Link>

        {/* Center search — submits to the existing search page; Ctrl+K opens the palette */}
        <form onSubmit={submitSearch} className="mx-auto hidden w-full max-w-md sm:block" role="search">
          <div className="relative">
            <Search className="pointer-events-none absolute left-2.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-vibe-muted" />
            <input
              name="q"
              type="search"
              placeholder={t('common:search.topBarPlaceholder')}
              aria-label={t('common:search.topBarPlaceholder')}
              className="h-8 w-full rounded-md border border-vibe-border bg-vibe-bg pl-8 pr-14 text-sm text-vibe-text placeholder-vibe-faint focus:border-vibe-accent"
            />
            <kbd className="pointer-events-none absolute right-2 top-1/2 hidden -translate-y-1/2 rounded border border-vibe-border bg-vibe-surface px-1 py-0.5 text-[11px] leading-none text-vibe-faint md:inline">
              {isApplePlatform() ? t('common:search.openPaletteHintMac') : t('common:search.openPaletteHint')}
            </kbd>
          </div>
        </form>

        {/* Right cluster: language, new post, user menu */}
        <div className="ml-auto flex shrink-0 items-center gap-1.5 sm:ml-0">
          <LanguageToggle />

          {isAuthenticated && (
            <Link
              to="/post/new"
              className="hidden items-center gap-1.5 rounded-md bg-vibe-accent px-3 py-1.5 text-xs font-medium text-white hover:opacity-90 sm:inline-flex"
            >
              <PenSquare className="h-3.5 w-3.5" />
              {t('common:user.newPost')}
            </Link>
          )}

          {!isAuthenticated ? (
            <div className="flex items-center gap-1.5">
              <Link to="/login" className="rounded-md p-2 text-sm text-vibe-muted hover:text-vibe-text" aria-label={t('common:user.login')}>
                <LogIn className="h-4 w-4" />
              </Link>
              <Link
                to="/register"
                className="inline-flex items-center gap-1 rounded-md border border-vibe-border bg-vibe-card px-2 py-1.5 text-xs font-medium text-vibe-text hover:border-vibe-accent sm:px-3"
              >
                <UserPlus className="h-3.5 w-3.5" />
                {t('common:user.register')}
              </Link>
            </div>
          ) : (
            <div className="relative" ref={menuRef}>
              <button
                type="button"
                onClick={() => setMenuOpen((v) => !v)}
                aria-label={t('common:user.menu')}
                aria-haspopup="menu"
                aria-expanded={menuOpen}
                className="flex h-8 w-8 items-center justify-center rounded-full bg-vibe-accent text-xs font-semibold text-white"
              >
                {(user?.nickname || user?.username || '?').charAt(0).toUpperCase()}
              </button>
              {menuOpen && (
                <div
                  role="menu"
                  aria-label={t('common:user.menu')}
                  className="absolute right-0 top-10 z-50 w-48 overflow-hidden rounded-lg border border-vibe-border bg-vibe-card shadow-lg"
                >
                  <div className="border-b border-vibe-border px-3 py-2">
                    <p className="truncate text-sm font-medium text-vibe-text">{user?.nickname || user?.username}</p>
                    <p className="truncate text-xs text-vibe-muted">@{user?.username}</p>
                  </div>
                  <Link role="menuitem" to={'/user/' + user?.id} onClick={() => setMenuOpen(false)} className={menuItemClass}>
                    <User className="h-4 w-4 text-vibe-muted" /> {t('common:user.profile')}
                  </Link>
                  <Link role="menuitem" to="/user/messages" onClick={() => setMenuOpen(false)} className={menuItemClass}>
                    <Mail className="h-4 w-4 text-vibe-muted" /> {t('common:nav.messages')}
                  </Link>
                  <Link role="menuitem" to="/drafts" onClick={() => setMenuOpen(false)} className={menuItemClass}>
                    <FileText className="h-4 w-4 text-vibe-muted" /> {t('common:nav.drafts')}
                  </Link>
                  <Link role="menuitem" to="/user/settings" onClick={() => setMenuOpen(false)} className={menuItemClass}>
                    <Settings className="h-4 w-4 text-vibe-muted" /> {t('common:nav.settings')}
                  </Link>
                  <button
                    type="button"
                    role="menuitem"
                    onClick={() => {
                      toggle();
                      setMenuOpen(false);
                    }}
                    className={menuItemClass}
                  >
                    {dark ? <Sun className="h-4 w-4 text-vibe-muted" /> : <Moon className="h-4 w-4 text-vibe-muted" />}
                    {dark ? t('common:theme.toLight') : t('common:theme.toDark')}
                  </button>
                  <button
                    type="button"
                    role="menuitem"
                    onClick={handleLogout}
                    className={menuItemClass + ' text-vibe-danger'}
                  >
                    <LogOut className="h-4 w-4" /> {t('common:user.logout')}
                  </button>
                </div>
              )}
            </div>
          )}
        </div>
      </div>
      <CommandPalette open={paletteOpen} onClose={() => setPaletteOpen(false)} />
    </nav>
  );
}