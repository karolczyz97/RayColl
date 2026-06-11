import type { ModeStep } from '@/types/models';
import type { TranslationFn } from '@/i18n';

export function formatStepSummary(step: ModeStep, t: TranslationFn): string {
  switch (step.type) {
    case 'show_page':
      return t('step.show_page', { index: step.pageIndex + 1 });
    case 'speak_page':
      return t('step.speak_page', { index: step.pageIndex + 1, multiplier: step.pauseMultiplier });
    case 'dynamic_pause':
      return t('step.dynamic_pause', { index: step.nextPageIndex + 1 });
    case 'wait':
      return t('step.wait', { ms: step.ms });
    case 'listen_and_branch':
      return t('step.listen_and_branch', {
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
