import React from 'react';
import { act, render } from '@testing-library/react-native';
import type { SetStateAction } from 'react';
import type { Flashcard, FlashcardGroup, StoreData, StudyMode } from '@/types/models';
import { createNewSrsState } from '@/srs/srsEngine';
import { DEFAULT_STUDY_FILTER } from '../storeDataNormalization';
import { useStoreActionsCore } from '../useStoreActions';
import type { PersistOptions } from '../FlashcardStoreTypes';

function makeCard(id = 'card-1'): Flashcard {
  return {
    id,
    pages: [`front-${id}`, `back-${id}`],
    srsState: createNewSrsState(),
    contentUpdatedAt: 0,
    srsUpdatedAt: 0,
  };
}

function makeGroup(overrides: Partial<FlashcardGroup> = {}): FlashcardGroup {
  return {
    id: 'group-1',
    name: 'Deck',
    cards: [makeCard()],
    activeModeId: 'classic',
    studyFilter: DEFAULT_STUDY_FILTER,
    cardOrder: 'sequential',
    pageLanguages: ['en-US', 'pl-PL'],
    pageNames: ['Front', 'Back'],
    activePageCount: 2,
    updatedAt: 0,
    ...overrides,
  } as FlashcardGroup;
}

function makeSnapshot(overrides: Partial<StoreData> = {}): StoreData {
  return {
    groups: [makeGroup()],
    studyModes: [],
    activityHeatmap: {},
    ...overrides,
  };
}

function applyStateAction<T>(current: T, action: SetStateAction<T>): T {
  return typeof action === 'function' ? (action as (value: T) => T)(current) : action;
}

function renderStoreActions(initialSnapshot = makeSnapshot()) {
  const groupsRef = { current: initialSnapshot.groups };
  const studyModesRef = { current: initialSnapshot.studyModes };
  const heatmapRef = { current: initialSnapshot.activityHeatmap };
  const persistCurrentSnapshot = jest.fn();

  const setGroups = jest.fn((value: SetStateAction<FlashcardGroup[]>) => {
    groupsRef.current = applyStateAction(groupsRef.current, value);
  });
  const setHeatmap = jest.fn((value: SetStateAction<Record<string, number>>) => {
    heatmapRef.current = applyStateAction(heatmapRef.current, value);
  });
  const setSyncStatus = jest.fn();
  const setLastPersistenceError = jest.fn();
  const setLastStoreError = jest.fn();
  const getCurrentUid = jest.fn(() => 'uid-1');
  const persistNow = jest.fn<Promise<void>, [StoreData & { uid: string | null }]>(
    () => Promise.resolve(),
  );
  const flushPersistence = jest.fn<Promise<void>, []>(() => Promise.resolve());

  const applySnapshot = jest.fn((snapshot: StoreData) => {
    groupsRef.current = snapshot.groups;
    studyModesRef.current = snapshot.studyModes;
    heatmapRef.current = snapshot.activityHeatmap;
    setGroups(snapshot.groups);
    setHeatmap(snapshot.activityHeatmap);
  });

  const commitGroups = jest.fn((nextGroups: FlashcardGroup[], options?: PersistOptions) => {
    groupsRef.current = nextGroups;
    setGroups(nextGroups);
    persistCurrentSnapshot(options);
  });
  const commitStudyModes = jest.fn((nextStudyModes: StudyMode[], options?: PersistOptions) => {
    studyModesRef.current = nextStudyModes;
    persistCurrentSnapshot(options);
  });
  const commitHeatmap = jest.fn((nextHeatmap: Record<string, number>, options?: PersistOptions) => {
    heatmapRef.current = nextHeatmap;
    setHeatmap(nextHeatmap);
    persistCurrentSnapshot(options);
  });
  const commitGroupsAndHeatmap = jest.fn((
    nextGroups: FlashcardGroup[],
    nextHeatmap: Record<string, number>,
    options?: PersistOptions,
  ) => {
    groupsRef.current = nextGroups;
    heatmapRef.current = nextHeatmap;
    setGroups(nextGroups);
    setHeatmap(nextHeatmap);
    persistCurrentSnapshot(options);
  });

  let actions: ReturnType<typeof useStoreActionsCore> | null = null;

  function Harness() {
    actions = useStoreActionsCore({
      groupsRef,
      studyModesRef,
      heatmapRef,
      setGroups,
      setHeatmap,
      setSyncStatus,
      setLastPersistenceError,
      setLastStoreError,
      getCurrentUid,
      applySnapshot,
      persistNow,
      flushPersistence,
      commitGroups,
      commitStudyModes,
      commitHeatmap,
      commitGroupsAndHeatmap,
    });
    return null;
  }

  render(<Harness />);

  return {
    actions: actions!,
    refs: { groupsRef, studyModesRef, heatmapRef },
    mocks: {
      applySnapshot,
      commitGroupsAndHeatmap,
      commitHeatmap,
      flushPersistence,
      persistCurrentSnapshot,
      persistNow,
      setLastPersistenceError,
      setLastStoreError,
      setSyncStatus,
    },
  };
}

describe('useStoreActionsCore persistence behavior', () => {
  beforeEach(() => {
    jest.useFakeTimers().setSystemTime(new Date('2026-06-05T12:00:00'));
  });

  afterEach(() => {
    jest.useRealTimers();
    jest.restoreAllMocks();
  });

  it('reviews a flashcard through one combined groups+heatmap commit in study cloud mode', () => {
    const { actions, mocks, refs } = renderStoreActions();
    const storedCard = refs.groupsRef.current[0].cards[0];

    act(() => {
      actions.reviewFlashcard('group-1', storedCard.id, 3);
    });

    expect(mocks.commitGroupsAndHeatmap).toHaveBeenCalledTimes(1);
    const [nextGroups, nextHeatmap, options] = mocks.commitGroupsAndHeatmap.mock.calls[0];
    expect(options).toEqual({ cloudMode: 'study' });
    // FSRS is recomputed on the stored card (id + rating), not on a passed-in object.
    expect(nextGroups[0].cards[0].srsState.repetitions).toBe(storedCard.srsState.repetitions + 1);
    expect(nextGroups[0].cards[0].srsUpdatedAt).toBeGreaterThan(0);
    expect(nextHeatmap['2026-06-05']).toBe(1);
  });

  it('records activity through the standard heatmap commit', () => {
    const { actions, mocks } = renderStoreActions();

    act(() => {
      actions.recordActivity();
    });

    expect(mocks.commitHeatmap).toHaveBeenCalledWith({ '2026-06-05': 1 });
  });

  it('does not mutate groups when importDeck pre-flush fails', async () => {
    const initial = makeSnapshot();
    const { actions, mocks, refs } = renderStoreActions(initial);
    mocks.flushPersistence.mockRejectedValueOnce(new Error('flush failed'));

    let result: Awaited<ReturnType<typeof actions.importDeck>> | undefined;
    await act(async () => {
      result = await actions.importDeck({
        name: 'Imported',
        languages: ['en-US', 'pl-PL'],
        pageNames: ['Front', 'Back'],
        cards: [{ pages: ['one', 'two'] }],
      });
    });

    expect(result).toEqual({ ok: false, error: 'flush failed' });
    expect(refs.groupsRef.current).toBe(initial.groups);
    expect(mocks.persistNow).not.toHaveBeenCalled();
    expect(mocks.setSyncStatus).toHaveBeenCalledWith('error');
    expect(mocks.setLastPersistenceError).toHaveBeenCalledWith('flush failed');
    expect(mocks.setLastStoreError).toHaveBeenCalledWith('flush failed');
  });

  it('rolls back importDeck when immediate persistence fails after optimistic mutation', async () => {
    const initial = makeSnapshot();
    const { actions, mocks, refs } = renderStoreActions(initial);
    mocks.persistNow.mockRejectedValueOnce(new Error('persist failed')).mockResolvedValueOnce();

    let result: Awaited<ReturnType<typeof actions.importDeck>> | undefined;
    await act(async () => {
      result = await actions.importDeck({
        name: 'Imported',
        languages: ['en-US', 'pl-PL'],
        pageNames: ['Front', 'Back'],
        cards: [{ pages: ['one', 'two'] }],
      });
    });

    expect(result).toEqual({ ok: false, error: 'persist failed' });
    expect(mocks.applySnapshot).toHaveBeenCalledWith(initial);
    expect(refs.groupsRef.current).toBe(initial.groups);
    expect(mocks.setSyncStatus).toHaveBeenCalledWith('error');
  });

  it('does not apply importState when JSON is invalid', async () => {
    const { actions, mocks } = renderStoreActions();

    await expect(actions.importState('{bad json')).rejects.toThrow('app_settings.import_error');

    expect(mocks.applySnapshot).not.toHaveBeenCalled();
    expect(mocks.persistNow).not.toHaveBeenCalled();
  });

  it('rolls back and rejects when a critical group op fails to persist', async () => {
    const errorSpy = jest.spyOn(console, 'error').mockImplementation(() => undefined);
    const { actions, mocks, refs } = renderStoreActions();
    const originalGroups = refs.groupsRef.current;
    mocks.flushPersistence.mockRejectedValue(new Error('flush failed'));

    await expect(actions.deleteGroup('group-1')).rejects.toThrow('flush failed');

    // The optimistic change was rolled back to the original groups reference.
    expect(refs.groupsRef.current).toBe(originalGroups);
    expect(mocks.setSyncStatus).toHaveBeenCalledWith('error');
    expect(mocks.setLastPersistenceError).toHaveBeenCalledWith('flush failed');
    expect(mocks.setLastStoreError).toHaveBeenCalledWith('flush failed');
    expect(errorSpy).toHaveBeenCalledWith('Critical group op persistence failed:', expect.any(Error));

    await expect(actions.archiveGroup('group-1')).rejects.toThrow('flush failed');
    await expect(actions.restoreGroup('group-1')).rejects.toThrow('flush failed');
    expect(refs.groupsRef.current).toBe(originalGroups);
  });
});
