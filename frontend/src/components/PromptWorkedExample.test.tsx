import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import PromptWorkedExample from './PromptWorkedExample';
import { mergeWorkedExample, readWorkedExample, withWorkedExample } from './promptMetadata';
// Source-level call-site scan (A5-6b), in the style of the repository's
// contract checks: the pages themselves cannot be mounted here (no providers),
// so the wiring is read the way NoStaleStatusAssertionTest reads Java sources.
// Removing the helper call from either page fails this file while the helpers
// above stay green — which is exactly the drift this asserts against.
import createPostSource from '../pages/CreatePostPage.tsx?raw';
import editPostSource from '../pages/EditPostPage.tsx?raw';

/**
 * The worked example (item 5) is two optional keys in the `promptMetadata` JSON
 * that already exists. The tests below pin the four properties that make it
 * safe to add to a stored format: it renders what is stored rather than a
 * literal, it renders nothing when there is nothing stored, copying it copies
 * the prompt, and it stays off every other post type.
 */

const writeText = vi.fn();

beforeEach(() => {
  writeText.mockReset();
  writeText.mockResolvedValue(undefined);
  Object.defineProperty(navigator, 'clipboard', {
    value: { writeText },
    configurable: true,
  });
});

function stored(exampleInput?: string, exampleOutput?: string) {
  return JSON.stringify({
    role: 'You are a reviewer.',
    recommendedModel: 'gpt-4o',
    temperature: 0.7,
    variables: ['language'],
    ...(exampleInput === undefined ? {} : { exampleInput }),
    ...(exampleOutput === undefined ? {} : { exampleOutput }),
  });
}

describe('PromptWorkedExample', () => {
  it('renders the text stored in promptMetadata, and follows it when it changes (A5-1)', () => {
    const { rerender } = render(
      <PromptWorkedExample
        postType="prompt"
        promptMetadata={stored('first stored input', 'first stored output')}
        prompt="rendered prompt"
      />,
    );

    expect(screen.getByText('first stored input')).toBeInTheDocument();
    expect(screen.getByText('first stored output')).toBeInTheDocument();

    rerender(
      <PromptWorkedExample
        postType="prompt"
        promptMetadata={stored('second stored input', 'second stored output')}
        prompt="rendered prompt"
      />,
    );

    expect(screen.getByText('second stored input')).toBeInTheDocument();
    expect(screen.getByText('second stored output')).toBeInTheDocument();
    expect(screen.queryByText('first stored input')).toBeNull();
    expect(screen.queryByText('first stored output')).toBeNull();
  });

  it('renders nothing at all when neither example key is stored (A5-2)', () => {
    const { container } = render(
      <PromptWorkedExample
        postType="prompt"
        promptMetadata={stored()}
        prompt="rendered prompt"
      />,
    );

    // No frame, no placeholder, no empty section — the DOM has no block at all.
    expect(container).toBeEmptyDOMElement();
  });

  it('treats blank strings the same as absent keys (A5-2)', () => {
    const { container } = render(
      <PromptWorkedExample
        postType="prompt"
        promptMetadata={stored('   ', '\n')}
        prompt="rendered prompt"
      />,
    );

    expect(container).toBeEmptyDOMElement();
  });

  it('copies the rendered prompt from the prompt control, and the input from its own (A5-3)', async () => {
    const renderedPrompt = 'Write a review of the {{language}} code.';
    render(
      <PromptWorkedExample
        postType="prompt"
        promptMetadata={stored('a pasted snippet', 'the output it produced')}
        prompt={renderedPrompt}
      />,
    );

    fireEvent.click(screen.getByRole('button', { name: 'Copy Prompt' }));
    await waitFor(() => expect(writeText).toHaveBeenCalledWith(renderedPrompt));
    // The payload is the prompt, not the example input and not the label.
    expect(writeText).not.toHaveBeenCalledWith('a pasted snippet');

    writeText.mockClear();
    fireEvent.click(screen.getByRole('button', { name: 'Copy Input' }));
    await waitFor(() => expect(writeText).toHaveBeenCalledWith('a pasted snippet'));
    expect(writeText).not.toHaveBeenCalledWith(renderedPrompt);
  });

  it('does not render on a post that is not the prompt type (A5-4)', () => {
    const { container } = render(
      <PromptWorkedExample
        postType="post"
        promptMetadata={stored('a pasted snippet', 'the output it produced')}
        prompt="rendered prompt"
      />,
    );

    expect(container).toBeEmptyDOMElement();
  });

  it('leaves metadata written before this feature alone (A5-5)', () => {
    // Exactly the shape CreatePostPage wrote before this change.
    const legacy = JSON.stringify({
      role: 'You are a reviewer.',
      recommendedModel: 'gpt-4o',
      temperature: 0.7,
      variables: ['language'],
    });

    expect(readWorkedExample(legacy)).toEqual({});
    const { container } = render(
      <PromptWorkedExample postType="prompt" promptMetadata={legacy} prompt="rendered prompt" />,
    );
    expect(container).toBeEmptyDOMElement();
  });

  it('reads corrupt JSON defensively instead of throwing (A5-5)', () => {
    expect(readWorkedExample('{not json')).toEqual({});
    expect(readWorkedExample(undefined)).toEqual({});
    expect(readWorkedExample('[]')).toEqual({});

    const { container } = render(
      <PromptWorkedExample postType="prompt" promptMetadata="{not json" prompt="rendered prompt" />,
    );
    expect(container).toBeEmptyDOMElement();
  });

  it('carries both example keys through create, edit and back to the detail block (A5-6)', () => {
    // Create: the form serialises its state into promptMetadata.
    const created = JSON.stringify(
      withWorkedExample(
        { role: 'reviewer', recommendedModel: 'gpt-4o', temperature: 0.2, variables: ['language'] },
        'a pasted snippet',
        'the output it produced',
      ),
    );
    expect(created).toContain('a pasted snippet');

    // Edit: the form loads the stored string into its fields, then merges the
    // unchanged fields back. Every key it does not own must survive.
    const loaded = readWorkedExample(created);
    expect(loaded.exampleInput).toBe('a pasted snippet');
    expect(loaded.exampleOutput).toBe('the output it produced');
    const saved = mergeWorkedExample(created, loaded.exampleInput ?? '', loaded.exampleOutput ?? '');

    const reloaded = JSON.parse(saved);
    expect(reloaded.exampleInput).toBe('a pasted snippet');
    expect(reloaded.exampleOutput).toBe('the output it produced');
    expect(reloaded.role).toBe('reviewer');
    expect(reloaded.recommendedModel).toBe('gpt-4o');
    expect(reloaded.temperature).toBe(0.2);
    expect(reloaded.variables).toEqual(['language']);

    // The detail block renders what the edit saved.
    render(<PromptWorkedExample postType="prompt" promptMetadata={saved} prompt="rendered prompt" />);
    expect(screen.getByText('a pasted snippet')).toBeInTheDocument();
    expect(screen.getByText('the output it produced')).toBeInTheDocument();
  });

  it('does not replace unparseable metadata when the edit changes nothing', () => {
    expect(mergeWorkedExample('{broken', '', '')).toBe('{broken');
    // With an example to add, a valid object is the only honest thing to write.
    expect(JSON.parse(mergeWorkedExample('{broken', 'an input', ''))).toEqual({
      exampleInput: 'an input',
    });
  });
});

/**
 * A5-6(b): the helpers above can be perfect while the forms have quietly
 * stopped calling them. These read the page sources and assert the call sites
 * themselves — the same shape as the repository's Java contract scans. Deleting
 * the `withWorkedExample` / `readWorkedExample` / `mergeWorkedExample` call from
 * a page fails here; changing the helpers does not.
 */
describe('the prompt forms bind the example fields to the shared helpers (A5-6b)', () => {
  it('CreatePostPage serialises both fields through withWorkedExample into promptMetadata', () => {
    expect(createPostSource).toMatch(
      /withWorkedExample\(\s*\{[\s\S]*?\}\s*,\s*exampleInput\s*,\s*exampleOutput\s*,?\s*\)/,
    );
    expect(createPostSource).toMatch(
      /body\.promptMetadata\s*=\s*JSON\.stringify\(\s*withWorkedExample\(/,
    );
    // Both fields are controlled inputs, so what is serialised is what was typed.
    expect(createPostSource).toMatch(/value=\{exampleInput\}/);
    expect(createPostSource).toMatch(/value=\{exampleOutput\}/);
  });

  it('EditPostPage reads the stored example and merges both fields back on save', () => {
    expect(editPostSource).toMatch(/readWorkedExample\(\s*post\.promptMetadata\s*\)/);
    expect(editPostSource).toMatch(
      /body\.promptMetadata\s*=\s*mergeWorkedExample\(\s*promptMetadata\s*,\s*exampleInput\s*,\s*exampleOutput\s*\)/,
    );
    expect(editPostSource).toMatch(/value=\{exampleInput\}/);
    expect(editPostSource).toMatch(/value=\{exampleOutput\}/);
  });
});
