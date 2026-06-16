import { StudyMode } from '@/types/models';

// Wbudowane tryby wyrażone WYŁĄCZNIE przez primitive steps — żadnych makro-kroków.
// To jest po prostu jawna lista kroków; runner robi dokładnie to, co na liście.
const SEED_MODES: StudyMode[] = [
  {
    id: 'classic',
    name: 'Klasyczny',
    isBuiltIn: true,
    builtInSourceId: 'classic',
    updatedAt: 0,
    steps: [
      { type: 'show_page', pageIndex: 0 },
      { type: 'speak_page', pageIndex: 0 },
      { type: 'wait_for_tap_to_reveal' },
      { type: 'show_all_pages' },
      { type: 'show_ratings' },
    ],
  },
  {
    id: 'listen-speak',
    name: 'Audio',
    isBuiltIn: true,
    builtInSourceId: 'listen-speak',
    updatedAt: 0,
    // STT z auto-oceną na correct/wrong. Skip (tap w STT) -> status skipped:
    // gałęzie correct/wrong się nie odpalają, więc spada na terminal "zawsze"
    // (Pokaż wszystkie strony + Pokaż przyciski oceny) -> ręczna ocena. Karta
    // nigdy nie zamarza.
    steps: [
      { type: 'show_page', pageIndex: 0 },
      { type: 'speak_page', pageIndex: 0 },
      { type: 'listen_and_check', pageIndex: 1, successThreshold: 70 },

      { type: 'feedback_success', condition: 'correct' },
      { type: 'show_all_pages', condition: 'correct' },
      { type: 'auto_rate_from_answer', condition: 'correct' },
      { type: 'next_card', condition: 'correct' },

      { type: 'feedback_error', condition: 'wrong' },
      { type: 'show_all_pages', condition: 'wrong' },
      { type: 'mark_failed', condition: 'wrong' },
      { type: 'auto_rate_fixed', rating: 1, condition: 'wrong' },
      { type: 'next_card', condition: 'wrong' },

      { type: 'show_all_pages' },
      { type: 'show_ratings' },
    ],
  },
];

export function createSeedModes(): StudyMode[] {
  // Return deep clones so callers can't mutate the shared seed definitions.
  return SEED_MODES.map((mode) => ({
    ...mode,
    steps: mode.steps.map((step) => ({ ...step })),
  }));
}

const BUILT_IN_SOURCE_IDS = SEED_MODES.map((mode) => mode.builtInSourceId).filter(
  (id): id is string => id !== undefined,
);

export function isBuiltInModeSourceId(value: string): boolean {
  return BUILT_IN_SOURCE_IDS.includes(value);
}
