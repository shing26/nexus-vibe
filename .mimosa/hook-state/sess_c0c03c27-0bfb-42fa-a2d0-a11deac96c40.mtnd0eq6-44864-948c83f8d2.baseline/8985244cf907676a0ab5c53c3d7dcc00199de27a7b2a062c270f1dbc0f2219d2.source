import { motion } from 'motion/react';
import { Outlet, useLocation } from 'react-router-dom';
import { useEffect } from 'react';
import Navbar from '../Navbar';
import Footer from '../Footer';
import BackButton from '../BackButton';

export default function MainLayout() {
  const { pathname } = useLocation();

  useEffect(() => {
    window.scrollTo(0, 0);
  }, [pathname]);

  return (
    <div className="min-h-screen flex flex-col">
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
    </div>
  );
}
