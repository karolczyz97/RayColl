const mockSaveUserData = jest.fn();
const mockLoadUserData = jest.fn();
const mockValidateBackupData = jest.fn();

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

jest.mock('@/store/persistence/firestoreV2Persistence', () => ({
  deleteActivityDay: jest.fn(),
  deleteCard: jest.fn(),
  deleteDeck: jest.fn(),
  deleteStudyMode: jest.fn(),
  saveActivityDay: jest.fn(),
  saveCard: jest.fn(),
  saveDeck: jest.fn(),
  saveStudyMode: jest.fn(),
}));

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
    mockValidateBackupData.mockImplementationOnce(() => {
      throw new Error('Validation failed');
    });

    // eslint-disable-next-line @typescript-eslint/no-require-imports
    const { saveCloudData } = require('../firebasePersistence');

    await expect(saveCloudData('uid-save-2', data)).rejects.toThrow('Validation failed');
    expect(mockSaveUserData).not.toHaveBeenCalled();
  });
});
