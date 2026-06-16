import { captureSnapshot, persistWithRollback } from '../rollbackHelper';
import type { StoreData } from '@/types/models';

function makeSnapshot(label: string): StoreData {
  return {
    groups: [
      {
        id: `group-${label}`,
        name: `Deck ${label}`,
        cards: [],
        activeModeId: 'classic',
        studyFilter: 'all',
        cardOrder: 'sequential',
        pageLanguages: ['en-US', 'pl-PL'],
        pageNames: ['Front', 'Back'],
        activePageCount: 2,
        updatedAt: 0,
      },
    ],
    studyModes: [],
    activityHeatmap: { [label]: 1 },
  };
}

describe('rollbackHelper', () => {
  it('captures the current store refs as a StoreData snapshot', () => {
    const snapshot = makeSnapshot('current');

    expect(
      captureSnapshot(
        { current: snapshot.groups },
        { current: snapshot.studyModes },
        { current: snapshot.activityHeatmap },
      ),
    ).toEqual(snapshot);
  });

  it('applies the previous snapshot and propagates the original persistence error', async () => {
    const previous = makeSnapshot('previous');
    const next = makeSnapshot('next');
    const originalError = new Error('write failed');
    const applySnapshot = jest.fn();
    const persistNow = jest.fn()
      .mockRejectedValueOnce(originalError)
      .mockResolvedValueOnce(undefined);

    await expect(
      persistWithRollback(applySnapshot, persistNow, previous, next, 'uid-1', 'Import state'),
    ).rejects.toBe(originalError);

    expect(applySnapshot).toHaveBeenCalledWith(previous);
    expect(persistNow).toHaveBeenNthCalledWith(1, { uid: 'uid-1', ...next });
    expect(persistNow).toHaveBeenNthCalledWith(2, { uid: 'uid-1', ...previous });
  });

  it('keeps the original error when rollback persistence also fails', async () => {
    const previous = makeSnapshot('previous');
    const next = makeSnapshot('next');
    const originalError = new Error('write failed');
    const rollbackError = new Error('rollback failed');
    const applySnapshot = jest.fn();
    const errorSpy = jest.spyOn(console, 'error').mockImplementation(() => undefined);
    const persistNow = jest.fn()
      .mockRejectedValueOnce(originalError)
      .mockRejectedValueOnce(rollbackError);

    await expect(
      persistWithRollback(applySnapshot, persistNow, previous, next, null, 'Reset'),
    ).rejects.toBe(originalError);

    expect(applySnapshot).toHaveBeenCalledWith(previous);
    expect(errorSpy).toHaveBeenCalledWith('Reset rollback persistence failed:', rollbackError);
    errorSpy.mockRestore();
  });
});
