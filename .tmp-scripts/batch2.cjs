const fs = require('fs');

// CommentBody: token colors, keep the prose contract.
let c = fs.readFileSync('src/components/CommentBody.tsx', 'utf8');
c = c.replace(
  '<div className="text-xs font-mono text-slate-400 prose prose-invert prose-sm max-w-none prose-headings:text-slate-200 prose-headings:font-mono prose-headings:text-xs prose-headings:mt-3 prose-headings:mb-1.5 prose-p:my-1.5 prose-ul:my-1.5 prose-li:my-0.5 prose-strong:text-slate-200 prose-code:text-vibe-cyan prose-code:bg-vibe-card prose-code:px-1 prose-code:py-0.5 prose-code:text-[11px] prose-code:before:content-none prose-code:after:content-none prose-hr:my-3">',
  '<div className="prose prose-sm max-w-none text-sm text-vibe-muted dark:prose-invert prose-headings:text-vibe-text prose-headings:text-sm prose-headings:mt-3 prose-headings:mb-1.5 prose-p:my-1.5 prose-ul:my-1.5 prose-li:my-0.5 prose-strong:text-vibe-text prose-code:text-vibe-accent prose-code:bg-vibe-card prose-code:px-1 prose-code:py-0.5 prose-code:text-xs prose-code:before:content-none prose-code:after:content-none prose-hr:my-3">',
);
fs.writeFileSync('src/components/CommentBody.tsx', c, 'utf8');

// CommentSentimentFilter: dictionary labels, token styling; behavior untouched.
let s = fs.readFileSync('src/components/CommentSentimentFilter.tsx', 'utf8');
s = s.replace(
  "import { useState } from 'react';",
  "import { useState } from 'react';\nimport { useTranslation } from 'react-i18next';",
);
s = s.replace(
  "const FILTERS: readonly FilterOption[] = [\n  { value: 'all', label: 'All' },\n  { value: 'question', label: 'Questions', code: 1 },\n  { value: 'complaint', label: 'Complaints', code: 2 },\n  { value: 'praise', label: 'Praise', code: 3 },\n  // Every row that is 0, and any row whose payload predates the column.\n  { value: 'other', label: 'Unclassified', code: 0 },\n];",
  "const FILTERS: readonly FilterOption[] = [\n  { value: 'all', labelKey: 'all' },\n  { value: 'question', labelKey: 'question', code: 1 },\n  { value: 'complaint', labelKey: 'complaint', code: 2 },\n  { value: 'praise', labelKey: 'praise', code: 3 },\n  // Every row that is 0, and any row whose payload predates the column.\n  { value: 'other', labelKey: 'other', code: 0 },\n];",
);
s = s.replace(
  "interface FilterOption {\n  value: SentimentFilter;\n  label: string;",
  "interface FilterOption {\n  value: SentimentFilter;\n  labelKey: string;",
);
s = s.replace(
  "  const [filter, setFilter] = useState<SentimentFilter>('all');\n  const visible = filterComments(comments, filter);",
  "  const { t } = useTranslation('post');\n  const [filter, setFilter] = useState<SentimentFilter>('all');\n  const visible = filterComments(comments, filter);",
);
s = s.replace(
  'aria-label="Group comments by sentiment"',
  "aria-label={t('sentiment.groupAria')}",
);
s = s.replace(
  "'px-2.5 py-1 rounded-md border text-[11px] font-mono transition-colors active:scale-[0.97] '\n                + (active\n                  ? 'bg-vibe-cyan/20 border-vibe-cyan/40 text-vibe-cyan'\n                  : 'bg-vibe-surface border-vibe-border text-slate-400 hover:text-slate-200 hover:border-slate-500')",
  "'rounded-full border px-2.5 py-1 text-xs transition-colors '\n                + (active\n                  ? 'border-vibe-accent/50 bg-vibe-accent/10 text-vibe-accent'\n                  : 'border-vibe-border bg-vibe-surface text-vibe-muted hover:border-vibe-accent/40 hover:text-vibe-text')",
);
s = s.replace('{option.label} ({countComments(comments, option.value)})', "{t('sentiment.' + option.labelKey)} ({countComments(comments, option.value)})");
s = s.replace(
  '<p className="mt-1.5 mb-4 text-[10px] font-mono text-slate-400">\n        Grouped by keyword — a rule heuristic, not a model&apos;s judgement.\n      </p>',
  '<p className="mt-1.5 mb-4 text-xs text-vibe-faint">\n        {t(\'sentiment.explanation\')}\n      </p>',
);
s = s.replace(
  '<div className="rounded-lg border border-vibe-border bg-vibe-surface px-4 py-3 font-mono text-xs text-slate-500">\n          No comments in this group. Nothing was removed — the other {comments.length} are still here.{\' \'}\n          <button\n            type="button"\n            onClick={() => setFilter(\'all\')}\n            className="text-vibe-cyan hover:underline"\n          >\n            Show all {comments.length}\n          </button>\n        </div>',
  '<div className="rounded-lg border border-vibe-border bg-vibe-surface px-4 py-3 text-xs text-vibe-muted">\n          {t(\'sentiment.emptyGroup\', { n: comments.length })}{\' \'}\n          <button\n            type="button"\n            onClick={() => setFilter(\'all\')}\n            className="text-vibe-accent hover:underline"\n          >\n            {t(\'sentiment.showAll\', { n: comments.length })}\n          </button>\n        </div>',
);
fs.writeFileSync('src/components/CommentSentimentFilter.tsx', s, 'utf8');

// ShowcaseEntry: i18n + tokens.
let sh = fs.readFileSync('src/components/ShowcaseEntry.tsx', 'utf8');
sh = sh.replace(
  "import { ArrowRight, ShieldCheck } from 'lucide-react';",
  "import { ArrowRight, ShieldCheck } from 'lucide-react';\nimport { useTranslation } from 'react-i18next';",
);
sh = sh.replace(
  "export default function ShowcaseEntry() {\n  const { data } = useQuery<ShowcasePost | null>({",
  "export default function ShowcaseEntry() {\n  const { t } = useTranslation('home');\n  const { data } = useQuery<ShowcasePost | null>({",
);
sh = sh.replace(
  'className="group block border border-vibe-cyan/25 rounded-xl bg-vibe-cyan/5 hover:bg-vibe-cyan/10 hover:border-vibe-cyan/40 transition-colors"',
  'className="group block rounded-lg border border-vibe-accent/30 bg-vibe-accent/5 transition-colors hover:border-vibe-accent/50 hover:bg-vibe-accent/10"',
);
sh = sh.replace(
  '<div className="w-8 h-8 shrink-0 rounded-lg border border-vibe-cyan/30 bg-vibe-cyan/10 flex items-center justify-center">\n          <ShieldCheck className="w-4 h-4 text-vibe-cyan" />\n        </div>',
  '<div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-md border border-vibe-accent/40 bg-vibe-accent/10">\n          <ShieldCheck className="h-4 w-4 text-vibe-accent" />\n        </div>',
);
sh = sh.replace(
  '<p className="text-[10px] font-mono uppercase tracking-widest text-vibe-cyan/80">\n            Read a real review — no account needed\n          </p>',
  '<p className="text-xs font-medium uppercase tracking-wide text-vibe-accent">\n            {t(\'showcaseEntry.kicker\')}\n          </p>',
);
sh = sh.replace(
  '<p className="mt-1 text-sm font-mono text-slate-200 truncate group-hover:text-white transition-colors">\n            {data.title}\n          </p>',
  '<p className="mt-1 truncate text-sm font-medium text-vibe-text">\n            {data.title}\n          </p>',
);
sh = sh.replace(
  '<p className="mt-0.5 text-[11px] font-mono text-slate-500">\n            A recorded run of the review pipeline: it scored this snippet{\' \'}\n            {data.aiReviewScore}/10 and wrote the findings on that page. Copied verbatim, not\n            written by hand.\n          </p>',
  '
