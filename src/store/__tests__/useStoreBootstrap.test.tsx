import React from 'react';
import { describe, it, jest, expect, beforeEach } from '@jest/globals';
import { render, waitFor } from '@testing-library/react-native';
import type { User } from 'firebase/auth';
import type { StoreData, FlashcardGroup } from '@/types/models';
import { DEFAULT_STUDY_FILTER } from '../storeDataNormalization';
import { useStoreBootstrap } from '../useStoreBootstrap';
import { loadCloudData } from '../persistence/firebasePersistence';
import { getSeedVersion, loadLocalData } from '../persistence/localPersistence';

jest.mock('@/services/firebase', () => ({
  onAuthChange: jest.fn(() => () => {}),
}));

jest.mock('../persistence/firebasePersistence', () => ({
  loadCloudData: jest.fn(),
}));

jest.mock('../persistence/localPersistence', () => ({
  getSeedVersion: jest.fn(),
  loadLocalData: jest.fn(),
  setSeedVersion: jest.fn(() => Promise.resolve()),
}));

jest.mock('../seed/seedGroups', () => ({
  createSeedGroups: jest.fn(() => []),
  SEED_VERSION: 1,
}));

jest.mock('../seed/seedModes', () => ({
  createSeedModes: jest.fn(() => []),
}));

jest.mock('../actions/groupActions', () => ({
  purgeExpiredArchivesAction: jest.fn((groups: FlashcardGroup[]) => ({ groups })),
}));

function makeGroup(id = 'g-1'): FlashcardGroup {
  return {
    id,
    name: 'Test Deck',
    cards: [],
    activeModeId: 'classic',
    studyFilter: DEFAULT_STUDY_FILTER,
    pageLanguages: ['en-US', 'pl-PL'],
    pageNames: ['Front', 'Back'],
    activePageCount: 2,
  };
}

function makeLocalData(groups: FlashcardGroup[] = []): StoreData {
  return { groups, studyModes: [], activityHeatmap: {} };
}

function createMocks() {
  return {
    setUser: jest.fn(),
    setIsLoading: jest.fn(),
    setLastSyncError: jest.fn(),
    setLastPersistenceError: jest.fn(),
    setLastStoreError: jest.fn(),
    setMigrationPending: jest.fn(),
    setPendingGuestSnapshot: jest.fn(),
    applySnapshot: jest.fn(),
    persistLocalSnapshot: jest.fn<() => Promise<void>>(() => Promise.resolve()),
    persistNow: jest.fn<() => Promise<void>>(() => Promise.resolve()),
  };
}

type Mocks = ReturnType<typeof createMocks>;

function Harness({ user, mocks }: { user: User | null; mocks: Mocks }) {
  useStoreBootstrap({
    user,
    setUser: mocks.setUser,
    setIsLoading: mocks.setIsLoading,
    setLastSyncError: mocks.setLastSyncError,
    setLastPersistenceError: mocks.setLastPersistenceError,
    setLastStoreError: mocks.setLastStoreError,
    setMigrationPending: mocks.setMigrationPending,
    setPendingGuestSnapshot: mocks.setPendingGuestSnapshot,
    applySnapshot: mocks.applySnapshot,
    persistLocalSnapshot: mocks.persistLocalSnapshot,
    persistNow: mocks.persistNow,
  });
  return null;
}

describe('useStoreBootstrap data flow', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    jest.mocked(getSeedVersion).mockResolvedValue(1);
  });

  it('applies snapshot and persists locally for a guest user loading local data', async () => {
    const mocks = createMocks();
    jest.mocked(loadLocalData).mockResolvedValue(makeLocalData([makeGroup()]));

    render(<Harness user={null} mocks={mocks} />);

    await waitFor(() => {
      expect(mocks.setIsLoading).toHaveBeenCalledWith(false);
    });

    expect(mocks.applySnapshot).toHaveBeenCalledTimes(1);
    expect(mocks.persistNow).toHaveBeenCalledWith(expect.objectContaining({ uid: null }));
    expect(mocks.persistLocalSnapshot).not.toHaveBeenCalled();
  });

  it('falls back to local cache and surfaces errors when cloud load fails', async () => {
    const mocks = createMocks();
    jest.mocked(loadLocalData).mockResolvedValue(makeLocalData([makeGroup()]));
    jest.mocked(loadCloudData).mockRejectedValue(new Error('network down'));

    render(<Harness user={{ uid: 'user-1' } as User} mocks={mocks} />);

    await waitFor(() => {
      expect(mocks.setIsLoading).toHaveBeenCalledWith(false);
    });

    expect(mocks.setLastSyncError).toHaveBeenCalledWith('network down');
    expect(mocks.setLastStoreError).toHaveBeenCalledWith('network down');
    expect(mocks.persistLocalSnapshot).toHaveBeenCalledTimes(1);
    expect(mocks.persistNow).not.toHaveBeenCalled();
  });

  it('triggers migration when signing in to a new account while guest has local data', async () => {
    const mocks = createMocks();
    const guestData = makeLocalData([makeGroup('guest-deck')]);

    jest.mocked(loadLocalData).mockImplementation(
      async (uid?: string): Promise<StoreData | null> =>
        uid ? makeLocalData([]) : guestData,
    );
    jest.mocked(loadCloudData).mockResolvedValue(null);

    render(<Harness user={{ uid: 'user-1' } as User} mocks={mocks} />);

    await waitFor(() => {
      expect(mocks.setMigrationPending).toHaveBeenCalledWith(true);
    });

    expect(mocks.setPendingGuestSnapshot).toHaveBeenCalledWith(guestData);
    expect(mocks.applySnapshot).not.toHaveBeenCalled();
    expect(mocks.persistNow).not.toHaveBeenCalled();
    expect(mocks.setIsLoading).toHaveBeenCalledWith(false);
  });
});
