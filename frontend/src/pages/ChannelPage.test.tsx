import { describe, expect, it } from 'vitest';
import channelPageSource from './ChannelPage.tsx?raw';

/**
 * A4-4 and A6-4, in their amended checkable form.
 *
 * <p>Mounting `ChannelPage` would need the router and query providers this
 * round assigns to no group, so these are source-level pins on the wiring:
 * they fail if the responsive gate, the empty branch, the URL-backed sort or
 * the toggle is removed. They are not a runtime proof of responsive behaviour;
 * that remains inspected, not asserted.</p>
 *
 * <p>Each assertion looks for the smallest string that would have to change
 * for the behaviour to disappear, rather than matching whole blocks of JSX,
 * so ordinary formatting does not make the test brittle.</p>
 */

const source = channelPageSource;

describe('ChannelPage wiring (A4-4, source-level)', () => {
  it('A4-4: the desktop gate uses the md breakpoint, (min-width: 768px)', () => {
    expect(source).toContain("const DESKTOP_QUERY = '(min-width: 768px)';");
    expect(source).toContain('function useIsDesktop(): boolean');
  });

  it('A4-4: the unanswered panel is mounted only under the isDesktop gate', () => {
    const gate = source.indexOf('{isDesktop && channel && (');
    const panel = source.indexOf('<aside aria-label="Waiting for an answer"');

    expect(gate, 'the isDesktop gate is present').toBeGreaterThan(-1);
    expect(panel, 'the panel is present').toBeGreaterThan(-1);
    expect(panel, 'the panel is inside the isDesktop gate').toBeGreaterThan(gate);
  });

  it('A4-4: the empty branch renders the item-2 EmptyState with its copy', () => {
    const asideStart = source.indexOf('<aside aria-label="Waiting for an answer"');
    const asideEnd = source.indexOf('</aside>', asideStart);
    expect(asideStart).toBeGreaterThan(-1);
    expect(asideEnd).toBeGreaterThan(asideStart);

    const aside = source.slice(asideStart, asideEnd);
    expect(aside).toContain('preset="noPosts"');
    expect(aside).toContain('title="Nothing is waiting"');
    expect(aside).toContain('Posts with no replies appear here');
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
    expect(source).toContain("option === 'latest' ? 'Latest' : 'Most liked'");
    expect(source).toContain('aria-pressed={sort === option}');
  });
});
