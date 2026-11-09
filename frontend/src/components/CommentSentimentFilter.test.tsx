import { describe, expect, it } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import CommentSentimentFilter from './CommentSentimentFilter';
import type { Comment } from '../types/post';

/**
 * The comment list groups rows by the sentiment the write path already stored.
 * Two properties matter: `0` is a real group (every pre-column comment lives
 * there) rather than a discard bin, and the control says out loud that the
 * grouping is a keyword rule, not a model's reading of the thread.
 */

function comment(id: string, content: string, sentiment?: number): Comment {
  return {
    id,
    postId: '9',
    authorName: 'author-' + id,
    content,
    userId: '3',
    createTime: '2026-11-01T00:00:00',
    sentiment,
  };
}

const COMMENTS: Comment[] = [
  comment('1', 'legacy row, never classified', 0),
  // A response from a build that predates the column has no field at all.
  comment('2', 'row from an older payload'),
  comment('3', 'how do I configure this?', 1),
  comment('4', 'the build is broken', 2),
  comment('5', 'great walkthrough', 3),
];

function Harness({ comments }: { comments: Comment[] }) {
  return (
    <CommentSentimentFilter
      comments={comments}
      renderComment={(c) => <p key={c.id}>{c.content}</p>}
    />
  );
}

describe('CommentSentimentFilter', () => {
  it('starts on All, where every row is visible including the unclassified ones (A7-6)', () => {
    render(<Harness comments={COMMENTS} />);

    expect(screen.getByRole('button', { name: 'All (5)' })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByText('legacy row, never classified')).toBeInTheDocument();
    expect(screen.getByText('row from an older payload')).toBeInTheDocument();
    expect(screen.getByText('great walkthrough')).toBeInTheDocument();
  });

  it('filters the rendered rows client-side from the rows it was given (A7-5)', () => {
    render(<Harness comments={COMMENTS} />);

    fireEvent.click(screen.getByRole('button', { name: 'Questions (1)' }));

    expect(screen.getByText('how do I configure this?')).toBeInTheDocument();
    // Rows from the other groups are not re-fetched or dropped from the data;
    // they are simply not in this view.
    expect(screen.queryByText('legacy row, never classified')).toBeNull();
    expect(screen.queryByText('great walkthrough')).toBeNull();
  });

  it('puts sentiment 0 and a missing field in the same reachable group (A7-6)', () => {
    render(<Harness comments={COMMENTS} />);

    fireEvent.click(screen.getByRole('button', { name: 'Unclassified (2)' }));

    expect(screen.getByText('legacy row, never classified')).toBeInTheDocument();
    expect(screen.getByText('row from an older payload')).toBeInTheDocument();
    expect(screen.queryByText('how do I configure this?')).toBeNull();
  });

  it('never silently drops rows when a group is empty', () => {
    // One praise comment, viewed under Questions: the list is empty, and the
    // reader is told the row is still there rather than shown a blank panel.
    render(<Harness comments={[comment('5', 'great walkthrough', 3)]} />);

    fireEvent.click(screen.getByRole('button', { name: 'Questions (0)' }));

    expect(screen.getByText(/nothing was removed/i)).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Show all 1' }));
    expect(screen.getByText('great walkthrough')).toBeInTheDocument();
  });

  it('labels the grouping as a keyword heuristic, not a model judgement (A7-5)', () => {
    render(<Harness comments={COMMENTS} />);

    expect(screen.getByText(/grouped by keyword/i)).toBeInTheDocument();
    expect(screen.getByText(/not a model's judgement/i)).toBeInTheDocument();
  });
});
