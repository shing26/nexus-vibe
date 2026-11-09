import { describe, expect, it } from 'vitest';
import { render } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import EmptyState from './EmptyState';
import type { PresetKey } from './EmptyState';

/**
 * Item 2: the empty state is the first lesson a blank surface gives.
 *
 * <p>The preset keys are runtime-derived from the component's own map, so a
 * preset added without copy fails here. The copy check is deliberately a pair
 * of weak signals — a length floor and a mechanism verb — rather than a
 * grammar: it exists to reject "No posts yet." and its longer cousins, not to
 * grade prose.</p>
 */

/**
 * Verbs that describe how a surface fills. The noun for the content itself is
 * not enough ("There are no posts in this channel right now." is a restatement
 * of absence with a longer sentence), so `posts`, `comments` and `activity`
 * are deliberately absent from this list.
 */
const MECHANISM_WORDS = [
  'publish',
  'write',
  'reply',
  'search',
  'filter',
  'appear',
  'create',
  'start',
  'follow',
  'moved',
  'record',
  'clear',
  'widen',
  'link',
];

const MIN_DESC_LENGTH = 40;

/** The component exposes its own preset keys, derived from the map. */
const presetKeys = (EmptyState as typeof EmptyState & { presetKeys: PresetKey[] }).presetKeys;

/** Reasons a description fails the copy contract; empty means it passes. */
function copyProblems(desc: string): string[] {
  const problems: string[] = [];
  const normalized = desc.trim();
  if (normalized.length < MIN_DESC_LENGTH) {
    problems.push(`desc is shorter than ${MIN_DESC_LENGTH} characters`);
  }
  const lower = normalized.toLowerCase();
  if (!MECHANISM_WORDS.some((word) => lower.includes(word))) {
    problems.push('desc does not name a mechanism that fills the surface');
  }
  return problems;
}

function renderPreset(preset: PresetKey) {
  const { container } = render(
    <MemoryRouter>
      <EmptyState preset={preset} />
    </MemoryRouter>,
  );
  return {
    container,
    title: container.querySelector('h3')?.textContent?.trim() ?? '',
    desc: container.querySelector('p')?.textContent?.trim() ?? '',
  };
}

describe('EmptyState presets (A2)', () => {
  for (const key of presetKeys) {
    it(`A2-1: "${key}" renders a non-empty title and desc`, () => {
      const { title, desc } = renderPreset(key);
      expect(title.length).toBeGreaterThan(0);
      expect(desc.length).toBeGreaterThan(0);
    });
  }

  it('A2-2: every desc is long enough and names a mechanism, not just the absence', () => {
    for (const key of presetKeys) {
      const { desc } = renderPreset(key);
      expect(copyProblems(desc), `preset "${key}": ${desc}`).toEqual([]);
    }
  });

  it('A2-2: the check rejects both a short desc and a long restatement of absence', () => {
    expect(copyProblems('No posts yet.')).not.toEqual([]);
    expect(copyProblems('There are no posts in this channel right now at all.')).not.toEqual([]);
  });

  it('A2-3: noComments keeps no action and explains that the reader can be first', () => {
    const { container, desc } = renderPreset('noComments');

    expect(desc.toLowerCase()).toContain('first comment');
    expect(desc.toLowerCase()).toContain('you write');
    // No action link: there is genuinely nothing to navigate to here.
    expect(container.querySelector('a')).toBeNull();
  });

  it('A2-4: preset copy has no exclamation marks and no "Oops"', () => {
    for (const key of presetKeys) {
      const { title, desc } = renderPreset(key);
      const text = `${title} ${desc}`;
      expect(text, `preset "${key}"`).not.toContain('!');
      expect(text.toLowerCase(), `preset "${key}"`).not.toContain('oops');
    }
  });
});
