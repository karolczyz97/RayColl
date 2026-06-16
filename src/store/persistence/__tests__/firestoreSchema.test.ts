import { describe, it, expect } from '@jest/globals';

import { createNewSrsState } from '../../../srs/srsEngine';
import { normalizeStoreData } from '../../storeDataNormalization';
import { deepEqual } from '../../../utils/deepEqual';
import { DEFAULT_CARD_ORDER } from '@/constants/cardOrder';
import {
  cloneUserData,
  deserializeCardDoc,
  deserializeDeckDoc,
  deserializeStudyModeDoc,
  serializeDeckDoc,
  type UserData,
} from '../firestoreSchema';
import { defaultCompoundParams } from '@/features/settings/compoundSteps';

describe('firestoreSchema', () => {
  const card = deserializeCardDoc('card-1', {
    pages: ['hello', 'czesc'],
    srsState: createNewSrsState(),
    contentUpdatedAt: 1,
    srsUpdatedAt: 1,
  });

  const deck = deserializeDeckDoc(
    'deck-1',
    {
      name: 'Travel',
      activeModeId: 'classic',
      pageNames: ['Term', 'Definition'],
      pageLanguages: ['en-US'],
      studyFilter: 'all',
      cardOrder: DEFAULT_CARD_ORDER,
      activePageCount: 1,
      updatedAt: 1,
    },
    [card],
  );

  const normalized = normalizeStoreData({
    groups: [deck],
    studyModes: [
      deserializeStudyModeDoc('classic', {
        name: 'Classic',
        steps: [],
        isBuiltIn: true,
        builtInSourceId: 'classic',
        updatedAt: 1,
      }),
    ],
    activityHeatmap: {},
  });

  it('round-trips cardOrder through deck docs', () => {
    const serialized = serializeDeckDoc({
      ...normalized.groups[0],
      cardOrder: 'hardest',
    });
    const deserialized = deserializeDeckDoc('deck-1', serialized, [card]);
    expect(deserialized.cardOrder).toBe('hardest');
  });

  it('throws when deck doc is missing a valid name', () => {
    expect(() =>
      deserializeDeckDoc(
        'broken-deck',
        {
          activeModeId: 'classic',
          pageNames: ['Front', 'Back'],
          pageLanguages: ['en-US', 'pl-PL'],
          studyFilter: 'all',
          cardOrder: DEFAULT_CARD_ORDER,
          activePageCount: 2,
          updatedAt: 1,
        },
        [],
      ),
    ).toThrow('missing a valid name');
  });

  it('throws when card has invalid SRS state', () => {
    expect(() =>
      deserializeCardDoc('broken-card', {
        pages: ['hello', 'czesc'],
        srsState: { ...createNewSrsState(), repetitions: '2' },
        contentUpdatedAt: 1,
        srsUpdatedAt: 1,
      }),
    ).toThrow('srsState.repetitions');
  });

  it('deserializes valid compound study mode steps', () => {
    const mode = deserializeStudyModeDoc('custom', {
      name: 'Custom',
      steps: [{ type: 'compound', version: 1, params: defaultCompoundParams('listen_grade') }],
      isBuiltIn: false,
      updatedAt: 1,
    });

    expect(mode.steps).toEqual([
      { type: 'compound', version: 1, params: defaultCompoundParams('listen_grade') },
    ]);
  });

  it('throws for invalid compound study mode steps', () => {
    expect(() =>
      deserializeStudyModeDoc('custom', {
        name: 'Custom',
        steps: [{ type: 'compound', version: 1, params: { kind: 'mystery' } }],
        isBuiltIn: false,
        updatedAt: 1,
      }),
    ).toThrow('invalid compound params');
  });
});

describe('cloneUserData', () => {
  it('preserves undefined-valued own keys', () => {
    const original = { a: 1, b: undefined as unknown };
    const clone = cloneUserData(original as unknown as UserData) as unknown as typeof original;
    expect('b' in clone).toBe(true);
    expect(clone.b).toBeUndefined();
  });

  it('is mutation-isolated from the original', () => {
    const original = { a: { b: 1 } };
    const clone = cloneUserData(original as unknown as UserData) as unknown as typeof original;
    clone.a.b = 2;
    expect(original.a.b).toBe(1);
  });

  it('is deepEqual to the original when original has undefined-valued keys', () => {
    const original = { a: 1, b: undefined };
    const clone = cloneUserData(original as unknown as UserData) as unknown as typeof original;
    expect(deepEqual(original, clone)).toBe(true);
  });
});
