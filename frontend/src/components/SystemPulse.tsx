import { useTranslation } from 'react-i18next';
import { useDegradedCapabilities } from '../api/useCapabilities';

const DOT_STYLES: Record<string, string> = {
  ok: 'bg-vibe-success',
  degraded: 'bg-vibe-warning',
  down: 'bg-vibe-danger',
};

function PulseRow({ dot, label }: { dot: string; label: string }) {
  return (
    <p className="flex items-start gap-1.5 text-xs leading-snug text-vibe-muted">
      <span
        aria-hidden="true"
        className={'mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full ' + (DOT_STYLES[dot] ?? DOT_STYLES.ok)}
      />
      <span className="min-w-0 break-words">{label}</span>
    </p>
  );
}

/**
 * The sidebar's system-pulse panel: the site's own operational state, rendered
 * from real data only. The API row reflects whether the capabilities request
 * itself succeeded; every other row is one degraded capability exactly as the
 * server's closed vocabulary words it (ADR-0007: degraded is not unhealthy, so
 * degraded rows read as "serving with impact", never as an outage).
 *
 * Spike note: reconcile-queue depth and circuit-breaker state have no public
 * endpoint yet — they stay out of the panel until the backend owns a surface
 * for them, because an invented number would break the honesty rule.
 */
export default function SystemPulse() {
  const { t } = useTranslation('common');
  const { data, isLoading, isError } = useDegradedCapabilities();

  return (
    <section
      aria-label={t('pulse.title')}
      data-testid="system-pulse"
      className="mx-2 mb-2 mt-4 rounded-md border border-vibe-border bg-vibe-surface p-2 lg:hidden xl:block"
    >
      <p className="pb-1.5 text-xs text-vibe-faint">
        {t('pulse.title')}
      </p>
      <div className="space-y-1.5">
        {isLoading ? (
          <PulseRow dot="ok" label={t('pulse.probing')} />
        ) : isError ? (
          <PulseRow dot="down" label={t('pulse.apiDown')} />
        ) : (
          <>
            <PulseRow dot="ok" label={t('pulse.apiServing')} />
            {(data ?? []).length === 0 ? (
              <PulseRow dot="ok" label={t('pulse.allNormal')} />
            ) : (
              (data ?? []).map((capability) => (
                <PulseRow key={capability.key} dot="degraded" label={capability.impact} />
              ))
            )}
          </>
        )}
      </div>
    </section>
  );
}
