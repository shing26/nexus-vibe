import { describe, expect, it } from 'vitest';

/**
 * Every HTTP call in the app is either inside a `try` that has a `catch`, or is a
 * react-query `queryFn` / `mutationFn`, which captures rejection as `isError`.
 *
 * <p>The census behind this was run by hand for round six and came back clean —
 * 41 call sites, none unguarded — so this test is not reporting a present bug. It
 * is the reason the next one shows up: an unhandled promise rejection in a React
 * event handler reaches `window.onerror` and the user sees nothing, and the only
 * defence the repo has today is that each new page is written by someone who
 * already knows the rule. Read the way it reads the tree, this is the same shape
 * as `NoEntityInControllerTest` and `NoStaleStatusAssertionTest` on the backend.</p>
 *
 * <p>The question is deliberately narrower than "is it in a try". A
 * `try { await call() } finally { setBusy(false) }` with no `catch` re-throws, so
 * the first version of this scan — which stopped at the `try` — would have passed
 * the exact case it exists to catch.</p>
 */

const CALL_SITE = /apiClient\s*\.\s*(get|post|put|delete|patch)\b/;
const TRY_OPEN = /\btry\s*\{/;
const TRY_CATCH = /^\s*\}\s*catch\b/;
const REACT_QUERY = /queryFn|mutationFn/;
const FUNCTION_BOUNDARY =
  /^\s*(export\s+)?(async\s+)?(function|const\s+\w+\s*=\s*(async\s*)?\(|\w+\s*:\s*async)/;

const sources = import.meta.glob('../**/*.{ts,tsx}', {
  query: '?raw',
  import: 'default',
  eager: true,
}) as Record<string, string>;

function opensBraces(line: string) {
  return (line.match(/\{/g) ?? []).length;
}

function closesBraces(line: string) {
  return (line.match(/\}/g) ?? []).length;
}

interface Guarded {
  guarded: boolean;
  why: string;
}

function checkCallSite(lines: string[], index: number): Guarded {
  let depth = 0;
  let tryLine = -1;

  for (let j = index; j >= 0; j--) {
    depth += closesBraces(lines[j]);
    depth -= opensBraces(lines[j]);
    if (TRY_OPEN.test(lines[j])) {
      tryLine = j;
      break;
    }
    if (REACT_QUERY.test(lines[j])) return { guarded: true, why: 'react-query' };
    if (j < index && FUNCTION_BOUNDARY.test(lines[j]) && depth < -1) break;
  }

  if (tryLine < 0) return { guarded: false, why: 'no enclosing try' };

  let brace = 0;
  for (let k = tryLine; k < lines.length; k++) {
    brace += opensBraces(lines[k]);
    brace -= closesBraces(lines[k]);
    if (k > tryLine && TRY_CATCH.test(lines[k])) return { guarded: true, why: 'try/catch' };
    if (brace <= 0) break;
  }

  return { guarded: false, why: `try at line ${tryLine + 1} has no catch` };
}

describe('api call sites', () => {
  it('are every one of them inside something that handles rejection', () => {
    const unguarded: string[] = [];
    let examined = 0;

    for (const [file, source] of Object.entries(sources)) {
      if (/\.test\.tsx?$/.test(file) || file.includes('/test/')) continue;
      const lines = source.split(/\r?\n/);
      for (let i = 0; i < lines.length; i++) {
        if (!CALL_SITE.test(lines[i])) continue;
        examined++;
        const verdict = checkCallSite(lines, i);
        if (!verdict.guarded) {
          unguarded.push(`${file.replace('../', 'src/')}:${i + 1} — ${verdict.why}`);
        }
      }
    }

    // Guards against a scan that silently reads nothing, which would be a green
    // test that proves the opposite of what it claims.
    expect(examined).toBeGreaterThan(30);
    expect(unguarded).toEqual([]);
  });
});
