import type { AtomicStep, StepCondition } from '@/types/models';
import { MAX_PAUSE_MULTIPLIER } from '@/store/storeDataNormalization';
import { clamp } from '@/utils/math';

export const ATOMIC_STEP_TYPE_ORDER: AtomicStep['type'][] = [
  'show_page',
  'show_all_pages',
  'wait_for_tap_to_reveal_next',
  'wait_for_tap_to_reveal',
  'speak_page',
  'listen_and_check',
  'dynamic_pause',
  'wait',
  'feedback_success',
  'feedback_error',
  'show_ratings',
  'auto_rate_from_answer',
  'auto_rate_fixed',
  'mark_failed',
  'next_card',
];

export const ATOMIC_STEP_LABEL_KEYS: Record<AtomicStep['type'], string> = {
  show_page: 'step.type.show_page',
  show_all_pages: 'step.type.show_all_pages',
  wait_for_tap_to_reveal_next: 'step.type.wait_for_tap_to_reveal_next',
  wait_for_tap_to_reveal: 'step.type.wait_for_tap_to_reveal',
  speak_page: 'step.type.speak_page',
  listen_and_check: 'step.type.listen_and_check',
  dynamic_pause: 'step.type.dynamic_pause',
  wait: 'step.type.wait',
  feedback_success: 'step.type.feedback_success',
  feedback_error: 'step.type.feedback_error',
  show_ratings: 'step.type.show_ratings',
  auto_rate_from_answer: 'step.type.auto_rate_from_answer',
  auto_rate_fixed: 'step.type.auto_rate_fixed',
  mark_failed: 'step.type.mark_failed',
  next_card: 'step.type.next_card',
};

export interface BuildModeStepForm {
  newStepType: string;
  pageCount: number;
  newPageIdx: number;
  newMs: number;
  newPauseMultiplier: number;
  newThreshold: number;
  newRating: number;
  newCondition: 'always' | StepCondition;
}

function toFiniteInteger(value: number, fallback: number): number {
  return Number.isFinite(value) ? Math.trunc(value) : fallback;
}

function withCondition(step: AtomicStep, condition: 'always' | StepCondition): AtomicStep {
  return condition === 'always' ? step : { ...step, condition };
}

export function buildModeStep(form: BuildModeStepForm): AtomicStep | null {
  const safePageCount = Math.max(1, toFiniteInteger(form.pageCount, 1));
  const safePageIdx = clamp(toFiniteInteger(form.newPageIdx, 0), 0, safePageCount - 1);
  const safeMultiplier = clamp(
    toFiniteInteger(form.newPauseMultiplier, 1),
    0,
    MAX_PAUSE_MULTIPLIER,
  );
  const safeRating = clamp(toFiniteInteger(form.newRating, 3), 1, 4);

  let step: AtomicStep;
  switch (form.newStepType) {
    case 'show_page':
      step = { type: 'show_page', pageIndex: safePageIdx };
      break;
    case 'show_all_pages':
      step = { type: 'show_all_pages' };
      break;
    case 'wait_for_tap_to_reveal_next':
      step = { type: 'wait_for_tap_to_reveal_next' };
      break;
    case 'wait_for_tap_to_reveal':
      step = { type: 'wait_for_tap_to_reveal' };
      break;
    case 'show_ratings':
      step = { type: 'show_ratings' };
      break;
    case 'speak_page':
      step = { type: 'speak_page', pageIndex: safePageIdx };
      break;
    case 'dynamic_pause':
      step = {
        type: 'dynamic_pause',
        nextPageIndex: safePageIdx,
        pauseMultiplier: safeMultiplier,
      };
      break;
    case 'wait':
      step = { type: 'wait', ms: form.newMs };
      break;
    case 'listen_and_check':
      step = {
        type: 'listen_and_check',
        pageIndex: safePageIdx,
        successThreshold: form.newThreshold,
      };
      break;
    case 'feedback_success':
      step = { type: 'feedback_success' };
      break;
    case 'feedback_error':
      step = { type: 'feedback_error' };
      break;
    case 'auto_rate_from_answer':
      step = { type: 'auto_rate_from_answer' };
      break;
    case 'auto_rate_fixed':
      step = { type: 'auto_rate_fixed', rating: safeRating };
      break;
    case 'mark_failed':
      step = { type: 'mark_failed' };
      break;
    case 'next_card':
      step = { type: 'next_card' };
      break;
    default:
      return null;
  }

  return withCondition(step, form.newCondition);
}
