import axios from 'axios';
import type { AxiosError } from 'axios';
import { useAuthStore } from '../stores/authStore';
import { useToastStore } from '../stores/toastStore';

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

/**
 * The server stamps every response with `X-Trace-Id`, and a 5xx repeats it in the
 * body. Showing the first eight characters gives a user something to say when
 * something breaks: it is the same string the log line carries.
 */
function shortTraceId(error: AxiosError): string | null {
  const header = error.response?.headers?.['x-trace-id'];
  const fromHeader = typeof header === 'string' ? header : undefined;
  const body = error.response?.data as { traceId?: unknown } | undefined;
  const traceId = typeof body?.traceId === 'string' ? body.traceId : fromHeader;
  return traceId ? traceId.slice(0, 8) : null;
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
    // A server-side failure is the one class of error a user cannot fix, so it is
    // the one worth naming itself. Pages keep their own copy; this adds the number.
    if ((error.response?.status ?? 0) >= 500) {
      const trace = shortTraceId(error as AxiosError);
      useToastStore.getState().addToast(
        trace ? `服务暂时不可用（追踪号 ${trace}）` : '服务暂时不可用，请稍后重试',
        'error',
      );
    }
    return Promise.reject(error);
  }
);

/**
 * The envelope the API actually returns: `ApiResponse<T>` in
 * `com.nexus.campus.dto`. `code` is the server's own view of the outcome and
 * mirrors the HTTP status; axios already rejects non-2x, so callers read `data`
 * on the happy path and `message` on the failed one. There is no `success`
 * field — nothing ever read the declared one, which is how the type drifted from
 * the response in the first place.
 */
export interface ApiResponse<T> {
  code: number;
  message?: string;
  data: T;
}
