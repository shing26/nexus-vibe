 import { create } from 'zustand';
 import { persist } from 'zustand/middleware';
 
 interface ThemeStore {
   dark: boolean;
   toggle: () => void;
 }
 
 export const useThemeStore = create<ThemeStore>()(
   persist(
     (set) => ({
       dark: window.matchMedia?.('(prefers-color-scheme: dark)').matches ?? false,
       toggle: () => set((s) => ({ dark: !s.dark })),
     }),
     { name: 'nexus-vibe-theme' }
   )
 );
