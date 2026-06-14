import { StudyMode } from '@/types/models';

const SEED_MODES: StudyMode[] = [
  {
    id: 'classic',
    name: 'Klasyczny',
    isBuiltIn: true,
    builtInSourceId: 'classic',
    steps: [
      { type: 'show_page', pageIndex: 0 },
      { type: 'speak_page', pageIndex: 0, pauseMultiplier: 0 },
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
      { type: 'speak_page', pageIndex: 0, pauseMultiplier: 0 },
      { type: 'listen_and_branch', pageIndex: 1, successThreshold: 70, incorrectTtsPageIndex: 1 },
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

const BUILT_IN_SOURCE_IDS = SEED_MODES.map((mode) => mode.builtInSourceId ?? mode.id);

export function getBuiltInModeSourceIds(): string[] {
  return BUILT_IN_SOURCE_IDS;
}

export function isBuiltInModeSourceId(value: string): boolean {
  return BUILT_IN_SOURCE_IDS.includes(value);
}


