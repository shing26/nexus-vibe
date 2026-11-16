import { afterEach, describe, expect, it } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import i18next from 'i18next';
import ReviewScoreCurve from './ReviewScoreCurve';
import type { UserReviewEntry } from './ReviewScoreCurve';

/**
 * The curve is a chart, so the assertions are about the contract rather than
 * pixels: one mark per returned record, oldest on the left, the same 0-100
 * normalization the voucher card uses, and a table that carries every point for
 * screen readers. The endpoint returns newest first; the component sorts.
 */
const entries: UserReviewEntry[] = [
  {
    logId: '3',
    postId: '3',
    postTitle: 'Newest post',
    score: 8,
    severity: 'low',
    isApproved: true,
    reviewedAt: '2026-11-03T10:00:00',
  },
  {
    logId: '1',
    postId: '1',
    postTitle: 'Oldest post',
    score: 85,
    severity: 'medium',
    isApproved: true,
    reviewedAt: '2026-11-01T10:00:00',
  },
  {
    logId: '2',
    postId: '2',
    postTitle: 'Middle post',
    score: 5,
    severity: 'high',
    isApproved: false,
    reviewedAt: '2026-11-02T10:00:00',
  },
];

function points(container: HTMLElement): SVGCircleElement[] {
  return Array.from(container.querySelectorAll<SVGCircleElement>('[data-curve-point]'));
}

describe('ReviewScoreCurve', () => {
  afterEach(async () => {
    await i18next.changeLanguage('en');
  });

  it('renders one point per returned entry', () => {
    const { container } = render(<ReviewScoreCurve entries={entries} />);

    expect(points(container)).toHaveLength(entries.length);
    expect(container.querySelector('polyline')).not.toBeNull();
  });

  it('plots the points oldest to newest even though the endpoint returns newest first', () => {
    const { container, getByRole } = render(<ReviewScoreCurve entries={entries} />);

    const xs = points(container).map((point) => Number(point.getAttribute('cx')));
    expect(xs).toHaveLength(3);
    expect(xs[0]).toBeLessThan(xs[1]);
    expect(xs[1]).toBeLessThan(xs[2]);

    // The label names the range and the count, not just "a chart".
    expect(getByRole('img').getAttribute('aria-label')).toBe(
      'AI score curve with 3 data points, scores from 50 to 85 out of 100',
    );
  });

  it('renders no SVG and says so when the index is empty', () => {
    const { container } = render(<ReviewScoreCurve entries={[]} />);

    expect(container.querySelector('svg')).toBeNull();
    expect(screen.queryByTestId('review-score-curve')).toBeNull();
    expect(screen.getByText('No review records to show yet')).toBeInTheDocument();
  });

  it('gives screen readers a table with date, score and title for every point', () => {
    render(<ReviewScoreCurve entries={entries} />);

    const table = screen.getByRole('table');
    const rows = within(table).getAllByRole('row').slice(1); // drop the header row
    expect(rows).toHaveLength(3);

    // Ascending order matches the visual curve.
    expect(rows[0]).toHaveTextContent('Oldest post');
    expect(rows[0]).toHaveTextContent('85');
    expect(rows[1]).toHaveTextContent('Middle post');
    expect(rows[1]).toHaveTextContent('50');
    expect(rows[2]).toHaveTextContent('Newest post');
    expect(rows[2]).toHaveTextContent('80');

    // Dates are plain localized text, not links (jumping is S4-3).
    expect(rows[0].querySelectorAll('a')).toHaveLength(0);
    expect(rows[0].textContent).toMatch(/2026/);
  });

  it('normalizes like the voucher: a raw 8 shows as 80, a raw 85 stays 85', () => {
    const { container } = render(<ReviewScoreCurve entries={entries} />);

    const labels = points(container).map((point) => point.getAttribute('aria-label') ?? '');
    expect(labels[2]).toContain('Newest post');
    expect(labels[2]).toContain('80');
    expect(labels[0]).toContain('85');
  });

  it('makes every point tabbable and names it', () => {
    const { container } = render(<ReviewScoreCurve entries={entries} />);

    for (const point of points(container)) {
      expect(point).toHaveAttribute('tabindex', '0');
      expect(point.getAttribute('aria-label')).toMatch(/AI score \d+ out of 100/);
    }
  });
});
