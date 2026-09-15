import { beforeEach, describe, expect, it } from 'vitest';
import axios, {
  AxiosError,
  AxiosHeaders,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from 'axios';
import { apiClient } from './client';
import { useAuthStore } from '../stores/authStore';
import { useToastStore } from '../stores/toastStore';

/**
 * The response interceptor, driven through axios itself.
 *
 * <p>Both instances get a fake `adapter` rather than a mocked `axios.post`, so
 * the code under test is the real request pipeline: the request interceptor,
 * the response interceptor, and the replay-on-refresh path all execute, and the
 * rejection object is a genuine `AxiosError` with a genuine `AxiosHeaders` —
 * which is what makes the trace-id assertions worth anything. Mocking the call
 * would only prove that the test's own fake behaves like the fake.</p>
 */

interface Step {
  status: number;
  data?: unknown;
  headers?: Record<string, string>;
}

/**
 * Snapshots, not the configs themselves. The interceptor retries by writing
 * `original.headers.Authorization` on the very object the first call handed the
 * adapter, so keeping the config would report the post-refresh token twice and
 * the assertion below would be un-loseable.
 */
interface Seen {
  authorization: string;
}

const clientCalls: Seen[] = [];
const refreshCalls: string[] = [];

let script: Step[] = [];
let refreshScript: Step[] = [];

function canned(step: Step, config: InternalAxiosRequestConfig): AxiosResponse {
  return {
    status: step.status,
    statusText: step.status < 400 ? 'OK' : 'Error',
    data: step.data ?? null,
    headers: new AxiosHeaders(step.headers ?? {}),
    config,
  };
}

function installAdapters() {
  apiClient.defaults.adapter = async (config) => {
    const step = script[Math.min(clientCalls.length, script.length - 1)];
    clientCalls.push({ authorization: String(config.headers.Authorization ?? '') });
    const response = canned(step, config);
    if (step.status >= 400) {
      throw new AxiosError(
        `Request failed with status code ${step.status}`,
        AxiosError.ERR_BAD_RESPONSE,
        config,
        {},
        response,
      );
    }
    return response;
  };

  axios.defaults.adapter = async (config) => {
    const index = refreshCalls.length;
    refreshCalls.push(String(config.url));
    const step = refreshScript[Math.min(index, refreshScript.length - 1)];
    const response = canned(step, config);
    if (step.status >= 400) {
      throw new AxiosError(
        `Request failed with status code ${step.status}`,
        AxiosError.ERR_BAD_RESPONSE,
        config,
        {},
        response,
      );
    }
    return response;
  };
}

const REFRESHED = { code: 200, message: 'Token refreshed.', data: { token: 'new-token', refreshToken: 'new-refresh' } };

beforeEach(() => {
  clientCalls.length = 0;
  refreshCalls.length = 0;
  script = [];
  refreshScript = [{ status: 200, data: REFRESHED }];
  installAdapters();
  useAuthStore.getState().logout();
  useAuthStore.getState().setAuth('old-token', { username: 'shing' }, 'old-refresh');
  useToastStore.setState({ toasts: [] });
});

describe('response interceptor', () => {
  it('replays a 401 once, against the refreshed token', async () => {
    script = [
      { status: 401, data: { code: 401, message: 'Session expired.' } },
      { status: 200, data: { code: 200, message: 'Ok.', data: { id: 1 } } },
    ];

    const res = await apiClient.get('/posts/1');

    expect(res.data.data).toEqual({ id: 1 });
    expect(refreshCalls).toEqual(['/api/v1/auth/refresh']);
    expect(clientCalls).toHaveLength(2);
    expect(clientCalls[0].authorization).toBe('Bearer old-token');
    expect(clientCalls[1].authorization).toBe('Bearer new-token');
    expect(useAuthStore.getState().token).toBe('new-token');
    expect(useToastStore.getState().toasts).toHaveLength(0);
  });

  it('shares one refresh across concurrent 401s', async () => {
    script = [
      { status: 401, data: { code: 401, message: 'Session expired.' } },
      { status: 401, data: { code: 401, message: 'Session expired.' } },
      { status: 200, data: { code: 200, message: 'Ok.', data: 'a' } },
      { status: 200, data: { code: 200, message: 'Ok.', data: 'b' } },
    ];

    const [left, right] = await Promise.all([apiClient.get('/posts/1'), apiClient.get('/posts/2')]);

    // Two failures, one /auth/refresh. Without the single-flight this is the
    // shape of a stampede: every in-flight request burns a token rotation.
    expect(refreshCalls).toHaveLength(1);
    expect(clientCalls).toHaveLength(4);
    expect(left.data.data).toBeDefined();
    expect(right.data.data).toBeDefined();
  });

  it('logs out when the refresh itself is refused, and does not loop', async () => {
    script = [{ status: 401, data: { code: 401, message: 'Session expired.' } }];
    refreshScript = [{ status: 401, data: { code: 401, message: 'Refresh token expired.' } }];

    await expect(apiClient.get('/posts/1')).rejects.toBeInstanceOf(AxiosError);

    expect(refreshCalls).toHaveLength(1);
    expect(clientCalls).toHaveLength(1);
    expect(useAuthStore.getState().isAuthenticated).toBe(false);
    expect(useAuthStore.getState().token).toBeNull();
  });

  it('rejects a 403 without refreshing, retrying, or announcing it', async () => {
    script = [{ status: 403, data: { code: 403, message: 'Access denied. Admin privileges required.' } }];

    await expect(apiClient.post('/posts/1/pin')).rejects.toMatchObject({
      response: { status: 403 },
    });

    expect(refreshCalls).toHaveLength(0);
    expect(clientCalls).toHaveLength(1);
    expect(useAuthStore.getState().isAuthenticated).toBe(true);
    expect(useToastStore.getState().toasts).toHaveLength(0);
  });

  it('surfaces a 401 from the login endpoint as the server wrote it', async () => {
    // Round six made this endpoint answer 401 instead of 200-with-code-401. The
    // interceptor must not treat it as an expired session and must not log the
    // user out of a token they never had — the page owns this failure.
    script = [{ status: 401, data: { code: 401, message: 'Invalid username or password.' } }];

    await expect(apiClient.post('/auth/login', { username: 'shing', password: 'nope' })).rejects.toMatchObject({
      response: { status: 401, data: { message: 'Invalid username or password.' } },
    });

    expect(refreshCalls).toHaveLength(0);
    expect(clientCalls).toHaveLength(1);
    expect(useToastStore.getState().toasts).toHaveLength(0);
  });

  it('names a 5xx with the trace id, from the body when it is there', async () => {
    script = [
      {
        status: 500,
        data: { code: 500, message: 'Internal server error.', traceId: 'deadbeefcafebabe' },
        headers: { 'x-trace-id': 'deadbeefcafebabe' },
      },
    ];

    await expect(apiClient.get('/posts/1')).rejects.toBeInstanceOf(AxiosError);

    const toasts = useToastStore.getState().toasts;
    expect(toasts).toHaveLength(1);
    expect(toasts[0].type).toBe('error');
    expect(toasts[0].message).toContain('deadbe');
  });

  it('falls back to the response header when the body carries no trace id', async () => {
    script = [
      {
        status: 502,
        data: 'Bad Gateway',
        headers: { 'x-trace-id': '1122334455667788' },
      },
    ];

    await expect(apiClient.get('/posts/1')).rejects.toBeInstanceOf(AxiosError);

    expect(useToastStore.getState().toasts[0].message).toContain('11223344');
  });
});
