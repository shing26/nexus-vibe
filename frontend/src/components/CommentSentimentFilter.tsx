import { useState } from 'react';
import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import type { Comment } from '../types/post';

/**
 * Client-side grouping of the comment list by the stored sentiment code.
 *
 * The codes come from the server (`vibe_comment.sentiment`): 0 unclassified,
 * 1 question, 2 complaint, 3 praise. The grouping is a keyword heuristic that
 * ran once, in the comment write path — this component only reads the label.
 * It never classifies anything and never asks the server a second time: the
 * rows it groups are the rows it was handed.
 *
 * `0` is not a discard bin. It is the "Unclassified" group, and it is where
 * every comment written before the column existed belongs, so it is a
 * first-class filter beside the other three.
 */
export type SentimentFilter = 'all' | 'question' | 'complaint' | 'praise' | 'other';

interface FilterOption {
  value: SentimentFilter;
  /** Dictionary key under `post:sentiment.*`; the label resolves per language. */
  labelKey: string;
  /** The stored code this option selects; `all` has none. */
  code?: number;
}

const FILTERS: readonly FilterOption[] = [
  { value: 'all', labelKey: 'all' },
  { value: 'question', labelKey: 'question', code: 1 },
  { value: 'complaint', labelKey: 'complaint', code: 2 },
  { value: 'praise', labelKey: 'praise', code: 3 },
  // Every row that is 0, and any row whose payload predates the column.
  { value: 'other', labelKey: 'other', code: 0 },
];

/**
 * The stored code, with a missing field read as `0`. A response served by an
 * older build has no `sentiment` at all; those rows must land in "Unclassified"
 * under the same rule as the rows that stayed 0, never vanish.
 */
function sentimentCode(comment: Comment): number {
  const code = comment.sentiment;
  return typeof code === 'number' && Number.isInteger(code) ? code : 0;
}

function filterComments(comments: Comment[], filter: SentimentFilter): Comment[] {
  if (filter === 'all') return comments;
  const option = FILTERS.find((f) => f.value === filter);
  if (!option || option.code === undefined) return comments;
  return comments.filter((comment) => sentimentCode(comment) === option.code);
}

function countComments(comments: Comment[], filter: SentimentFilter): number {
  return filterComments(comments, filter).length;
}

interface CommentSentimentFilterProps {
  comments: Comment[];
  /** Renders one row; the component owns the order and the grouping. */
  renderComment: (comment: Comment, index: number) => ReactNode;
}

/**
 * The control above the comment list, plus the rows it selects. Its visible
 * label names the mechanism on purpose: a reader must not mistake a keyword
 * count for a model's reading of the thread.
 */
export default function CommentSentimentFilter({
  comments,
  renderComment,
}: CommentSentimentFilterProps) {
  const { t } = useTranslation('post');
  const [filter, setFilter] = useState<SentimentFilter>('all');
  const visible = filterComments(comments, filter);

  return (
    <div>
      <div
        role="group"
        aria-label={t('sentiment.groupAria')}
        className="flex flex-wrap items-center gap-2"
      >
        {FILTERS.map((option) => {
          const active = option.value === filter;
          return (
            <button
              key={option.value}
              type="button"
              aria-pressed={active}
              onClick={() => setFilter(option.value)}
              className={
                'rounded-full border px-2.5 py-1 text-xs transition-colors '
                + (active
                  ? 'border-vibe-text/30 bg-vibe-text/10 text-vibe-text'
                  : 'border-vibe-border bg-vibe-surface text-vibe-muted hover:border-vibe-text/25 hover:text-vibe-text')
              }
            >
              {t('sentiment.' + option.labelKey)} ({countComments(comments, option.value)})
            </button>
          );
        })}
      </div>
      <p className="mt-1.5 mb-4 text-xs text-vibe-faint">
        {t('sentiment.explanation')}
      </p>

      {visible.length > 0 ? (
        <div className="space-y-4">{visible.map(renderComment)}</div>
      ) : (
        <div className="rounded-lg border border-vibe-border bg-vibe-surface px-4 py-3 text-xs text-vibe-muted">
          {t('sentiment.emptyGroup', { n: comments.length })}{' '}
          <button
            type="button"
            onClick={() => setFilter('all')}
            className="text-vibe-text underline underline-offset-2 hover:text-vibe-text/80"
          >
            {t('sentiment.showAll', { n: comments.length })}
          </button>
        </div>
      )}
    </div>
  );
}
