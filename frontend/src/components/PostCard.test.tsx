import { afterEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import PostCard, { PostStateBadges } from './PostCard';
import type { PostPageVo } from '../types/post';

/**
 * Behavioral coverage for the v2 topic-row.
 *
 * <p>The v2 redesign turned `PostCard` from a spotlight-card box into a compact
 * topic-row, which retired the effect components the old assertions pinned
 * byte-for-byte (`container.innerHTML` from base commit `1ce682a`). Those
 * fixtures asserted retired markup (the gradient beam wrapper, spotlight
 * overlay, the terminal preview block), so they are replaced by assertions on
 * what the row must still do: link to the post, print the real counts, expose
 * the copy/fork actions, and keep a rejected post's reason out of the public
 * feed. `Date.now` is pinned because the metadata line prints a relative time.</p>
 */

const NOW = new Date('2026-11-09T12:00:00.000Z').getTime();

const fixtures: Record<string, PostPageVo> = {
  activeText: {
    id: '1001',
    title: 'A plain active post',
    content: 'First line\nSecond line',
    summary: '',
    authorName: 'alice',
    categoryName: 'general',
    viewCount: 12,
    likeCount: 3,
    commentCount: 1,
    status: 1,
    createTime: '2026-11-08T12:00:00.000Z',
    aiReviewed: 1,
    aiReviewScore: 8.5,
    postType: 'text',
  },
  pendingPrompt: {
    id: '1002',
    title: 'A held prompt',
    content: 'Intro\n```ts\nconst x = 1;\nconst y = 2;\n```\nTail',
    summary: '',
    authorName: 'bob',
    categoryName: 'prompts',
    viewCount: 0,
    likeCount: 0,
    commentCount: 0,
    status: 2,
    createTime: '2026-11-09T06:00:00.000Z',
    aiReviewed: 2,
    aiReviewScore: 0,
    postType: 'prompt',
    promptMetadata: '{"role":"reviewer"}',
  },
  rejected: {
    id: '1003',
    title: 'A rejected post',
    content: '<p>Bad <b>stuff</b></p>',
    summary: '',
    authorName: 'carol',
    categoryName: 'general',
    viewCount: 5,
    likeCount: 0,
    commentCount: 0,
    status: 3,
    createTime: '2026-11-07T12:00:00.000Z',
    aiReviewed: 0,
    aiReviewScore: 0,
    rejectionReason: 'Spam',
  },
  emptyFailed: {
    id: '1004',
    title: 'A failed empty post',
    content: '',
    summary: '',
    authorName: 'dave',
    categoryName: 'general',
    viewCount: 0,
    likeCount: 0,
    commentCount: 0,
    status: 1,
    createTime: '2026-11-09T11:59:00.000Z',
    aiReviewed: 3,
    aiReviewScore: 0,
  },
};

function renderCard(post: PostPageVo) {
  return render(
    <MemoryRouter>
      <PostCard post={post} />
    </MemoryRouter>,
  );
}

describe('PostCard rendered without the status prop (A7)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('renders the active post as a topic row: title link, counts, relative time', () => {
    vi.spyOn(Date, 'now').mockReturnValue(NOW);

    renderCard(fixtures.activeText);

    const title = screen.getByRole('link', { name: 'A plain active post' });
    expect(title).toHaveAttribute('href', '/post/1001');

    // Counts come from the fields, in tabular numerals, in the right-aligned block.
    const meta = title.closest('article')!.querySelector('.ml-auto')!;
    expect(within(meta as HTMLElement).getByText('3')).toBeInTheDocument();
    expect(within(meta as HTMLElement).getByText('1')).toBeInTheDocument();
    expect(within(meta as HTMLElement).getByText('12')).toBeInTheDocument();
    expect(screen.getByText('1d ago')).toBeInTheDocument();
    expect(screen.getByText('alice')).toBeInTheDocument();

    // A reviewed post renders its 0-100 score as a verified credential chip.
    const credential = screen.getByTestId('ai-credential');
    expect(credential).toHaveAttribute('title', 'AI review score 85 / 10, verified');
    expect(within(credential).getByText('85')).toBeInTheDocument();
    expect(within(credential).getByText('verified')).toBeInTheDocument();
    // A text post offers copy (of its first lines) but no fork.
    expect(screen.getByTitle('Copy prompt')).toBeInTheDocument();
    expect(screen.queryByTitle('Fork template')).not.toBeInTheDocument();
  });

  it('renders the held prompt with copy and fork actions, without the retired row metadata', () => {
    vi.spyOn(Date, 'now').mockReturnValue(NOW);

    renderCard(fixtures.pendingPrompt);

    expect(screen.getByRole('link', { name: 'A held prompt' })).toHaveAttribute('href', '/post/1002');
    expect(screen.getByTitle('Copy prompt')).toBeInTheDocument();
    expect(screen.getByTitle('Fork template')).toBeInTheDocument();
    expect(screen.getByTitle('Fork template')).not.toBeDisabled();
    // B3: role and template badges left the row; the detail page carries them now.
    expect(screen.queryByText('reviewer')).not.toBeInTheDocument();
    expect(screen.queryByText('Template')).not.toBeInTheDocument();
    expect(screen.queryByTestId('post-status-badge')).not.toBeInTheDocument();
  });

  it('reveals the first code block on demand, collapsed by default (D1)', () => {
    vi.spyOn(Date, 'now').mockReturnValue(NOW);

    renderCard(fixtures.pendingPrompt);

    const toggle = screen.getByRole('button', { name: 'Toggle ts preview' });
    expect(toggle).toHaveAttribute('aria-expanded', 'false');
    expect(screen.queryByText(/const x = 1;/)).not.toBeInTheDocument();

    fireEvent.click(toggle);

    expect(toggle).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByText(/const x = 1;/)).toBeInTheDocument();
    // The metadata line is still a single non-wrapping row in the DOM.
    const meta = screen.getByRole('link', { name: 'A held prompt' }).closest('article')!.querySelector('.whitespace-nowrap');
    expect(meta).not.toBeNull();
  });

  it('keeps a rejected post off the state badges: no reason in the public feed', () => {
    vi.spyOn(Date, 'now').mockReturnValue(NOW);

    renderCard(fixtures.rejected);

    expect(screen.getByRole('link', { name: 'A rejected post' })).toBeInTheDocument();
    // `rejectionReason` is in the data but the public feed renders no state at all.
    expect(screen.queryByTestId('rejection-reason')).not.toBeInTheDocument();
    expect(screen.queryByText('Spam')).not.toBeInTheDocument();
  });

  it('renders the empty failed post as a bare row without actions', () => {
    vi.spyOn(Date, 'now').mockReturnValue(NOW);

    renderCard(fixtures.emptyFailed);

    expect(screen.getByRole('link', { name: 'A failed empty post' })).toHaveAttribute('href', '/post/1004');
    expect(screen.getByText('1m ago')).toBeInTheDocument();
    // No content means no preview, so no copy or fork action is offered.
    expect(screen.queryByTitle('Copy prompt')).not.toBeInTheDocument();
    expect(screen.queryByTitle('Fork template')).not.toBeInTheDocument();
  });

  it('renders the author state badges with a visible label for every code', () => {
    vi.spyOn(Date, 'now').mockReturnValue(NOW);

    render(
      <MemoryRouter>
        <PostStateBadges post={fixtures.pendingPrompt} />
        <PostStateBadges post={fixtures.rejected} />
        <PostStateBadges post={fixtures.emptyFailed} />
      </MemoryRouter>,
    );

    const badges = screen.getAllByTestId('post-status-badge');
    expect(badges).toHaveLength(3);
    // Known codes become words — pending, rejected, active, in render order.
    expect(badges[0]).toHaveTextContent('Pending review');
    expect(badges[1]).toHaveTextContent('Rejected');
    expect(badges[2]).toHaveTextContent('Active');
    expect(screen.getAllByTestId('ai-review-badge').length).toBe(3);
    expect(screen.getByTestId('rejection-reason')).toHaveTextContent('Reason: Spam');
  });
});

describe('PostCard counts (A8)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('renders view, like and comment counts from the post object, not constants', () => {
    vi.spyOn(Date, 'now').mockReturnValue(NOW);

    const post: PostPageVo = {
      ...fixtures.activeText,
      viewCount: 4321,
      likeCount: 17,
      commentCount: 9,
    };

    const { rerender } = render(
      <MemoryRouter>
        <PostCard post={post} />
      </MemoryRouter>,
    );

    expect(screen.getByText('4321')).toBeInTheDocument();
    expect(screen.getByText('17')).toBeInTheDocument();
    expect(screen.getByText('9')).toBeInTheDocument();

    // Same card, different object: all three counts follow their fields.
    rerender(
      <MemoryRouter>
        <PostCard post={{ ...post, viewCount: 8765, likeCount: 23, commentCount: 11 }} />
      </MemoryRouter>,
    );

    expect(screen.getByText('8765')).toBeInTheDocument();
    expect(screen.getByText('23')).toBeInTheDocument();
    expect(screen.getByText('11')).toBeInTheDocument();
    expect(screen.queryByText('4321')).not.toBeInTheDocument();
    expect(screen.queryByText('17')).not.toBeInTheDocument();
    expect(screen.queryByText('9')).not.toBeInTheDocument();
  });
});

describe('PostStateBadges review severity chip', () => {
  const reviewed = { ...fixtures.activeText, aiReviewScore: 8.5 };

  it('shows the reviewer severity verdict with an honest tone per level', () => {
    render(
      <MemoryRouter>
        <PostStateBadges post={{ ...reviewed, reviewSeverity: 'critical' }} />
        <PostStateBadges post={{ ...reviewed, reviewSeverity: 'high' }} />
        <PostStateBadges post={{ ...reviewed, reviewSeverity: 'low' }} />
      </MemoryRouter>,
    );

    const badges = screen.getAllByTestId('review-severity-badge');
    expect(badges).toHaveLength(3);
    // Known values become words in the dictionary, and the tone follows the verdict.
    expect(badges[0]).toHaveTextContent('critical');
    expect(badges[0]).toHaveClass('text-vibe-danger');
    expect(badges[1]).toHaveTextContent('high');
    expect(badges[1]).toHaveClass('text-vibe-warning');
    expect(badges[2]).toHaveTextContent('low');
    expect(badges[2]).toHaveClass('text-vibe-muted');
  });

  it('renders nothing for an unknown severity value rather than guessing a tone', () => {
    render(
      <MemoryRouter>
        <PostStateBadges post={{ ...reviewed, reviewSeverity: 'catastrophic' }} />
      </MemoryRouter>,
    );

    expect(screen.queryByTestId('review-severity-badge')).not.toBeInTheDocument();
  });

  it('renders nothing for rows whose review has not completed', () => {
    render(
      <MemoryRouter>
        <PostStateBadges post={{ ...fixtures.pendingPrompt, reviewSeverity: 'high' }} />
        <PostStateBadges post={{ ...fixtures.emptyFailed, reviewSeverity: 'low' }} />
        <PostStateBadges post={{ ...fixtures.rejected, reviewSeverity: 'critical' }} />
      </MemoryRouter>,
    );

    expect(screen.queryByTestId('review-severity-badge')).not.toBeInTheDocument();
  });
});
