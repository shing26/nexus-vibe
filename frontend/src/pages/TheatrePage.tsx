import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { useReducedMotion } from 'motion/react';
import { AlertCircle, ArrowRight, ExternalLink, RotateCw, Theater } from 'lucide-react';
import { apiClient } from '../api/client';
import type { ApiResponse } from '../api/client';
import Skeleton from '../components/Skeleton';

const REPO = 'https://github.com/shing26/nexus-vibe';

interface TheatreStep {
  id: string;
  title: string;
  detail: string;
  mode: string;
  outcome: string;
  payload: string | null;
  offsetMs: string | number;
}

export interface TheatreScenario {
  id: string;
  title: string;
  subtitle: string;
  lesson: string;
  sampleCode: string;
  promptPreview: string;
  rawOutput: string;
  repairedJson: string | null;
  score: number | null;
  severity: string | null;
  approved: boolean | null;
  modelAttempts: number;
  durationMs: string | number;
  failClosed: boolean;
  steps: TheatreStep[];
  adr: string;
}

const REPLAY_STEP_MS = 600;

const MODE_STYLES: Record<string, string> = {
  REAL: 'border-vibe-success/40 bg-vibe-success/10 text-vibe-success',
  INJECTED: 'border-vibe-warning/40 bg-vibe-warning/10 text-vibe-warning',
  POLICY: 'border-vibe-accent/40 bg-vibe-accent/10 text-vibe-accent',
};

const OUTCOME_STYLES: Record<string, string> = {
  OK: 'text-vibe-success',
  WARN: 'text-vibe-warning',
  FAIL: 'text-vibe-danger',
  INFO: 'text-vibe-muted',
};

function asNumber(value: string | number | undefined | null): number {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : 0;
}

function formatDuration(value: string | number | undefined | null): string {
  const ms = asNumber(value);
  if (ms < 1000) return `${Math.round(ms)} ms`;
  return `${(ms / 1000).toFixed(1)} s`;
}

function adrUrl(adr: string): string {
  return REPO + '/blob/master/' + adr;
}

function ModeBadge({ mode }: { mode: string }) {
  const { t } = useTranslation('theatre');
  return (
    <span
      data-testid="mode-badge"
      data-mode={mode}
      className={
        'rounded-full border px-2 py-0.5 text-[11px] font-medium ' +
        (MODE_STYLES[mode] ?? 'border-vibe-border bg-vibe-card text-vibe-muted')
      }
    >
      {t('mode.' + mode.toLowerCase())}
    </span>
  );
}

export default function TheatrePage() {
  const { t } = useTranslation('theatre');
  const reduceMotion = useReducedMotion();
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [revealed, setRevealed] = useState(0);

  const { data, isLoading, isError, refetch } = useQuery<TheatreScenario[]>({
    queryKey: ['demo', 'chaos-scenarios'],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<TheatreScenario[]>>('/demo/chaos-scenarios');
      return res.data.data ?? [];
    },
    staleTime: 1000 * 60 * 10,
  });

  const scenarios = data ?? [];
  const selected =
    scenarios.find((scenario) => scenario.id === selectedId) ?? scenarios[0] ?? null;

  useEffect(() => {
    if (!selected) return;
    const total = selected.steps.length;
    if (reduceMotion) {
      setRevealed(total);
      return;
    }
    setRevealed(0);
    let count = 0;
    const timer = setInterval(() => {
      count += 1;
      setRevealed(count);
      if (count >= total) clearInterval(timer);
    }, REPLAY_STEP_MS);
    return () => clearInterval(timer);
  }, [selected, reduceMotion]);

  if (isLoading) {
    return (
      <div className="mx-auto max-w-4xl space-y-4 px-4 py-8">
        <Skeleton className="h-8 w-1/2" />
        <Skeleton className="h-16 rounded-lg" />
        <Skeleton className="h-64 rounded-lg" />
      </div>
    );
  }

  if (isError) {
    return (
      <div role="alert" className="mx-auto max-w-4xl px-4 py-8">
        <div className="flex items-center justify-between gap-3 rounded-lg border border-vibe-danger/40 bg-vibe-danger/5 p-4 text-sm text-vibe-danger">
          <span className="flex items-center gap-2">
            <AlertCircle className="h-4 w-4 shrink-0" />
            {t('error')}
          </span>
          <button
            type="button"
            onClick={() => refetch()}
            className="inline-flex shrink-0 items-center gap-1 rounded-md border border-vibe-danger/30 bg-vibe-danger/10 px-2 py-1.5 text-xs hover:bg-vibe-danger/20"
          >
            <RotateCw className="h-3.5 w-3.5" />
            {t('retry')}
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-4xl space-y-5 px-4 py-8">
      <header>
        <h1 className="flex items-center gap-2 text-2xl font-semibold text-vibe-text">
          <Theater className="h-6 w-6 text-vibe-accent" />
          {t('title')}
        </h1>
        <p className="mt-1 text-sm text-vibe-muted">{t('subtitle')}</p>
      </header>

      <p
        data-testid="theatre-disclosure"
        className="rounded-lg border border-vibe-border bg-vibe-surface p-3 text-xs leading-relaxed text-vibe-muted"
      >
        {t('disclosure')}
      </p>

      <nav aria-label={t('scenarioListAria')} className="grid grid-cols-1 gap-2 sm:grid-cols-2">
        {scenarios.map((scenario) => {
          const active = selected?.id === scenario.id;
          return (
            <button
              key={scenario.id}
              type="button"
              aria-pressed={active}
              onClick={() => setSelectedId(scenario.id)}
              className={
                'rounded-lg border p-3 text-left transition-colors ' +
                (active
                  ? 'border-vibe-accent bg-vibe-accent/10'
                  : 'border-vibe-border bg-vibe-surface hover:border-vibe-accent/50')
              }
            >
              <p className="text-sm font-medium text-vibe-text">{scenario.title}</p>
              <p className="mt-0.5 text-xs text-vibe-muted">{scenario.subtitle}</p>
            </button>
          );
        })}
      </nav>

      {selected && (
        <section aria-label={t('traceAria')} className="space-y-4">
          <div className="rounded-lg border border-vibe-border bg-vibe-surface p-4">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <h2 className="text-lg font-semibold text-vibe-text">{selected.title}</h2>
              <button
                type="button"
                onClick={() => setRevealed(0)}
                className="inline-flex items-center gap-1 rounded-md border border-vibe-border bg-vibe-card px-2 py-1.5 text-xs text-vibe-muted hover:border-vibe-accent/50 hover:text-vibe-text"
              >
                <RotateCw className="h-3.5 w-3.5" />
                {t('replay')}
              </button>
            </div>
            <p className="mt-1 text-sm text-vibe-muted">{selected.lesson}</p>

            <div className="mt-3 flex flex-wrap gap-2 text-xs">
              <span className="rounded-md border border-vibe-border bg-vibe-card px-2 py-1 text-vibe-muted">
                {t('meta.attempts')}: {selected.modelAttempts}
              </span>
              <span className="rounded-md border border-vibe-border bg-vibe-card px-2 py-1 text-vibe-muted">
                {t('meta.duration')}: {formatDuration(selected.durationMs)}
              </span>
              {selected.failClosed ? (
                <span
                  data-testid="fail-closed-chip"
                  className="rounded-md border border-vibe-danger/40 bg-vibe-danger/10 px-2 py-1 text-vibe-danger"
                >
                  {t('meta.failClosed')}
                </span>
              ) : (
                selected.score != null && (
                  <span className="rounded-md border border-vibe-border bg-vibe-card px-2 py-1 text-vibe-muted">
                    {t('meta.score')}: {selected.score}/10 · {selected.severity}
                  </span>
                )
              )}
              <a
                href={adrUrl(selected.adr)}
                target="_blank"
                rel="noreferrer"
                className="inline-flex items-center gap-1 rounded-md border border-vibe-border bg-vibe-card px-2 py-1 text-vibe-accent hover:border-vibe-accent/50"
              >
                <ExternalLink className="h-3 w-3" />
                {t('meta.adr')}
              </a>
            </div>
          </div>

          <ol className="space-y-2">
            {selected.steps.map((step, index) => {
              const isRevealed = index < revealed;
              const outcomeClass = OUTCOME_STYLES[step.outcome] ?? 'text-vibe-muted';
              return (
                <li
                  key={step.id + '-' + index}
                  data-revealed={isRevealed}
                  className={
                    'rounded-lg border border-vibe-border bg-vibe-surface p-3 transition-opacity ' +
                    (isRevealed ? 'opacity-100' : 'opacity-40')
                  }
                >
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="text-xs tabular-nums text-vibe-faint">
                      {String(index + 1).padStart(2, '0')}
                    </span>
                    <ModeBadge mode={step.mode} />
                    <span className="text-sm font-medium text-vibe-text">{step.title}</span>
                    <span className={'ml-auto text-xs ' + outcomeClass}>{t('outcome.' + step.outcome.toLowerCase())}</span>
                  </div>
                  <p className="mt-1.5 text-xs leading-relaxed text-vibe-muted">{step.detail}</p>
                  {step.payload && (
                    <details className="mt-2">
                      <summary className="cursor-pointer text-xs text-vibe-accent hover:underline">
                        {t('showPayload')}
      </summary>
                      <pre className="mt-1 max-h-48 overflow-auto rounded-md border border-vibe-border bg-vibe-bg p-2 text-[11px] leading-snug text-vibe-text">
                        {step.payload}
                      </pre>
                    </details>
                  )}
                </li>
              );
            })}
          </ol>

          {revealed < selected.steps.length && (
            <p role="status" className="text-xs text-vibe-muted">{t('replaying')}</p>
          )}
        </section>
      )}
    </div>
  );
}

export function TheatreEntry({ label, desc }: { label: string; desc: string }) {
  return (
    <Link
      to="/theatre"
      data-testid="theatre-entry"
      className="press-feedback group flex items-center justify-between gap-3 rounded-lg border border-vibe-accent/30 bg-vibe-accent/5 px-4 py-3 transition-colors hover:border-vibe-accent/50 hover:bg-vibe-accent/10"
    >
      <span className="min-w-0">
        <span className="block text-sm font-medium text-vibe-text">{label}</span>
        <span className="mt-0.5 block text-xs text-vibe-muted">{desc}</span>
      </span>
      <ArrowRight className="h-4 w-4 shrink-0 text-vibe-accent" />
    </Link>
  );
}
