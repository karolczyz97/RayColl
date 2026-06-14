import type { ModeStep, StepCondition } from '@/types/models';
import { MAX_PAUSE_MULTIPLIER } from '@/store/storeDataNormalization';

export interface BuildModeStepForm {
  id?: string;
  newStepType: string;
  pageCount: number;
  newPageIdx: number;
  newMs: number;
  newPauseMultiplier: number;
  newThreshold: number;
  newCondition: 'always' | StepCondition;
}

function toFiniteInteger(value: number, fallback: number): number {
  return Number.isFinite(value) ? Math.trunc(value) : fallback;
}

function clamp(value: number, min: number, max: number): number {
  return Math.max(min, Math.min(max, value));
}

function withCondition(step: ModeStep, condition: 'always' | StepCondition): ModeStep {
  return condition === 'always' ? step : { ...step, condition };
}

function withId(id: string | undefined): Pick<ModeStep, 'id'> {
  return id ? { id } : {};
}

export function buildModeStep(form: BuildModeStepForm): ModeStep | null {
  const safePageCount = Math.max(1, toFiniteInteger(form.pageCount, 1));
  const safePageIdx = clamp(toFiniteInteger(form.newPageIdx, 0), 0, safePageCount - 1);
  const safeMultiplier = clamp(
    toFiniteInteger(form.newPauseMultiplier, 1),
    0,
    MAX_PAUSE_MULTIPLIER,
  );

  let step: ModeStep;
  switch (form.newStepType) {
    case 'show_page':
      step = { ...withId(form.id), type: 'show_page', pageIndex: safePageIdx };
      break;
    case 'speak_page':
      step = {
        ...withId(form.id),
        type: 'speak_page',
        pageIndex: safePageIdx,
        pauseMultiplier: safeMultiplier,
      };
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
    case 'listen_and_branch':
      step = {
        ...withId(form.id),
        type: 'listen_and_branch',
        pageIndex: safePageIdx,
        successThreshold: form.newThreshold,
      };
      break;
    case 'listen_and_check':
      step = {
        ...withId(form.id),
        type: 'listen_and_check',
        pageIndex: safePageIdx,
        successThreshold: form.newThreshold,
      };
      break;
    case 'rate':
      step = { ...withId(form.id), type: 'rate' };
      break;
    case 'next_card':
      step = { ...withId(form.id), type: 'next_card' };
      break;
    default:
      return null;
  }

  return withCondition(step, form.newCondition);
}
