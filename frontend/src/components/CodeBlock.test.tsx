import { describe, expect, it, beforeEach } from 'vitest';
import { render } from '@testing-library/react';
import CodeBlock from './CodeBlock';
import { useThemeStore } from '../stores/themeStore';

/**
 * Slice C3: the Chaos Theatre points at the line it is talking about, so
 * CodeBlock gained an optional `highlightLines`. The default must not change —
 * every existing call site passes no prop and must render exactly as before.
 */

const CODE = `public int divide(int value, int divisor) {
    return value / divisor;
}`;

describe('CodeBlock line highlighting', () => {
  beforeEach(() => {
    useThemeStore.setState({ dark: true });
  });

  it('renders no highlight rail when the prop is absent', () => {
    const { container } = render(<CodeBlock language="java" code={CODE} />);

    expect(container.querySelectorAll('[data-testid="code-highlight-line"]')).toHaveLength(0);
  });

  it('marks only the requested 1-based line', () => {
    const { container } = render(<CodeBlock language="java" code={CODE} highlightLines={[2]} />);

    const marked = container.querySelectorAll('[data-testid="code-highlight-line"]');
    expect(marked).toHaveLength(1);
    expect(marked[0]).toHaveTextContent('return value / divisor;');
  });

  it('falls back to a plain pre for an unknown language and still marks the line', () => {
    const { container } = render(<CodeBlock language="not-a-language" code={CODE} highlightLines={[1]} />);

    expect(container.querySelectorAll('[data-testid="code-highlight-line"]')).toHaveLength(1);
  });
});
