/**
 * FE-1 — frontend crash reporting. A crash that never leaves the browser is a
 * bug only the first visitor ever sees, so boundary errors, window errors and
 * unhandled rejections each get one report to `/api/v1/crash-reports`, which
 * turns them into structured log lines on the backend.
 *
 * The reporter is deliberately not `apiClient`: it must keep working when the
 * thing that broke is the auth store or the toast store those interceptors
 * depend on, so it is a bare `fetch` with `keepalive` — the report survives
 * even a page that is being torn down.
 */

export interface CrashReport {
  kind: 'boundary' | 'window' | 'promise';
  message: string;
  stack?: string;
  componentStack?: string;
  url?: string;
  traceId?: string | null;
}

/** Window size and send budget for the self-rate-limit. */
const WINDOW_MS = 60_000;
const MAX_SENDS_PER_WINDOW = 5;

let windowStart = 0;
let sendsInWindow = 0;
/** Messages already reported inside the current window, keyed by kind+message. */
const reportedKeys = new Set<string>();

export function resetCrashReporterForTest() {
  windowStart = 0;
  sendsInWindow = 0;
  reportedKeys.clear();
}

function withinBudget(now: number): boolean {
  if (now - windowStart >= WINDOW_MS) {
    windowStart = now;
    sendsInWindow = 0;
    reportedKeys.clear();
  }
  return sendsInWindow < MAX_SENDS_PER_WINDOW;
}

/**
 * Sends one report unless the limiter says otherwise. Two gates, in order:
 * the same kind+message is reported at most once per window (React can
 * re-render a broken component many times before anyone notices), and a
 * window sends at most {@link MAX_SENDS_PER_WINDOW} reports (one broken
 * render loop must not flood the backend log). Failures are swallowed —
 * a crash reporter that can crash the app has failed at its one job.
 */
export async function reportCrash(report: CrashReport): Promise<void> {
  const now = Date.now();
  const key = `${report.kind}|${report.message}`;
  if (!withinBudget(now) || reportedKeys.has(key)) {
    return;
  }
  reportedKeys.add(key);
  sendsInWindow += 1;

  try {
    await fetch('/api/v1/crash-reports', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      keepalive: true,
      body: JSON.stringify({
        kind: report.kind,
        message: report.message.slice(0, 500),
        stack: report.stack?.slice(0, 4000),
        componentStack: report.componentStack?.slice(0, 4000),
        url: (report.url ?? window.location.href).slice(0, 300),
        traceId: report.traceId ?? null,
      }),
    });
  } catch {
    // The backend being down must not turn a frontend crash into a second one.
  }
}

/**
 * Catches what never reaches a React boundary: uncaught exceptions
 * (`window.onerror`) and rejected promises (`unhandledrejection`). Installed
 * once, in `main.tsx`, next to the root render.
 */
export function installGlobalCrashReporter(): void {
  window.addEventListener('error', (event) => {
    // Resource-load failures (a missing image) come through the same event
    // without an error object; they are noise, not crashes.
    if (!event.error) return;
    void reportCrash({
      kind: 'window',
      message: event.error.message || String(event.error),
      stack: event.error.stack,
    });
  });
  window.addEventListener('unhandledrejection', (event) => {
    const reason = event.reason;
    void reportCrash({
      kind: 'promise',
      message: reason instanceof Error ? reason.message : String(reason),
      stack: reason instanceof Error ? reason.stack : undefined,
    });
  });
}
