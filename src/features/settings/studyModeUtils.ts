import type { ModeStep, StudyMode } from '@/types/models';
import type { TranslationFn } from '@/i18n';
import { createSeedModes } from '@/store/seed/seedModes';
import { deepEqual } from '@/utils/deepEqual';

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
  const body = formatStepBody(step, t);
  if (step.condition === 'correct') return `${t('step.condition.correct')}: ${body}`;
  if (step.condition === 'wrong') return `${t('step.condition.wrong')}: ${body}`;
  return body;
}

function formatStepBody(step: ModeStep, t: TranslationFn): string {
  switch (step.type) {
    case 'show_page':
      return t('step.show_page', { index: step.pageIndex + 1 });
    case 'speak_page':
      return t('step.speak_page', { index: step.pageIndex + 1, multiplier: step.pauseMultiplier });
    case 'dynamic_pause':
      return t('step.dynamic_pause', { index: step.nextPageIndex + 1, multiplier: step.pauseMultiplier });
    case 'wait':
      return t('step.wait', { ms: step.ms });
    case 'listen_and_branch':
      return t('step.listen_and_branch', {
        index: step.pageIndex + 1,
        threshold: step.successThreshold,
      });
    case 'listen_and_check':
      return t('step.listen_and_check', {
        index: step.pageIndex + 1,
        threshold: step.successThreshold,
      });
    case 'reveal_on_tap':
      return t('step.reveal_on_tap');
    case 'rate':
      return t('step.rate');
    case 'next_card':
      return t('step.next_card');

    default: {
      void (step as never);
      return t('step.unknown');
    }
  }
}
