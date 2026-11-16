import { afterEach, describe, expect, it, vi } from 'vitest';
import { act, fireEvent, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import i18next from 'i18next';
import { MemoryRouter } from 'react-router-dom';
import type { ComponentProps } from 'react';
import { AiReviewTerminal } from './AiReviewTerminal';
import type { AiReviewDetail } from '../types/post';

/**
 * The one panel whose content the server assembles. It takes six states and the
 * three that matter are the ones a user can misread: "reviewing" when nothing is
 * coming, a score that is not there, and a failure that retried itself out of
 * existence. Each test asserts on the sentence the user would act on.
 *
 * <p>Since the v2 collapse, the replay plays on the first expand of the panel
 * rather than at mount, so the two pacing tests expand the panel before
 * advancing the timers. Everything the header shows is visible collapsed.</p>
 */

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
  durationMs: 1234,
};

/** The voucher links to the admin log surface, so the component needs a router. */
function renderTerminal(props: ComponentProps<typeof AiReviewTerminal>) {
  return render(
    <MemoryRouter>
      <AiReviewTerminal {...props} />
    </MemoryRouter>,
  );
}

describe('AiReviewTerminal', () => {
  afterEach(async () => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
    await i18next.changeLanguage('en');
  });

  it('says a review is in flight, without inventing a score', () => {
    renderTerminal({ state: 'pending' });

    expect(screen.getByRole('status')).toHaveTextContent('AI review in progress...');
    expect(screen.queryByText(/Score:/)).not.toBeInTheDocument();
  });

  it('offers the retry it actually wires up when the read failed', async () => {
    const user = userEvent.setup();
    const onRetry = vi.fn();

    renderTerminal({ state: 'error', errorMessage: 'Failed to load AI review.', onRetry });

    expect(screen.getByRole('alert')).toHaveTextContent('Failed to load AI review.');
    await user.click(screen.getByRole('button', { name: 'Retry AI review' }));
    expect(onRetry).toHaveBeenCalledTimes(1);
  });

  it('renders the severity through the dictionary (zh shows the translated name)', async () => {
    await i18next.changeLanguage('zh');
    renderTerminal({ state: 'data', detail });

    // The badge no longer echoes the normalized key: zh readers read the
    // agentLogs severityNames block (高 for high), en readers the English one.
    expect(screen.getByText('高')).toBeInTheDocument();
    expect(screen.queryByText('high')).not.toBeInTheDocument();
  });

  it('renders a 0-10 score as the 0-100 the panel claims', () => {
    renderTerminal({ state: 'data', detail });

    // The API stores 8.5; the panel is labelled /100. The scaling lives here, in
    // the component, and this is the only place that keeps it honest.
    expect(screen.getByText('Score: 85/100')).toBeInTheDocument();
    expect(screen.getByText('high')).toBeInTheDocument();
    expect(screen.getByText('Approved')).toBeInTheDocument();
    expect(screen.getByText('Prefer early returns here.')).toBeInTheDocument();
    expect(screen.getByText('No findings.')).toBeInTheDocument();
  });

  it('renders the voucher without expanding: model, calls, duration and raw-log link', () => {
    renderTerminal({ state: 'data', detail, reviewCount: 3 });

    const voucher = screen.getByTestId('review-voucher');
    // The findings are still collapsed; the voucher is not.
    expect(screen.getByRole('button', { name: 'Toggle review details' })).toHaveAttribute('aria-expanded', 'false');
    expect(voucher).toHaveTextContent('Model: agent');
    expect(within(voucher).getByText('3 call(s)')).toBeInTheDocument();
    expect(within(voucher).getByText('1.2 s')).toBeInTheDocument();
    expect(within(voucher).getByRole('link', { name: /Raw review log/ })).toHaveAttribute(
      'href',
      '/agent-logs?postId=1',
    );
  });

  it('shows an explicit unavailable state rather than an empty scorecard', () => {
    renderTerminal({ state: 'data', detail: null });

    const region = screen.getByRole('status');
    expect(region).toHaveTextContent('AI review data unavailable.');
    expect(region).not.toHaveTextContent('/100');
  });

  describe('replaying a finished run', () => {
    const stateOf = (container: HTMLElement, key: string) =>
      container.querySelector(`[data-step="${key}"]`)!.getAttribute('data-state');

    it('lights the steps in order and lands on the score', () => {
      vi.useFakeTimers();
      const { container } = renderTerminal({ state: 'data', detail });

      // The replay belongs to the expand, not the page load: open the panel first.
      fireEvent.click(screen.getByRole('button', { name: 'Toggle review details' }));

      expect(stateOf(container, 'codeQuality')).toBe('active');
      expect(stateOf(container, 'securityConcerns')).toBe('waiting');

      act(() => { vi.advanceTimersByTime(500); });
      expect(stateOf(container, 'codeQuality')).toBe('done');
      expect(stateOf(container, 'securityConcerns')).toBe('active');

      act(() => { vi.advanceTimersByTime(500); });
      expect(stateOf(container, 'securityConcerns')).toBe('done');
      expect(stateOf(container, 'optimizationSuggestions')).toBe('active');

      act(() => { vi.advanceTimersByTime(500); });
      expect(stateOf(container, 'optimizationSuggestions')).toBe('done');
      expect(stateOf(container, 'score')).toBe('active');

      act(() => { vi.advanceTimersByTime(500); });
      expect(stateOf(container, 'score')).toBe('done');
      expect(screen.getByText('0')).toBeInTheDocument();

      act(() => { vi.advanceTimersByTime(700); });
      expect(screen.getByText('85')).toBeInTheDocument();
      expect(screen.getByText('Score: 85/100')).toBeInTheDocument();
    });

    it('renders a stored zero as an empty meter rather than NaN', () => {
      vi.useFakeTimers();
      const { container } = renderTerminal({ state: 'data', detail: { ...detail, score: 0 } });

      // Same rule as above: the replay starts on the first expand.
      fireEvent.click(screen.getByRole('button', { name: 'Toggle review details' }));
      act(() => { vi.advanceTimersByTime(3000); });

      // A stored 0 is a real score; the meter must stay at 0% instead of dividing 0 by 0.
      expect(screen.getByText('Score: 0/100')).toBeInTheDocument();
      const meter = container.querySelector<HTMLElement>('div[style*="width"]');
      expect(meter?.style.width).toBe('0%');
    });

    it('names the panel a replay of a completed run and never claims it is live', () => {
      renderTerminal({ state: 'data', detail });

      // The panel renders a stored review, so the caption may say what it is - a replay of
      // the run that finished at the recorded time - and may not say the pipeline is
      // working on it now. Same rule ADR-0010 set for the landing card, one surface down.
      const caption = screen.getByText(/replay of completed run/i);
      expect(caption).toHaveTextContent('2026-09-15 10:00');
      expect(caption).not.toHaveTextContent(/live/i);
    });
  });
});
