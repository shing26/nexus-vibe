import { describe, expect, it } from 'vitest';
import {
  AI_REVIEW,
  POST_STATUS,
  aiReviewLabel,
  postStatusLabel,
} from './postStatus';

/**
 * A5: the four AI review states are distinguishable, and no code — known or not —
 * renders as a blank badge. The failure this pins is quiet: `aiReviewed === 1` is
 * the only comparison the old cards made, so `FAILED(3)` and `NOT_REVIEWED(0)`
 * both rendered as "no badge", which looks like a healthy post.
 */
describe('postStatus', () => {
  it('maps the three post statuses to distinct, non-blank labels', () => {
    const labels = [POST_STATUS.ACTIVE, POST_STATUS.PENDING_REVIEW, POST_STATUS.REJECTED].map(
      postStatusLabel,
    );

    expect(labels).toEqual(['Active', 'Pending review', 'Rejected']);
    expect(new Set(labels).size).toBe(3);
    for (const label of labels) {
      expect(label.trim()).not.toBe('');
    }
  });

  it('maps all four AI review codes to distinct labels', () => {
    const labels = [
      AI_REVIEW.NOT_REVIEWED,
      AI_REVIEW.REVIEWED,
      AI_REVIEW.REVIEWING,
      AI_REVIEW.FAILED,
    ].map(aiReviewLabel);

    expect(labels).toEqual(['Not reviewed', 'Reviewed', 'Reviewing', 'Failed']);
    expect(new Set(labels).size).toBe(4);
  });

  it('does not render FAILED(3) as NOT_REVIEWED(0)', () => {
    expect(aiReviewLabel(AI_REVIEW.FAILED)).not.toBe(aiReviewLabel(AI_REVIEW.NOT_REVIEWED));
    expect(aiReviewLabel(AI_REVIEW.FAILED)).toMatch(/failed/i);
  });

  it('falls back to a visible unknown for a code it does not know', () => {
    const postLabel = postStatusLabel(99);
    const aiLabel = aiReviewLabel(42);

    expect(postLabel).toMatch(/unknown/i);
    expect(aiLabel).toMatch(/unknown/i);
    expect(postLabel).toContain('99');
    expect(aiLabel).toContain('42');
  });

  it('falls back to a visible unknown when the code is missing entirely', () => {
    for (const label of [postStatusLabel(null), postStatusLabel(undefined), aiReviewLabel(null)]) {
      expect(label).toMatch(/unknown/i);
      expect(label.trim()).not.toBe('');
    }
  });
});
