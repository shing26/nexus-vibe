import { useState } from 'react';
import { Outlet, useLocation } from 'react-router-dom';
import { useEffect } from 'react';
import Navbar from '../Navbar';
import Footer from '../Footer';
import MobileTabBar from '../MobileTabBar';
import Sidebar from '../Sidebar';
import SidebarDrawer from '../SidebarDrawer';
import DegradationBanner from '../DegradationBanner';

/**
 * The v2 app shell. One top bar; below it a fixed channel sidebar at ≥lg and a
 * hamburger drawer below lg (which closes on route change); the bottom
 * MobileTabBar remains the thumb-level entrance on phones. The channel
 * navigation lives in the sidebar family only — never in the top bar.
 */
export default function MainLayout() {
  const { pathname } = useLocation();
  const [drawerOpen, setDrawerOpen] = useState(false);

  useEffect(() => {
    window.scrollTo(0, 0);
    // Route changes close the drawer: an open drawer is a navigation in progress,
    // not a state to keep.
    setDrawerOpen(false);
  }, [pathname]);

  return (
    /* The tab bar is fixed over the page, so the padding has to be on the wrapper:
       on <main> alone it would clear the feed but leave the footer underneath the bar,
       which is the same defect one element further down. pb-16 covers the bar
       plus a row of air; lg:pb-0 restores the desktop layout exactly. */
    <div className="min-h-screen flex flex-col pb-16 lg:pb-0">
      <Navbar onOpenDrawer={() => setDrawerOpen(true)} />
      {/* Site-wide, so it sits in the one shell every public route renders under. The
          component renders nothing on a healthy deployment. */}
      <DegradationBanner />
      <div className="flex flex-1">
        {/* ≥lg: the fixed left rail. `sticky` keeps it in view while content scrolls
            and its own overflow-y handles tall channel lists. */}
        <aside
          aria-label="Channel navigation"
          className="sticky top-12 hidden h-[calc(100vh-3rem)] w-60 shrink-0 overflow-y-auto border-r border-vibe-border bg-vibe-surface px-3 pb-8 lg:block"
        >
          <Sidebar />
        </aside>
        <main className="min-w-0 flex-1">
          <Outlet />
        </main>
      </div>
      <Footer />
      <MobileTabBar />
      <SidebarDrawer open={drawerOpen} onClose={() => setDrawerOpen(false)} />
    </div>
  );
}
