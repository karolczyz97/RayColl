import { normalizeStoreData } from '../storeDataNormalization';

describe('normalizeStoreData', () => {
  it('normalizes invalid activity heatmap values to a safe object', () => {
    const normalized = normalizeStoreData({
      groups: [],
      studyModes: [],
      activityHeatmap: null as unknown as Record<string, number>,
    });

    expect(normalized.activityHeatmap).toEqual({});
  });

  it('keeps only finite numeric activity heatmap counts', () => {
    const normalized = normalizeStoreData({
      groups: [],
      studyModes: [],
      activityHeatmap: {
        good: 3,
        badString: '4',
        badNaN: Number.NaN,
      } as unknown as Record<string, number>,
    });

    expect(normalized.activityHeatmap).toEqual({ good: 3 });
  });
});
