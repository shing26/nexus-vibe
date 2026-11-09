import { useState } from 'react';
import { Check, Copy } from 'lucide-react';
import { readWorkedExample } from './promptMetadata';

function CopyControl({ label, text }: { label: string; text: string }) {
  const [copied, setCopied] = useState(false);

  const handleCopy = async () => {
    await navigator.clipboard.writeText(text);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <button
      type="button"
      onClick={handleCopy}
      className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-vibe-cyan/20 border border-vibe-cyan/30 text-vibe-cyan text-[11px] font-mono hover:bg-vibe-cyan/30 transition-colors active:scale-[0.97]"
    >
      {copied ? <Check className="w-3.5 h-3.5" /> : <Copy className="w-3.5 h-3.5" />}
      {copied ? 'Copied' : label}
    </button>
  );
}

interface PromptWorkedExampleProps {
  /** The post's `postType`; anything but `prompt` renders nothing at all. */
  postType?: string;
  /** The stored `promptMetadata` JSON this block reads the example from. */
  promptMetadata?: string;
  /** The prompt as the playground renders it (variables substituted). */
  prompt: string;
}

/**
 * One input/output block beside the playground, filled from the post's stored
 * metadata. Returns `null` — no frame, no placeholder — when the post is not a
 * prompt or when neither example key is present, so a post written before this
 * feature is pixel-identical to before.
 *
 * The primary copy control copies the rendered prompt, not the example: a
 * reader who copies here wants the prompt the example was produced by. A second
 * control copies the example input and says so.
 */
export default function PromptWorkedExample({
  postType,
  promptMetadata,
  prompt,
}: PromptWorkedExampleProps) {
  if (postType !== 'prompt') return null;
  const { exampleInput, exampleOutput } = readWorkedExample(promptMetadata);
  if (!exampleInput && !exampleOutput) return null;

  return (
    <div className="max-w-3xl mx-auto mb-8">
      <div className="bg-vibe-surface border border-vibe-border rounded-lg overflow-hidden">
        <div className="flex items-center h-9 bg-vibe-card border-b border-vibe-border select-none">
          <div className="flex items-center gap-1.5 px-3">
            <div className="w-2.5 h-2.5 rounded-full bg-red-500/60" />
            <div className="w-2.5 h-2.5 rounded-full bg-yellow-500/60" />
            <div className="w-2.5 h-2.5 rounded-full bg-green-500/60" />
          </div>
          <span className="flex-1 text-center text-[11px] font-mono text-slate-500 truncate px-2">
            worked_example — stored with this prompt
          </span>
          <div className="w-16" />
        </div>

        <div className="p-4 space-y-4">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <span className="text-[11px] font-mono text-slate-500">
              // one input and the output it produced
            </span>
            <div className="flex flex-wrap gap-2">
              {exampleInput && <CopyControl label="Copy Input" text={exampleInput} />}
              <CopyControl label="Copy Prompt" text={prompt} />
            </div>
          </div>

          {exampleInput && (
            <div>
              <h3 className="text-[10px] font-mono uppercase tracking-wider text-slate-400 mb-1.5">Input</h3>
              <pre className="w-full max-h-48 overflow-y-auto p-3 bg-vibe-bg border border-vibe-border rounded-lg text-xs font-mono text-slate-200 leading-relaxed whitespace-pre-wrap">
                {exampleInput}
              </pre>
            </div>
          )}

          {exampleOutput && (
            <div>
              <h3 className="text-[10px] font-mono uppercase tracking-wider text-slate-400 mb-1.5">Output</h3>
              <pre className="w-full max-h-48 overflow-y-auto p-3 bg-vibe-bg border border-vibe-border rounded-lg text-xs font-mono text-slate-200 leading-relaxed whitespace-pre-wrap">
                {exampleOutput}
              </pre>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
