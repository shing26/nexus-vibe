import { describe, expect, it, vi } from 'vitest';
import { act, render, screen } from '@testing-library/react';
import { AiReviewTerminal } from './AiReviewTerminal';
import { ReviewStepStrip } from './ui/ReviewStepStrip';
import type { AiReviewDetail } from '../types/post';

/**
 * The reveal is decoration: with `prefers-reduced-motion: reduce` every animation collapses
 * and the finished state renders at once.
 *
 * <p>The stub is installed at module scope, before the first render, because motion-dom
 * caches the media-query answer the first time any component asks for it - a per-test stub
 * would arrive after the answer was already latched to `false` by an earlier render in the
 * same file.</p>
 */

vi.stubGlobal('matchMedia', (query: string) => ({
  matches: true,
  media: query,
  onchange: null,
  addListener: vi.fn(),
  removeListener: vi.fn(),
  addEventListener: vi.fn(),
  removeEventListener: vi.fn(),
  dispatchEvent: vi.fn(),
}));

const detail: AiReviewDetail = {
  postId: '1',
  reviewer: 'agent',
  score: 8.5,
  severity: 'high',
  isApproved: 1,
  codeQuality: ['Prefer early returns here.'],
  securityConcerns: [],
  optimizationSuggestions: ['Cache the tokenizer.'],
  reviewedAt: '2026-09-15T10:00:00',
};

describe('reduced motion', () => {
  it('renders the finished replay without walking through the steps', () => {
    const { container } = render(<AiReviewTerminal state="data" detail={detail} />);

    expect(container.querySelector('[data-step="score"]')).toHaveAttribute('data-state', 'done');
    expect(container.querySelector('[data-step="codeQuality"]')).toHaveAttribute('data-state', 'done');
    expect(screen.getByText('85')).toBeInTheDocument();
    expect(screen.getByText('Score: 85/100')).toBeInTheDocument();
  });

  it('keeps the running strip still instead of cycling it', () => {
    vi.useFakeTimers();

    const { container } = render(<ReviewStepStrip mode="running" />);
    act(() => {
      vi.advanceTimersByTime(10000);
    });

    expect(container.querySelector('[data-step="codeQuality"]')).toHaveAttribute('data-state', 'active');
    expect(container.querySelectorAll('[data-state="active"]')).toHaveLength(1);
    vi.useRealTimers();
  });
});
