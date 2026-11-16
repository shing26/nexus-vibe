import { useState } from 'react';
import { Outlet, useLocation } from 'react-router-dom';
import { useEffect } from 'react';
import Navbar from '../Navbar';
import Footer from '../Footer';
import MobileTabBar from '../MobileTabBar';
import Sidebar from '../Sidebar';
import SidebarDrawer from '../SidebarDrawer';
import DegradationBanner from '../DegradationBanner';
import BackButton from '../BackButton';

/**
 * The v2 app shell. One top bar; below it a fixed channel sidebar at ≥lg and a
 * hamburger drawer below lg (which closes on route change); the bottom
 * MobileTabBar remains the thumb-level entrance on phones. The channel
 * navigation lives in the sidebar family only — never in the top bar.
 *
 * <p>The sidebar has three widths: full 240px at ≥1280px, a 48px icon rail at
 * 1024–1279px (tooltips on hover/focus), and the drawer below 1024px. A page
 * return control sits inside every public non-home page, as it did in v1.</p>
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
        {/* ≥lg: the fixed left rail. `sticky` keeps it in view while content scrolls.
            At 1024–1279px it is a 48px icon rail, so it must not clip the tooltips
            that leave it horizontally; from 1280px it becomes the 240px sidebar and
            scrolls its own tall list. */}
        <aside
          aria-label="Channel navigation"
          className="sticky top-12 hidden h-[calc(100vh-3rem)] w-12 shrink-0 overflow-visible border-r border-vibe-border bg-vibe-surface px-1 pb-8 lg:block xl:w-60 xl:overflow-y-auto xl:px-3"
        >
          <Sidebar />
        </aside>
        <main className="min-w-0 flex-1">
          {pathname !== '/' && (
            <div className="mx-auto max-w-[1400px] px-4 pt-4 sm:px-6 lg:px-8">
              <BackButton />
            </div>
          )}
          <Outlet />
        </main>
      </div>
      <Footer />
      <MobileTabBar />
      <SidebarDrawer open={drawerOpen} onClose={() => setDrawerOpen(false)} />
    </div>
  );
}
