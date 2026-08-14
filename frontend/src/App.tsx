import { lazy, Suspense, useEffect } from 'react';
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ReactQueryDevtools } from '@tanstack/react-query-devtools';
import { useThemeStore } from './stores/themeStore';
import { useAuthStore } from './stores/authStore';
import { apiClient } from './api/client';
import MainLayout from './components/layout/MainLayout';
import AdminLayout from './components/layout/AdminLayout';
import AdminRouteGuard from './components/AdminRouteGuard';
import ErrorBoundary from './components/ErrorBoundary';
import ToastContainer from './components/ToastContainer';

const NotFoundPage = lazy(() => import('./pages/NotFoundPage'));
const HomePage = lazy(() => import('./pages/HomePage'));
const ChannelPage = lazy(() => import('./pages/ChannelPage'));
const PostDetailPage = lazy(() => import('./pages/PostDetailPage'));
const CreatePostPage = lazy(() => import('./pages/CreatePostPage'));
const EditPostPage = lazy(() => import('./pages/EditPostPage'));
const SearchPage = lazy(() => import('./pages/SearchPage'));
const LoginPage = lazy(() => import('./pages/LoginPage'));
const RegisterPage = lazy(() => import('./pages/RegisterPage'));
const UserProfilePage = lazy(() => import('./pages/UserProfilePage'));
const SettingsPage = lazy(() => import('./pages/SettingsPage'));
const MessagesPage = lazy(() => import('./pages/MessagesPage'));
const DraftsPage = lazy(() => import('./pages/DraftsPage'));
const TagsPage = lazy(() => import('./pages/TagsPage'));
const AuditPage = lazy(() => import('./pages/AuditPage'));
const AgentLogsPage = lazy(() => import('./pages/AgentLogsPage'));
const DashboardPage = lazy(() => import('./pages/DashboardPage'));

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 1000 * 60 * 5,
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
});

export default function App() {
  const dark = useThemeStore((s) => s.dark);
  const token = useAuthStore((s) => s.token);
  const authUser = useAuthStore((s) => s.user);
  const setAuth = useAuthStore((s) => s.setAuth);

  useEffect(() => {
    document.documentElement.classList.toggle('dark', dark);
  }, [dark]);

  useEffect(() => {
    if (!token || !authUser) return;
    if (authUser.id && authUser.nickname) return;
    apiClient
      .get('/users/profile')
      .then((res) => {
        const u = res.data.data;
        setAuth(token, {
          id: u.id,
          username: u.username,
          nickname: u.nickname,
          role: u.role,
          avatar: u.avatar,
          avatarUrl: u.avatar,
          bio: u.bio,
        });
      })
      .catch(() => {});
  }, [token, authUser, setAuth]);

  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <ErrorBoundary>
          <ToastContainer />
          <Suspense fallback={<div className="flex min-h-screen items-center justify-center text-sm text-zinc-500">Loading...</div>}>
            <Routes>
              <Route element={<MainLayout />}>
                <Route path="/" element={<HomePage />} />
                <Route path="/channel/:slug" element={<ChannelPage />} />
                <Route path="/post/:id" element={<PostDetailPage />} />
                <Route path="/post/new" element={<CreatePostPage />} />
                <Route path="/post/:id/edit" element={<EditPostPage />} />
                <Route path="/search" element={<SearchPage />} />
                <Route path="/login" element={<LoginPage />} />
                <Route path="/register" element={<RegisterPage />} />
                <Route path="/user/:id" element={<UserProfilePage />} />
                <Route path="/user/settings" element={<SettingsPage />} />
                <Route path="/user/messages" element={<MessagesPage />} />
                <Route path="/drafts" element={<DraftsPage />} />
                <Route path="/tags" element={<TagsPage />} />
                <Route path="*" element={<NotFoundPage />} />
              </Route>
              <Route element={<AdminLayout />}>
                <Route element={<AdminRouteGuard />}>
                  <Route path="/admin/audit" element={<AuditPage />} />
                  <Route path="/admin/dashboard" element={<DashboardPage />} />
                  <Route path="/agent-logs" element={<AgentLogsPage />} />
                </Route>
              </Route>
            </Routes>
          </Suspense>
        </ErrorBoundary>
      </BrowserRouter>
      {import.meta.env.DEV && <ReactQueryDevtools initialIsOpen={false} />}
    </QueryClientProvider>
  );
}
