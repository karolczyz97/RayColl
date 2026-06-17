import React, { useEffect } from 'react';
import { describe, expect, it, jest, beforeEach } from '@jest/globals';
import { render, act } from '@testing-library/react-native';
import { Text } from 'react-native';
import type { Flashcard } from '@/types/models';

import { useFlashcardListEditing } from '../useFlashcardListEditing';

type HookResult = ReturnType<typeof useFlashcardListEditing>;

const mockOnSaveCard = jest.fn();
const mockOnCreateCard = jest.fn();
const mockOnDeleteCard = jest.fn();

function Harness({
  pageCount,
  hookRef,
}: {
  pageCount: number;
  hookRef: { current: HookResult | null };
}) {
  const result = useFlashcardListEditing({
    pageCount,
    onSaveCard: mockOnSaveCard,
    onCreateCard: mockOnCreateCard,
    onDeleteCard: mockOnDeleteCard,
  });

  useEffect(() => {
    hookRef.current = result;
  });

  return <Text>ok</Text>;
}

function renderHook(pageCount: number) {
  const hookRef: { current: HookResult | null } = { current: null };
  render(<Harness pageCount={pageCount} hookRef={hookRef} />);
  return hookRef;
}

describe('useFlashcardListEditing', () => {
  beforeEach(() => {
    mockOnSaveCard.mockClear();
    mockOnCreateCard.mockClear();
    mockOnDeleteCard.mockClear();
  });

  it('startEdit pads page array to pageCount and preserves existing pages if longer', () => {
    const hookRef = renderHook(2);

    const card: Flashcard = {
      id: 'c1',
      pages: ['a', 'b', 'c'],
      contentUpdatedAt: 0,
      srsUpdatedAt: 0,
      srsState: {
        difficulty: 0,
        stability: 0,
        repetitions: 0,
        state: 0,
        lastReviewTimestamp: 0,
        nextReviewTimestamp: 0,
      },
    };

    act(() => {
      hookRef.current?.startEdit(card);
    });

    // padArray does not truncate, so editPages should contain all 3 pages
    expect(hookRef.current?.editPages).toEqual(['a', 'b', 'c']);
  });

  it('saveEdit saves the entire modified page array preserving extra pages', () => {
    const hookRef = renderHook(2);

    const card: Flashcard = {
      id: 'c1',
      pages: ['a', 'b', 'c'],
      contentUpdatedAt: 0,
      srsUpdatedAt: 0,
      srsState: {
        difficulty: 0,
        stability: 0,
        repetitions: 0,
        state: 0,
        lastReviewTimestamp: 0,
        nextReviewTimestamp: 0,
      },
    };

    act(() => {
      hookRef.current?.startEdit(card);
    });

    // Modify a visible page (index 0)
    act(() => {
      const nextPages = [...(hookRef.current?.editPages || [])];
      nextPages[0] = 'a_modified';
      hookRef.current?.setEditPages(nextPages);
    });

    // Save
    act(() => {
      hookRef.current?.saveEdit();
    });

    // Verify it saved the whole array, including index 2 (hidden 'c')
    expect(mockOnSaveCard).toHaveBeenCalledWith('c1', ['a_modified', 'b', 'c']);
  });

  it('startCreate initializes pages of length pageCount', () => {
    const hookRef = renderHook(3);

    act(() => {
      hookRef.current?.startCreate();
    });

    expect(hookRef.current?.editPages).toEqual(['', '', '']);
  });
});
