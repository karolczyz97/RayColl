import type { AtomicStep, CompoundStep, ModeStep, StudyMode } from '@/types/models';
import type { TranslationFn } from '@/i18n';
import { createSeedModes } from '@/store/seed/seedModes';
import { deepEqual } from '@/utils/deepEqual';
import { COMPOUND_LABEL_KEYS } from './compoundSteps';
import { getStepDefinition, getStepSummaryParams, isAtomicStepType } from './stepRegistry';

/** Czy tryb jest wbudowany i czy jego kroki odbiegają od seedów (można je zresetować). */
export function getModeCustomization(mode: StudyMode): {
  isDefaultMode: boolean;
  hasCustomSteps: boolean;
} {
  const sourceId = mode.isBuiltIn ? mode.builtInSourceId : undefined;
  if (!sourceId) {
    return { isDefaultMode: false, hasCustomSteps: false };
  }
  const seed = createSeedModes().find((seedMode) => seedMode.id === sourceId);
  return {
    isDefaultMode: true,
    hasCustomSteps: seed ? !deepEqual(seed.steps, mode.steps) : false,
  };
}

export function formatStepSummary(step: ModeStep, t: TranslationFn): string {
  if (step.type === 'compound') {
    return formatCompoundSummary(step, t);
  }
  const body = formatStepBody(step, t);
  if (step.condition === 'correct') return `${t('step.condition.correct')}: ${body}`;
  if (step.condition === 'wrong') return `${t('step.condition.wrong')}: ${body}`;
  return body;
}

export function formatCompoundSummary(step: CompoundStep, t: TranslationFn): string {
  const name = t(COMPOUND_LABEL_KEYS[step.params.kind]);
  switch (step.params.kind) {
    case 'present_front':
      return `${name} (${t('compound.summary.page', { page: step.params.page + 1 })})`;
    case 'listen_grade':
      return `${name} (${t('compound.summary.stt', {
        page: step.params.answerPage + 1,
        threshold: step.params.threshold,
      })})`;
    case 'auto_pass_next':
      return `${name} (${t('compound.summary.rating', { rating: step.params.rating })})`;
    default:
      return name;
  }
}

function formatStepBody(step: AtomicStep, t: TranslationFn): string {
  if (!isAtomicStepType(step.type)) return t('step.unknown');
  const def = getStepDefinition(step.type);
  return t(def.summaryKey, getStepSummaryParams(step));
}
