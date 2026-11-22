import { useEffect, useState, type ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { useReducedMotion } from 'motion/react';
import {
  AlertCircle,
  ArrowRight,
  Braces,
  ExternalLink,
  FileQuestion,
  RotateCw,
  ShieldAlert,
  Theater,
  Unplug,
  Wrench,
  type LucideIcon,
} from 'lucide-react';
import { apiClient } from '../api/client';
import type { ApiResponse } from '../api/client';
import CodeBlock from '../components/CodeBlock';
import Skeleton from '../components/Skeleton';

const REPO = 'https://github.com/shing26/nexus-vibe';
const REPLAY_STEP_MS = 600;
const EMPTY_STEPS: TheatreStep[] = [];

interface TheatreStep {
  id: string;
  title: string;
  detail: string;
  mode: string;
  outcome: string;
  payload: string | null;
}

export interface TheatreCounterfactual {
  id: string;
  label: string;
  summary: string;
  modelAttempts: number;
  durationMs: string | number;
  score: number | null;
  severity: string | null;
  failClosed: boolean;
  steps: TheatreStep[];
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
  fixedCode?: string | null;
  afterScore?: number | null;
  afterSeverity?: string | null;
  afterApproved?: boolean | null;
  afterSteps?: TheatreStep[] | null;
  counterfactual?: TheatreCounterfactual | null;
}

const SCENARIO_ICONS: Record<string, LucideIcon> = {
  'fix-challenge': Wrench,
  'repaired-json': Braces,
  'empty-placeholder': FileQuestion,
  'prompt-injection': ShieldAlert,
  'model-down': Unplug,
};

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

const STAMP_STYLES: Record<string, string> = {
  approved: 'border-vibe-success/50 bg-vibe-success/10 text-vibe-success',
  attention: 'border-vibe-warning/50 bg-vibe-warning/10 text-vibe-warning',
  unpublished: 'border-vibe-danger/50 bg-vibe-danger/10 text-vibe-danger',
};

const JSON_TOKEN_RE = /("(?:\\.|[^"\\])*")(\s*:)?|\b(?:true|false|null)\b|-?\d+(?:\.\d+)?/g;

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

function highlightJson(value: string): ReactNode[] {
  const nodes: ReactNode[] = [];
  const re = new RegExp(JSON_TOKEN_RE.source, 'g');
  let last = 0;
  let index = 0;
  let match: RegExpExecArray | null;
  while ((match = re.exec(value)) !== null) {
    if (match.index > last) nodes.push(value.slice(last, match.index));
    const [token, str, colon] = match;
    if (str && colon) {
      nodes.push(
        <span key={`k${index}`} data-testid="json-highlight" className="text-vibe-accent">
          {str}
        </span>,
        colon,
      );
    } else if (str) {
      nodes.push(
        <span key={`v${index}`} data-testid="json-highlight" className="text-vibe-success">
          {str}
        </span>,
      );
    } else {
      nodes.push(
        <span key={`p${index}`} data-testid="json-highlight" className="text-vibe-warning">
          {token}
        </span>,
      );
    }
    index += 1;
    last = re.lastIndex;
  }
  if (last < value.length) nodes.push(value.slice(last));
  return nodes;
}

function stripJsonFence(payload: string): string {
  return payload
    .replace(/^```(?:json)?\s*/m, '')
    .replace(/```\s*$/m, '')
    .trim();
}

/**
 * The exhibit points at the line it is talking about. Rather than carrying line
 * numbers in the DTO (they would go stale the moment the snippet changes), the
 * page finds the line by what it does: the unguarded division, or the guard the
 * fix adds.
 */
function findLine(code: string, pattern: RegExp): number[] {
  return code
    .split('\n')
    .map((line, index) => (pattern.test(line) ? index + 1 : 0))
    .filter((line) => line > 0);
}

const DIVISION_RE = /\/\s*divisor/;
const GUARD_RE = /divisor\s*==\s*0/;
const INJECTION_RE = /Ignore previous instructions/i;

function CodePanel({
  label,
  code,
  highlight,
  testId,
}: {
  label: string;
  code: string;
  highlight: 'defect' | 'guard' | 'injection' | 'none';
  testId: string;
}) {
  if (highlight === 'none') {
    return (
      <figure data-testid={testId} className="min-w-0">
        <figcaption className="mb-1 text-xs text-vibe-muted">{label}</figcaption>
        <div className="overflow-hidden rounded-md border border-vibe-border">
          <CodeBlock language="java" code={code} />
        </div>
      </figure>
    );
  }
  const lines = findLine(
    code,
    highlight === 'guard' ? GUARD_RE : highlight === 'injection' ? INJECTION_RE : DIVISION_RE,
  );
  const badge =
    highlight === 'guard'
      ? { text: '守卫行', cls: 'border-vibe-success/40 bg-vibe-success/10 text-vibe-success' }
      : highlight === 'injection'
        ? { text: '注入行', cls: 'border-vibe-danger/40 bg-vibe-danger/10 text-vibe-danger' }
        : { text: '危险行', cls: 'border-vibe-danger/40 bg-vibe-danger/10 text-vibe-danger' };
  return (
    <figure data-testid={testId} className="min-w-0">
      <figcaption className="mb-1 flex items-center gap-2 text-xs text-vibe-muted">
        <span>{label}</span>
        <span
          data-testid={highlight === 'guard' ? 'code-guard-line' : 'code-defect-line'}
          className={'rounded border px-1.5 py-0.5 text-[10px] ' + badge.cls}
        >
          {badge.text}
        </span>
      </figcaption>
      <div className="overflow-hidden rounded-md border border-vibe-border">
        <CodeBlock language="java" code={code} highlightLines={lines} />
      </div>
    </figure>
  );
}

function PayloadPanel({ payload }: { payload: string }) {
  const stripped = stripJsonFence(payload);
  let isJson = false;
  try {
    const parsed: unknown = JSON.parse(stripped);
    isJson = parsed !== null && typeof parsed === 'object';
  } catch {
    isJson = false;
  }
  return (
    <pre
      data-testid="payload-pre"
      className="mt-1 max-h-48 overflow-auto rounded-md border border-vibe-border bg-vibe-bg p-2 text-[11px] leading-snug text-vibe-text"
    >
      {isJson ? highlightJson(stripped) : payload}
    </pre>
  );
}

function BrokenJsonPayload({ raw }: { raw: string }) {
  const match = /,(\s*\])/.exec(raw);
  return (
    <pre
      data-testid="payload-pre"
      className="mt-1 max-h-48 overflow-auto rounded-md border border-vibe-danger/40 bg-vibe-bg p-2 text-[11px] leading-snug text-vibe-text"
    >
      {match ? (
        <>
          {raw.slice(0, match.index)}
          <span
            data-testid="json-defect"
            title="trailing comma"
            className="rounded bg-vibe-danger/25 px-0.5 font-bold text-vibe-danger"
          >
            ,
          </span>
          {raw.slice(match.index + 1)}
          <span
            data-testid="json-defect"
            title="missing closing brace"
            className="ml-0.5 inline-block rounded border border-dashed border-vibe-danger px-0.5 text-vibe-danger"
          >
            {'}'}
          </span>
        </>
      ) : (
        raw
      )}
    </pre>
  );
}

function ScoreDial({ score, reduced }: { score: number; reduced: boolean }) {
  const { t } = useTranslation('theatre');
  const [display, setDisplay] = useState(reduced ? score : 0);

  useEffect(() => {
    if (reduced) {
      setDisplay(score);
      return;
    }
    setDisplay(0);
    let current = 0;
    const timer = setInterval(() => {
      current += 1;
      setDisplay(Math.min(current, score));
      if (current >= score) clearInterval(timer);
    }, Math.max(30, Math.floor(900 / Math.max(score, 1))));
    return () => clearInterval(timer);
  }, [score, reduced]);

  const radius = 52;
  const circumference = 2 * Math.PI * radius;
  const pct = Math.max(0, Math.min(1, display / 10));
  const strokeClass = score >= 5 ? 'text-vibe-success' : score >= 3 ? 'text-vibe-warning' : 'text-vibe-danger';

  return (
    <svg
      data-testid="score-dial"
      data-score={display}
      viewBox="0 0 120 120"
      role="img"
      aria-label={t('payoff.dialAria', { score: display })}
      className="h-24 w-24 shrink-0"
    >
      <circle cx="60" cy="60" r={radius} fill="none" stroke="currentColor" strokeWidth="8" className="text-vibe-border" />
      <circle
        cx="60"
        cy="60"
        r={radius}
        fill="none"
        stroke="currentColor"
        strokeWidth="8"
        strokeLinecap="round"
        strokeDasharray={`${circumference * pct} ${circumference}`}
        transform="rotate(-90 60 60)"
        className={strokeClass}
      />
      <text x="60" y="58" textAnchor="middle" className="fill-current text-2xl font-semibold text-vibe-text">
        {display}
      </text>
      <text x="60" y="76" textAnchor="middle" className="fill-current text-xs text-vibe-faint">
        /10
      </text>
    </svg>
  );
}

function VerdictPayoff({
  score,
  severity,
  stamp,
  reduced,
}: {
  score: number | null;
  severity: string | null;
  stamp: string;
  reduced: boolean;
}) {
  const { t } = useTranslation('theatre');
  return (
    <div
      data-testid="verdict-payoff"
      className="flex flex-wrap items-center gap-4 rounded-lg border border-vibe-border bg-vibe-card p-3"
    >
      {score != null && <ScoreDial score={score} reduced={reduced} />}
      <div className="space-y-2">
        {severity && (
          <span className="inline-block rounded-md border border-vibe-border bg-vibe-surface px-2 py-0.5 text-xs text-vibe-muted">
            severity: {severity}
          </span>
        )}
        <div>
          <span
            data-testid="verdict-stamp"
            data-stamp={stamp}
            className={
              'inline-block rounded-md border px-2 py-1 text-xs font-semibold tracking-wide ' +
              (STAMP_STYLES[stamp] ?? 'border-vibe-border text-vibe-muted')
            }
          >
            {t('payoff.stamp.' + stamp)}
          </span>
        </div>
      </div>
    </div>
  );
}

function stampKey(scenario: TheatreScenario, showAfter: boolean): string {
  const approved = showAfter ? scenario.afterApproved : scenario.approved;
  if (scenario.failClosed && !showAfter) return 'unpublished';
  return approved === true ? 'approved' : 'attention';
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

function StepList({
  steps,
  scenarioId,
  cfId,
  revealed,
  pulseIndex,
  compactIds,
}: {
  steps: TheatreStep[];
  scenarioId: string;
  cfId?: string;
  revealed: number;
  pulseIndex: number | null;
  compactIds?: string[];
}) {
  const { t } = useTranslation('theatre');
  const reduceMotion = useReducedMotion();
  return (
    <ol className="space-y-1.5">
      {steps.map((step, index) => {
        const isRevealed = index < revealed;
        const compact = Boolean(compactIds?.includes(step.id));
        const title = t('steps.' + step.id + '.title', { defaultValue: step.title });
        const detail = t((cfId ? 'cf.' + cfId : 'details.' + scenarioId) + '.' + step.id, {
          defaultValue: step.detail,
        });
        const outcomeClass = OUTCOME_STYLES[step.outcome] ?? 'text-vibe-muted';
        return (
          <li
            key={step.id + '-' + index}
            data-revealed={isRevealed}
            data-compact={compact}
            className={
              'rounded-lg border border-vibe-border bg-vibe-surface px-3 py-2 transition-opacity ' +
              (isRevealed ? 'opacity-100' : 'opacity-40') +
              (index === pulseIndex && !reduceMotion ? ' animate-pulse' : '') +
              (compact ? ' opacity-70' : '')
            }
          >
            <div className="flex flex-wrap items-center gap-2">
              <span className="text-xs tabular-nums text-vibe-faint">{String(index + 1).padStart(2, '0')}</span>
              <ModeBadge mode={step.mode} />
              <span className={compact ? 'text-xs font-medium text-vibe-muted' : 'text-sm font-medium text-vibe-text'}>
                {title}
              </span>
              <span className={'ml-auto text-xs ' + outcomeClass}>
                {t('outcome.' + step.outcome.toLowerCase())}
              </span>
            </div>
            <p className={'mt-1.5 leading-relaxed text-vibe-muted ' + (compact ? 'text-[11px]' : 'text-xs')}>{detail}</p>
            {step.payload && (
              <details className="mt-2">
                <summary className="cursor-pointer text-xs text-vibe-accent hover:underline">
                  {t('showPayload')}
                </summary>
                <PayloadPanel payload={step.payload} />
              </details>
            )}
          </li>
        );
      })}
    </ol>
  );
}

function CounterfactualPanel({
  scenario,
  open,
  onToggle,
}: {
  scenario: TheatreScenario;
  open: boolean;
  onToggle: () => void;
}) {
  const { t } = useTranslation('theatre');
  const cf = scenario.counterfactual;
  if (!cf) return null;
  return (
    <section aria-label={t('cfPanel.show')} className="rounded-lg border border-vibe-border bg-vibe-surface p-3">
      <button
        type="button"
        data-testid="cf-toggle"
        aria-expanded={open}
        onClick={onToggle}
        className="inline-flex items-center gap-1 rounded-md border border-vibe-border bg-vibe-card px-2 py-1.5 text-xs text-vibe-muted hover:border-vibe-accent/50 hover:text-vibe-text"
      >
        {open ? t('cfPanel.hide') : t('cfPanel.show')}
      </button>
      {open && (
        <div data-testid="cf-panel" className="mt-3 space-y-3">
          <div className="flex flex-wrap items-center gap-4">
            {cf.score != null && <ScoreDial score={cf.score} reduced />}
            <div className="min-w-0 space-y-1">
              <p className="text-sm font-medium text-vibe-text">{t('cf.' + cf.id + '.label', { defaultValue: cf.label })}</p>
              <p className="text-xs text-vibe-muted">{t('cf.' + cf.id + '.summary', { defaultValue: cf.summary })}</p>
              <p className="text-xs text-vibe-faint">
                {t('cfPanel.attempts')}: {cf.modelAttempts} · {t('cfPanel.score')}: {cf.score ?? '—'}
                {cf.severity ? ` · ${cf.severity}` : ''}
              </p>
            </div>
          </div>
          <StepList steps={cf.steps} scenarioId={scenario.id} cfId={cf.id} revealed={cf.steps.length} pulseIndex={null} />
        </div>
      )}
    </section>
  );
}

export default function TheatrePage() {
  const { t } = useTranslation('theatre');
  const reduceMotion = useReducedMotion();
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [revealed, setRevealed] = useState(0);
  const [fixedApplied, setFixedApplied] = useState(false);
  const [cfOpen, setCfOpen] = useState(true);

  const { data, isLoading, isError, refetch } = useQuery<TheatreScenario[]>({
    queryKey: ['demo', 'chaos-scenarios'],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<TheatreScenario[]>>('/demo/chaos-scenarios');
      return res.data.data ?? [];
    },
    staleTime: 1000 * 60 * 10,
  });

  const scenarios = data ?? [];
  const selected = scenarios.find((scenario) => scenario.id === selectedId) ?? scenarios[0] ?? null;
  const selectedKey = selected ? selected.id : '';
  const activeSteps = selected
    ? fixedApplied && selected.afterSteps
      ? selected.afterSteps
      : selected.steps
    : EMPTY_STEPS;

  useEffect(() => {
    setFixedApplied(false);
    setCfOpen(true);
  }, [selectedKey]);

  useEffect(() => {
    const total = activeSteps.length;
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
  }, [activeSteps, reduceMotion]);

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

  const showAfter = Boolean(selected?.fixedCode) && fixedApplied;
  const payoffScore = selected ? (showAfter ? selected.afterScore ?? null : selected.score) : null;
  const payoffSeverity = selected ? (showAfter ? selected.afterSeverity ?? null : selected.severity) : null;
  const stamp = selected ? stampKey(selected, showAfter) : 'attention';
  const pulseIndex = revealed > 0 && revealed < activeSteps.length ? revealed - 1 : null;

  return (
    <div data-testid="theatre-root" className="mx-auto max-w-[1400px] space-y-4 px-4 py-6 sm:px-6 lg:px-8">
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-[minmax(0,1.7fr)_minmax(0,1fr)] lg:items-start">
        <div>
          <header>
            <h1 className="flex items-center gap-2 text-2xl font-semibold text-vibe-text">
              <Theater className="h-6 w-6 text-vibe-accent" />
              {t('title')}
            </h1>
            <p className="mt-1 text-sm text-vibe-muted">{t('subtitle')}</p>
          </header>

          <p
            data-testid="theatre-thesis"
            className="mt-3 border-l-2 border-vibe-accent pl-3 text-sm leading-relaxed text-vibe-text"
          >
            {t('thesis')}
          </p>
        </div>

        <div>
          <p
            data-testid="theatre-disclosure"
            className="rounded-lg border border-vibe-border bg-vibe-surface p-2.5 text-xs leading-relaxed text-vibe-muted"
          >
            {t('disclosure')}
          </p>
          <div data-testid="theatre-stats" className="mt-2 grid grid-cols-3 gap-2">
            {[
              { value: scenarios.length || '—', label: t('stats.cases'), accent: false },
              { value: 3, label: t('stats.tags'), accent: false },
              { value: 0, label: t('stats.writes'), accent: true },
            ].map((stat) => (
              <div
                key={stat.label}
                className="rounded-md border border-vibe-border bg-vibe-surface px-2 py-1.5"
              >
                <div
                  className={
                    'text-base font-semibold leading-tight tabular-nums ' +
                    (stat.accent ? 'text-vibe-accent' : 'text-vibe-text')
                  }
                >
                  {stat.value}
                </div>
                <div className="text-[10px] leading-tight text-vibe-faint">{stat.label}</div>
              </div>
            ))}
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 gap-5 lg:grid-cols-[260px_minmax(0,1fr)] lg:items-start xl:grid-cols-[304px_minmax(0,1fr)]">
        <div data-testid="case-rail" className="min-w-0 lg:sticky lg:top-16">
          <p className="mb-2 hidden text-[11px] tracking-wide text-vibe-faint lg:block">{t('index')}</p>
          <nav
            aria-label={t('scenarioListAria')}
            className="grid grid-cols-1 gap-2 sm:grid-cols-2 lg:flex lg:flex-col lg:gap-1.5"
          >
        {scenarios.map((scenario) => {
          const active = selected?.id === scenario.id;
          const Icon = SCENARIO_ICONS[scenario.id] ?? Theater;
          return (
            <button
              key={scenario.id}
              type="button"
              aria-pressed={active}
              onClick={() => setSelectedId(scenario.id)}
              className={
                'rounded-lg border p-2.5 text-left transition-colors ' +
                (active
                  ? 'border-vibe-accent bg-vibe-accent/10'
                  : 'border-vibe-border bg-vibe-surface hover:border-vibe-accent/50')
              }
            >
              <span className="flex items-center gap-2 text-sm font-medium text-vibe-text">
                <Icon className="h-4 w-4 shrink-0 text-vibe-accent" aria-hidden="true" />
                {t('cards.' + scenario.id + '.title', { defaultValue: scenario.title })}
              </span>
              <span className="mt-0.5 block text-xs text-vibe-muted">
                {t('cards.' + scenario.id + '.subtitle', { defaultValue: scenario.subtitle })}
              </span>
              <span className="mt-2 grid grid-cols-1 gap-1.5 border-t border-vibe-border pt-2 text-[11px] leading-snug">
                {(['fault', 'defense', 'verdict'] as const).map((axis) => (
                  <span key={axis} data-testid={'card-' + axis} className="flex min-w-0 items-baseline gap-2">
                    <span className="w-8 shrink-0 text-[10px] uppercase tracking-wide text-vibe-faint">
                      {t('axis.' + axis)}
                    </span>
                    <span className="block text-vibe-muted">
                      {t('cards.' + scenario.id + '.' + axis, { defaultValue: '' })}
                    </span>
                  </span>
                ))}
              </span>
              <span data-testid="card-meta" className="mt-1.5 block text-[10px] tabular-nums text-vibe-faint">
                {t('meta.attempts')}: {scenario.modelAttempts} · {t('meta.duration')}: {formatDuration(scenario.durationMs)}
              </span>
            </button>
          );
        })}
          </nav>
          <p className="mt-1.5 hidden text-[9px] leading-tight text-vibe-faint lg:block">{t('railSource')}</p>
        </div>

        {selected && (
        <section aria-label={t('traceAria')} className="min-w-0 space-y-3">
          <div className="rounded-lg border border-vibe-border bg-vibe-surface p-3.5">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <h2 className="text-lg font-semibold text-vibe-text">
                {t('cards.' + selected.id + '.title', { defaultValue: selected.title })}
              </h2>
              <button
                type="button"
                onClick={() => setRevealed(0)}
                className="inline-flex items-center gap-1 rounded-md border border-vibe-border bg-vibe-card px-2 py-1.5 text-xs text-vibe-muted hover:border-vibe-accent/50 hover:text-vibe-text"
              >
                <RotateCw className="h-3.5 w-3.5" />
                {t('replay')}
              </button>
            </div>
            <p className="mt-1 text-sm text-vibe-muted">
              {t('lessons.' + selected.id, { defaultValue: selected.lesson })}
            </p>

            <div className="mt-3 flex flex-wrap gap-2 text-xs">
              <span className="rounded-md border border-vibe-border bg-vibe-card px-2 py-1 text-vibe-muted">
                {t('meta.attempts')}: {selected.modelAttempts}
              </span>
              <span className="rounded-md border border-vibe-border bg-vibe-card px-2 py-1 text-vibe-muted">
                {t('meta.duration')}: {formatDuration(selected.durationMs)}
                {selected.fixedCode ? ' · ' + t('meta.durationBothRuns') : ''}
              </span>
              {selected.failClosed && !showAfter ? (
                <span
                  data-testid="fail-closed-chip"
                  className="rounded-md border border-vibe-danger/40 bg-vibe-danger/10 px-2 py-1 text-vibe-danger"
                >
                  {t('meta.failClosed')}
                </span>
              ) : (
                payoffScore != null && (
                  <span className="rounded-md border border-vibe-border bg-vibe-card px-2 py-1 text-vibe-muted">
                    {t('meta.score')}: {payoffScore}/10{payoffSeverity ? ` · ${payoffSeverity}` : ''}
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

            {selected.fixedCode ? (
              <div className="mt-3 grid gap-3 sm:grid-cols-2">
                <CodePanel label={t('code.before')} code={selected.sampleCode} highlight="defect" testId="code-before" />
                <CodePanel label={t('code.after')} code={selected.fixedCode} highlight="guard" testId="code-after" />
              </div>
            ) : (
              <div className="mt-3">
                <CodePanel
                  label={t('code.reviewed')}
                  code={selected.sampleCode}
                  highlight={
                    selected.id === 'prompt-injection'
                      ? 'injection'
                      : selected.id === 'fix-challenge'
                        ? 'defect'
                        : 'none'
                  }
                  testId="code-reviewed"
                />
              </div>
            )}

            <div
              data-testid="verdict-row"
              className={
                'mt-3 flex flex-col gap-2 ' +
                (selected.fixedCode ? 'sm:flex-row sm:items-stretch' : '')
              }
            >
              <div className={selected.fixedCode ? 'sm:shrink-0' : 'min-w-0 flex-1'}>
                <VerdictPayoff score={payoffScore} severity={payoffSeverity} stamp={stamp} reduced={Boolean(reduceMotion)} />
              </div>

              {Boolean(selected.fixedCode) && (
                <div
                  data-testid="fix-panel"
                  className="flex-1 rounded-lg border border-vibe-accent/30 bg-vibe-accent/5 p-3"
                >
                  <div className="flex flex-wrap items-center gap-2">
                    <button
                      type="button"
                      data-testid="apply-fix"
                      aria-pressed={fixedApplied}
                      onClick={() => setFixedApplied((value) => !value)}
                      className="inline-flex items-center gap-1 rounded-md border border-vibe-accent/50 bg-vibe-accent/10 px-2 py-1.5 text-xs font-medium text-vibe-accent hover:bg-vibe-accent/20"
                    >
                      <Wrench className="h-3.5 w-3.5" />
                      {fixedApplied ? t('fix.revert') : t('fix.apply')}
                    </button>
                    <span data-testid="fix-state" className="text-xs text-vibe-muted">
                      {fixedApplied ? t('fix.after') : t('fix.before')}: {payoffScore ?? '—'}/10
                    </span>
                  </div>
                  <p className="mt-1.5 text-xs leading-snug text-vibe-muted">{t('fix.applyHint')}</p>
                </div>
              )}
            </div>

            {selected.id === 'repaired-json' && !showAfter && selected.repairedJson && (
              <div data-testid="json-compare" className="mt-3 grid gap-3 sm:grid-cols-2">
                <figure className="min-w-0">
                  <figcaption className="text-xs text-vibe-muted">{t('payoff.compare.broken')}</figcaption>
                  <BrokenJsonPayload raw={selected.rawOutput} />
                </figure>
                <figure className="min-w-0">
                  <figcaption className="text-xs text-vibe-muted">{t('payoff.compare.repaired')}</figcaption>
                  <PayloadPanel payload={selected.repairedJson} />
                </figure>
              </div>
            )}
          </div>

          {!selected.fixedCode ? (
            <div data-testid="trace-pair" className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <div data-testid="trace-actual" className="min-w-0 space-y-3">
                <h3 className="text-xs font-medium uppercase tracking-wide text-vibe-faint">{t('pair.actual')}</h3>
                <div className="h-1 w-full overflow-hidden rounded-full bg-vibe-border">
                  <div
                    data-testid="replay-progress"
                    data-progress={revealed}
                    className="h-full bg-vibe-accent transition-[width] duration-300"
                    style={{ width: `${activeSteps.length ? (revealed / activeSteps.length) * 100 : 0}%` }}
                  />
                </div>
                <StepList
                  steps={activeSteps}
                  scenarioId={selected.id}
                  revealed={revealed}
                  pulseIndex={pulseIndex}
                  compactIds={['input', 'prompt']}
                />
              </div>
              <div data-testid="trace-counterfactual" className="min-w-0 space-y-3">
                <h3 className="text-xs font-medium uppercase tracking-wide text-vibe-faint">{t('pair.counterfactual')}</h3>
                <CounterfactualPanel
                  scenario={selected}
                  open={cfOpen}
                  onToggle={() => setCfOpen((value) => !value)}
                />
              </div>
            </div>
          ) : (
            <>
              <div className="h-1 w-full overflow-hidden rounded-full bg-vibe-border">
                <div
                  data-testid="replay-progress"
                  data-progress={revealed}
                  className="h-full bg-vibe-accent transition-[width] duration-300"
                  style={{ width: `${activeSteps.length ? (revealed / activeSteps.length) * 100 : 0}%` }}
                />
              </div>
              <StepList
                steps={activeSteps}
                scenarioId={selected.id}
                revealed={revealed}
                pulseIndex={pulseIndex}
                compactIds={['input', 'prompt']}
              />
            </>
          )}

          {revealed < activeSteps.length && (
            <p role="status" className="text-xs text-vibe-muted">
              {t('replaying')}
            </p>
          )}
        </section>
      )}
      </div>
    </div>
  );
}

export function TheatreEntry({ label, desc }: { label: string; desc: string }) {
  return (
    <Link
      to="/theatre"
      data-testid="theatre-entry"
      className="press-feedback group flex items-center justify-between gap-3 rounded-lg border border-vibe-border bg-vibe-surface px-4 py-3 transition-colors hover:bg-vibe-card"
    >
      <span className="min-w-0">
        <span className="block text-sm font-medium text-vibe-text">{label}</span>
        <span className="mt-0.5 block text-xs text-vibe-muted">{desc}</span>
      </span>
      <ArrowRight className="h-4 w-4 shrink-0 text-vibe-faint transition-colors group-hover:text-vibe-text" />
    </Link>
  );
}
