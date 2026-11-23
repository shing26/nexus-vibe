 import { create } from 'zustand';
 import { persist } from 'zustand/middleware';
 
 interface ThemeStore {
   dark: boolean;
   toggle: () => void;
 }
 
 export const useThemeStore = create<ThemeStore>()(
   persist(
     (set) => ({
      // Dark is the product default: the console world is where the audit
      // stamps, mono timestamps and status lights were designed. `persist`
      // restores a reader's stored choice (including light) on top of this.
      dark: true,
       toggle: () => set((s) => ({ dark: !s.dark })),
     }),
     { name: 'nexus-vibe-theme' }
   )
 );
