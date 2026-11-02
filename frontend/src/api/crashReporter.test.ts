import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  installGlobalCrashReporter,
  reportCrash,
  resetCrashReporterForTest,
} from './crashReporter';

const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 200 }));

beforeEach(() => {
  vi.stubGlobal('fetch', fetchMock);
  resetCrashReporterForTest();
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});
// ANCHOR1
describe('reportCrash', () => {
  it('posts one JSON report', async () => {
    await reportCrash({ kind: 'boundary', message: 'boom' });
    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/v1/crash-reports');
    const body = JSON.parse(init.body);
    expect(body.kind).toBe('boundary');
    expect(body.message).toBe('boom');
  });

  it('dedupes same kind+message in one window', async () => {
    await reportCrash({ kind: 'boundary', message: 'boom' });
    await reportCrash({ kind: 'boundary', message: 'boom' });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});
// ANCHOR2
describe('rate limit', () => {
  it('caps sends at five per window even across distinct messages', async () => {
    for (let i = 0; i < 100; i++) {
      await reportCrash({ kind: 'boundary', message: `boom ${i}` });
    }
    expect(fetchMock).toHaveBeenCalledTimes(5);
  });

  it('truncates oversized fields to the backend caps', async () => {
    await reportCrash({ kind: 'window', message: 'm'.repeat(600), stack: 's'.repeat(5000) });
    const body = JSON.parse(fetchMock.mock.calls[0][1].body);
    expect(body.message.length).toBeLessThanOrEqual(500);
    expect(body.stack.length).toBeLessThanOrEqual(4000);
  });

  it('never rejects when fetch itself fails', async () => {
    fetchMock.mockRejectedValueOnce(new TypeError('network down'));
    await expect(reportCrash({ kind: 'boundary', message: 'x' })).resolves.toBeUndefined();
  });
});
// ANCHOR3
describe('installGlobalCrashReporter', () => {
  it('reports an unhandled rejection exactly once', async () => {
    installGlobalCrashReporter();
    // Synthetic dispatch rather than a real dangling rejection: the runtime
    // surfaces those itself and the test cannot intercept the handoff.
    window.dispatchEvent(
      new PromiseRejectionEvent('unhandledrejection', {
        promise: Promise.resolve(),
        reason: new Error('async boom'),
      }),
    );
    await new Promise((r) => setTimeout(r, 0));
    const calls = fetchMock.mock.calls.filter(
      ([, init]) => JSON.parse(init.body).message === 'async boom',
    );
    expect(calls).toHaveLength(1);
  });

  it('skips resource-load noise (no error object)', async () => {
    installGlobalCrashReporter();
    window.dispatchEvent(new ErrorEvent('error'));
    await new Promise((r) => setTimeout(r, 0));
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
