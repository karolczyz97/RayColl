import React from 'react';
import { describe, it, jest, expect, beforeEach } from '@jest/globals';
import { render, act, waitFor } from '@testing-library/react-native';
import type { User } from 'firebase/auth';
import type { StoreData, FlashcardGroup, StudyMode } from '@/types/models';
import { DEFAULT_STUDY_FILTER } from '../storeDataNormalization';
import { useStoreBootstrap, getGuestHasData } from '../useStoreBootstrap';
import { loadCloudData } from '../persistence/firebasePersistence';
import { loadLocalData } from '../persistence/localPersistence';
import { onAuthChange } from '@/services/firebase';
import { createSeedModes } from '../seed/seedModes';

// Default mock: fires the callback synchronously with null (mirrors the
// "unconfigured Firebase" contract from src/services/firebase.ts:78-84 and
// the native auth-persistence path that emits without a network round-trip).
// This ensures authResolved=true on mount so existing tests are unaffected.
jest.mock('@/services/firebase', () => ({
  onAuthChange: jest.fn((cb: (u: User | null) => void) => {
    cb(null);
    return () => {};
  }),
}));

jest.mock('../persistence/firebasePersistence', () => ({
  loadCloudData: jest.fn(),
}));

jest.mock('../persistence/localPersistence', () => ({
  loadLocalData: jest.fn(),
}));

jest.mock('../seed/seedGroups', () => ({
  createSeedGroups: jest.fn(() => []),
}));

jest.mock('../seed/seedModes', () => ({
  createSeedModes: jest.fn(() => []),
  isBuiltInModeSourceId: jest.fn(() => false),
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
    cardOrder: 'sequential',
    pageLanguages: ['en-US', 'pl-PL'],
    pageNames: ['Front', 'Back'],
    activePageCount: 2,
    updatedAt: 0,
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
    applySnapshot: jest.fn<(snapshot: StoreData) => void>(),
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

  it('does not cache seed decks for a signed-in user when cloud load fails with no local data', async () => {
    const mocks = createMocks();
    jest.mocked(loadLocalData).mockResolvedValue(null);
    jest.mocked(loadCloudData).mockRejectedValue(new Error('offline'));

    render(<Harness user={{ uid: 'user-1' } as User} mocks={mocks} />);

    await waitFor(() => {
      expect(mocks.setIsLoading).toHaveBeenCalledWith(false);
    });

    // Seeds are shown ephemerally but never persisted as the user's local cache.
    expect(mocks.applySnapshot).toHaveBeenCalledTimes(1);
    expect(mocks.persistLocalSnapshot).not.toHaveBeenCalled();
    expect(mocks.persistNow).not.toHaveBeenCalled();
    expect(mocks.setLastStoreError).toHaveBeenCalledWith('offline');
  });

  it('triggers migration when the guest has only activity history (no decks)', async () => {
    const mocks = createMocks();
    const guestData: StoreData = { groups: [], studyModes: [], activityHeatmap: { '2025-01-01': 2 } };
    jest.mocked(loadLocalData).mockImplementation(
      async (uid?: string): Promise<StoreData | null> => (uid ? makeLocalData([]) : guestData),
    );
    jest.mocked(loadCloudData).mockResolvedValue(null);

    render(<Harness user={{ uid: 'user-1' } as User} mocks={mocks} />);

    await waitFor(() => {
      expect(mocks.setMigrationPending).toHaveBeenCalledWith(true);
    });
    expect(mocks.setPendingGuestSnapshot).toHaveBeenCalledWith(guestData);
  });

  it('waits for the cloud before rendering for a signed-in user', async () => {
    const mocks = createMocks();
    jest.mocked(loadLocalData).mockResolvedValue(makeLocalData([makeGroup()]));

    // Cloud load that stays pending until we resolve it below.
    let resolveCloud: (value: StoreData | null) => void = () => {};
    jest.mocked(loadCloudData).mockReturnValue(
      new Promise<StoreData | null>((resolve) => {
        resolveCloud = resolve;
      }),
    );

    render(<Harness user={{ uid: 'user-1' } as User} mocks={mocks} />);

    // Nothing is shown while the cloud load is pending — no flash of stale data.
    await waitFor(() => {
      expect(jest.mocked(loadCloudData)).toHaveBeenCalledTimes(1);
    });
    expect(mocks.applySnapshot).not.toHaveBeenCalled();
    expect(mocks.setIsLoading).not.toHaveBeenCalledWith(false);

    // Cloud settles → exactly one render with the merged data.
    resolveCloud(makeLocalData([makeGroup()]));
    await waitFor(() => {
      expect(mocks.persistNow).toHaveBeenCalledTimes(1);
    });
    expect(mocks.applySnapshot).toHaveBeenCalledTimes(1);
    expect(mocks.applySnapshot.mock.calls[0][0].groups).toHaveLength(1);
    expect(mocks.setIsLoading).toHaveBeenCalledWith(false);
  });

  it('renders the merged result once when the cloud adds a deck', async () => {
    const mocks = createMocks();
    jest.mocked(loadLocalData).mockResolvedValue(makeLocalData([makeGroup('local-deck')]));
    jest.mocked(loadCloudData).mockResolvedValue(
      makeLocalData([makeGroup('local-deck'), makeGroup('cloud-deck')]),
    );

    render(<Harness user={{ uid: 'user-1' } as User} mocks={mocks} />);

    await waitFor(() => {
      expect(mocks.applySnapshot).toHaveBeenCalledTimes(1);
    });
    expect(mocks.applySnapshot.mock.calls[0][0].groups).toHaveLength(2);
    expect(mocks.persistNow).toHaveBeenCalledTimes(1);
  });

  it('clears loading before the cloud save resolves, even with no local cache', async () => {
    const mocks = createMocks();
    jest.mocked(loadLocalData).mockResolvedValue(null);
    jest.mocked(loadCloudData).mockResolvedValue(makeLocalData([makeGroup('cloud-deck')]));
    // Cloud save that never settles — it must not hold the spinner hostage.
    mocks.persistNow.mockReturnValue(new Promise<void>(() => {}));

    render(<Harness user={{ uid: 'user-1' } as User} mocks={mocks} />);

    await waitFor(() => {
      expect(mocks.setIsLoading).toHaveBeenCalledWith(false);
    });
    expect(mocks.applySnapshot).toHaveBeenCalledTimes(1);
  });

  it('does not start loading data before the first auth emission', async () => {
    // Withhold the auth callback to simulate a slow auth-state restoration.
    jest.mocked(onAuthChange).mockImplementationOnce(() => () => {});
    const mocks = createMocks();
    jest.mocked(loadLocalData).mockResolvedValue(makeLocalData([makeGroup()]));

    render(<Harness user={null} mocks={mocks} />);

    // Give React time to flush any synchronous effects.
    await act(async () => {
      await Promise.resolve();
    });

    expect(loadLocalData).not.toHaveBeenCalled();
    expect(mocks.setIsLoading).not.toHaveBeenCalledWith(false);
  });

  it('loads exactly once after the first auth emission', async () => {
    let fireAuth!: (user: User | null) => void;
    jest.mocked(onAuthChange).mockImplementationOnce((cb) => {
      fireAuth = cb;
      return () => {};
    });
    const mocks = createMocks();
    jest.mocked(loadLocalData).mockResolvedValue(makeLocalData([makeGroup()]));

    render(<Harness user={null} mocks={mocks} />);

    await act(async () => {
      await Promise.resolve();
    });
    expect(mocks.applySnapshot).not.toHaveBeenCalled();

    // Fire the first auth emission (null = guest).
    await act(async () => {
      fireAuth(null);
      await Promise.resolve();
    });

    await waitFor(() => {
      expect(mocks.applySnapshot).toHaveBeenCalledTimes(1);
    });
    expect(mocks.setIsLoading).toHaveBeenCalledWith(false);
  });
});

function makeMode(over: Partial<StudyMode> & Pick<StudyMode, 'id' | 'name'>): StudyMode {
  return { isBuiltIn: false, steps: [{ type: 'show_ratings' }], updatedAt: 0, ...over };
}

describe('getGuestHasData', () => {
  it('returns false for null or empty guest data', () => {
    expect(getGuestHasData(null)).toBe(false);
    expect(getGuestHasData({ groups: [], studyModes: [], activityHeatmap: {} })).toBe(false);
  });

  it('returns true when decks exist', () => {
    expect(getGuestHasData(makeLocalData([makeGroup()]))).toBe(true);
  });

  it('returns true when only activity history exists', () => {
    expect(getGuestHasData({ groups: [], studyModes: [], activityHeatmap: { '2025-01-01': 1 } })).toBe(true);
  });

  it('returns true for a custom (non-built-in) study mode', () => {
    expect(
      getGuestHasData({ groups: [], studyModes: [makeMode({ id: 'x', name: 'Custom' })], activityHeatmap: {} }),
    ).toBe(true);
  });

  it('detects a modified built-in mode', () => {
    const seed = makeMode({ id: 'classic', name: 'Klasyczny', isBuiltIn: true, builtInSourceId: 'classic', steps: [{ type: 'show_ratings' }] });
    jest.mocked(createSeedModes).mockReturnValueOnce([seed]);
    const modified = makeMode({ id: 'classic', name: 'Klasyczny', isBuiltIn: true, builtInSourceId: 'classic', steps: [{ type: 'wait', ms: 500 }] });
    expect(getGuestHasData({ groups: [], studyModes: [modified], activityHeatmap: {} })).toBe(true);
  });

  it('returns false for an unmodified built-in mode', () => {
    const seed = makeMode({ id: 'classic', name: 'Klasyczny', isBuiltIn: true, builtInSourceId: 'classic', steps: [{ type: 'show_ratings' }] });
    jest.mocked(createSeedModes).mockReturnValueOnce([seed]);
    expect(getGuestHasData({ groups: [], studyModes: [seed], activityHeatmap: {} })).toBe(false);
  });
});
