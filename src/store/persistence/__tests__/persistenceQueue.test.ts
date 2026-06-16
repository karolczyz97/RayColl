import { describe, it, expect } from '@jest/globals';

import { createPersistenceQueueController } from '@/store/persistence/persistenceQueue';
import type { PersistenceSnapshot, PersistenceQueueCallbacks } from '@/store/persistence/persistenceQueue';
import type { FlashcardGroup } from '@/types/models';
import { DEFAULT_STUDY_FILTER } from '@/store/storeDataNormalization';

function createGroup(id: string): FlashcardGroup {
  return {
    id,
    name: id,
    cards: [],
    activeModeId: 'mode-1',
    studyFilter: DEFAULT_STUDY_FILTER,
    cardOrder: 'sequential',
    pageLanguages: ['en-US', 'pl-PL'],
    pageNames: ['Front', 'Back'],
    activePageCount: 2,
    updatedAt: 0,
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

  it('rejects and invokes onError on a failed flush, then syncs a newer snapshot', async () => {
    let shouldFail = true;
    let errorCount = 0;
    let syncedCount = 0;
    const persisted: string[] = [];

    const controller = createPersistenceQueueController({
      delayMs: 10,
      callbacksRef: makeCallbacksRef({
        persistNow: async (snapshot: PersistenceSnapshot) => {
          persisted.push(snapshot.groups[0]?.id ?? 'none');
          if (shouldFail) throw new Error('cloud down');
        },
        onError: () => { errorCount += 1; },
        onSynced: () => { syncedCount += 1; },
      }),
    });

    controller.enqueue(makeSnapshot('e1'));
    await expect(controller.flush()).rejects.toThrow('cloud down');
    expect(errorCount).toBe(1);
    expect(syncedCount).toBe(0);

    shouldFail = false;
    controller.enqueue(makeSnapshot('e2'));
    await controller.flush();
    expect(syncedCount).toBe(1);
    expect(persisted.at(-1)).toBe('e2');
  });

  it('retries the same retained snapshot when no newer one is enqueued', async () => {
    let attempts = 0;
    const persisted: string[] = [];

    const controller = createPersistenceQueueController({
      delayMs: 10,
      callbacksRef: makeCallbacksRef({
        persistNow: async (snapshot: PersistenceSnapshot) => {
          attempts += 1;
          if (attempts === 1) throw new Error('transient');
          persisted.push(snapshot.groups[0]?.id ?? 'none');
        },
      }),
    });

    controller.enqueue(makeSnapshot('retry-me'));
    await expect(controller.flush()).rejects.toThrow('transient');
    await controller.flush(); // no re-enqueue: retained snapshot is retried
    expect(attempts).toBe(2);
    expect(persisted).toEqual(['retry-me']);
  });
});
