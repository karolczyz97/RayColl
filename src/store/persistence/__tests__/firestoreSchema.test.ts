import { describe, it, expect } from '@jest/globals';

import { createNewSrsState } from '../../../srs/srsEngine';
import { normalizeStoreData } from '../../storeDataNormalization';
import {
  deserializeCardDoc,
  deserializeDeckDoc,
  deserializeStudyModeDoc,
} from '../firestoreSchema';

describe('firestoreSchema', () => {
  const card = deserializeCardDoc('card-1', {
    pages: ['hello', 'czesc'],
    srsState: createNewSrsState(),
  });

  const deck = deserializeDeckDoc(
    'deck-1',
    {
      name: 'Travel',
      activeModeId: 'classic',
      pageNames: 'broken',
      pageLanguages: ['en-US'],
      studyFilter: 'unsupported',
    },
    [card],
  );

  const normalized = normalizeStoreData({
    groups: [deck],
    studyModes: [
      deserializeStudyModeDoc('classic', {
        name: 'Classic',
        steps: [],
      }),
    ],
    activityHeatmap: {},
  });

  it('normalizes missing activePageCount from surviving page arrays', () => {
    expect(normalized.groups[0].activePageCount).toBe(2);
  });

  it('regenerates missing page names', () => {
    expect(normalized.groups[0].pageNames).toEqual(['Page 1', 'Page 2']);
  });

  it('pads missing page languages safely', () => {
    expect(normalized.groups[0].pageLanguages).toEqual(['en-US', 'en-US']);
  });

  it('falls back to default studyFilter for invalid value', () => {
    expect(normalized.groups[0].studyFilter).toBe('new+review');
  });

  it('throws when deck doc is missing a valid name', () => {
    expect(() =>
      deserializeDeckDoc(
        'broken-deck',
        { activeModeId: 'classic', pageNames: ['Front', 'Back'], pageLanguages: ['en-US', 'pl-PL'] },
        [],
      ),
    ).toThrow('missing a valid name');
  });

  it('throws when card has invalid SRS state', () => {
    expect(() =>
      deserializeCardDoc('broken-card', {
        pages: ['hello', 'czesc'],
        srsState: { ...createNewSrsState(), repetitions: '2' },
      }),
    ).toThrow('srsState.repetitions');
  });
});
