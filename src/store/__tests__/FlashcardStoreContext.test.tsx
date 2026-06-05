import React from 'react';
import { describe, it, jest, expect } from '@jest/globals';
import { render } from '@testing-library/react-native';
import { Text } from 'react-native';
import { FlashcardStoreProvider, useFlashcardStore } from '../FlashcardStoreContext';

jest.mock('@/services/firebase', () => ({
  signInWithGoogle: jest.fn(() => Promise.resolve()),
  signOutUser: jest.fn(() => Promise.resolve()),
  onAuthChange: jest.fn(() => () => {}),
}));

jest.mock('../persistence/localPersistence', () => ({
  loadLocalData: jest.fn(() => Promise.resolve(null)),
  saveLocalData: jest.fn(() => Promise.resolve()),
  getSeedVersion: jest.fn(() => Promise.resolve(1)),
  setSeedVersion: jest.fn(() => Promise.resolve()),
}));

jest.mock('../persistence/firebasePersistence', () => ({
  loadCloudData: jest.fn(() => Promise.resolve(null)),
  saveCloudData: jest.fn(() => Promise.resolve()),
}));

jest.mock('../persistence/persistenceQueue', () => ({
  usePersistenceQueue: jest.fn(() => ({
    enqueue: jest.fn(),
    flush: jest.fn(() => Promise.resolve()),
    cancel: jest.fn(),
  })),
}));

jest.mock('../seed/seedGroups', () => ({
  createSeedGroups: jest.fn(() => []),
  SEED_VERSION: 1,
}));

jest.mock('../seed/seedModes', () => ({
  createSeedModes: jest.fn(() => []),
}));

jest.mock('../actions/groupActions', () => ({
  purgeExpiredArchivesAction: jest.fn((groups: unknown[]) => ({ groups })),
}));

jest.mock('../selectors/liveSelectors', () => ({
  selectActiveGroups: jest.fn(<T,>(g: T) => g),
  selectArchivedGroups: jest.fn(() => []),
  selectLiveStudyModes: jest.fn(<T,>(m: T) => m),
}));

jest.mock('@/components/dialogs/MigrationDialog', () => ({
  MigrationDialog: jest.fn(() => null),
}));

jest.mock('@/utils/withTimeout', () => ({
  withTimeout: jest.fn(<T,>(promise: Promise<T>) => promise),
}));

// Prevent the async bootstrap from firing async state updates after render
// (avoids act() warnings in this smoke-test suite — bootstrap is tested separately).
jest.mock('../useStoreBootstrap', () => ({
  useStoreBootstrap: jest.fn(),
}));

describe('FlashcardStoreContext', () => {
  it('provides the store to children through the provider', () => {
    let store: ReturnType<typeof useFlashcardStore> | null = null;

    function Consumer() {
      store = useFlashcardStore();
      return <Text>{store.isLoading ? 'loading' : 'ready'}</Text>;
    }

    render(
      <FlashcardStoreProvider>
        <Consumer />
      </FlashcardStoreProvider>,
    );

    expect(store).not.toBeNull();
    expect(typeof store!.signIn).toBe('function');
    expect(typeof store!.signOut).toBe('function');
    expect(typeof store!.addGroup).toBe('function');
  });

  it('throws a descriptive error when used outside the provider', () => {
    function Consumer() {
      useFlashcardStore();
      return null;
    }

    expect(() => render(<Consumer />)).toThrow(
      'useFlashcardStore must be used within a FlashcardStoreProvider',
    );
  });
});
