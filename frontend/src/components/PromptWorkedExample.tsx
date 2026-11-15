import { useState } from 'react';
import { Check, Copy } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { readWorkedExample } from './promptMetadata';

function CopyControl({ label, text }: { label: string; text: string }) {
  const { t } = useTranslation('post');
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
      className="inline-flex items-center gap-1.5 rounded-md border border-vibe-border bg-vibe-surface px-3 py-1.5 text-xs font-medium text-vibe-text transition-colors hover:border-vibe-accent/40 hover:text-vibe-accent focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-vibe-accent"
    >
      {copied ? <Check className="h-3.5 w-3.5 text-vibe-success" /> : <Copy className="h-3.5 w-3.5" />}
      {copied ? t('workedExample.copied') : label}
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
  const { t } = useTranslation('post');
  if (postType !== 'prompt') return null;
  const { exampleInput, exampleOutput } = readWorkedExample(promptMetadata);
  if (!exampleInput && !exampleOutput) return null;

  return (
    <div className="mx-auto mb-8 max-w-3xl">
      <div className="overflow-hidden rounded-lg border border-vibe-border bg-vibe-surface">
        <div className="flex items-center border-b border-vibe-border bg-vibe-card px-4 py-2.5">
          <span className="min-w-0 truncate text-xs font-medium text-vibe-text">
            {t('workedExample.title')}
          </span>
        </div>

        <div className="space-y-4 p-4">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <span className="text-xs text-vibe-faint">
              {t('workedExample.subtitle')}
            </span>
            <div className="flex flex-wrap gap-2">
              {exampleInput && <CopyControl label={t('workedExample.copyInput')} text={exampleInput} />}
              <CopyControl label={t('workedExample.copyPrompt')} text={prompt} />
            </div>
          </div>

          {exampleInput && (
            <div>
              <h3 className="mb-1.5 text-xs font-semibold uppercase tracking-wide text-vibe-muted">
                {t('workedExample.input')}
              </h3>
              <pre className="max-h-48 w-full overflow-y-auto whitespace-pre-wrap rounded-lg border border-vibe-border bg-vibe-bg p-3 font-mono text-xs leading-relaxed text-vibe-text">
                {exampleInput}
              </pre>
            </div>
          )}

          {exampleOutput && (
            <div>
              <h3 className="mb-1.5 text-xs font-semibold uppercase tracking-wide text-vibe-muted">
                {t('workedExample.output')}
              </h3>
              <pre className="max-h-48 w-full overflow-y-auto whitespace-pre-wrap rounded-lg border border-vibe-border bg-vibe-bg p-3 font-mono text-xs leading-relaxed text-vibe-text">
                {exampleOutput}
              </pre>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
