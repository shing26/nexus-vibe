import { describe, expect, it } from 'vitest';
import { normalizeReviewScore } from './score';

describe('normalizeReviewScore', () => {
  it('scales the stored 0-10 range to the 0-100 the UI claims', () => {
    expect(normalizeReviewScore(8)).toBe(80);
    expect(normalizeReviewScore(8.5)).toBe(85);
    expect(normalizeReviewScore(0)).toBe(0);
    expect(normalizeReviewScore(10)).toBe(100);
  });

  it('leaves rows already stored on the 0-100 scale alone', () => {
    expect(normalizeReviewScore(85)).toBe(85);
    expect(normalizeReviewScore(100)).toBe(100);
  });
});
