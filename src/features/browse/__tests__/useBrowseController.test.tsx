import React, { useEffect } from 'react';
import { describe, expect, it, jest, beforeEach } from '@jest/globals';
import { render, act } from '@testing-library/react-native';
import { Text } from 'react-native';
import { useBrowseController } from '../useBrowseController';

const mockGroupId = { groupId: 'g1' };
jest.mock('expo-router', () => ({
  useLocalSearchParams: () => mockGroupId,
}));

const mockStore = {
  groups: [
    {
      id: 'g1',
      name: 'Group 1',
      cards: [
        {
          id: 'c1',
          pages: ['front_1', 'back_1', 'hidden_1'],
          srsState: {
            difficulty: 0,
            stability: 0,
            repetitions: 0,
            state: 0,
            lastReviewTimestamp: 0,
            nextReviewTimestamp: 0,
          },
        },
      ],
      activeModeId: 'm1',
      studyFilter: 'all',
      cardOrder: 'sequential',
      pageLanguages: ['en', 'en', 'en'],
      pageNames: ['Front', 'Back', 'Extra'],
      activePageCount: 2,
      updatedAt: 0,
    },
  ],
  archivedGroups: [],
  isLoading: false,
  addFlashcard: jest.fn(),
  updateFlashcard: jest.fn(),
  deleteFlashcard: jest.fn(),
};

jest.mock('@/store/FlashcardStoreContext', () => ({
  useFlashcardStore: () => mockStore,
}));

// Mock useI18n
jest.mock('@/i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
    language: 'pl',
  }),
}));



type Controller = ReturnType<typeof useBrowseController>;

function Harness({ hookRef }: { hookRef: { current: Controller | null } }) {
  const controller = useBrowseController();
  useEffect(() => {
    hookRef.current = controller;
  });
  return <Text>ok</Text>;
}

function renderController() {
  const hookRef: { current: Controller | null } = { current: null };
  const { rerender } = render(<Harness hookRef={hookRef} />);
  return { hookRef, rerender };
}

describe('useBrowseController', () => {
  beforeEach(() => {
    mockStore.addFlashcard.mockClear();
    mockStore.updateFlashcard.mockClear();
    mockStore.deleteFlashcard.mockClear();
    mockGroupId.groupId = 'g1';
    mockStore.groups[0].activePageCount = 2;
  });

  it('correctly initializes state and hasHiddenPages flag', () => {
    const { hookRef } = renderController();
    expect(hookRef.current?.viewHidden).toBe(false);
    expect(hookRef.current?.hasHiddenPages).toBe(true);
  });

  it('resets viewHidden when hasHiddenPages becomes false', () => {
    const { hookRef, rerender } = renderController();
    act(() => {
      hookRef.current?.setViewHidden(true);
    });
    expect(hookRef.current?.viewHidden).toBe(true);

    // Simulate page count updated in settings so no hidden pages exist
    act(() => {
      mockStore.groups[0].activePageCount = 3;
    });

    // Re-render the same component to run the state adjustment
    act(() => {
      rerender(<Harness hookRef={hookRef} />);
    });
    expect(hookRef.current?.hasHiddenPages).toBe(false);
    expect(hookRef.current?.viewHidden).toBe(false);
  });

  it('resets viewHidden when groupId changes', () => {
    const { hookRef, rerender } = renderController();
    act(() => {
      hookRef.current?.setViewHidden(true);
    });
    expect(hookRef.current?.viewHidden).toBe(true);

    // Simulate switching groups
    act(() => {
      mockGroupId.groupId = 'g2';
    });

    // Re-render the same component to run the state adjustment
    act(() => {
      rerender(<Harness hookRef={hookRef} />);
    });
    expect(hookRef.current?.viewHidden).toBe(false);
  });

  it('correctly performs search filtering based on viewHidden state', () => {
    const { hookRef } = renderController();

    // When viewHidden is false, searching for text in hidden page ('hidden_1') returns no results
    act(() => {
      hookRef.current?.setSearch('hidden_1');
    });
    expect(hookRef.current?.filteredCards.length).toBe(0);

    // Searching for text in visible page ('front_1') returns results
    act(() => {
      hookRef.current?.setSearch('front_1');
    });
    expect(hookRef.current?.filteredCards.length).toBe(1);

    // When viewHidden is true, searching for text in hidden page ('hidden_1') returns results
    act(() => {
      hookRef.current?.setSearch('hidden_1');
      hookRef.current?.setViewHidden(true);
    });
    expect(hookRef.current?.filteredCards.length).toBe(1);
  });
});
