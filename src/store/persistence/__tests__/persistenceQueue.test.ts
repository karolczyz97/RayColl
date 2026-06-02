import { createPersistenceQueueController } from '../persistenceQueue';
import type { FlashcardGroup } from '../../../types/models';
import { DEFAULT_STUDY_FILTER } from '../../storeDataNormalization';

function assertEqual<T>(actual: T, expected: T, message: string) {
  if (actual !== expected) {
    throw new Error(`${message}: expected ${expected}, got ${actual}`);
  }
}

function assertDeepEqual(actual: unknown, expected: unknown, message: string) {
  const actualJson = JSON.stringify(actual);
  const expectedJson = JSON.stringify(expected);

  if (actualJson !== expectedJson) {
    throw new Error(`${message}: expected ${expectedJson}, got ${actualJson}`);
  }
}

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

export async function runTests() {
  console.log('\n--- Running Persistence Queue Tests ---');

  const savedSnapshots: string[] = [];
  const controller = createPersistenceQueueController({
    delayMs: 10,
    persistNow: async (snapshot) => {
      savedSnapshots.push(snapshot.groups[0]?.id ?? 'none');
    },
  });

  controller.enqueue({
    uid: 'u1',
    groups: [createGroup('first')],
    studyModes: [],
    activityHeatmap: {},
  });
  controller.enqueue({
    uid: 'u1',
    groups: [createGroup('second')],
    studyModes: [],
    activityHeatmap: {},
  });
  await controller.flush();

  assertEqual(savedSnapshots.length, 1, 'Queue should collapse multiple enqueues into one save');
  assertEqual(savedSnapshots[0], 'second', 'Queue should persist the latest snapshot');

  const sequenceSnapshots: string[] = [];
  let markFirstPersistStarted = () => {};
  const firstPersistStarted = new Promise<void>((resolve) => {
    markFirstPersistStarted = resolve;
  });
  let releaseFirstPersist = () => {};
  const firstPersistCanFinish = new Promise<void>((resolve) => {
    releaseFirstPersist = resolve;
  });
  const controller2 = createPersistenceQueueController({
    delayMs: 10,
    persistNow: async (snapshot) => {
      sequenceSnapshots.push(snapshot.groups[0]?.id ?? 'none');
      if (snapshot.groups[0]?.id === 'first') {
        markFirstPersistStarted();
        await firstPersistCanFinish;
      }
    },
  });

  controller2.enqueue(
    {
      uid: 'u1',
      groups: [createGroup('first')],
      studyModes: [],
      activityHeatmap: {},
    },
    { immediate: true },
  );
  await firstPersistStarted;
  controller2.enqueue({
    uid: 'u1',
    groups: [createGroup('second')],
    studyModes: [],
    activityHeatmap: {},
  });
  controller2.enqueue({
    uid: 'u1',
    groups: [createGroup('third')],
    studyModes: [],
    activityHeatmap: {},
  });
  releaseFirstPersist();
  await controller2.flush();

  assertDeepEqual(
    sequenceSnapshots,
    ['first', 'third'],
    'Queue should continue with the most recent pending snapshot after a flush',
  );

  // Error recovery: a failed immediate flush surfaces onError; a later success reports onSynced.
  let shouldFail = true;
  let errorCount = 0;
  let syncedCount = 0;
  const controller3 = createPersistenceQueueController({
    delayMs: 10,
    persistNow: async () => {
      if (shouldFail) {
        throw new Error('cloud down');
      }
    },
    onError: () => {
      errorCount += 1;
    },
    onSynced: () => {
      syncedCount += 1;
    },
  });

  controller3.enqueue(
    { uid: 'u1', groups: [createGroup('e1')], studyModes: [], activityHeatmap: {} },
    { immediate: true },
  );
  await controller3.flush();
  assertEqual(errorCount, 1, 'Failed immediate flush should invoke onError once');
  assertEqual(syncedCount, 0, 'Failed flush should not invoke onSynced');

  shouldFail = false;
  controller3.enqueue(
    { uid: 'u1', groups: [createGroup('e2')], studyModes: [], activityHeatmap: {} },
    { immediate: true },
  );
  await controller3.flush();
  assertEqual(syncedCount, 1, 'Subsequent successful flush should invoke onSynced once');
  assertEqual(errorCount, 1, 'Successful flush should not add another onError');

  console.log('Persistence queue tests passed');
}
