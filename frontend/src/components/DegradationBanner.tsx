import { useState } from 'react';
import { AlertTriangle, X } from 'lucide-react';
import { useDegradedCapabilities } from '../api/useCapabilities';
import { ExternalLink } from './ui/ExternalLink';

/**
 * Says which optional capability is running limited, in the visitor's terms.
 *
 * <p>It renders only when the server has something to report, so a healthy deployment looks
 * exactly as it did before this existed. The sentences come from the endpoint rather than being
 * reconstructed here: the server owns the closed vocabulary, and a second copy in the SPA is how
 * the two drift.</p>
 *
 * <p>Dismissal is per session and keyed by capability, not a single boolean. If the banner is
 * dismissed while search is down and review later degrades too, the new key is not in the
 * dismissed set and the banner returns with only that new consequence - a dismissal covers what
 * the reader actually saw.</p>
 */

const DISMISSED_KEY = 'nexus.degraded-capabilities.dismissed';

// links.ts is outside this change's allowed paths, so the ADR URL is built here. ADR-0007 is the
// decision this banner links to: a degraded dependency is not an unhealthy site.
const ADR_0007_URL =
  'https://github.com/shing26/nexus-vibe/blob/master/docs/adr/0007-degraded-status-is-not-unhealthy.md';

function readDismissed(): string[] {
  try {
    const raw = sessionStorage.getItem(DISMISSED_KEY);
    if (!raw) return [];
    const parsed: unknown = JSON.parse(raw);
    return Array.isArray(parsed)
      ? parsed.filter((value): value is string => typeof value === 'string')
      : [];
  } catch {
    // Storage can be unavailable (private mode, disabled quota). Treat it as "nothing dismissed".
    return [];
  }
}

function writeDismissed(keys: string[]): void {
  try {
    sessionStorage.setItem(DISMISSED_KEY, JSON.stringify(keys));
  } catch {
    // A failed write only costs the dismissal; the banner can reappear, which is the safe side.
  }
}

export default function DegradationBanner() {
  const { data } = useDegradedCapabilities();
  const [dismissed, setDismissed] = useState<string[]>(readDismissed);

  const visible = (data ?? []).filter((capability) => !dismissed.includes(capability.key));
  if (visible.length === 0) return null;

  const dismiss = () => {
    const next = Array.from(new Set([...dismissed, ...visible.map((capability) => capability.key)]));
    setDismissed(next);
    writeDismissed(next);
  };

  return (
    <div role="status" className="border-b border-amber-500/20 bg-amber-500/5">
      <div className="mx-auto flex max-w-[1400px] items-start gap-3 px-4 py-2 sm:px-6 lg:px-8">
        <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-amber-400" aria-hidden="true" />
        <div className="min-w-0 flex-1">
          <p className="text-[10px] font-mono uppercase tracking-widest text-amber-400/90">
            Some features are limited
          </p>
          <ul className="mt-0.5 space-y-0.5">
            {visible.map((capability) => (
              <li key={capability.key} className="text-xs text-amber-100/80">
                {capability.impact}
              </li>
            ))}
          </ul>
          <ExternalLink
            href={ADR_0007_URL}
            title="ADR-0007: a degraded dependency is not an unhealthy site"
          >
            Why this happens
          </ExternalLink>
        </div>
        <button
          type="button"
          onClick={dismiss}
          aria-label="Dismiss status notice"
          className="mt-0.5 shrink-0 rounded p-1 text-amber-400/70 transition-colors hover:bg-amber-400/10 hover:text-amber-200 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-vibe-neon"
        >
          <X className="h-3.5 w-3.5" aria-hidden="true" />
        </button>
      </div>
    </div>
  );
}
