import type { AtomicStep, StepCondition } from '@/types/models';
import { MAX_PAUSE_MULTIPLIER } from '@/store/storeDataNormalization';
import { clamp } from '@/utils/math';

export interface BuildModeStepForm {
  id?: string;
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

function withId(id: string | undefined): Pick<AtomicStep, 'id'> {
  return id ? { id } : {};
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
      step = { ...withId(form.id), type: 'show_page', pageIndex: safePageIdx };
      break;
    case 'show_all_pages':
      step = { ...withId(form.id), type: 'show_all_pages' };
      break;
    case 'wait_for_tap_to_reveal_next':
      step = { ...withId(form.id), type: 'wait_for_tap_to_reveal_next' };
      break;
    case 'wait_for_tap_to_reveal':
      step = { ...withId(form.id), type: 'wait_for_tap_to_reveal' };
      break;
    case 'show_ratings':
      step = { ...withId(form.id), type: 'show_ratings' };
      break;
    case 'speak_page':
      step = { ...withId(form.id), type: 'speak_page', pageIndex: safePageIdx };
      break;
    case 'dynamic_pause':
      step = {
        ...withId(form.id),
        type: 'dynamic_pause',
        nextPageIndex: safePageIdx,
        pauseMultiplier: safeMultiplier,
      };
      break;
    case 'wait':
      step = { ...withId(form.id), type: 'wait', ms: form.newMs };
      break;
    case 'listen_and_check':
      step = {
        ...withId(form.id),
        type: 'listen_and_check',
        pageIndex: safePageIdx,
        successThreshold: form.newThreshold,
      };
      break;
    case 'feedback_success':
      step = { ...withId(form.id), type: 'feedback_success' };
      break;
    case 'feedback_error':
      step = { ...withId(form.id), type: 'feedback_error' };
      break;
    case 'auto_rate_from_answer':
      step = { ...withId(form.id), type: 'auto_rate_from_answer' };
      break;
    case 'auto_rate_fixed':
      step = { ...withId(form.id), type: 'auto_rate_fixed', rating: safeRating };
      break;
    case 'mark_failed':
      step = { ...withId(form.id), type: 'mark_failed' };
      break;
    case 'next_card':
      step = { ...withId(form.id), type: 'next_card' };
      break;
    default:
      return null;
  }

  return withCondition(step, form.newCondition);
}
