import { Outlet, useLocation } from 'react-router-dom';
import { useEffect } from 'react';
import Sidebar from '../Sidebar';
import BackButton from '../BackButton';

export default function AdminLayout() {
  const { pathname } = useLocation();

  useEffect(() => {
    window.scrollTo(0, 0);
  }, [pathname]);

  return (
    <div className="flex min-h-screen">
      <Sidebar />
      <main className="flex-1 bg-vibe-bg p-8">
        <BackButton className="mb-6" />
        <Outlet />
      </main>
    </div>
  );
}
