import React from 'react';
import { describe, it, jest, expect, beforeEach } from '@jest/globals';
import { render, act } from '@testing-library/react-native';
import type { User } from 'firebase/auth';
import type { FlashcardGroup, StudyMode } from '@/types/models';
import { useStorePersistence } from '@/store/useStorePersistence';
import { saveLocalData } from '@/store/persistence/localPersistence';
import { saveCloudData } from '@/store/persistence/firebasePersistence';
import { usePersistenceQueue } from '@/store/persistence/persistenceQueue';

jest.mock('../persistence/localPersistence', () => ({
  saveLocalData: jest.fn(() => Promise.resolve()),
}));

jest.mock('../persistence/firebasePersistence', () => ({
  saveCloudData: jest.fn(() => Promise.resolve()),
}));

jest.mock('../persistence/persistenceQueue', () => ({
  usePersistenceQueue: jest.fn(),
}));

jest.mock('@/utils/withTimeout', () => ({
  withTimeout: jest.fn(<T,>(promise: Promise<T>) => promise),
}));

let mockCloudEnqueue: ReturnType<typeof jest.fn>;
let mockCloudFlush: ReturnType<typeof jest.fn>;

function renderPersistence(uid: string | null = 'user-1') {
  const groupsRef = { current: [] as FlashcardGroup[] };
  const studyModesRef = { current: [] as StudyMode[] };
  const heatmapRef = { current: {} as Record<string, number> };
  const userRef = { current: uid ? ({ uid } as User) : null };

  const setGroups = jest.fn();
  const setStudyModes = jest.fn();
  const setHeatmap = jest.fn();
  const setSyncStatus = jest.fn();
  const setLastSyncError = jest.fn();
  const setLastPersistenceError = jest.fn();
  const setLastStoreError = jest.fn();

  let persistence: ReturnType<typeof useStorePersistence> | null = null;

  function Harness() {
    persistence = useStorePersistence({
      groupsRef,
      studyModesRef,
      heatmapRef,
      userRef,
      setGroups,
      setStudyModes,
      setHeatmap,
      setSyncStatus,
      setLastSyncError,
      setLastPersistenceError,
      setLastStoreError,
    });
    return null;
  }

  render(<Harness />);

  return {
    persistence: persistence!,
    mocks: { setSyncStatus, setLastSyncError, setLastPersistenceError, setLastStoreError },
  };
}

describe('useStorePersistence', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockCloudEnqueue = jest.fn();
    mockCloudFlush = jest.fn(() => Promise.resolve());
    jest.mocked(usePersistenceQueue).mockReturnValue({
      enqueue: mockCloudEnqueue,
      flush: mockCloudFlush,
      cancel: jest.fn(),
    });
  });

  describe('cloudMode: study throttle', () => {
    it('accumulates 9 study reviews without enqueuing a cloud write', () => {
      const { persistence } = renderPersistence();

      for (let i = 0; i < 9; i++) {
        act(() => persistence.persistCurrentSnapshot({ cloudMode: 'study' }));
      }

      expect(mockCloudEnqueue).not.toHaveBeenCalled();
    });

    it('enqueues an immediate cloud write on the 10th study review', () => {
      const { persistence } = renderPersistence();

      for (let i = 0; i < 10; i++) {
        act(() => persistence.persistCurrentSnapshot({ cloudMode: 'study' }));
      }

      expect(mockCloudEnqueue).toHaveBeenCalledTimes(1);
      expect(mockCloudEnqueue).toHaveBeenCalledWith(
        expect.objectContaining({ uid: 'user-1' }),
        { immediate: true },
      );
    });
  });

  describe('flushPersistence', () => {
    it('writes local snapshot and enqueues an immediate cloud write then flushes', async () => {
      const { persistence } = renderPersistence();

      await act(async () => {
        await persistence.flushPersistence();
      });

      expect(saveLocalData).toHaveBeenCalledTimes(1);
      expect(mockCloudEnqueue).toHaveBeenCalledWith(
        expect.objectContaining({ uid: 'user-1' }),
        { immediate: true },
      );
      expect(mockCloudFlush).toHaveBeenCalled();
    });
  });

  describe('error reporting', () => {
    it('surfaces local write failures through lastPersistenceError and syncStatus', async () => {
      jest.mocked(saveLocalData).mockRejectedValueOnce(new Error('storage full'));
      const errorSpy = jest.spyOn(console, 'error').mockImplementation(() => {});
      const { persistence, mocks } = renderPersistence();

      await act(async () => {
        await expect(
          persistence.persistNow({
            uid: 'user-1',
            groups: [],
            studyModes: [],
            activityHeatmap: {},
          }),
        ).rejects.toThrow('storage full');
      });

      expect(mocks.setLastPersistenceError).toHaveBeenCalledWith('storage full');
      expect(mocks.setSyncStatus).toHaveBeenCalledWith('error');
      errorSpy.mockRestore();
    });

    it('surfaces cloud write failures through lastSyncError and syncStatus', async () => {
      jest.mocked(saveCloudData).mockRejectedValueOnce(new Error('cloud down'));
      const errorSpy = jest.spyOn(console, 'error').mockImplementation(() => {});
      const { persistence, mocks } = renderPersistence();

      await act(async () => {
        await expect(
          persistence.persistNow({
            uid: 'user-1',
            groups: [],
            studyModes: [],
            activityHeatmap: {},
          }),
        ).rejects.toThrow('cloud down');
      });

      expect(mocks.setLastSyncError).toHaveBeenCalledWith('cloud down');
      expect(mocks.setSyncStatus).toHaveBeenCalledWith('error');
      errorSpy.mockRestore();
    });
  });
});
