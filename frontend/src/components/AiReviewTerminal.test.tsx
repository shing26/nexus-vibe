import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AiReviewTerminal } from './AiReviewTerminal';
import type { AiReviewDetail } from '../types/post';

/**
 * The one panel whose content the server assembles. It takes six states and the
 * three that matter are the ones a user can misread: "reviewing" when nothing is
 * coming, a score that is not there, and a failure that retried itself out of
 * existence. Each test asserts on the sentence the user would act on.
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
};

describe('AiReviewTerminal', () => {
  it('says a review is in flight, without inventing a score', () => {
    render(<AiReviewTerminal state="pending" />);

    expect(screen.getByRole('status')).toHaveTextContent('AI Agent reviewing...');
    expect(screen.queryByText(/Score:/)).not.toBeInTheDocument();
  });

  it('offers the retry it actually wires up when the read failed', async () => {
    const user = userEvent.setup();
    const onRetry = vi.fn();

    render(<AiReviewTerminal state="error" errorMessage="Failed to load AI review." onRetry={onRetry} />);

    expect(screen.getByRole('alert')).toHaveTextContent('Failed to load AI review.');
    await user.click(screen.getByRole('button', { name: 'Retry AI review' }));
    expect(onRetry).toHaveBeenCalledTimes(1);
  });

  it('renders a 0-10 score as the 0-100 the panel claims', () => {
    render(<AiReviewTerminal state="data" detail={detail} />);

    // The API stores 8.5; the panel is labelled /100. The scaling lives here, in
    // the component, and this is the only place that keeps it honest.
    expect(screen.getByText('Score: 85/100')).toBeInTheDocument();
    expect(screen.getByText('high')).toBeInTheDocument();
    expect(screen.getByText('Approved')).toBeInTheDocument();
    expect(screen.getByText('Prefer early returns here.')).toBeInTheDocument();
    expect(screen.getByText('// No findings.')).toBeInTheDocument();
  });

  it('shows an explicit unavailable state rather than an empty scorecard', () => {
    render(<AiReviewTerminal state="data" detail={null} />);

    const region = screen.getByRole('status');
    expect(region).toHaveTextContent('AI review data unavailable.');
    expect(region).not.toHaveTextContent('/100');
  });
});
