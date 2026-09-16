import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import CommentBody from './CommentBody';

/**
 * These tests exist because the bug they cover shipped: the comment list rendered
 * `{comment.content}` inside a single `<p>`, so the AI review - the one comment this product
 * most wants read - printed its own markdown as literal text in one unbroken paragraph.
 *
 * The first test is written against the bug rather than against the current output: it quotes
 * the exact string a reader used to see.
 */
describe('CommentBody', () => {
  it('renders the reviewer heading as a heading, not as literal hashes', () => {
    render(<CommentBody content={'## AI Code Review\n\n**Overall Score**: 3/10\n'} />);

    expect(screen.getByRole('heading', { name: 'AI Code Review' })).toBeInTheDocument();
    expect(screen.queryByText(/## AI Code Review/)).toBeNull();
    // Bold has to arrive as an element, or the emphasised labels read as stray asterisks.
    expect(screen.getByText('Overall Score').tagName).toBe('STRONG');
  });

  it('keeps paragraphs apart instead of collapsing them into one run of text', () => {
    render(<CommentBody content={'First finding.\n\nSecond finding.\n'} />);

    const first = screen.getByText('First finding.');
    const second = screen.getByText('Second finding.');
    expect(first.tagName).toBe('P');
    expect(second.tagName).toBe('P');
    expect(first).not.toBe(second);
  });

  it('renders each of the three review sections the panel also shows', () => {
    render(
      <CommentBody
        content={'### Code Quality\nFine.\n\n### Security\nNone found.\n\n### Suggestions\nNone.\n'}
      />,
    );

    for (const section of ['Code Quality', 'Security', 'Suggestions']) {
      expect(screen.getByRole('heading', { name: section })).toBeInTheDocument();
    }
  });

  it('renders an inline code span rather than backticks', () => {
    render(<CommentBody content={'The `HashMap` is not thread-safe.\n'} />);

    expect(screen.getByText('HashMap').tagName).toBe('CODE');
    expect(screen.queryByText(/`HashMap`/)).toBeNull();
  });
});
