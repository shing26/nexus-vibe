import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { normalizeReviewScore } from '../lib/score';

/**
 * One point in the S4-1 evidence index (`GET /agent-logs/user/{userId}`).
 * Longs leave the server as strings (JacksonConfig), timestamps as local ISO
 * strings, and a review that produced no score leaves `score` null.
 */
export interface UserReviewEntry {
  logId: string;
  postId: string;
  postTitle: string;
  score: number | null;
  severity: string | null;
  isApproved: boolean | null;
  reviewedAt: string;
  durationMs?: number | null;
}

interface ReviewScoreCurveProps {
  entries: UserReviewEntry[];
}

interface CurvePoint {
  entry: UserReviewEntry;
  score: number;
  x: number;
  y: number;
}

const WIDTH = 600;
const HEIGHT = 200;
const PAD = { top: 20, right: 16, bottom: 30, left: 36 };
const PLOT_WIDTH = WIDTH - PAD.left - PAD.right;
const PLOT_HEIGHT = HEIGHT - PAD.top - PAD.bottom;
const Y_TICKS = [0, 50, 100];
const POINT_RADIUS = 5;
const TOOLTIP_WIDTH = 190;
const TOOLTIP_HEIGHT = 58;

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value));
}

function formatReviewDate(value: string, locale: string | undefined): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleDateString(locale, { year: 'numeric', month: 'short', day: 'numeric' });
}

function truncateTitle(title: string): string {
  return title.length > 32 ? title.slice(0, 31) + '…' : title;
}

/**
 * A hand-rolled SVG curve of the latest AI review per post. Deliberately no
 * chart library: the whole surface is one polyline and one circle per real
 * record. The screen-reader equivalent is the table below the graphic, because
 * a role="img" alone cannot carry per-point detail.
 */
export default function ReviewScoreCurve({ entries }: ReviewScoreCurveProps) {
  const { t, i18n } = useTranslation('profile');
  const [activeIndex, setActiveIndex] = useState<number | null>(null);
  const locale = i18n.resolvedLanguage ?? i18n.language;

  const points = useMemo<CurvePoint[]>(() => {
    // Oldest on the left, newest on the right. The endpoint returns newest
    // first, so this sort is what makes the curve read left-to-right in time.
    const sorted = entries
      .map((entry) => ({ entry, time: new Date(entry.reviewedAt).getTime() }))
      .filter(
        ({ entry, time }) =>
          Number.isFinite(time) &&
          entry.score != null &&
          Number.isFinite(entry.score),
      )
      .sort((a, b) => a.time - b.time);

    if (sorted.length === 0) return [];

    const firstTime = sorted[0].time;
    const lastTime = sorted[sorted.length - 1].time;
    const timeSpan = lastTime - firstTime;

    return sorted.map(({ entry, time }) => {
      const score = clamp(normalizeReviewScore(entry.score as number), 0, 100);
      const ratio = timeSpan === 0 ? 0.5 : (time - firstTime) / timeSpan;
      return {
        entry,
        score,
        x: PAD.left + ratio * PLOT_WIDTH,
        y: PAD.top + (1 - score / 100) * PLOT_HEIGHT,
      };
    });
  }, [entries]);

  if (points.length === 0) {
    return (
      <p className="rounded-lg border border-vibe-border bg-vibe-surface p-4 text-sm text-vibe-muted">
        {t('curve.empty')}
      </p>
    );
  }

  const scores = points.map((point) => point.score);
  const minScore = Math.min(...scores);
  const maxScore = Math.max(...scores);
  const active = activeIndex == null ? null : points[activeIndex];
  const linePoints = points.map((point) => `${point.x},${point.y}`).join(' ');
  const firstDate = formatReviewDate(points[0].entry.reviewedAt, locale);
  const lastDate = formatReviewDate(points[points.length - 1].entry.reviewedAt, locale);

  const tooltipX = active ? clamp(active.x - TOOLTIP_WIDTH / 2, 2, WIDTH - TOOLTIP_WIDTH - 2) : 0;
  const tooltipY = active
    ? active.y - TOOLTIP_HEIGHT - 10 < 2
      ? active.y + 14
      : active.y - TOOLTIP_HEIGHT - 10
    : 0;

  return (
    <div className="rounded-lg border border-vibe-border bg-vibe-surface p-3" data-testid="review-score-curve">
      <svg
        viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
        role="img"
        aria-label={t('curve.aria', { n: points.length, min: minScore, max: maxScore })}
        className="h-auto w-full"
      >
        {Y_TICKS.map((tick) => {
          const y = PAD.top + (1 - tick / 100) * PLOT_HEIGHT;
          return (
            <g key={tick}>
              <line
                x1={PAD.left}
                x2={WIDTH - PAD.right}
                y1={y}
                y2={y}
                className="stroke-vibe-border opacity-60"
                strokeWidth={1}
              />
              <text
                x={PAD.left - 6}
                y={y + 3}
                textAnchor="end"
                className="hidden fill-vibe-muted text-[10px] tabular-nums sm:block"
              >
                {tick}
              </text>
            </g>
          );
        })}

        <line
          x1={PAD.left}
          x2={WIDTH - PAD.right}
          y1={HEIGHT - PAD.bottom}
          y2={HEIGHT - PAD.bottom}
          className="stroke-vibe-border"
          strokeWidth={1}
        />

        <polyline points={linePoints} fill="none" className="stroke-vibe-border" strokeWidth={2} />

        {points.map((point, index) => {
          const label = t('curve.pointAria', {
            title: point.entry.postTitle,
            date: formatReviewDate(point.entry.reviewedAt, locale),
            score: point.score,
          });
          return (
            <g key={point.entry.logId}>
              <circle
                cx={point.x}
                cy={point.y}
                r={POINT_RADIUS}
                data-curve-point
                tabIndex={0}
                aria-label={label}
                className="fill-vibe-accent stroke-vibe-surface stroke-2 focus-visible:stroke-vibe-text focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-vibe-accent"
                onMouseEnter={() => setActiveIndex(index)}
                onMouseLeave={() => setActiveIndex(null)}
                onFocus={() => setActiveIndex(index)}
                onBlur={() => setActiveIndex(null)}
              />
              {/* A larger transparent target makes the point reachable by touch without
                  enlarging the visible mark. */}
              <circle
                cx={point.x}
                cy={point.y}
                r={14}
                fill="transparent"
                aria-hidden="true"
                onMouseEnter={() => setActiveIndex(index)}
                onMouseLeave={() => setActiveIndex(null)}
              />
            </g>
          );
        })}

        <text x={PAD.left} y={HEIGHT - 10} className="hidden fill-vibe-muted text-[10px] sm:block">
          {firstDate}
        </text>
        {points.length > 1 && (
          <text
            x={WIDTH - PAD.right}
            y={HEIGHT - 10}
            textAnchor="end"
            className="hidden fill-vibe-muted text-[10px] sm:block"
          >
            {lastDate}
          </text>
        )}

        {active && (
          <g aria-hidden="true" pointerEvents="none">
            <rect
              x={tooltipX}
              y={tooltipY}
              width={TOOLTIP_WIDTH}
              height={TOOLTIP_HEIGHT}
              rx={6}
              className="fill-vibe-card stroke-vibe-border"
              strokeWidth={1}
            />
            <text x={tooltipX + 10} y={tooltipY + 18} className="fill-vibe-text text-[11px] font-medium">
              {truncateTitle(active.entry.postTitle)}
            </text>
            <text x={tooltipX + 10} y={tooltipY + 35} className="fill-vibe-text text-[11px] tabular-nums">
              {t('curve.tooltipScore', { score: active.score })}
            </text>
            <text x={tooltipX + 10} y={tooltipY + 51} className="fill-vibe-muted text-[10px]">
              {formatReviewDate(active.entry.reviewedAt, locale)}
            </text>
          </g>
        )}
      </svg>

      <p className="mt-1 text-xs text-vibe-muted">{t('curve.caption', { n: points.length })}</p>

      <table className="sr-only">
        <caption>{t('curve.tableCaption')}</caption>
        <thead>
          <tr>
            <th scope="col">{t('curve.columns.date')}</th>
            <th scope="col">{t('curve.columns.score')}</th>
            <th scope="col">{t('curve.columns.title')}</th>
          </tr>
        </thead>
        <tbody>
          {points.map((point) => (
            <tr key={point.entry.logId}>
              <td>{formatReviewDate(point.entry.reviewedAt, locale)}</td>
              <td>{point.score}</td>
              <td>{point.entry.postTitle}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
