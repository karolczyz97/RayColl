import {
  IMPORT_STATE_JSON_ERROR,
  normalizeBackupImportData,
  parseBackupJson,
} from '@/store/useStoreActions';

describe('parseBackupJson', () => {
  it('parses valid JSON', () => {
    expect(parseBackupJson('{"groups":[]}')).toEqual({ groups: [] });
  });

  it('throws a stable translation key for invalid JSON', () => {
    expect(() => parseBackupJson('{bad json')).toThrow(IMPORT_STATE_JSON_ERROR);
  });
});

describe('normalizeBackupImportData', () => {
  const srsState = {
    difficulty: 1,
    stability: 1,
    repetitions: 0,
    state: 0,
    lastReviewTimestamp: 0,
    nextReviewTimestamp: 0,
  };

  it('normalizes repairable boundary data before validating the backup', () => {
    const normalized = normalizeBackupImportData({
      groups: [
        {
          id: 'g1',
          name: 'Deck',
          cards: [
            {
              id: 'c1',
              pages: ['front', 'back'],
              srsState,
            },
          ],
          activeModeId: 'classic',
          pageNames: ['Front'],
          cardOrder: 'unknown-order',
        },
      ],
      studyModes: [
        {
          id: 'custom',
          name: 'Custom',
          isBuiltIn: false,
          steps: [
            { type: 'unsupported_step' },
            { type: 'show_page', pageIndex: 0, condition: 'nonsense' },
          ],
        },
      ],
      activityHeatmap: {
        good: 2,
        bad: '3',
      },
    });

    expect(normalized.groups[0]).toEqual(
      expect.objectContaining({
        activePageCount: 2,
        cardOrder: 'sequential',
        pageLanguages: ['en-US', 'en-US'],
        pageNames: ['Front', 'Page 2'],
        studyFilter: 'new+review',
      }),
    );
    expect(normalized.studyModes.find((mode) => mode.id === 'custom')?.steps).toEqual([
      { type: 'show_page', pageIndex: 0 },
    ]);
    expect(normalized.activityHeatmap).toEqual({ good: 2 });
  });

  it('still rejects backups without the required root collections', () => {
    expect(() => normalizeBackupImportData({ groups: [] })).toThrow(
      'Backup data must contain a "studyModes" array.',
    );
  });
});
