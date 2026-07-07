import React, { useEffect } from 'react';
import { describe, expect, it, jest, beforeEach } from '@jest/globals';
import { render, act, waitFor } from '@testing-library/react-native';
import { Text, View } from 'react-native';
import type { FlashcardGroup, Flashcard, AtomicStep } from '@/types/models';
import { createNewSrsState } from '@/srs/srsEngine';

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
import { useStudySession } from '@/features/study/hooks/useStudySession';
// eslint-disable-next-line import/first
import { ttsService } from '@/services/ttsService';
// eslint-disable-next-line import/first
import { getSttService } from '@/services/sttService';

const mockedTtsService = ttsService as jest.Mocked<typeof ttsService>;
const mockedSttService = getSttService() as jest.Mocked<ReturnType<typeof getSttService>>;

function makeCard(id: string, pages: string[]): Flashcard {
  return { id, pages, srsState: createNewSrsState(), contentUpdatedAt: 0, srsUpdatedAt: 0 };
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
    cardOrder: 'sequential',
    pageLanguages: Array.from({ length: pageCount }, () => 'en-US'),
    pageNames: Array.from({ length: pageCount }, (_, i) => `Page ${i + 1}`),
    activePageCount,
    updatedAt: 0,
  };
}

// Domyślny tryb manualny: pokaż stronę, odkryj resztę tapnięciami,
// jawnie pokaż całą kartę, pokaż ratingi.
function manualSteps(): AtomicStep[] {
  return [
    { type: 'show_page', pageIndex: 0 },
    { type: 'wait_for_tap_to_reveal' },
    { type: 'show_all_pages' },
    { type: 'show_ratings' },
  ];
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

async function tapCard(hookRef: HookResult) {
  await act(async () => {
    hookRef.current!.setHolding(true);
    hookRef.current!.setHolding(false);
    hookRef.current!.handleCardPress();
  });
  await flushQueuedSessionWork();
}

// Manual gate: tap odsłania kolejne strony aż runner pokaże przyciski oceny.
async function revealViaTapGate(hookRef: HookResult) {
  await waitFor(() => {
    const { showRatingButtons, waitingForTap } = hookRef.current!.sessionState;
    expect(showRatingButtons || waitingForTap).toBe(true);
  });
  for (let i = 0; i < 6 && !hookRef.current!.sessionState.showRatingButtons; i += 1) {
    await tapCard(hookRef);
  }
  await waitFor(() => {
    expect(hookRef.current!.sessionState.showRatingButtons).toBe(true);
  });
}

function TestHookWrapper({
  group,
  steps,
  onCardReviewed,
  hookRef,
}: {
  group: FlashcardGroup | null;
  steps: AtomicStep[];
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
    </View>
  );
}

function renderSession(group: FlashcardGroup | null, steps: AtomicStep[]) {
  const onCardReviewed = jest.fn();
  const hookRef: HookResult = { current: null };
  render(
    <TestHookWrapper
      group={group}
      steps={steps}
      onCardReviewed={onCardReviewed}
      hookRef={hookRef}
    />,
  );
  return { onCardReviewed, hookRef };
}

describe('useStudySession', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedTtsService.speak.mockImplementation(() => Promise.resolve());
    mockedSttService.startListening.mockImplementation(() => Promise.resolve('hello'));
    mockedSttService.stopListening.mockImplementation(() => Promise.resolve());
  });

  it('starts in idle state', () => {
    const { hookRef } = renderSession(makeGroup(2, 2, []), manualSteps());
    expect(hookRef.current).not.toBeNull();
    expect(hookRef.current!.sessionState.status).toBe('idle');
    expect(hookRef.current!.sessionState.currentCardIndex).toBe(0);
    expect(hookRef.current!.dueCards).toEqual([]);
  });

  it('startSession sets due cards', async () => {
    const card1 = makeCard('c1', ['hello', 'world']);
    const { hookRef } = renderSession(makeGroup(2, 2, [card1]), manualSteps());
    await startSession(hookRef, [card1]);
    expect(hookRef.current!.dueCards).toEqual([card1]);
  });

  it('manual flow: taps reveal pages, show_all_pages confirms reveal, then ratings show', async () => {
    const card1 = makeCard('c1', ['hello', 'world']);
    const { onCardReviewed, hookRef } = renderSession(makeGroup(2, 2, [card1]), manualSteps());

    await startSession(hookRef, [card1]);

    // Po starcie czekamy w tap-gate (strona 1 ukryta), ratingi jeszcze nie.
    await waitFor(() => {
      expect(hookRef.current!.sessionState.waitingForTap).toBe(true);
    });
    expect(hookRef.current!.sessionState.showRatingButtons).toBe(false);

    await revealViaTapGate(hookRef);
    expect(hookRef.current!.sessionState.revealedPages).toEqual([0, 1]);
    await rateCurrentCard(hookRef, 3);

    await waitFor(() => {
      expect(onCardReviewed).toHaveBeenCalledWith('g1', 'c1', 3);
    });
  });

  it('gate auto-completes when all pages are already revealed (show_all_pages -> tap gate -> ratings)', async () => {
    const card1 = makeCard('c1', ['hello', 'world']);
    const steps: AtomicStep[] = [
      { type: 'show_all_pages' },
      { type: 'wait_for_tap_to_reveal' },
      { type: 'show_ratings' },
    ];
    const { hookRef } = renderSession(makeGroup(2, 2, [card1]), steps);

    await startSession(hookRef, [card1]);

    // Bez ani jednego tapa: gate widzi że wszystko odkryte i runner pokazuje ratingi.
    await waitFor(() => {
      expect(hookRef.current!.sessionState.showRatingButtons).toBe(true);
    });
  });

  it('single tap reveal gate resumes after one page so STT can run before the rest is shown', async () => {
    const card1 = makeCard('c1', ['front', 'spoken answer', 'extra note']);
    const steps: AtomicStep[] = [
      { type: 'show_page', pageIndex: 0 },
      { type: 'wait_for_tap_to_reveal_next' },
      { type: 'listen_and_check', pageIndex: 1, successThreshold: 70 },
      { type: 'show_all_pages' },
      { type: 'show_ratings' },
    ];
    mockedSttService.startListening.mockImplementationOnce(() => new Promise<string>(() => {}));
    const { hookRef } = renderSession(makeGroup(3, 3, [card1]), steps);

    await startSession(hookRef, [card1]);
    await waitFor(() => {
      expect(hookRef.current!.sessionState.waitingForTap).toBe(true);
    });

    await tapCard(hookRef);

    await waitFor(() => {
      expect(hookRef.current!.sessionState.isSttListening).toBe(true);
    });
    expect(hookRef.current!.sessionState.revealedPages).toEqual([0, 1]);
    expect(hookRef.current!.sessionState.showRatingButtons).toBe(false);
    expect(hookRef.current!.sessionState.waitingForTap).toBe(false);

    await tapCard(hookRef);
    await waitFor(() => {
      expect(hookRef.current!.sessionState.showRatingButtons).toBe(true);
    });
  });

  it('STT correct path auto-rates once and advances to the next card without manual input', async () => {
    const card1 = makeCard('c1', ['hello']);
    const steps: AtomicStep[] = [
      { type: 'listen_and_check', pageIndex: 0, successThreshold: 60 },
      { type: 'auto_rate_from_answer', condition: 'correct' },
      { type: 'next_card', condition: 'correct' },
    ];
    const { onCardReviewed, hookRef } = renderSession(makeGroup(1, 1, [card1]), steps);

    await startSession(hookRef, [card1]);

    await waitFor(() => {
      expect(hookRef.current!.sessionState.status).toBe('finished');
    });
    expect(onCardReviewed).toHaveBeenCalledTimes(1);
    expect(onCardReviewed).toHaveBeenCalledWith('g1', 'c1', expect.any(Number));
  });

  it('tap during STT sets skipped: no auto-rate fires, flow falls through to the always terminal', async () => {
    const errorSpy = jest.spyOn(console, 'error').mockImplementation(() => {});
    try {
      const card1 = makeCard('c1', ['hello', 'world']);
      const steps: AtomicStep[] = [
        { type: 'listen_and_check', pageIndex: 0, successThreshold: 60 },
        { type: 'auto_rate_from_answer', condition: 'correct' },
        { type: 'next_card', condition: 'correct' },
        { type: 'show_all_pages' },
        { type: 'show_ratings' },
      ];
      // STT nie kończy się sam — tap musi je skipnąć.
      mockedSttService.startListening.mockImplementationOnce(() => new Promise<string>(() => {}));
      const { onCardReviewed, hookRef } = renderSession(makeGroup(2, 2, [card1]), steps);

      await startSession(hookRef, [card1]);
      await waitFor(() => {
        expect(hookRef.current!.sessionState.isSttListening).toBe(true);
      });

      await tapCard(hookRef);

      await waitFor(() => {
        expect(hookRef.current!.sessionState.showRatingButtons).toBe(true);
      });
      // skipped nie odpala correct/wrong -> brak auto-oceny, karta się nie zmieniła.
      expect(onCardReviewed).not.toHaveBeenCalled();
      expect(hookRef.current!.sessionState.currentCardIndex).toBe(0);
    } finally {
      errorSpy.mockRestore();
    }
  });

  it('speak_all_pages speaks every active page in order and continues to the next step', async () => {
    const card1 = makeCard('c1', ['front', 'middle', 'back']);
    const steps: AtomicStep[] = [
      { type: 'speak_all_pages' },
      { type: 'show_all_pages' },
      { type: 'show_ratings' },
    ];
    // activePageCount 2: trzecia strona jest nieaktywna i NIE powinna być czytana.
    const { hookRef } = renderSession(makeGroup(3, 2, [card1]), steps);

    await startSession(hookRef, [card1]);

    await waitFor(() => {
      expect(hookRef.current!.sessionState.showRatingButtons).toBe(true);
    });
    expect(mockedTtsService.speak).toHaveBeenCalledTimes(2);
    expect(mockedTtsService.speak).toHaveBeenNthCalledWith(
      1,
      expect.objectContaining({ text: 'front' }),
    );
    expect(mockedTtsService.speak).toHaveBeenNthCalledWith(
      2,
      expect.objectContaining({ text: 'middle' }),
    );
  });

  it('speak_all_pages stops immediately and breaks the loop when a skip is requested', async () => {
    const card1 = makeCard('c1', ['front', 'middle', 'back']);
    const steps: AtomicStep[] = [
      { type: 'speak_all_pages' },
      { type: 'show_all_pages' },
      { type: 'show_ratings' },
    ];
    const { hookRef } = renderSession(makeGroup(3, 2, [card1]), steps);

    // Simulate a tap (skip request) during the first page TTS.
    mockedTtsService.speak.mockImplementationOnce(async () => {
      // Trigger a skip dynamically when speak starts.
      await act(async () => {
        hookRef.current!.handleCardPress();
      });
    });

    await startSession(hookRef, [card1]);

    await waitFor(() => {
      expect(hookRef.current!.sessionState.showRatingButtons).toBe(true);
    });
    // It should speak 'front', get skipped, and break without speaking 'middle'.
    expect(mockedTtsService.speak).toHaveBeenCalledTimes(1);
    expect(mockedTtsService.speak).toHaveBeenNthCalledWith(
      1,
      expect.objectContaining({ text: 'front' }),
    );
  });

  it('wait_for_tap stops the runner until a tap, without revealing any page', async () => {
    const card1 = makeCard('c1', ['hello', 'world']);
    const steps: AtomicStep[] = [
      { type: 'show_page', pageIndex: 0 },
      { type: 'wait_for_tap' },
      { type: 'show_all_pages' },
      { type: 'show_ratings' },
    ];
    const { hookRef } = renderSession(makeGroup(2, 2, [card1]), steps);

    await startSession(hookRef, [card1]);

    // Gate otwarty: czekamy na tap, nic się nie odsłoniło poza show_page.
    await waitFor(() => {
      expect(hookRef.current!.sessionState.waitingForTap).toBe(true);
    });
    expect(hookRef.current!.sessionState.revealedPages).toEqual([0]);
    expect(hookRef.current!.sessionState.showRatingButtons).toBe(false);

    await tapCard(hookRef);

    // Tap niczego sam nie odsłania — domyka gate; resztę odsłania krok show_all_pages.
    await waitFor(() => {
      expect(hookRef.current!.sessionState.showRatingButtons).toBe(true);
    });
    expect(hookRef.current!.sessionState.revealedPages).toEqual([0, 1]);
  });

  it('next_card advances without recording an SRS rating', async () => {
    const card1 = makeCard('c1', ['hello']);
    const card2 = makeCard('c2', ['world']);
    const steps: AtomicStep[] = [{ type: 'show_all_pages' }, { type: 'next_card' }];
    const { onCardReviewed, hookRef } = renderSession(makeGroup(1, 1, [card1, card2]), steps);

    await startSession(hookRef, [card1, card2]);

    await waitFor(() => {
      expect(hookRef.current!.sessionState.status).toBe('finished');
    });
    expect(onCardReviewed).not.toHaveBeenCalled();
  });

  it('STT incorrect path can auto-rate a fixed rating and mark the card failed', async () => {
    mockedSttService.startListening.mockImplementation(() => Promise.resolve('totally different'));
    const card1 = makeCard('c1', ['hello']);
    const steps: AtomicStep[] = [
      { type: 'listen_and_check', pageIndex: 0, successThreshold: 90 },
      { type: 'mark_failed', condition: 'wrong' },
      { type: 'auto_rate_fixed', rating: 1, condition: 'wrong' },
      { type: 'next_card', condition: 'wrong' },
    ];
    const { onCardReviewed, hookRef } = renderSession(makeGroup(1, 1, [card1]), steps);

    await startSession(hookRef, [card1]);

    await waitFor(() => {
      expect(hookRef.current!.sessionState.status).toBe('finished');
    });
    expect(onCardReviewed).toHaveBeenCalledTimes(1);
    expect(onCardReviewed).toHaveBeenCalledWith('g1', 'c1', 1);
    expect(hookRef.current!.failedCount).toBe(1);
  });
});
