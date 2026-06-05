import { describe, it, expect } from '@jest/globals';

import { createPersistenceQueueController } from '../persistenceQueue';
import type { PersistenceSnapshot, PersistenceQueueCallbacks } from '../persistenceQueue';
import type { FlashcardGroup } from '../../../types/models';
import { DEFAULT_STUDY_FILTER } from '../../storeDataNormalization';

function createGroup(id: string): FlashcardGroup {
  return {
    id,
    name: id,
    cards: [],
    activeModeId: 'mode-1',
    studyFilter: DEFAULT_STUDY_FILTER,
    pageLanguages: ['en-US', 'pl-PL'],
    pageNames: ['Front', 'Back'],
    activePageCount: 2,
  };
}

function makeCallbacksRef(callbacks: Partial<PersistenceQueueCallbacks>) {
  return { current: { persistNow: async () => {}, ...callbacks } };
}

function makeSnapshot(groupId: string): PersistenceSnapshot {
  return { uid: 'u1', groups: [createGroup(groupId)], studyModes: [], activityHeatmap: {} };
}

describe('persistenceQueue', () => {
  it('collapses multiple enqueues into one save', async () => {
    const savedSnapshots: string[] = [];
    const controller = createPersistenceQueueController({
      delayMs: 10,
      callbacksRef: makeCallbacksRef({
        persistNow: async (snapshot: PersistenceSnapshot) => {
          savedSnapshots.push(snapshot.groups[0]?.id ?? 'none');
        },
      }),
    });

    controller.enqueue(makeSnapshot('first'));
    controller.enqueue(makeSnapshot('second'));
    await controller.flush();

    expect(savedSnapshots.length).toBe(1);
    expect(savedSnapshots[0]).toBe('second');
  });

  it('continues with the most recent pending snapshot after a flush during active persist', async () => {
    const sequenceSnapshots: string[] = [];
    let markFirstPersistStarted = () => {};
    const firstPersistStarted = new Promise<void>((resolve) => {
      markFirstPersistStarted = resolve;
    });
    let releaseFirstPersist = () => {};
    const firstPersistCanFinish = new Promise<void>((resolve) => {
      releaseFirstPersist = resolve;
    });

    const controller = createPersistenceQueueController({
      delayMs: 10,
      callbacksRef: makeCallbacksRef({
        persistNow: async (snapshot: PersistenceSnapshot) => {
          sequenceSnapshots.push(snapshot.groups[0]?.id ?? 'none');
          if (snapshot.groups[0]?.id === 'first') {
            markFirstPersistStarted();
            await firstPersistCanFinish;
          }
        },
      }),
    });

    controller.enqueue(makeSnapshot('first'), { immediate: true });
    await firstPersistStarted;
    controller.enqueue(makeSnapshot('second'));
    controller.enqueue(makeSnapshot('third'));
    releaseFirstPersist();
    await controller.flush();

    expect(sequenceSnapshots).toEqual(['first', 'third']);
  });

  it('invokes onError on failed flush and onSynced on subsequent success', async () => {
    let shouldFail = true;
    let errorCount = 0;
    let syncedCount = 0;

    const controller = createPersistenceQueueController({
      delayMs: 10,
      callbacksRef: makeCallbacksRef({
        persistNow: async () => {
          if (shouldFail) throw new Error('cloud down');
        },
        onError: () => { errorCount += 1; },
        onSynced: () => { syncedCount += 1; },
      }),
    });

    controller.enqueue(makeSnapshot('e1'), { immediate: true });
    await controller.flush();
    expect(errorCount).toBe(1);
    expect(syncedCount).toBe(0);

    shouldFail = false;
    controller.enqueue(makeSnapshot('e2'), { immediate: true });
    await controller.flush();
    expect(syncedCount).toBe(1);
    expect(errorCount).toBe(1);
  });
});
