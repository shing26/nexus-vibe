import { Check, Loader2 } from 'lucide-react';
import { useEffect, useState, type FC } from 'react';
import { useReducedMotion } from 'motion/react';
import { REVIEW_STEPS, type ReviewStepStatus } from './reviewSteps';

interface ReviewStepStripProps {
  /**
   * `replay` reveals the steps of a finished run in order, confirming each one as it goes:
   * `completed` counts how many are done (0..4).
   *
   * `running` is the honest opposite - a review really is in flight, so exactly one step
   * shows a spinner and none is ever confirmed, because the UI only knows REVIEWING and not
   * which stage the worker is standing in.
   */
  mode: 'replay' | 'running';
  completed?: number;
  className?: string;
}

const RUNNING_CYCLE_MS = 1400;

const rowStyles: Record<ReviewStepStatus, string> = {
  waiting: 'border-vibe-border/60 bg-vibe-surface/40 text-slate-500',
  active: 'border-vibe-neon/40 bg-vibe-surface text-vibe-neon',
  done: 'border-vibe-border bg-vibe-surface/80 text-slate-300',
};

export const ReviewStepStrip: FC<ReviewStepStripProps> = ({ mode, completed = 0, className = '' }) => {
  const reduceMotion = useReducedMotion();
  const [cycling, setCycling] = useState(0);

  useEffect(() => {
    if (mode !== 'running' || reduceMotion) return;
    const timer = setInterval(() => setCycling((i) => (i + 1) % REVIEW_STEPS.length), RUNNING_CYCLE_MS);
    return () => clearInterval(timer);
  }, [mode, reduceMotion]);

  const statusFor = (index: number): ReviewStepStatus => {
    if (mode === 'replay') {
      if (index < completed) return 'done';
      return index === completed ? 'active' : 'waiting';
    }
    return index === (reduceMotion ? 0 : cycling) ? 'active' : 'waiting';
  };

  return (
    <ol className={'space-y-1 ' + className} aria-label="Review steps">
      {REVIEW_STEPS.map((step, index) => {
        const status = statusFor(index);
        const StepIcon = step.icon;
        return (
          <li
            key={step.key}
            data-step={step.key}
            data-state={status}
            className={'flex items-center gap-2 rounded-md border px-2 py-1.5 transition-colors ' + rowStyles[status]}
          >
            <span className="w-4 shrink-0 text-[10px] font-mono tabular-nums text-slate-500">
              {String(index + 1).padStart(2, '0')}
            </span>
            {status === 'done' ? (
              <Check className="h-3.5 w-3.5 shrink-0 text-vibe-emerald" />
            ) : status === 'active' ? (
              <Loader2 className="h-3.5 w-3.5 shrink-0 animate-spin motion-reduce:animate-none" />
            ) : (
              <StepIcon className="h-3.5 w-3.5 shrink-0 opacity-50" />
            )}
            <span className="min-w-0 flex-1 truncate text-[11px] font-mono">{step.label}</span>
            {status === 'done' && <span className="shrink-0 text-[10px] font-mono text-vibe-emerald">ok</span>}
            {status === 'active' && mode === 'running' && (
              <span className="shrink-0 text-[10px] font-mono text-slate-500">in progress</span>
            )}
          </li>
        );
      })}
    </ol>
  );
};
