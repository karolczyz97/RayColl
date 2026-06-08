import React from 'react';
import { renderAsync, screen } from '@testing-library/react-native';

jest.mock('expo-speech-recognition', () => {
  const listeners: Record<string, Set<(event: unknown) => void>> = {};
  const ExpoSpeechRecognitionModule = {
    addListener: jest.fn(() => ({ remove: jest.fn() })),
    start: jest.fn(),
    stop: jest.fn(),
    abort: jest.fn(),
    isRecognitionAvailable: jest.fn(() => false),
    getPermissionsAsync: jest.fn(async () => ({ granted: false })),
    requestPermissionsAsync: jest.fn(async () => ({ granted: false })),
  };
  return { ExpoSpeechRecognitionModule };
});

import Dashboard from '../index';
import { TestProviders } from '../../test/renderWithAppProviders';

jest.mock('@/store/FlashcardStoreContext', () => ({
  useFlashcardStore: () => ({
    activityHeatmap: {},
    clearLastLoginError: jest.fn(),
    getDueCards: jest.fn(() => []),
    groups: [],
    isLoading: false,
    lastLoginError: '',
    lastPersistenceError: null,
    lastStoreError: null,
    lastSyncError: null,
    signIn: jest.fn(),
    signOut: jest.fn(),
    syncRefreshKey: 0,
    syncStatus: 'idle',
    updateGroup: jest.fn(),
    user: null,
  }),
}));

describe('Dashboard top navigation', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('shows only app branding in the top bar while the navigation rail is visible', async () => {
    await renderAsync(
      <TestProviders
        shellContext={{
          isCompact: false,
          isMedium: false,
          isExpanded: true,
          showPersistentNavigation: true,
          navWidth: 80,
          contentWidth: 760,
        }}
      >
        <Dashboard />
      </TestProviders>,
    );

    expect(screen.getByText('RayColl')).toBeOnTheScreen();
    expect(screen.queryByLabelText('Study Statistics')).toBeNull();
    expect(screen.queryByLabelText('Archived')).toBeNull();
    expect(screen.queryByLabelText('App Settings')).toBeNull();
  });

  it('keeps home actions in the top bar on compact screens without the rail', async () => {
    await renderAsync(
      <TestProviders
        shellContext={{
          isCompact: true,
          isMedium: false,
          isExpanded: false,
          showPersistentNavigation: false,
          navWidth: 0,
          contentWidth: 390,
        }}
      >
        <Dashboard />
      </TestProviders>,
    );

    expect(screen.getByLabelText('Study Statistics')).toBeOnTheScreen();
    expect(screen.getByLabelText('Archived')).toBeOnTheScreen();
    expect(screen.getByLabelText('App Settings')).toBeOnTheScreen();
  });
});
