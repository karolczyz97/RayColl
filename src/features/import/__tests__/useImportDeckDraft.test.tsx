import React, { useEffect } from 'react';
import { describe, expect, it, jest, beforeEach, afterEach } from '@jest/globals';
import { render, act } from '@testing-library/react-native';
import { Text } from 'react-native';

jest.mock('expo-document-picker', () => ({
  getDocumentAsync: jest.fn(),
}));

jest.mock('@/store/FlashcardStoreContext', () => ({
  useFlashcardStore: () => ({
    importDeck: jest.fn(async () => ({ ok: true })),
  }),
}));

// eslint-disable-next-line import/first
import { getActiveSepValue } from '../importDraftUtils';
// eslint-disable-next-line import/first
import { useImportDeckDraft } from '../useImportDeckDraft';

type Draft = ReturnType<typeof useImportDeckDraft>;

function Harness({ hookRef }: { hookRef: { current: Draft | null } }) {
  const draft = useImportDeckDraft();
  useEffect(() => {
    hookRef.current = draft;
  });
  return <Text>{draft.cards.length}</Text>;
}

function renderDraft() {
  const hookRef: { current: Draft | null } = { current: null };
  render(<Harness hookRef={hookRef} />);
  return hookRef;
}

function settle() {
  act(() => {
    jest.advanceTimersByTime(300);
  });
}

describe('getActiveSepValue', () => {
  it('returns the key directly for non-custom separators', () => {
    expect(getActiveSepValue('semicolon', '')).toBe('semicolon');
    expect(getActiveSepValue('comma', 'x')).toBe('comma');
  });

  it('uses the custom separator, falling back to a comma when empty', () => {
    expect(getActiveSepValue('custom', '|')).toBe('|');
    expect(getActiveSepValue('custom', '')).toBe(',');
  });
});

describe('useImportDeckDraft cascade', () => {
  beforeEach(() => {
    jest.useFakeTimers();
  });

  afterEach(() => {
    jest.runOnlyPendingTimers();
    jest.useRealTimers();
  });

  it('builds preview cards from pasted text and reacts to the header toggle', () => {
    const hookRef = renderDraft();

    // Simulate a paste: the explicit paste flag forces full re-detection.
    act(() => {
      hookRef.current!.handlePaste();
      hookRef.current!.handleTextChange('a;b\nc;d');
    });
    settle();

    expect(hookRef.current!.rawColumnCount).toBe(2);

    // Force header OFF: every row becomes a card.
    act(() => {
      hookRef.current!.handleHeaderToggle(false);
    });
    settle();

    expect(hookRef.current!.firstRowIsHeader).toBe(false);
    expect(hookRef.current!.cards.map((card) => card.pages)).toEqual([
      ['a', 'b'],
      ['c', 'd'],
    ]);

    // Force header ON: the first row is consumed as page names, not a card.
    act(() => {
      hookRef.current!.handleHeaderToggle(true);
    });
    settle();

    expect(hookRef.current!.firstRowIsHeader).toBe(true);
    expect(hookRef.current!.cards.map((card) => card.pages)).toEqual([['c', 'd']]);
    expect(hookRef.current!.pageNames.slice(0, 2)).toEqual(['a', 'b']);
  });

  it('clears the preview when the source text is emptied', () => {
    const hookRef = renderDraft();

    act(() => {
      hookRef.current!.handlePaste();
      hookRef.current!.handleTextChange('one;two\nthree;four');
    });
    settle();
    act(() => {
      hookRef.current!.handleHeaderToggle(false);
    });
    settle();
    expect(hookRef.current!.cards.length).toBe(2);

    act(() => {
      hookRef.current!.handleTextChange('');
    });
    settle();

    expect(hookRef.current!.cards).toEqual([]);
    expect(hookRef.current!.rawText).toBe('');
  });

  it('pageCount decreases when re-importing text with fewer columns', () => {
    const hookRef = renderDraft();

    act(() => {
      hookRef.current!.handlePaste();
      hookRef.current!.handleTextChange('a;b;c;d;e\nf;g;h;i;j');
    });
    settle();

    expect(hookRef.current!.pageCount).toBe(5);
    expect(hookRef.current!.rawColumnCount).toBe(5);

    act(() => {
      hookRef.current!.handlePaste();
      hookRef.current!.handleTextChange('x;y\nz;w');
    });
    settle();

    expect(hookRef.current!.pageCount).toBe(2);
    expect(hookRef.current!.rawColumnCount).toBe(2);
  });
});
