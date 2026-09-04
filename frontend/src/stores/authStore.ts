import { create } from 'zustand';
import { persist } from 'zustand/middleware';

interface User {
  id?: string;
  username: string;
  nickname?: string;
  email?: string;
  role?: string;
  avatarUrl?: string;
  avatar?: string;
  bio?: string;
}

interface AuthState {
  token: string | null;
  refreshToken: string | null;
  user: User | null;
  isAuthenticated: boolean;
  setAuth: (token: string, user: User, refreshToken?: string) => void;
  setToken: (token: string, refreshToken?: string) => void;
  logout: () => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      token: null,
      refreshToken: null,
      user: null,
      isAuthenticated: false,
      setAuth: (token: string, user: User, refreshToken?: string) =>
        set({ token, user, isAuthenticated: true, refreshToken: refreshToken ?? null }),
      setToken: (token: string, refreshToken?: string) =>
        set({ token, refreshToken: refreshToken ?? undefined }),
      logout: () =>
        set({ token: null, refreshToken: null, user: null, isAuthenticated: false }),
    }),
    {
      name: 'nexus-vibe-auth',
      partialize: (state) => ({
        token: state.token,
        refreshToken: state.refreshToken,
        user: state.user,
        isAuthenticated: state.isAuthenticated,
      }),
    }
  )
);
