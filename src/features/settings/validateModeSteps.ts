import type { AtomicStep } from '@/types/models';
import { getStepPageIndex } from './stepRegistry';

export type ModeStepIssueSeverity = 'error' | 'warning';

export interface ModeStepIssue {
  severity: ModeStepIssueSeverity;
  messageKey: string;
  params?: Record<string, string | number>;
  sourceIndex?: number;
}

// Kroki opuszczające kartę / kończące ją oceną — każda sensowna konfiguracja
// powinna mieć przynajmniej jeden taki terminal, inaczej karta utknie.
const TERMINAL_STEP_TYPES = new Set<AtomicStep['type']>(['show_ratings', 'next_card']);

/**
 * Waliduje konfigurację trybu. NIE naprawia niczego automatycznie i NIE dodaje
 * kroków — tylko zwraca listę problemów do pokazania/zablokowania zapisu.
 *
 * - error: krok wskazuje stronę poza zakresem (blokuje zapis).
 * - warning: lista nie ma jawnego terminala (show_ratings / next_card).
 */
export function validateModeSteps(steps: AtomicStep[], pageCount: number): ModeStepIssue[] {
  return validateModeStepSources(
    steps.map((step, sourceIndex) => ({ step, sourceIndex })),
    pageCount,
  ).map(({ sourceIndex: _sourceIndex, ...issue }) => issue);
}

export function validateModeStepSources(
  stepSources: { step: AtomicStep; sourceIndex: number }[],
  pageCount: number,
): ModeStepIssue[] {
  const issues: ModeStepIssue[] = [];

  stepSources.forEach(({ step, sourceIndex }) => {
    const pageIndex = getStepPageIndex(step);
    if (pageIndex !== null && (pageIndex < 0 || pageIndex >= pageCount)) {
      issues.push({
        severity: 'error',
        messageKey: 'settings.validation.step_page_out_of_range',
        params: { index: sourceIndex + 1, page: pageIndex + 1 },
        sourceIndex,
      });
    }
  });

  if (
    stepSources.length > 0 &&
    !stepSources.some(({ step }) => TERMINAL_STEP_TYPES.has(step.type))
  ) {
    issues.push({ severity: 'warning', messageKey: 'settings.validation.no_terminal' });
  }

  // auto_rate_from_answer czyta wynik ostatniego listen_and_check — bez
  // wcześniejszego STT jest zawsze no-opem, co niemal na pewno jest pomyłką.
  const firstListenIndex = stepSources.find(
    ({ step }) => step.type === 'listen_and_check',
  )?.sourceIndex;
  const orphanAutoRate = stepSources.find(
    ({ step, sourceIndex }) =>
      step.type === 'auto_rate_from_answer' &&
      (firstListenIndex === undefined || sourceIndex < firstListenIndex),
  );
  if (orphanAutoRate) {
    issues.push({
      severity: 'warning',
      messageKey: 'settings.validation.auto_rate_without_stt',
      sourceIndex: orphanAutoRate.sourceIndex,
    });
  }

  return issues;
}

export function hasBlockingStepIssue(issues: ModeStepIssue[]): boolean {
  return issues.some((issue) => issue.severity === 'error');
}
