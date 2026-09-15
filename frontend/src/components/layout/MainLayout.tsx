import { motion } from 'motion/react';
import { Outlet, useLocation } from 'react-router-dom';
import { useEffect } from 'react';
import Navbar from '../Navbar';
import Footer from '../Footer';
import MobileTabBar from '../MobileTabBar';
import BackButton from '../BackButton';

export default function MainLayout() {
  const { pathname } = useLocation();

  useEffect(() => {
    window.scrollTo(0, 0);
  }, [pathname]);

  return (
    /* The tab bar is fixed over the page, so the padding has to be on the wrapper:
       on <main> alone it would clear the feed but leave the footer underneath the bar,
       which is the same defect one element further down. pb-20 covers the 55px bar
       plus a row of air; lg:pb-0 restores the desktop layout exactly. */
    <div className="min-h-screen flex flex-col pb-20 lg:pb-0">
      <Navbar />
     <main className="flex-1">
       <motion.div
         initial={{ opacity: 0, y: 6 }}
         animate={{ opacity: 1, y: 0 }}
         transition={{ duration: 0.25, ease: [0.25, 0.1, 0.25, 1] }}
       >
         {pathname !== '/' && (
           <div className="max-w-[1400px] mx-auto px-4 sm:px-6 lg:px-8 pt-4">
             <BackButton />
           </div>
         )}
         <Outlet />
       </motion.div>
     </main>
      <Footer />
      <MobileTabBar />
    </div>
  );
}
