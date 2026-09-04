import axios from 'axios';
import { useAuthStore } from '../stores/authStore';

export const apiClient = axios.create({
  baseURL: '/api/v1',
  headers: {
    'Content-Type': 'application/json',
  },
});

apiClient.interceptors.request.use((config) => {
  const token = useAuthStore.getState().token;
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Single-flight refresh: concurrent 401s wait for one /auth/refresh call.
let refreshPromise: Promise<string | null> | null = null;

async function refreshAccessToken(): Promise<string | null> {
  const { refreshToken, setToken, logout } = useAuthStore.getState();
  if (!refreshToken) {
    logout();
    return null;
  }
  if (!refreshPromise) {
    refreshPromise = axios
      .post('/api/v1/auth/refresh', { refreshToken })
      .then((res) => {
        const d = res.data?.data;
        if (!d?.token) throw new Error('no token in refresh response');
        setToken(d.token, d.refreshToken);
        return d.token as string;
      })
      .catch(() => {
        // Refresh expired or rejected: full logout, next navigation hits /login.
        logout();
        return null;
      })
      .finally(() => {
        refreshPromise = null;
      });
  }
  return refreshPromise;
}

apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const original = error.config;
    const isAuthCall =
      original?.url === '/auth/refresh' || original?.url === '/auth/login';

    if (error.response?.status === 401 && original && !isAuthCall && !original._retried) {
      original._retried = true;
      const newToken = await refreshAccessToken();
      if (newToken) {
        original.headers.Authorization = `Bearer ${newToken}`;
        return apiClient(original);
      }
    } else if (error.response?.status === 401 && (!original || isAuthCall)) {
      // Login failed with wrong credentials or the refresh call itself 401'd —
      // don't loop; surface the error (login page handles it) / log out.
      if (original?.url !== '/auth/login') {
        useAuthStore.getState().logout();
      }
    }
    return Promise.reject(error);
  }
);

export interface ApiResponse<T> {
  success: boolean;
  data: T;
  message?: string;
}
