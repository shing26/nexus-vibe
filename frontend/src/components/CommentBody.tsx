import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';

/**
 * A comment, rendered as markdown.
 *
 * It exists because of one specific failure. The AI reviewer writes headings, bold runs and a
 * trailing rule, and the comment list used to print `{comment.content}` inside a single `<p>`:
 * every newline collapsed, so the longest and most important comment on the page read as one
 * paragraph of literal `## AI Code Review **Overall Score**: 3/10`. The collapse was never
 * specific to the agent - a user pasting a stack trace hit it too - which is why this renders
 * every comment the same way rather than branching on the author.
 *
 * It is a component rather than an inline expression so that the behaviour has a seam a test
 * can hold; the page it came from is long enough that the rendering was invisible to review.
 */
export default function CommentBody({ content }: { content: string }) {
  return (
    <div className="text-xs font-mono text-slate-400 prose prose-invert prose-sm max-w-none prose-headings:text-slate-200 prose-headings:font-mono prose-headings:text-xs prose-headings:mt-3 prose-headings:mb-1.5 prose-p:my-1.5 prose-ul:my-1.5 prose-li:my-0.5 prose-strong:text-slate-200 prose-code:text-vibe-cyan prose-code:bg-vibe-card prose-code:px-1 prose-code:py-0.5 prose-code:text-[11px] prose-code:before:content-none prose-code:after:content-none prose-hr:my-3">
      <ReactMarkdown remarkPlugins={[remarkGfm]}>{content}</ReactMarkdown>
    </div>
  );
}
