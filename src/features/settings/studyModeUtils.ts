import type { AtomicStep, CompoundStep, ModeStep, StudyMode } from '@/types/models';
import type { TranslationFn } from '@/i18n';
import { createSeedModes } from '@/store/seed/seedModes';
import { deepEqual } from '@/utils/deepEqual';
import { COMPOUND_LABEL_KEYS } from './compoundSteps';

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
  switch (step.type) {
    case 'show_page':
      return t('step.show_page', { index: step.pageIndex + 1 });
    case 'show_all_pages':
      return t('step.show_all_pages');
    case 'wait_for_tap_to_reveal_next':
      return t('step.wait_for_tap_to_reveal_next');
    case 'wait_for_tap_to_reveal':
      return t('step.wait_for_tap_to_reveal');
    case 'show_ratings':
      return t('step.show_ratings');
    case 'speak_page':
      return t('step.speak_page', { index: step.pageIndex + 1 });
    case 'dynamic_pause':
      return t('step.dynamic_pause', { index: step.nextPageIndex + 1, multiplier: step.pauseMultiplier });
    case 'wait':
      return t('step.wait', { ms: step.ms });
    case 'listen_and_check':
      return t('step.listen_and_check', {
        index: step.pageIndex + 1,
        threshold: step.successThreshold,
      });
    case 'feedback_success':
      return t('step.feedback_success');
    case 'feedback_error':
      return t('step.feedback_error');
    case 'auto_rate_from_answer':
      return t('step.auto_rate_from_answer');
    case 'auto_rate_fixed':
      return t('step.auto_rate_fixed', { rating: step.rating });
    case 'mark_failed':
      return t('step.mark_failed');
    case 'next_card':
      return t('step.next_card');

    default: {
      void (step as never);
      return t('step.unknown');
    }
  }
}
