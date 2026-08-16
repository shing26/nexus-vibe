import { AlertCircle, Bot, Code2, Gauge, Loader2, RotateCw, ShieldAlert, ShieldCheck, Wand2 } from 'lucide-react';
import type { FC, ReactNode } from 'react';
import { BorderBeam } from './ui/BorderBeam';
import { DecryptedText } from './ui/DecryptedText';
import type { AiReviewDetail } from '../types/post';

export type AiReviewTerminalState = 'pending' | 'loading' | 'error' | 'unavailable' | 'data';

interface AiReviewTerminalProps {
  state: AiReviewTerminalState;
  detail?: AiReviewDetail | null;
  errorMessage?: string;
  onRetry?: () => void;
}

const severityStyles: Record<string, string> = {
  low: 'text-vibe-emerald bg-vibe-emerald/10 border-vibe-emerald/30',
  medium: 'text-yellow-400 bg-yellow-400/10 border-yellow-400/30',
  high: 'text-orange-400 bg-orange-400/10 border-orange-400/30',
  critical: 'text-red-400 bg-red-400/10 border-red-400/30',
  unknown: 'text-slate-400 bg-slate-400/10 border-slate-400/30',
};

function toList(value?: string | string[] | null): string[] {
  if (!value) return [];
  if (Array.isArray(value)) {
    return value.map((item) => item.trim()).filter(Boolean);
  }
  return value
    .split(/\r?\n+/)
    .map((item) => item.trim())
    .filter(Boolean);
}

function SeverityBadge({ severity }: { severity: string }) {
  const normalized = (severity || 'unknown').trim().toLowerCase();
  const className = severityStyles[normalized] || severityStyles.unknown;
  return (
    <span className={'rounded-md border px-2 py-0.5 text-[10px] font-semibold uppercase ' + className}>
      {normalized}
    </span>
  );
}

function VerdictPill({ isApproved }: { isApproved: boolean | number }) {
  const approved = isApproved === true || isApproved === 1 || String(isApproved).toLowerCase() === 'true';
  return (
    <span
      className={
        approved
          ? 'rounded-md border border-vibe-emerald/30 bg-vibe-emerald/10 px-2 py-0.5 text-[10px] font-semibold text-vibe-emerald'
          : 'rounded-md border border-yellow-400/30 bg-yellow-400/10 px-2 py-0.5 text-[10px] font-semibold text-yellow-400'
      }
    >
      {approved ? 'Approved' : 'Needs Review'}
    </span>
  );
}

function Findings({ values }: { values?: string | string[] | null }) {
  const items = toList(values);
  if (items.length === 0) {
    return <p className="text-slate-500">// No findings.</p>;
  }
  return (
    <ul className="space-y-1">
      {items.map((item, index) => (
        <li key={index} className="leading-relaxed text-slate-300">
          {item}
        </li>
      ))}
    </ul>
  );
}

function TerminalChrome({ children }: { children: ReactNode }) {
  return (
    <section className="relative my-6 overflow-hidden rounded-lg border border-vibe-purple/40 bg-vibe-card/90 p-0.5">
      <BorderBeam size={250} duration={6} colorFrom="#06B6D4" colorTo="#A855F7" />
      <div className="rounded-md border border-vibe-border bg-vibe-bg/95 p-4 font-mono text-xs">
        {children}
      </div>
    </section>
  );
}

function LoadingTerminal() {
  return (
    <TerminalChrome>
      <div role="status" aria-live="polite" className="mb-3 flex flex-wrap items-center justify-between gap-2 border-b border-vibe-border pb-2.5">
        <span className="flex items-center gap-1.5 font-semibold text-vibe-purple">
          <Bot className="h-4 w-4" />
          <span>AI Co-Pilot Automated Review System</span>
        </span>
        <div className="h-3 w-28 animate-pulse motion-reduce:animate-none rounded bg-vibe-card" />
      </div>
      <div className="mb-3 flex items-center gap-3">
        <div className="h-12 w-16 animate-pulse motion-reduce:animate-none rounded-lg bg-vibe-card" />
        <div className="h-1.5 flex-1 animate-pulse motion-reduce:animate-none rounded-full bg-vibe-card" />
      </div>
      <div className="space-y-2">
        {[0, 1, 2].map((section) => (
          <div key={section} className="rounded-md border border-vibe-border bg-vibe-surface/80 p-3">
            <div className="mb-2 h-3 w-32 animate-pulse motion-reduce:animate-none rounded bg-vibe-card" />
            <div className="h-3 animate-pulse motion-reduce:animate-none rounded bg-vibe-card/70" />
          </div>
        ))}
      </div>
    </TerminalChrome>
  );
}

export const AiReviewTerminal: FC<AiReviewTerminalProps> = ({ state, detail, errorMessage, onRetry }) => {
  if (state === 'unavailable' || (state === 'data' && !detail) || detail?.severity === 'unavailable') {
    return (
      <div role="status" className="flex items-center gap-2 p-3 text-slate-500">
        <AlertCircle className="h-3.5 w-3.5" />
        AI review data unavailable.
      </div>
    );
  }

  if (state === 'pending') {
    return (
      <div role="status" aria-live="polite" className="flex items-center gap-2 p-3 text-vibe-neon">
        <Loader2 className="h-3.5 w-3.5 animate-spin motion-reduce:animate-none" />
        AI Agent reviewing...
      </div>
    );
  }

  if (state === 'error') {
    return (
      <div role="alert" className="rounded-lg border border-red-500/40 bg-red-950/40 p-4 font-mono text-xs text-red-400">
        <div className="flex items-center justify-between gap-3">
          <span className="flex min-w-0 items-center gap-2">
            <AlertCircle className="h-3.5 w-3.5 shrink-0" />
            {errorMessage || 'Failed to load AI review.'}
          </span>
          {onRetry && (
            <button
              type="button"
              onClick={onRetry}
              aria-label="Retry AI review"
              className="inline-flex shrink-0 items-center rounded-md border border-red-500/30 bg-red-500/10 p-1.5 text-red-400 hover:bg-red-500/20 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-vibe-neon"
            >
              <RotateCw className="h-3.5 w-3.5" />
            </button>
          )}
        </div>
      </div>
    );
  }

  if (state === 'loading') {
    return <LoadingTerminal />;
  }

  const score = detail?.score ?? null;
  const displayScore = score == null ? null : score <= 10 ? Math.round(score * 10) : Math.round(score);
  const normalizedScore = displayScore ?? 0;
  const securityItems = toList(detail?.securityConcerns);
  const securityClear = securityItems.length === 0;
  const sections = [
    {
      key: 'code',
      icon: Code2,
      color: 'text-vibe-neon',
      title: 'Code Quality',
      value: detail?.codeQuality,
    },
    {
      key: 'security',
      icon: securityClear ? ShieldCheck : ShieldAlert,
      color: securityClear ? 'text-vibe-emerald' : 'text-red-400',
      title: 'Security Concerns',
      value: detail?.securityConcerns,
    },
    {
      key: 'suggestions',
      icon: Wand2,
      color: 'text-vibe-purple',
      title: 'Optimization Suggestions',
      value: detail?.optimizationSuggestions,
    },
  ];

  return (
    <TerminalChrome>
      <div className="mb-3 flex flex-wrap items-center justify-between gap-2 border-b border-vibe-border pb-2.5">
        <span className="flex items-center gap-1.5 font-semibold text-vibe-purple">
          <Bot className="h-4 w-4" />
          <DecryptedText text="AI Co-Pilot Automated Review System" speed={30} />
        </span>
        <span className="flex flex-wrap items-center gap-2">
          <span className="flex items-center gap-1 text-vibe-neon tabular-nums">
            <Gauge className="h-3.5 w-3.5" />
            Score: {displayScore ?? '--'}/100
          </span>
          <SeverityBadge severity={detail?.severity || 'unknown'} />
          <VerdictPill isApproved={detail?.isApproved ?? 0} />
        </span>
      </div>

      <div className="mb-3 flex items-center gap-3">
        <div className="flex h-12 w-16 shrink-0 items-baseline justify-center rounded-lg border border-vibe-neon/30 bg-vibe-surface">
          <span className="text-xl font-semibold text-vibe-neon tabular-nums">{displayScore ?? '--'}</span>
          <span className="text-[10px] text-slate-500">/100</span>
        </div>
        <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-vibe-border">
          <div
            className="h-full rounded-full bg-gradient-to-r from-vibe-emerald to-vibe-neon"
            style={{ width: `${Math.max(0, Math.min(100, normalizedScore))}%` }}
          />
        </div>
      </div>

      <div className="space-y-2">
        {sections.map((section) => {
          const Icon = section.icon;
          return (
            <section key={section.key} className="rounded-md border border-vibe-border bg-vibe-surface/80 p-3">
              <h4 className={'mb-1.5 flex items-center gap-1.5 text-[11px] font-semibold ' + section.color}>
                <Icon className="h-3.5 w-3.5" />
                [{section.title}]
              </h4>
              <Findings values={section.value} />
            </section>
          );
        })}
      </div>
    </TerminalChrome>
  );
};
