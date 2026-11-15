import { Outlet, useLocation } from 'react-router-dom';
import { useEffect } from 'react';
import Sidebar from '../Sidebar';
import BackButton from '../BackButton';

/**
 * The admin panel shares the v2 sidebar: the 管理 group renders there for an
 * ADMIN, so this layout simply provides a wider content column beside it.
 */
export default function AdminLayout() {
  const { pathname } = useLocation();

  useEffect(() => {
    window.scrollTo(0, 0);
  }, [pathname]);

  return (
    <div className="flex min-h-screen">
      <aside
        aria-label="Channel navigation"
        className="sticky top-0 hidden h-screen w-60 shrink-0 overflow-y-auto border-r border-vibe-border bg-vibe-surface px-3 py-4 sm:block"
      >
        <Sidebar />
      </aside>
      <main className="min-w-0 flex-1 bg-vibe-bg p-4 sm:p-8">
        <BackButton className="mb-6" />
        <Outlet />
      </main>
    </div>
  );
}
