import { describe, it, expect, beforeEach, afterEach } from '@jest/globals';
import type { Flashcard, FlashcardGroup, StudyMode } from '@/types/models';
import type { UserData } from '../firestoreSchema';

const mockSaveUserData = jest.fn<Promise<void>, [string, unknown]>();
const mockLoadUserData = jest.fn<Promise<unknown | null>, [string]>();
const mockValidateBackupData = jest.fn<void, [unknown]>();
const mockDeleteActivityDay = jest.fn<Promise<void>, [string, string]>();
const mockDeleteCard = jest.fn<Promise<void>, [string, string, string]>();
const mockDeleteDeck = jest.fn<Promise<void>, [string, { id: string }]>();
const mockDeleteStudyMode = jest.fn<Promise<void>, [string, string]>();
const mockSaveActivityDay = jest.fn<Promise<void>, [string, string, number]>();
const mockSaveCard = jest.fn<Promise<void>, [string, string, unknown]>();
const mockSaveDeck = jest.fn<Promise<void>, [string, unknown]>();
const mockSaveStudyMode = jest.fn<Promise<void>, [string, unknown]>();
const mockTouchUserRoot = jest.fn<Promise<void>, [string]>();

jest.mock('@/services/firebase', () => ({
  saveUserData: (...args: unknown[]) => (mockSaveUserData as (...a: unknown[]) => Promise<void>)(...args),
  loadUserData: (...args: unknown[]) => (mockLoadUserData as (...a: unknown[]) => Promise<unknown | null>)(...args),
}));

jest.mock('@/utils/backupValidation', () => ({
  validateBackupData: (...args: unknown[]) => (mockValidateBackupData as (...a: unknown[]) => void)(...args),
}));

jest.mock('@/store/persistence/firestorePersistence', () => ({
  deleteActivityDay: (...args: unknown[]) => (mockDeleteActivityDay as (...a: unknown[]) => Promise<void>)(...args),
  deleteCard: (...args: unknown[]) => (mockDeleteCard as (...a: unknown[]) => Promise<void>)(...args),
  deleteDeck: (...args: unknown[]) => (mockDeleteDeck as (...a: unknown[]) => Promise<void>)(...args),
  deleteStudyMode: (...args: unknown[]) => (mockDeleteStudyMode as (...a: unknown[]) => Promise<void>)(...args),
  saveActivityDay: (...args: unknown[]) => (mockSaveActivityDay as (...a: unknown[]) => Promise<void>)(...args),
  saveCard: (...args: unknown[]) => (mockSaveCard as (...a: unknown[]) => Promise<void>)(...args),
  saveDeck: (...args: unknown[]) => (mockSaveDeck as (...a: unknown[]) => Promise<void>)(...args),
  saveStudyMode: (...args: unknown[]) => (mockSaveStudyMode as (...a: unknown[]) => Promise<void>)(...args),
  touchUserRoot: (...args: unknown[]) => (mockTouchUserRoot as (...a: unknown[]) => Promise<void>)(...args),
}));

const srsState = {
  difficulty: 1,
  stability: 1,
  repetitions: 0,
  state: 0 as const,
  lastReviewTimestamp: 0,
  nextReviewTimestamp: 0,
};

function makeCard(id: string, pages?: string[]): Flashcard {
  return {
    id,
    pages: pages ?? [`front-${id}`, `back-${id}`],
    srsState: { ...srsState },
    contentUpdatedAt: 1,
    srsUpdatedAt: 0,
  };
}

function makeDeck(id: string, cardIds: string[]): FlashcardGroup {
  return {
    id,
    name: `Deck ${id}`,
    cards: cardIds.map((cid) => makeCard(cid)),
    activeModeId: 'classic',
    studyFilter: 'all',
    pageLanguages: ['en-US', 'pl-PL'],
    pageNames: ['Front', 'Back'],
    activePageCount: 2,
    updatedAt: 1,
  };
}

function makeStudyMode(id: string, name?: string): StudyMode {
  return {
    id,
    name: name ?? id,
    steps: [],
    isBuiltIn: false,
    updatedAt: 1,
  };
}

function makeData(groups: FlashcardGroup[], modes: StudyMode[] = []): UserData {
  return {
    groups,
    studyModes: [...modes, makeStudyMode('classic', 'Classic')],
    activityHeatmap: {},
  };
}

function resetMocks() {
  mockSaveUserData.mockReset();
  mockLoadUserData.mockReset();
  mockValidateBackupData.mockReset();
  mockDeleteActivityDay.mockReset();
  mockDeleteCard.mockReset();
  mockDeleteDeck.mockReset();
  mockDeleteStudyMode.mockReset();
  mockSaveActivityDay.mockReset();
  mockSaveCard.mockReset();
  mockSaveDeck.mockReset();
  mockSaveStudyMode.mockReset();
  mockTouchUserRoot.mockReset();

  mockSaveUserData.mockResolvedValue(undefined);
  mockTouchUserRoot.mockResolvedValue(undefined);
  mockSaveDeck.mockResolvedValue(undefined);
  mockSaveCard.mockResolvedValue(undefined);
  mockSaveStudyMode.mockResolvedValue(undefined);
  mockSaveActivityDay.mockResolvedValue(undefined);
  mockDeleteCard.mockResolvedValue(undefined);
  mockDeleteDeck.mockResolvedValue(undefined);
  mockDeleteStudyMode.mockResolvedValue(undefined);
  mockDeleteActivityDay.mockResolvedValue(undefined);
}

describe('saveCloudData integration (real normalizeStoreData / deepEqual / cloneUserData)', () => {
  beforeEach(() => {
    jest.resetModules();
    resetMocks();

    // eslint-disable-next-line @typescript-eslint/no-require-imports
    const { clearCloudSnapshotCache } = require('../firebasePersistence');
    clearCloudSnapshotCache();
  });

  afterEach(() => {
    jest.restoreAllMocks();
  });

  it('does not re-save any entity on identical subsequent data', async () => {
    const userId = 'uid-no-resave';
    const data = makeData([makeDeck('deck-1', ['c1', 'c2'])], [makeStudyMode('custom-1', 'Custom')]);

    // eslint-disable-next-line @typescript-eslint/no-require-imports
    const { saveCloudData } = require('../firebasePersistence');

    await saveCloudData(userId, data);
    expect(mockSaveUserData).toHaveBeenCalledWith(userId, data);

    mockSaveUserData.mockClear();
    mockSaveDeck.mockClear();
    mockSaveCard.mockClear();
    mockSaveStudyMode.mockClear();

    await saveCloudData(userId, data);

    expect(mockSaveUserData).not.toHaveBeenCalled();
    expect(mockSaveDeck).not.toHaveBeenCalled();
    expect(mockSaveCard).not.toHaveBeenCalled();
    expect(mockSaveStudyMode).not.toHaveBeenCalled();
  });

  it('saves only the changed card when one card page changes', async () => {
    const userId = 'uid-partial-card';
    const data = makeData([makeDeck('deck-1', ['c1', 'c2'])]);

    // eslint-disable-next-line @typescript-eslint/no-require-imports
    const { saveCloudData } = require('../firebasePersistence');

    await saveCloudData(userId, data);

    mockSaveUserData.mockClear();
    mockSaveDeck.mockClear();
    mockSaveCard.mockClear();

    const modified = makeData([makeDeck('deck-1', ['c1', 'c2'])]);
    modified.groups[0].cards[0].pages = ['changed'];
    modified.groups[0].cards[0].contentUpdatedAt = 2;

    await saveCloudData(userId, modified);

    expect(mockSaveUserData).not.toHaveBeenCalled();
    expect(mockSaveDeck).not.toHaveBeenCalled();
    expect(mockSaveCard).toHaveBeenCalledTimes(1);
    expect((mockSaveCard.mock.calls[0][2] as Flashcard).id).toBe('c1');
  });

  it('cache is deepEqual to original normalized data after cloneUserData', async () => {
    const userId = 'uid-cache-fidelity';

    // eslint-disable-next-line @typescript-eslint/no-require-imports
    const { saveCloudData, loadCloudData } = require('../firebasePersistence');

    const rawData = {
      groups: [{
        id: 'deck-1',
        name: 'Deck',
        cards: [{
          id: 'c1',
          pages: ['a', 'b'],
          srsState: { ...srsState },
        }],
        activeModeId: 'classic',
        studyFilter: 'all',
        pageLanguages: ['en-US', 'pl-PL'],
        pageNames: ['Front', 'Back'],
        activePageCount: 2,
      }],
      studyModes: [],
      activityHeatmap: {},
    };

    mockLoadUserData.mockResolvedValue(rawData);
    const loaded = await loadCloudData(userId);
    expect(loaded).not.toBeNull();

    resetMocks();
    await saveCloudData(userId, loaded as UserData);

    expect(mockSaveUserData).not.toHaveBeenCalled();
    expect(mockSaveDeck).not.toHaveBeenCalled();
    expect(mockSaveCard).not.toHaveBeenCalled();
  });
});
