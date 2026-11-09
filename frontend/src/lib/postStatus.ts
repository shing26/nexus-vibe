/**
 * The two numeric enums the API sends, and the only place their codes become words.
 *
 * <p>`PostPageVo.status` and `PostPageVo.aiReviewed` are integers on the wire
 * (`PostStatus` 1/2/3 and `AiReviewStatus` 0/1/2/3 on the server). Before this
 * module each page compared them to a literal, so a state the page had not been
 * taught about rendered as nothing at all — a badge that is simply absent reads as
 * "fine", which is the wrong lie for a held post.</p>
 *
 * <p>Every lookup here falls back to a visible `Unknown (<code>)`. A blank badge is
 * the one outcome that cannot be distinguished from a healthy post.</p>
 */

/** `com.nexus.campus.entity.PostStatus` */
export const POST_STATUS = {
  ACTIVE: 1,
  PENDING_REVIEW: 2,
  REJECTED: 3,
} as const;

/** `com.nexus.campus.entity.AiReviewStatus` */
export const AI_REVIEW = {
  NOT_REVIEWED: 0,
  REVIEWED: 1,
  REVIEWING: 2,
  FAILED: 3,
} as const;

const POST_STATUS_LABELS: Partial<Record<number, string>> = {
  [POST_STATUS.ACTIVE]: 'Active',
  [POST_STATUS.PENDING_REVIEW]: 'Pending review',
  [POST_STATUS.REJECTED]: 'Rejected',
};

const AI_REVIEW_LABELS: Partial<Record<number, string>> = {
  [AI_REVIEW.NOT_REVIEWED]: 'Not reviewed',
  [AI_REVIEW.REVIEWED]: 'Reviewed',
  [AI_REVIEW.REVIEWING]: 'Reviewing',
  [AI_REVIEW.FAILED]: 'Failed',
};

function unknownLabel(code: number | null | undefined): string {
  return code == null ? 'Unknown' : `Unknown (${code})`;
}

/** Label for `PostPageVo.status`; never blank. */
export function postStatusLabel(code: number | null | undefined): string {
  if (code == null) return unknownLabel(code);
  return POST_STATUS_LABELS[code] ?? unknownLabel(code);
}

/** Label for `PostPageVo.aiReviewed`; never blank, and 3 never reads as 0. */
export function aiReviewLabel(code: number | null | undefined): string {
  if (code == null) return unknownLabel(code);
  return AI_REVIEW_LABELS[code] ?? unknownLabel(code);
}
