/**
 * The single place that turns a stored review score into the 0-100 number the
 * UI prints.
 *
 * <p>The model emits 0-10 (or, on a few older rows, 0-100) and every surface
 * claims a /100 scale. Keeping the rule here means the voucher card and the
 * profile curve cannot drift apart on the same raw score.</p>
 */
export function normalizeReviewScore(score: number): number {
  // Math.round matches what the voucher has always shown: 8.5 -> 85, 8.33 -> 83.
  // The >10 branch leaves rows that were already stored on the 0-100 scale alone.
  return score <= 10 ? Math.round(score * 10) : Math.round(score);
}
