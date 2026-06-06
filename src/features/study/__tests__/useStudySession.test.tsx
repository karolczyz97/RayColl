import React, { useEffect } from 'react';
import { describe, expect, it, jest, beforeEach } from '@jest/globals';
import { render, act, waitFor } from '@testing-library/react-native';
import { Text, View } from 'react-native';
import type { FlashcardGroup, Flashcard, ModeStep } from '@/types/models';
import { createNewSrsState } from '@/srs/srsEngine';
import { PEEK_HOLD_THRESHOLD_MS } from '@/features/study/session/sessionUtils';

jest.mock('@/services/ttsService', () => ({
  ttsService: { speak: jest.fn(() => Promise.resolve()), cancel: jest.fn() },
}));

jest.mock('@/services/sttService', () => {
  const service = {
    startListening: jest.fn(() => Promise.resolve('hello')),
    stopListening: jest.fn(() => Promise.resolve()),
    isSupported: () => true,
  };
  return {
    getSttService: jest.fn(() => service),
  };
});

jest.mock('@/services/audioFeedback', () => ({
  playMicOnSound: jest.fn(),
  playMicOffSound: jest.fn(),
  playSuccessSound: jest.fn(),
  playErrorSound: jest.fn(),
}));

jest.mock('@/contexts/UserPreferencesContext', () => ({
  useAppTheme: () => ({ ttsRate: 1.0 }),
}));

// eslint-disable-next-line import/first
import { useStudySession } from '@/hooks/useStudySession';
// eslint-disable-next-line import/first
import { ttsService } from '@/services/ttsService';
// eslint-disable-next-line import/first
import { getSttService } from '@/services/sttService';

const mockedTtsService = ttsService as jest.Mocked<typeof ttsService>;
const mockedSttService = getSttService() as jest.Mocked<ReturnType<typeof getSttService>>;

function makeCard(id: string, pages: string[]): Flashcard {
  return { id, pages, srsState: createNewSrsState() };
}

function makeGroup(
  pageCount: number,
  activePageCount: number,
  cards: Flashcard[],
): FlashcardGroup {
  return {
    id: 'g1',
    name: 'Test',
    cards,
    activeModeId: 'basic',
    studyFilter: 'all',
    pageLanguages: Array.from({ length: pageCount }, () => 'en-US'),
    pageNames: Array.from({ length: pageCount }, (_, i) => `Page ${i + 1}`),
    activePageCount,
  };
}

function makeSteps(): ModeStep[] {
  return [{ type: 'rate' }];
}

interface HookResult {
  current: ReturnType<typeof useStudySession> | null;
}

async function flushQueuedSessionWork() {
  await act(async () => {
    await new Promise<void>((resolve) => {
      setTimeout(resolve, 0);
    });
  });
}

async function startSession(hookRef: HookResult, cards: Flashcard[]) {
  await act(async () => {
    hookRef.current!.startSession(cards);
  });
  await flushQueuedSessionWork();
}

async function rateCurrentCard(hookRef: HookResult, rating: number) {
  await act(async () => {
    await hookRef.current!.handleRating(rating);
  });
}

function TestHookWrapper({
  group,
  steps,
  onCardReviewed,
  hookRef,
}: {
  group: FlashcardGroup | null;
  steps: ModeStep[];
  onCardReviewed: (groupId: string, cardId: string, rating: number) => void;
  hookRef: HookResult;
}) {
  const hookResult = useStudySession(group, steps, onCardReviewed);

  useEffect(() => {
    hookRef.current = hookResult;
  });

  return (
    <View>
      <Text testID="status">{hookResult.sessionState.status}</Text>
      <Text testID="cardIndex">{hookResult.sessionState.currentCardIndex}</Text>
      <Text testID="failedCount">{hookResult.failedCount}</Text>
      <Text testID="dueCards">{hookResult.dueCards.length}</Text>
    </View>
  );
}

describe('useStudySession', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedTtsService.speak.mockImplementation(() => Promise.resolve());
    mockedSttService.startListening.mockImplementation(() => Promise.resolve('hello'));
    mockedSttService.stopListening.mockImplementation(() => Promise.resolve());
  });

  it('starts in idle state', () => {
    const group = makeGroup(2, 2, []);
    const onCardReviewed = jest.fn();
    const hookRef: HookResult = { current: null };

    render(
      <TestHookWrapper
        group={group}
        steps={makeSteps()}
        onCardReviewed={onCardReviewed}
        hookRef={hookRef}
      />,
    );

    expect(hookRef.current).not.toBeNull();
    expect(hookRef.current!.sessionState.status).toBe('idle');
    expect(hookRef.current!.sessionState.currentCardIndex).toBe(0);
    expect(hookRef.current!.dueCards).toEqual([]);
    expect(hookRef.current!.failedCount).toBe(0);
  });

  it('startSession sets due cards', async () => {
    const card1 = makeCard('c1', ['hello', 'world']);
    const group = makeGroup(2, 2, [card1]);
    const onCardReviewed = jest.fn();
    const hookRef: HookResult = { current: null };

    render(
      <TestHookWrapper
        group={group}
        steps={makeSteps()}
        onCardReviewed={onCardReviewed}
        hookRef={hookRef}
      />,
    );

    await startSession(hookRef, [card1]);

    expect(hookRef.current!.dueCards).toEqual([card1]);
  });

  it('handleRating calls onCardReviewed', async () => {
    const card1 = makeCard('c1', ['hello', 'world']);
    const group = makeGroup(2, 2, [card1]);
    const onCardReviewed = jest.fn();
    const hookRef: HookResult = { current: null };

    render(
      <TestHookWrapper
        group={group}
        steps={makeSteps()}
        onCardReviewed={onCardReviewed}
        hookRef={hookRef}
      />,
    );

    await startSession(hookRef, [card1]);

    // With a 'rate' step, the session should immediately transition to revealed
    await waitFor(() => {
      expect(hookRef.current!.sessionState.showRatingButtons).toBe(true);
    });

    await rateCurrentCard(hookRef, 3);

    await waitFor(() => {
      expect(onCardReviewed).toHaveBeenCalledWith('g1', 'c1', 3);
    });
  });

  it('STT service error reveals the card for manual rating without auto-recording a review', async () => {
    const card1 = makeCard('c1', ['hello', 'world']);
    const group = makeGroup(2, 2, [card1]);
    const onCardReviewed = jest.fn();
    const hookRef: HookResult = { current: null };
    const steps: ModeStep[] = [
      { type: 'listen_and_branch', pageIndex: 0, successThreshold: 60 },
      { type: 'rate' },
    ];

    mockedSttService.startListening.mockImplementationOnce(() =>
      Promise.reject(new Error('service-failure')),
    );

    render(
      <TestHookWrapper
        group={group}
        steps={steps}
        onCardReviewed={onCardReviewed}
        hookRef={hookRef}
      />,
    );

    await startSession(hookRef, [card1]);

    await waitFor(() => {
      expect(hookRef.current!.sessionState.showRatingButtons).toBe(true);
    });
    expect(onCardReviewed).not.toHaveBeenCalled();
    expect(hookRef.current!.sessionState.currentCardIndex).toBe(0);
  });

  it('rating 1 adds card to failed count', async () => {
    const card1 = makeCard('c1', ['hello', 'world']);
    const group = makeGroup(2, 2, [card1]);
    const onCardReviewed = jest.fn();
    const hookRef: HookResult = { current: null };

    render(
      <TestHookWrapper
        group={group}
        steps={makeSteps()}
        onCardReviewed={onCardReviewed}
        hookRef={hookRef}
      />,
    );

    await startSession(hookRef, [card1]);

    await rateCurrentCard(hookRef, 1);

    expect(hookRef.current!.failedCount).toBe(1);
  });

  it('rating 1 adds card to failed count only once', async () => {
    const card1 = makeCard('c1', ['hello', 'world']);
    const group = makeGroup(2, 2, [card1]);
    const onCardReviewed = jest.fn();
    const hookRef: HookResult = { current: null };

    render(
      <TestHookWrapper
        group={group}
        steps={makeSteps()}
        onCardReviewed={onCardReviewed}
        hookRef={hookRef}
      />,
    );

    await startSession(hookRef, [card1]);

    await rateCurrentCard(hookRef, 1);
    await rateCurrentCard(hookRef, 1);

    expect(hookRef.current!.failedCount).toBe(1);
    expect(onCardReviewed).toHaveBeenCalledTimes(1);
  });

  it('stopSession stops audio', async () => {
    const card1 = makeCard('c1', ['hello', 'world']);
    const group = makeGroup(2, 2, [card1]);
    const onCardReviewed = jest.fn();
    const hookRef: HookResult = { current: null };

    render(
      <TestHookWrapper
        group={group}
        steps={makeSteps()}
        onCardReviewed={onCardReviewed}
        hookRef={hookRef}
      />,
    );

    await startSession(hookRef, [card1]);

    act(() => {
      hookRef.current!.stopSession();
    });

    expect(ttsService.cancel).toHaveBeenCalled();
    expect(mockedSttService.stopListening).toHaveBeenCalled();
  });

  it('tap during speak_page skips and advances', async () => {
    const card1 = makeCard('c1', ['hello', 'world']);
    const group = makeGroup(2, 2, [card1]);
    const onCardReviewed = jest.fn();
    const hookRef: HookResult = { current: null };
    const steps: ModeStep[] = [
      { type: 'speak_page', pageIndex: 0, extraPauseMs: 10000 },
      { type: 'rate' },
    ];

    mockedTtsService.speak.mockImplementationOnce(() => new Promise<void>(() => {}));

    render(
      <TestHookWrapper
        group={group}
        steps={steps}
        onCardReviewed={onCardReviewed}
        hookRef={hookRef}
      />,
    );

    await startSession(hookRef, [card1]);

    await waitFor(() => {
      expect(hookRef.current!.sessionState.isTtsPlaying).toBe(true);
    });

    await act(async () => {
      hookRef.current!.handleCardPress();
    });

    await waitFor(() => {
      expect(hookRef.current!.sessionState.showRatingButtons).toBe(true);
    });
    expect(ttsService.cancel).toHaveBeenCalled();
  });

  it('restartSession restarts with fresh cards from group', async () => {
    const card1 = makeCard('c1', ['hello', 'world']);
    const originalCard1 = makeCard('c1', ['hello', 'world']);
    const group = makeGroup(2, 2, [originalCard1]);
    const onCardReviewed = jest.fn();
    const hookRef: HookResult = { current: null };

    render(
      <TestHookWrapper
        group={group}
        steps={makeSteps()}
        onCardReviewed={onCardReviewed}
        hookRef={hookRef}
      />,
    );

    await startSession(hookRef, [card1]);

    await act(async () => {
      hookRef.current!.restartSession();
    });
    await flushQueuedSessionWork();

    expect(hookRef.current!.dueCards).toEqual([originalCard1]);
  });

  it('restartFailed only restarts with failed cards', async () => {
    const card1 = makeCard('c1', ['hello']);
    const card2 = makeCard('c2', ['world']);
    const group = makeGroup(2, 2, [card1, card2]);
    const onCardReviewed = jest.fn();
    const hookRef: HookResult = { current: null };

    render(
      <TestHookWrapper
        group={group}
        steps={makeSteps()}
        onCardReviewed={onCardReviewed}
        hookRef={hookRef}
      />,
    );

    await startSession(hookRef, [card1, card2]);

    await rateCurrentCard(hookRef, 1);

    expect(hookRef.current!.failedCount).toBe(1);

    await act(async () => {
      hookRef.current!.restartFailed();
    });
    await flushQueuedSessionWork();

    expect(hookRef.current!.dueCards).toEqual([card1]);
  });

  it('restartFailed uses fresh card state from group', async () => {
    const staleCard1 = makeCard('c1', ['old']);
    const freshCard1 = makeCard('c1', ['fresh']);
    const group = makeGroup(1, 1, [freshCard1]);
    const onCardReviewed = jest.fn();
    const hookRef: HookResult = { current: null };

    render(
      <TestHookWrapper
        group={group}
        steps={makeSteps()}
        onCardReviewed={onCardReviewed}
        hookRef={hookRef}
      />,
    );

    await startSession(hookRef, [staleCard1]);
    await rateCurrentCard(hookRef, 1);

    await act(async () => {
      hookRef.current!.restartFailed();
    });
    await flushQueuedSessionWork();

    expect(hookRef.current!.dueCards).toEqual([freshCard1]);
  });

  it('long hold sets and clears peekedPageIndex', () => {
    jest.useFakeTimers();
    try {
      const group = makeGroup(2, 2, []);
      const onCardReviewed = jest.fn();
      const hookRef: HookResult = { current: null };

      render(
        <TestHookWrapper
          group={group}
          steps={makeSteps()}
          onCardReviewed={onCardReviewed}
          hookRef={hookRef}
        />,
      );

      act(() => {
        hookRef.current!.setHolding(true);
      });

      act(() => {
        jest.advanceTimersByTime(PEEK_HOLD_THRESHOLD_MS);
      });

      expect(hookRef.current!.sessionState.peekedPageIndex).toBe(1);

      act(() => {
        hookRef.current!.setHolding(false);
      });

      expect(hookRef.current!.sessionState.peekedPageIndex).toBeNull();
    } finally {
      jest.useRealTimers();
    }
  });

  it('getFreshCards returns current card state from group', async () => {
    const card1 = makeCard('c1', ['old']);
    const freshCard1 = makeCard('c1', ['fresh']);
    const group = makeGroup(2, 2, [freshCard1]);
    const onCardReviewed = jest.fn();
    const hookRef: HookResult = { current: null };

    render(
      <TestHookWrapper
        group={group}
        steps={makeSteps()}
        onCardReviewed={onCardReviewed}
        hookRef={hookRef}
      />,
    );

    await startSession(hookRef, [card1]);

    // restartSession calls getFreshCards internally
    await act(async () => {
      hookRef.current!.restartSession();
    });
    await flushQueuedSessionWork();

    expect(hookRef.current!.dueCards).toEqual([freshCard1]);
  });
});
