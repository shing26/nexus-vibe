/**
 * Where the repository's own evidence lives, as public URLs.
 *
 * <p>The navigation surfaces link out to these because the strongest material in this
 * repository - fifteen ADRs, a reproducible load test, a failure drill - is in Git, and a
 * visitor who wants to check a claim should reach it in one click. The branch is {@code
 * master}: ADRs are append-only and the tree link shows the current set without a release
 * tag that a reader would have to guess.</p>
 */

const REPO = 'https://github.com/shing26/nexus-vibe';

export const ADR_INDEX_URL = REPO + '/tree/master/docs/adr';
export const LOADTEST_URL = REPO + '/blob/master/docs/research/async-pool-loadtest.md';
export const DRILL_URL = REPO + '/blob/master/docs/research/observability-drill-2026-09.md';
