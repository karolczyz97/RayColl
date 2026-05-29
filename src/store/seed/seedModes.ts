import { StudyMode } from '../../types/models';

export function createSeedModes(): StudyMode[] {
  return [
    {
      id: 'classic',
      name: 'Klasyczny',
      isBuiltIn: true,
      builtInSourceId: 'classic',
      steps: [
        { type: 'show_page', pageIndex: 0 },
        { type: 'speak_page', pageIndex: 0, extraPauseMs: 0 },
        { type: 'reveal_on_tap' },
        { type: 'rate' },
      ],
    },
    {
      id: 'listen-speak',
      name: 'Audio',
      isBuiltIn: true,
      builtInSourceId: 'listen-speak',
      steps: [
        { type: 'show_page', pageIndex: 0 },
        { type: 'speak_page', pageIndex: 0, extraPauseMs: 0 },
        { type: 'listen_and_branch', pageIndex: 1, successThreshold: 70, incorrectTtsPageIndex: 1 },
      ],
    },
  ];
}

export function getBuiltInModeSourceIds(): string[] {
  return createSeedModes().map((mode) => mode.builtInSourceId ?? mode.id);
}

export function isBuiltInModeSourceId(value: string): boolean {
  return getBuiltInModeSourceIds().includes(value);
}

export const DEFAULT_MODE_IDS = getBuiltInModeSourceIds();
