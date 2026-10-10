import { afterEach, describe, expect, it, vi } from 'vitest';
import { act, render } from '@testing-library/react';
import { ReviewStepStrip } from './ReviewStepStrip';

/**
 * The strip has two jobs that must not blend: confirming the steps of a finished run in
 * order, and keeping the "a review is in flight" picture alive without ever confirming a
 * step it has no evidence for.
 */

describe('ReviewStepStrip', () => {
  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it('confirms steps in order as a replay advances', () => {
    const { container } = render(<ReviewStepStrip mode="replay" completed={2} />);

    expect(container.querySelector('[data-step="codeQuality"]')).toHaveAttribute('data-state', 'done');
    expect(container.querySelector('[data-step="securityConcerns"]')).toHaveAttribute('data-state', 'done');
    expect(container.querySelector('[data-step="optimizationSuggestions"]')).toHaveAttribute('data-state', 'active');
    expect(container.querySelector('[data-step="score"]')).toHaveAttribute('data-state', 'waiting');
  });

  it('cycles one active step while a review is in flight and confirms none of them', () => {
    vi.useFakeTimers();
    const { container } = render(<ReviewStepStrip mode="running" />);

    expect(container.querySelector('[data-step="codeQuality"]')).toHaveAttribute('data-state', 'active');
    expect(container.querySelectorAll('[data-state="done"]')).toHaveLength(0);

    act(() => { vi.advanceTimersByTime(1400); });

    expect(container.querySelector('[data-step="securityConcerns"]')).toHaveAttribute('data-state', 'active');
    expect(container.querySelector('[data-step="codeQuality"]')).toHaveAttribute('data-state', 'waiting');
    expect(container.querySelectorAll('[data-state="active"]')).toHaveLength(1);
    expect(container.querySelectorAll('[data-state="done"]')).toHaveLength(0);
  });

});
