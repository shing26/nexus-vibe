import { Code2, Gauge, ShieldCheck, Wand2 } from 'lucide-react';
import type { LucideIcon } from 'lucide-react';

/**
 * The four beats a review panel is allowed to name.
 *
 * <p>Each of the first three maps to a field the backend actually returns in
 * {@code AiReviewDetail} - {@code codeQuality}, {@code securityConcerns},
 * {@code optimizationSuggestions} - and the fourth is the score writeback. They are the
 * reading order of a stored review, not a claim that the pipeline runs them as sequential
 * stages: the three findings arrive in one JSON from one model call, so nothing here says
 * one finished before the next. What runs in sequence upstream - extract code blocks, call
 * the model, repair and validate, write back - is deliberately not named, because the UI
 * cannot show that a given row ever took the repair path.</p>
 */
export interface ReviewStep {
  key: string;
  label: string;
  icon: LucideIcon;
}

export const REVIEW_STEPS: ReviewStep[] = [
  { key: 'codeQuality', label: 'Code Quality', icon: Code2 },
  { key: 'securityConcerns', label: 'Security Concerns', icon: ShieldCheck },
  { key: 'optimizationSuggestions', label: 'Optimization Suggestions', icon: Wand2 },
  { key: 'score', label: 'Score Evaluation', icon: Gauge },
];

export type ReviewStepStatus = 'waiting' | 'active' | 'done';
