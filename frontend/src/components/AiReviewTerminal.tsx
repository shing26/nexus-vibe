import { AlertCircle, Bot, Code2, ChevronDown, ExternalLink, Gauge, Loader2, RotateCw, ShieldAlert, ShieldCheck, Wand2 } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import type { FC } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useReducedMotion } from 'motion/react';
import { ReviewStepStrip } from './ui/ReviewStepStrip';
import { REVIEW_STEPS } from './ui/reviewSteps';
import type { AiReviewDetail } from '../types/post';

export type AiReviewTerminalState = 'pending' | 'loading' | 'error' | 'unavailable' | 'data' | 'failed';

interface AiReviewTerminalProps {
  state: AiReviewTerminalState;
  detail?: AiReviewDetail | null;
  errorMessage?: string;
  onRetry?: () => void;
  /** Review-log rows recorded for this post, when the caller has them. */
  reviewCount?: number;
}

/**
 * The severity→tone map of the v2 system: low is calm, critical is loud, and
 * `high` carries its own token (v2 retires the borrowed orange).
 */
const severityStyles: Record<string, string> = {
  low: 'text-vibe-success bg-vibe-success/10 border-vibe-success/30',
  medium: 'text-vibe-warning bg-vibe-warning/10 border-vibe-warning/30',
  high: 'text-vibe-high bg-vibe-high/10 border-vibe-high/30',
  critical: 'text-vibe-danger bg-vibe-danger/10 border-vibe-danger/30',
  unknown: 'text-vibe-muted bg-vibe-muted/10 border-vibe-border',
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

// The three verdict sections travel in shared JSON keys, but what they mean
// depends on who reviewed: the prompt reviewer reads codeQuality as structure
// and clarity, not code. Unknown reviewers fall back to the code headings.
const REVIEWER_SECTION_TITLES: Record<string, string[]> = {
  'prompt-review-agent': ['prompt:codeQuality', 'prompt:securityConcerns', 'prompt:optimizationSuggestions'],
};
const DEFAULT_SECTION_TITLES = ['codeQuality', 'securityConcerns', 'optimizationSuggestions'];

// The names are shared with the agent-logs surface through the same
// `agentLogs:severityNames` block: one dictionary per severity, not one per panel.
function SeverityBadge({ severity }: { severity: string }) {
  const { t } = useTranslation('agentLogs');
  const normalized = (severity || 'unknown').trim().toLowerCase();
  const className = severityStyles[normalized] || severityStyles.unknown;
  return (
    <span className={'rounded-full border px-2 py-0.5 text-xs font-medium ' + className}>
      {t('severityNames.' + normalized, { defaultValue: normalized })}
    </span>
  );
}

function VerdictPill({ isApproved }: { isApproved: boolean | number }) {
  const { t } = useTranslation('post');
  const approved = isApproved === true || isApproved === 1 || String(isApproved).toLowerCase() === 'true';
  return (
    <span
      className={
        approved
          ? 'rounded-full border border-vibe-success/40 bg-vibe-success/10 px-2 py-0.5 text-xs font-medium text-vibe-success'
          : 'rounded-full border border-vibe-warning/40 bg-vibe-warning/10 px-2 py-0.5 text-xs font-medium text-vibe-warning'
      }
    >
      {approved ? t('review.approved') : t('review.needsReview')}
    </span>
  );
}

function Findings({ values }: { values?: string | string[] | null }) {
  const { t } = useTranslation('post');
  const items = toList(values);
  if (items.length === 0) {
    return <p className="text-vibe-muted">{t('review.noFindings')}</p>;
  }
  return (
    <ul className="space-y-1">
      {items.map((item, index) => (
        <li key={index} className="leading-relaxed text-vibe-text">
          {item}
        </li>
      ))}
    </ul>
  );
}

/**
 * The quiet check-run card of v2: a plain bordered surface, no beam, no
 * terminal chrome. The header is always visible; the findings and the step
 * strip live inside the collapsible region below it.
 */
function ReviewCard({ children }: { children: React.ReactNode }) {
  return (
    <section className="my-6 rounded-lg border border-vibe-border bg-vibe-surface">
      {children}
    </section>
  );
}

/** Pacing of the replay. Four steps at 500ms, then the score counts itself in over ~600ms. */
const REPLAY_STEP_MS = 500;
const SCORE_COUNT_TICKS = 12;
const SCORE_COUNT_STEP_MS = 50;

function formatRunTime(value?: string): string {
  if (!value) return '';
  return value.replace('T', ' ').slice(0, 16);
}

/**
 * The review's own wall-clock time, written into `result_json.durationMs` by
 * `AiReviewService`. Rows recorded before that instrument existed have no
 * value, and the voucher simply omits the field rather than guessing.
 */
function formatDuration(value?: number | null): string {
  if (value == null || !Number.isFinite(value) || value < 0) return '';
  if (value < 1000) return `${Math.round(value)} ms`;
  return `${(value / 1000).toFixed(1)} s`;
}

function LoadingTerminal() {
  const { t } = useTranslation('post');
  return (
    <ReviewCard>
      <div role="status" aria-live="polite" className="flex flex-wrap items-center justify-between gap-2 border-b border-vibe-border px-4 py-3">
        <span className="flex items-center gap-1.5 text-sm font-medium text-vibe-text">
          <Bot className="h-4 w-4 text-vibe-accent" />
          <span>{t('review.title')}</span>
        </span>
        <div className="h-3 w-28 animate-pulse motion-reduce:animate-none rounded bg-vibe-card" />
      </div>
      <div className="space-y-3 p-4">
        <div className="flex items-center gap-3">
          <div className="h-12 w-16 animate-pulse motion-reduce:animate-none rounded bg-vibe-card" />
          <div className="h-1.5 flex-1 animate-pulse motion-reduce:animate-none rounded-full bg-vibe-card" />
        </div>
        {[0, 1, 2].map((section) => (
          <div key={section} className="rounded-md border border-vibe-border bg-vibe-card p-3">
            <div className="mb-2 h-3 w-32 animate-pulse motion-reduce:animate-none rounded bg-vibe-card" />
            <div className="h-3 animate-pulse motion-reduce:animate-none rounded bg-vibe-surface" />
          </div>
        ))}
      </div>
    </ReviewCard>
  );
}

export const AiReviewTerminal: FC<AiReviewTerminalProps> = ({
  state,
  detail,
  errorMessage,
  onRetry,
  reviewCount,
}) => {
  const { t } = useTranslation('post');
  const reduceMotion = useReducedMotion();
  const [expanded, setExpanded] = useState(false);
  // The replay is the reveal of a run that already finished, so it plays once —
  // on the first expand of this mount — and never on the page-load path, where
  // the panel sits collapsed and the header already tells the whole story.
  const [hasExpandedOnce, setHasExpandedOnce] = useState(false);
  const [replayCompleted, setReplayCompleted] = useState(0);
  const [countedScore, setCountedScore] = useState(0);
  const countTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const score = detail?.score ?? null;
  const finalScore = score == null ? null : score <= 10 ? Math.round(score * 10) : Math.round(score);
  const replaying = state === 'data' && !!detail && detail.severity !== 'unavailable';

  // The data state is a finished review being replayed, not a review being performed: this
  // panel renders a stored row, so the steps confirm what already happened and the header
  // names the run and its time. Anything that read as "working on it now" would be the same
  // false claim ADR-0010 removed from the landing card, one surface down.
  useEffect(() => {
    if (!replaying) return;
    if (reduceMotion) {
      setReplayCompleted(REVIEW_STEPS.length);
      setCountedScore(finalScore ?? 0);
      return;
    }
    if (!hasExpandedOnce) return;
    let step = 0;
    const stepTimer = setInterval(() => {
      step += 1;
      setReplayCompleted(step);
      if (step < REVIEW_STEPS.length) return;
      clearInterval(stepTimer);
      let tick = 0;
      countTimerRef.current = setInterval(() => {
        tick += 1;
        setCountedScore(Math.round((finalScore ?? 0) * (tick / SCORE_COUNT_TICKS)));
        if (tick >= SCORE_COUNT_TICKS) {
          if (countTimerRef.current) clearInterval(countTimerRef.current);
          countTimerRef.current = null;
        }
      }, SCORE_COUNT_STEP_MS);
    }, REPLAY_STEP_MS);
    return () => {
      clearInterval(stepTimer);
      if (countTimerRef.current) clearInterval(countTimerRef.current);
      countTimerRef.current = null;
    };
  }, [replaying, hasExpandedOnce, reduceMotion, finalScore]);

  const toggleExpanded = () => {
    setExpanded((v) => {
      if (!v) setHasExpandedOnce(true);
      return !v;
    });
  };

  if (state === 'unavailable' || (state === 'data' && !detail) || detail?.severity === 'unavailable') {
    return (
      <div role="status" className="flex items-center gap-2 p-3 text-sm text-vibe-muted">
        <AlertCircle className="h-3.5 w-3.5" />
        {t('review.unavailable')}
      </div>
    );
  }

  if (state === 'pending') {
    return (
      <div role="status" aria-live="polite" className="flex items-center gap-2 p-3 text-sm text-vibe-accent">
        <Loader2 className="h-3.5 w-3.5 animate-spin motion-reduce:animate-none" />
        {t('aiPending')}
      </div>
    );
  }

  if (state === 'error') {
    return (
      <div role="alert" className="rounded-lg border border-vibe-danger/40 bg-vibe-danger/5 p-4 text-sm text-vibe-danger">
        <div className="flex items-center justify-between gap-3">
          <span className="flex min-w-0 items-center gap-2">
            <AlertCircle className="h-3.5 w-3.5 shrink-0" />
            {errorMessage || t('review.failedLoading')}
          </span>
          {onRetry && (
            <button
              type="button"
              onClick={onRetry}
              aria-label={t('review.retryAria')}
              className="inline-flex shrink-0 items-center rounded-md border border-vibe-danger/30 bg-vibe-danger/10 p-1.5 text-vibe-danger hover:bg-vibe-danger/20 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-vibe-accent"
            >
              <RotateCw className="h-3.5 w-3.5" />
            </button>
          )}
        </div>
      </div>
    );
  }

  if (state === 'failed') {
    return (
      <div role="status" aria-live="polite" className="flex items-center gap-2 p-3 text-sm text-vibe-warning">
        <AlertCircle className="h-3.5 w-3.5 shrink-0" />
        {t('review.failed')}
      </div>
    );
  }

  if (state === 'loading') {
    return <LoadingTerminal />;
  }

  const securityItems = toList(detail?.securityConcerns);
  const securityClear = securityItems.length === 0;
  const sectionTitles =
    (detail?.reviewer && REVIEWER_SECTION_TITLES[detail.reviewer]) || DEFAULT_SECTION_TITLES;
  const sections = [
    {
      key: 'code',
      stepKey: 'codeQuality',
      icon: Code2,
      color: 'text-vibe-accent',
      titleKey: sectionTitles[0],
      value: detail?.codeQuality,
    },
    {
      key: 'security',
      stepKey: 'securityConcerns',
      icon: securityClear ? ShieldCheck : ShieldAlert,
      color: securityClear ? 'text-vibe-success' : 'text-vibe-danger',
      titleKey: sectionTitles[1],
      value: detail?.securityConcerns,
    },
    {
      key: 'suggestions',
      stepKey: 'optimizationSuggestions',
      icon: Wand2,
      color: 'text-vibe-muted',
      titleKey: sectionTitles[2],
      value: detail?.optimizationSuggestions,
    },
  ];

  const duration = formatDuration(detail?.durationMs);

  return (
    <ReviewCard>
      {/* The voucher — always visible, collapsed or not. It names the verdict and
          the machinery that produced it (model, calls, duration) and links to the
          raw log row; the findings below stay one click away. Reversed from the
          v2 spec's "collapsed by default" because the recording is the product's
          claim, not a detail behind a chevron. */}
      <div data-testid="review-voucher">
        <div className="flex flex-wrap items-center justify-between gap-2 border-b border-vibe-border px-4 py-3">
          <div className="flex min-w-0 items-center gap-1.5 text-sm font-medium text-vibe-text">
            <Bot className="h-4 w-4 shrink-0 text-vibe-accent" />
            <span>{t('review.title')}</span>
          </div>
          <span className="flex flex-wrap items-center gap-2">
            <span className="flex items-center gap-1 text-xs font-medium tabular-nums text-vibe-accent">
              <Gauge className="h-3.5 w-3.5" />
              {t('review.scoreLabel', { score: finalScore ?? '--' })}
            </span>
            <SeverityBadge severity={detail?.severity || 'unknown'} />
            <VerdictPill isApproved={detail?.isApproved ?? 0} />
            <button
              type="button"
              onClick={toggleExpanded}
              aria-expanded={expanded}
              aria-label={t('review.toggleDetails')}
              className="rounded p-1 text-vibe-muted hover:text-vibe-text focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-vibe-accent"
            >
              <ChevronDown className={'h-4 w-4 transition-transform motion-reduce:transition-none ' + (expanded ? 'rotate-180' : '')} />
            </button>
          </span>
        </div>
        <div className="flex flex-wrap items-center gap-x-4 gap-y-1 px-4 py-2 text-xs text-vibe-muted">
          {detail?.reviewer && (
            <span className="min-w-0 truncate" title={detail.reviewer}>
              {t('review.model')}: <span className="font-mono text-vibe-text">{detail.reviewer}</span>
            </span>
          )}
          {reviewCount != null && reviewCount > 0 && (
            <span className="tabular-nums">{t('review.calls', { n: reviewCount })}</span>
          )}
          {duration && <span className="tabular-nums">{t('review.duration', { value: duration })}</span>}
          {detail?.postId && (
            <Link
              to={'/agent-logs?postId=' + detail.postId}
              className="inline-flex items-center gap-1 text-vibe-accent hover:underline"
            >
              {t('review.rawLog')}
              <ExternalLink className="h-3 w-3" />
            </Link>
          )}
        </div>
      </div>

      {/* Collapsible region — mounted but hidden when collapsed, so the data a
          reader (or a test) wants is in the DOM either way and the reveal is
          purely a presentation choice. */}
      <div className={expanded ? 'space-y-3 p-4' : 'hidden space-y-3 p-4'}>
        <span className="block truncate text-xs text-vibe-faint">
          {t('review.replayCaption', {
            time: detail?.reviewedAt ? formatRunTime(detail.reviewedAt) : t('review.timeNotRecorded'),
          })}
        </span>

        <ReviewStepStrip mode="replay" completed={replayCompleted} />

        <div className="flex items-center gap-3">
          <div className="flex h-12 w-16 shrink-0 items-baseline justify-center rounded-md border border-vibe-border bg-vibe-card">
            <span className="text-xl font-semibold tabular-nums text-vibe-accent">
              {finalScore == null ? '--' : countedScore}
            </span>
            <span className="text-xs text-vibe-muted">{t('review.scoreSuffix')}</span>
          </div>
          <div
            role="meter"
            aria-label={t('review.meterAria')}
            aria-valuemin={0}
            aria-valuemax={100}
            aria-valuenow={countedScore}
            className="h-1.5 flex-1 overflow-hidden rounded-full bg-vibe-border"
          >
            <div
              className="h-full rounded-full bg-vibe-accent transition-[width] duration-700 ease-out motion-reduce:transition-none"
              // Guard the divisor: a stored score of 0 is real (0/100, meter empty) and must not
              // become NaN% the way 0/0 would.
              style={{ width: `${!finalScore ? 0 : Math.max(0, Math.min(100, (countedScore / finalScore) * 100))}%` }}
            />
          </div>
        </div>

        <div className="space-y-2">
          {sections.map((section) => {
            const Icon = section.icon;
            // Which step names this section, found by key rather than by position: the strip's
            // order is its own, and a positional link between the two lists would rot silently
            // the day either one is reordered.
            const stepIndex = REVIEW_STEPS.findIndex((step) => step.key === section.stepKey);
            // A section comes in with the step that names it, dim before that. The content is in
            // the DOM from the start - this is a reveal for the eye, not a fetch - so assistive
            // technology and tests read the finished review either way.
            const revealed = reduceMotion || replayCompleted > stepIndex;
            return (
              <section
                key={section.key}
                className={
                  'rounded-md border p-3 transition-opacity duration-500 motion-reduce:transition-none ' +
                  (revealed ? 'border-vibe-border bg-vibe-card opacity-100' : 'border-vibe-border/60 bg-vibe-card/60 opacity-40')
                }
              >
                <h4 className={'mb-1.5 flex items-center gap-1.5 text-xs font-semibold ' + section.color}>
                  <Icon className="h-3.5 w-3.5" />
                  {t('review.sections.' + section.titleKey)}
                </h4>
                <Findings values={section.value} />
              </section>
            );
          })}
        </div>
      </div>
    </ReviewCard>
  );
};
