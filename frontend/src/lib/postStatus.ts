import i18next from 'i18next';

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

const POST_STATUS_KEYS: Partial<Record<number, string>> = {
  [POST_STATUS.ACTIVE]: 'common:status.active',
  [POST_STATUS.PENDING_REVIEW]: 'common:status.pendingReview',
  [POST_STATUS.REJECTED]: 'common:status.rejected',
};

const AI_REVIEW_KEYS: Partial<Record<number, string>> = {
  [AI_REVIEW.NOT_REVIEWED]: 'common:status.notReviewed',
  [AI_REVIEW.REVIEWED]: 'common:status.reviewed',
  [AI_REVIEW.REVIEWING]: 'common:status.reviewing',
  [AI_REVIEW.FAILED]: 'common:status.failed',
};

function unknownLabel(code: number | null | undefined): string {
  return code == null
    ? i18next.t('common:status.unknown')
    : i18next.t('common:status.unknownWithCode', { code });
}

/** Label for `PostPageVo.status`; never blank. */
export function postStatusLabel(code: number | null | undefined): string {
  if (code == null) return unknownLabel(code);
  const key = POST_STATUS_KEYS[code];
  return key ? i18next.t(key) : unknownLabel(code);
}

/** Label for `PostPageVo.aiReviewed`; never blank, and 3 never reads as 0. */
export function aiReviewLabel(code: number | null | undefined): string {
  if (code == null) return unknownLabel(code);
  const key = AI_REVIEW_KEYS[code];
  return key ? i18next.t(key) : unknownLabel(code);
}
