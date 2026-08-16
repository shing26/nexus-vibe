import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuthStore } from '../stores/authStore';
import { useThemeStore } from '../stores/themeStore';
import { ShimmerButton } from './ui/ShimmerButton';
import { Plus, Search, Sun, Moon, Mail, Settings } from 'lucide-react';
import CommandPalette from './CommandPalette';

export default function Navbar() {
  const navigate = useNavigate();
  const { isAuthenticated, user, logout } = useAuthStore();
  const { dark, toggle } = useThemeStore();
  const [paletteOpen, setPaletteOpen] = useState(false);

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

  const handleLogout = () => {
    logout();
    navigate('/', { replace: true });
  };

  return (
   <nav className="bg-vibe-bg border-b border-vibe-border sticky top-0 z-50">
     <div className="max-w-[1400px] mx-auto px-4 sm:px-6 lg:px-8">
       <div className="flex items-center justify-between h-12">
          {/* Logo */}
          <Link to="/" className="text-base font-bold text-slate-100 font-mono shrink-0">
            Nexus.<span className="text-vibe-cyan">Vibe</span>
          </Link>

          {/* Cmd+K Search */}
          <button
            onClick={() => setPaletteOpen(true)}
            className="flex-1 min-w-0 max-w-lg mx-2 sm:mx-6 group"
            type="button"
            aria-label="Search"
          >
            <div className="relative">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-slate-500" />
              <span className="w-full flex items-center justify-between pl-9 pr-3 py-1.5 text-xs bg-vibe-surface border border-vibe-border rounded-md text-slate-500 font-mono group-hover:border-vibe-cyan/40 group-hover:text-slate-400 transition-colors">
                <span className="hidden sm:inline truncate">Search prompts, code...</span>
                <span className="hidden sm:flex items-center gap-1 pointer-events-none">
                  <kbd className="px-1 py-0.5 rounded bg-vibe-card border border-vibe-border text-[9px] leading-none">⌘</kbd>
                  <kbd className="px-1 py-0.5 rounded bg-vibe-card border border-vibe-border text-[9px] leading-none">K</kbd>
                </span>
              </span>
            </div>
          </button>

          {/* Right toolbar */}
          <div className="flex items-center gap-1.5 sm:gap-3 shrink-0">
            {/* AI Status */}
            <div className="hidden md:flex items-center gap-1.5 px-2 py-1 rounded-md bg-vibe-emerald/10 border border-vibe-emerald/20">
              <span className="w-1.5 h-1.5 rounded-full bg-vibe-emerald animate-pulse" />
              <span className="text-[10px] font-mono text-vibe-emerald">Agent Active</span>
            </div>

            {/* Dark mode toggle */}
            <button
              onClick={toggle}
              aria-label={dark ? 'Switch to light mode' : 'Switch to dark mode'}
              className="p-1.5 rounded-md hover:bg-vibe-surface transition-colors active:scale-[0.97]"
            >
              {dark ? <Sun className="w-3.5 h-3.5 text-slate-400" /> : <Moon className="w-3.5 h-3.5 text-slate-400" />}
            </button>

            {/* New Post */}
            {isAuthenticated && (
              <Link to="/post/new">
                <ShimmerButton>
                  <Plus className="w-3.5 h-3.5" />
                  <span>New Post</span>
                </ShimmerButton>
              </Link>
            )}

            {isAuthenticated && (
              <div className="hidden sm:flex items-center gap-1">
                <Link to="/user/messages" title="Messages" aria-label="Messages" className="p-1.5 rounded-md hover:bg-vibe-surface transition-colors active:scale-[0.97]">
                  <Mail className="w-3.5 h-3.5 text-slate-400" />
                </Link>
                <Link to="/user/settings" title="Settings" aria-label="Settings" className="p-1.5 rounded-md hover:bg-vibe-surface transition-colors active:scale-[0.97]">
                  <Settings className="w-3.5 h-3.5 text-slate-400" />
                </Link>
              </div>
            )}

            {/* Auth */}
            {!isAuthenticated ? (
              <div className="flex items-center gap-2">
                <Link to="/login" className="text-xs font-mono text-slate-400 hover:text-slate-200 transition-colors">Login</Link>
                <Link to="/register">
                  <ShimmerButton><span>Register</span></ShimmerButton>
                </Link>
              </div>
            ) : (
              <div className="flex items-center gap-2">
                <Link to={'/user/' + user?.id} className="hidden md:inline text-xs font-mono text-slate-400 hover:text-slate-200">{user?.username}</Link>
                <button onClick={handleLogout} aria-label="Logout" className="text-[10px] font-mono text-slate-600 hover:text-red-400 active:scale-[0.97]">Logout</button>
              </div>
            )}
          </div>
        </div>
      </div>
      <CommandPalette open={paletteOpen} onClose={() => setPaletteOpen(false)} />
    </nav>
  );
}
