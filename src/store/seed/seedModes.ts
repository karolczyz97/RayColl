import { StudyMode } from '../../types/models';

export function createSeedModes(): StudyMode[] {
  return [
    {
      id: 'classic',
      name: 'Klasyczny',
      steps: [
        { type: 'show_page', pageIndex: 0 },
        { type: 'rate_knowledge' },
      ],
    },
    {
      id: 'listen-speak',
      name: 'Audio',
      steps: [
        { type: 'show_page', pageIndex: 0 },
        { type: 'speak_page', pageIndex: 0, extraPauseMs: 0 },
        { type: 'listen_and_branch', pageIndex: 1, successThreshold: 70, incorrectTtsPageIndex: 1 },
      ],
    },
  ];
}

export const DEFAULT_MODE_IDS = ['classic', 'listen-speak'];
