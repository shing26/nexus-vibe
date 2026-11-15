import { useEffect, useRef } from 'react';
import { X } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import Sidebar from './Sidebar';

interface SidebarDrawerProps {
  open: boolean;
  onClose: () => void;
}

/**
 * The below-`lg` entrance to the same routes the fixed sidebar serves.
 *
 * <p>A left slide-in over a dimmed overlay. While it is open it traps focus —
 * Tab and Shift+Tab cycle inside the drawer, Escape closes it — and on close
 * focus returns to the hamburger that opened it, so a keyboard user never falls
 * to `<body>`. Route changes close it in the parent.</p>
 */
export default function SidebarDrawer({ open, onClose }: SidebarDrawerProps) {
  const { t } = useTranslation('common');
  const panelRef = useRef<HTMLDivElement>(null);
  const closeButtonRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (!open) return;
    const previousFocus = document.activeElement as HTMLElement | null;
    const timer = setTimeout(() => closeButtonRef.current?.focus(), 10);

    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        e.preventDefault();
        onClose();
        return;
      }
      if (e.key !== 'Tab' || !panelRef.current) return;
      // Focus trap: cycle Tab within the drawer while it is open.
      const focusables = panelRef.current.querySelectorAll<HTMLElement>(
        'a[href], button:not([disabled]), input, select, textarea, [tabindex]:not([tabindex="-1"])',
      );
      if (focusables.length === 0) return;
      const first = focusables[0];
      const last = focusables[focusables.length - 1];
      if (e.shiftKey && document.activeElement === first) {
        e.preventDefault();
        last.focus();
      } else if (!e.shiftKey && document.activeElement === last) {
        e.preventDefault();
        first.focus();
      }
    };

    document.addEventListener('keydown', onKeyDown);
    return () => {
      clearTimeout(timer);
      document.removeEventListener('keydown', onKeyDown);
      previousFocus?.focus();
    };
  }, [open, onClose]);

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-[80] lg:hidden">
      <div className="absolute inset-0 bg-black/50 animate-fade-in motion-reduce:animate-none" aria-hidden="true" onClick={onClose} />
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-label={t('nav.sidebarAria')}
        className="absolute inset-y-0 left-0 flex w-60 flex-col overflow-y-auto border-r border-vibe-border bg-vibe-surface shadow-xl animate-fade-in motion-reduce:animate-none"
      >
        <div className="flex items-center justify-between border-b border-vibe-border px-3 py-2">
          <span className="text-sm font-semibold text-vibe-text">Nexus-Vibe</span>
          <button
            ref={closeButtonRef}
            type="button"
            onClick={onClose}
            aria-label={t('nav.closeDrawer')}
            className="rounded-md p-1.5 text-vibe-muted hover:bg-vibe-card hover:text-vibe-text"
          >
            <X className="h-4 w-4" />
          </button>
        </div>
        <div className="px-2 pb-6">
          <Sidebar onNavigate={onClose} />
        </div>
      </div>
    </div>
  );
}
