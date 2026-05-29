import { StudyMode } from '../../types/models';

export const BUILT_IN_MODE_SOURCE_IDS = ['classic', 'listen-speak'] as const;

export type BuiltInModeSourceId = (typeof BUILT_IN_MODE_SOURCE_IDS)[number];

export function isBuiltInModeSourceId(value: string): value is BuiltInModeSourceId {
  return BUILT_IN_MODE_SOURCE_IDS.includes(value as BuiltInModeSourceId);
}

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

export const DEFAULT_MODE_IDS = BUILT_IN_MODE_SOURCE_IDS;
