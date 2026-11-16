import { describe, expect, it } from 'vitest';
import channelPageSource from './ChannelPage.tsx?raw';

/**
 * A6-4 and B7, in their amended checkable form.
 *
 * <p>Mounting `ChannelPage` would need the router and query providers, so these
 * are source-level pins on the wiring: they fail if the URL-backed sort or the
 * toggle is removed. The A4-4 unanswered-panel pins are replaced by the reverse
 * assertion — B7 deleted the panel and its query, and the page must not grow
 * them back silently.</p>
 *
 * <p>Each assertion looks for the smallest string that would have to change
 * for the behaviour to disappear, rather than matching whole blocks of JSX,
 * so ordinary formatting does not make the test brittle.</p>
 */

const source = channelPageSource;

describe('ChannelPage wiring (B7, source-level)', () => {
  it('B7: the 等待回答 panel and its unanswered query are gone', () => {
    expect(source).not.toContain('waiting.ariaLabel');
    expect(source).not.toContain('/posts/unanswered');
    expect(source).not.toContain('useIsDesktop');
  });

  it('B7: the feed keeps its empty state as the only aside-shaped branch', () => {
    expect(source).toContain('<EmptyState preset="noPosts" {...emptyGuide} />');
  });
});

describe('ChannelPage ordering wiring (A6-4, source-level)', () => {
  it('A6-4: sort is read from useSearchParams and written through its setter', () => {
    expect(source).toContain('const [searchParams, setSearchParams] = useSearchParams();');
    expect(source).toMatch(
      /const sort = searchParams\.get\('sort'\) === 'liked' \? 'liked' : 'latest';/,
    );
    expect(source).toMatch(/nextParams\.set\('sort', 'liked'\)/);
    expect(source).toMatch(/nextParams\.delete\('sort'\)/);
    expect(source).toMatch(/setSearchParams\(nextParams/);
  });

  it('A6-4: sort is part of the posts query key', () => {
    expect(source).toMatch(/queryKey: \['posts', 'channel', slug, page, sort,/);
  });

  it('A6-4: the toggle renders both ordering options', () => {
    expect(source).toContain("(['latest', 'liked'] as const).map((option) => (");
    expect(source).toContain("option === 'latest' ? t('sortLatest') : t('sortLiked')");
    expect(source).toContain('aria-pressed={sort === option}');
  });
});
