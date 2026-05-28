import { createNewSrsState } from '../../../srs/srsEngine';
import { mergeUserData } from '../merge';
import { DEFAULT_STUDY_FILTER } from '../../storeDataNormalization';

function assertEqual<T>(actual: T, expected: T, message: string) {
  if (actual !== expected) {
    throw new Error(`${message}: expected ${expected}, got ${actual}`);
  }
}

export async function runTests() {
  console.log('\n--- Running Merge Selector Tests ---');

  const cloudCard = {
    id: 'c1',
    pages: ['cloud', 'card'],
    srsState: { ...createNewSrsState(), repetitions: 1 },
  };
  const localCard = {
    id: 'c1',
    pages: ['local', 'card'],
    srsState: { ...createNewSrsState(), repetitions: 4 },
  };

  const merged = mergeUserData(
    {
      groups: [
        {
          id: 'g1',
          name: 'Local deck',
          cards: [localCard],
          activeModeId: 'custom-mode',
          studyFilter: 'new+review',
          pageLanguages: ['en-US', 'pl-PL'],
          pageNames: ['Front', 'Back'],
          activePageCount: 2,
        },
      ],
      studyModes: [{ id: 'custom-mode', name: 'Custom', steps: [] }],
      activityHeatmap: { '2026-05-27': 3 },
    },
    {
      groups: [
        {
          id: 'g1',
          name: 'Cloud deck',
          cards: [cloudCard],
          activeModeId: '',
          studyFilter: DEFAULT_STUDY_FILTER,
          pageLanguages: ['en-US', 'pl-PL'],
          pageNames: ['Front', 'Back'],
          activePageCount: 2,
        },
      ],
      studyModes: [{ id: 'classic', name: 'Classic', steps: [] }],
      activityHeatmap: { '2026-05-27': 1 },
    },
  );

  assertEqual(merged.studyModes.some((mode) => mode.id === 'custom-mode'), true, 'Merge should preserve custom modes');
  assertEqual(merged.groups[0].cards[0].pages[0], 'local', 'Merge should keep the card with more repetitions');
  assertEqual(merged.activityHeatmap['2026-05-27'], 3, 'Merge should keep the maximum activity count');

  console.log('Merge selector tests passed');
}
