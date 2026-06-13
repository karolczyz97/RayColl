import { describe, it, expect } from '@jest/globals';

import { createNewSrsState } from '../../../srs/srsEngine';
import { normalizeStoreData } from '../../storeDataNormalization';
import { deepEqual } from '../../../utils/deepEqual';
import { CARD_ORDERS } from '@/constants/cardOrder';
import {
  cloneUserData,
  deserializeCardDoc,
  deserializeDeckDoc,
  deserializeStudyModeDoc,
  serializeDeckDoc,
  type UserData,
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

  it('round-trips cardOrder through deck docs', () => {
    const serialized = serializeDeckDoc({
      ...normalized.groups[0],
      cardOrder: CARD_ORDERS.hardest,
    });
    const deserialized = deserializeDeckDoc('deck-1', serialized, [card]);
    expect(deserialized.cardOrder).toBe(CARD_ORDERS.hardest);
  });

  it('throws when deck doc is missing a valid name', () => {
    expect(() =>
      deserializeDeckDoc(
        'broken-deck',
        {
          activeModeId: 'classic',
          pageNames: ['Front', 'Back'],
          pageLanguages: ['en-US', 'pl-PL'],
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
      }),
    ).toThrow('srsState.repetitions');
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
