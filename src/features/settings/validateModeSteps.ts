import type { ModeStep } from '@/types/models';

export type ModeStepIssueSeverity = 'error' | 'warning';

export interface ModeStepIssue {
  severity: ModeStepIssueSeverity;
  messageKey: string;
  params?: Record<string, string | number>;
}

// Kroki opuszczające kartę / kończące ją oceną — każda sensowna konfiguracja
// powinna mieć przynajmniej jeden taki terminal, inaczej karta utknie.
const TERMINAL_STEP_TYPES = new Set<ModeStep['type']>(['show_ratings', 'next_card']);

function stepPageIndex(step: ModeStep): number | null {
  if (
    step.type === 'show_page' ||
    step.type === 'speak_page' ||
    step.type === 'listen_and_check'
  ) {
    return step.pageIndex;
  }
  if (step.type === 'dynamic_pause') return step.nextPageIndex;
  return null;
}

/**
 * Waliduje konfigurację trybu. NIE naprawia niczego automatycznie i NIE dodaje
 * kroków — tylko zwraca listę problemów do pokazania/zablokowania zapisu.
 *
 * - error: krok wskazuje stronę poza zakresem (blokuje zapis).
 * - warning: lista nie ma jawnego terminala (show_ratings / next_card).
 */
export function validateModeSteps(steps: ModeStep[], pageCount: number): ModeStepIssue[] {
  const issues: ModeStepIssue[] = [];

  steps.forEach((step, index) => {
    const pageIndex = stepPageIndex(step);
    if (pageIndex !== null && (pageIndex < 0 || pageIndex >= pageCount)) {
      issues.push({
        severity: 'error',
        messageKey: 'settings.validation.step_page_out_of_range',
        params: { index: index + 1, page: pageIndex + 1 },
      });
    }
  });

  if (steps.length > 0 && !steps.some((step) => TERMINAL_STEP_TYPES.has(step.type))) {
    issues.push({ severity: 'warning', messageKey: 'settings.validation.no_terminal' });
  }

  return issues;
}

export function hasBlockingStepIssue(issues: ModeStepIssue[]): boolean {
  return issues.some((issue) => issue.severity === 'error');
}
