const mockSaveUserData = jest.fn();
const mockLoadUserData = jest.fn();
const mockValidateBackupData = jest.fn();
const mockDeleteActivityDay = jest.fn();
const mockDeleteCard = jest.fn();
const mockDeleteDeck = jest.fn();
const mockDeleteStudyMode = jest.fn();
const mockSaveActivityDay = jest.fn();
const mockSaveCard = jest.fn();
const mockSaveDeck = jest.fn();
const mockSaveStudyMode = jest.fn();
const mockTouchUserRoot = jest.fn();

jest.mock('@/services/firebase', () => ({
  saveUserData: (...args: unknown[]) => mockSaveUserData(...args),
  loadUserData: (...args: unknown[]) => mockLoadUserData(...args),
}));

jest.mock('@/utils/backupValidation', () => ({
  validateBackupData: (...args: unknown[]) => mockValidateBackupData(...args),
}));

jest.mock('@/store/storeDataNormalization', () => ({
  normalizeStoreData: (data: unknown) => data,
}));

jest.mock('@/store/persistence/firestorePersistence', () => ({
  deleteActivityDay: (...args: unknown[]) => mockDeleteActivityDay(...args),
  deleteCard: (...args: unknown[]) => mockDeleteCard(...args),
  deleteDeck: (...args: unknown[]) => mockDeleteDeck(...args),
  deleteStudyMode: (...args: unknown[]) => mockDeleteStudyMode(...args),
  saveActivityDay: (...args: unknown[]) => mockSaveActivityDay(...args),
  saveCard: (...args: unknown[]) => mockSaveCard(...args),
  saveDeck: (...args: unknown[]) => mockSaveDeck(...args),
  saveStudyMode: (...args: unknown[]) => mockSaveStudyMode(...args),
  touchUserRoot: (...args: unknown[]) => mockTouchUserRoot(...args),
}));

function makeCloudCard(id: string) {
  return {
    id,
    pages: [`front-${id}`, `back-${id}`],
    srsState: {
      difficulty: 1,
      stability: 1,
      repetitions: 0,
      state: 0,
      lastReviewTimestamp: 0,
      nextReviewTimestamp: 0,
    },
    contentUpdatedAt: 1,
    srsUpdatedAt: 0,
  };
}

function makeCloudDeck(cards: ReturnType<typeof makeCloudCard>[] = []) {
  return {
    id: 'deck-1',
    name: 'Deck',
    cards,
    activeModeId: 'classic',
    studyFilter: 'all',
    pageLanguages: ['en-US', 'pl-PL'],
    pageNames: ['Front', 'Back'],
    activePageCount: 2,
    updatedAt: 1,
  };
}

describe('loadCloudData', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    jest.resetModules();
  });

  it('returns normalized data when load succeeds', async () => {
    const rawData = { groups: [], studyModes: [], activityHeatmap: {} };
    mockLoadUserData.mockResolvedValueOnce(rawData);

    // eslint-disable-next-line @typescript-eslint/no-require-imports
    const { loadCloudData } = require('../firebasePersistence');

    const result = await loadCloudData('uid-1');
    expect(result).toBe(rawData);
    expect(mockLoadUserData).toHaveBeenCalledWith('uid-1');
    expect(mockValidateBackupData).toHaveBeenCalled();
  });

  it('returns null when loadUserData returns null', async () => {
    mockLoadUserData.mockResolvedValueOnce(null);

    // eslint-disable-next-line @typescript-eslint/no-require-imports
    const { loadCloudData } = require('../firebasePersistence');

    const result = await loadCloudData('uid-2');
    expect(result).toBeNull();
  });

  it('throws when validateBackupData fails', async () => {
    const rawData = { groups: [], studyModes: [], activityHeatmap: {} };
    mockLoadUserData.mockResolvedValueOnce(rawData);
    mockValidateBackupData.mockImplementationOnce(() => {
      throw new Error('Invalid backup data');
    });

    // eslint-disable-next-line @typescript-eslint/no-require-imports
    const { loadCloudData } = require('../firebasePersistence');

    await expect(loadCloudData('uid-3')).rejects.toThrow('Invalid backup data');
  });
});

describe('saveCloudData', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    jest.resetModules();
  });

  afterEach(() => {
    jest.restoreAllMocks();
  });

  it('saves data via full write when no previous snapshot exists', async () => {
    const data = { groups: [], studyModes: [], activityHeatmap: {} };

    // eslint-disable-next-line @typescript-eslint/no-require-imports
    const { saveCloudData } = require('../firebasePersistence');

    await saveCloudData('uid-save-1', data);
    expect(mockValidateBackupData).toHaveBeenCalledWith(data);
    expect(mockSaveUserData).toHaveBeenCalledWith('uid-save-1', data);
  });

  it('throws when validateBackupData fails', async () => {
    const data = { groups: [], studyModes: [], activityHeatmap: {} };
    const errorSpy = jest.spyOn(console, 'error').mockImplementation(() => undefined);
    mockValidateBackupData.mockImplementationOnce(() => {
      throw new Error('Validation failed');
    });

    // eslint-disable-next-line @typescript-eslint/no-require-imports
    const { saveCloudData } = require('../firebasePersistence');

    await expect(saveCloudData('uid-save-2', data)).rejects.toThrow('Validation failed');
    expect(mockSaveUserData).not.toHaveBeenCalled();
    expect(errorSpy).toHaveBeenCalledWith('Failed to save cloud data:', expect.any(Error));
  });

  it('does not advance the diff cache when an entity save fails part-way through', async () => {
    const errorSpy = jest.spyOn(console, 'error').mockImplementation(() => undefined);
    const userId = 'uid-partial-save';
    const initialData = {
      groups: [makeCloudDeck([])],
      studyModes: [],
      activityHeatmap: {},
    };
    const nextData = {
      groups: [makeCloudDeck(['c1', 'c2', 'c3', 'c4', 'c5'].map(makeCloudCard))],
      studyModes: [],
      activityHeatmap: {},
    };

    // eslint-disable-next-line @typescript-eslint/no-require-imports
    const { saveCloudData } = require('../firebasePersistence');

    await saveCloudData(userId, initialData);
    mockSaveUserData.mockClear();

    mockSaveCard.mockRejectedValueOnce(new Error('card save failed'));
    await expect(saveCloudData(userId, nextData)).rejects.toThrow('card save failed');
    expect(mockSaveCard).toHaveBeenCalledTimes(1);

    mockSaveCard.mockClear();
    await saveCloudData(userId, nextData);

    expect(mockSaveCard).toHaveBeenCalledTimes(5);
    expect(errorSpy).toHaveBeenCalledWith('Failed to save cloud data:', expect.any(Error));
    expect(mockSaveCard.mock.calls.map((call) => call[2].id)).toEqual([
      'c1',
      'c2',
      'c3',
      'c4',
      'c5',
    ]);
  });
});
