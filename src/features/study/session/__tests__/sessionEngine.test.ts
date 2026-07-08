import { describe, expect, it, jest } from '@jest/globals';

import type { AtomicStep, Flashcard, FlashcardGroup } from '@/types/models';
import { createNewSrsState } from '@/srs/srsEngine';
import { SessionEngine } from '@/features/study/session/SessionEngine';
import type { StudySkipState } from '@/features/study/hooks/useStudyAudio';
import { buildSessionViewModel } from '@/features/study/session/sessionViewModel';
import { INITIAL_STUDY_SESSION_STATE } from '@/features/study/session/sessionReducer';

jest.mock('@/services/audioFeedback', () => ({
  playSuccessSound: jest.fn(),
  playErrorSound: jest.fn(),
}));

jest.mock('@/services/ttsService', () => ({
  ttsService: {
    speak: jest.fn(() => Promise.resolve()),
    cancel: jest.fn(),
  },
}));

// eslint-disable-next-line import/first
import { ttsService } from '@/services/ttsService';

function makeCard(id: string, pages: string[]): Flashcard {
  return { id, pages, srsState: createNewSrsState(), contentUpdatedAt: 0, srsUpdatedAt: 0 };
}

function makeCards(count: number, pages = ['a']): Flashcard[] {
  return Array.from({ length: count }, (_, i) => makeCard(`c${i + 1}`, pages));
}

function makeSimpleSteps(types: string[]): AtomicStep[] {
  return types.map((type) => ({ type } as AtomicStep));
}

function makeGroup(pageCount: number, activePageCount: number, cards: Flashcard[]): FlashcardGroup {
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

function flush(ms = 0): Promise<void> {
  return new Promise((resolve) => {
    setTimeout(resolve, ms);
  });
}

interface EngineHarness {
  engine: SessionEngine;
  reviews: [string, string, number][];
  // Ponowne oceny tej samej karty w tej samej próbie (replaceFromBase obecne).
  reReviews: [string, string, number][];
  stopAudio: jest.Mock;
  skip: StudySkipState;
}

function makeEngine(group: FlashcardGroup, steps: AtomicStep[]): EngineHarness {
  const engine = new SessionEngine();
  const reviews: [string, string, number][] = [];
  const reReviews: [string, string, number][] = [];
  const skip: StudySkipState = { requested: false, armed: false, signalResolve: () => {} };
  const stopAudio = jest.fn();
  engine.configure(
    {
      runSpeechRecognition: () => Promise.resolve({ status: 'ok', text: 'hello' }),
      guardedAwait: async <T>(promise: Promise<T>) =>
        (await promise.catch(() => undefined)) as T | undefined,
      stopAudio,
      skip,
      onCardReviewed: (groupId, cardId, rating, replaceFromBase) => {
        if (replaceFromBase) {
          reReviews.push([groupId, cardId, rating]);
        } else {
          reviews.push([groupId, cardId, rating]);
        }
      },
    },
    1.0,
  );
  engine.setGroup(group);
  engine.setActiveSteps(steps);
  return { engine, reviews, reReviews, stopAudio, skip };
}

describe('SessionEngine', () => {
  it('runs the step chain after start and finishes after rating every card', async () => {
    const cards = makeCards(2, ['a']);
    const { engine, reviews } = makeEngine(
      makeGroup(1, 1, cards),
      makeSimpleSteps(['show_all_pages', 'show_ratings']),
    );

    engine.start(cards);
    await flush();
    expect(engine.getState().status).toBe('revealed');

    await engine.rate(3);
    await flush();
    expect(engine.getState().currentCardIndex).toBe(1);
    expect(engine.getState().status).toBe('revealed');

    await engine.rate(1);
    await flush();
    expect(engine.getState().status).toBe('finished');
    expect(reviews).toEqual([
      ['g1', 'c1', 3],
      ['g1', 'c2', 1],
    ]);
    // Rating 1 wrzuca kartę na listę failed.
    expect(engine.getFailedCount()).toBe(1);
  });

  it('rates a card at most once', async () => {
    const cards = makeCards(1);
    const { engine, reviews } = makeEngine(
      makeGroup(1, 1, cards),
      makeSimpleSteps(['show_all_pages', 'show_ratings']),
    );
    engine.start(cards);
    await flush();

    await Promise.all([engine.rate(3), engine.rate(4)]);
    await flush();
    expect(reviews).toEqual([['g1', 'c1', 3]]);
  });

  it('pause invalidates the running chain; resume replays the card from step 0', async () => {
    const cards = [makeCard('c1', ['a'])];
    const { engine, stopAudio } = makeEngine(makeGroup(1, 1, cards), [
      { type: 'show_all_pages' },
      { type: 'wait', ms: 40 },
      { type: 'show_ratings' },
    ]);

    engine.start(cards);
    await flush();
    // Jesteśmy w kroku wait (status idle, brak ratingów).
    expect(engine.getState().status).toBe('idle');

    engine.pause();
    expect(stopAudio).toHaveBeenCalled();

    // Stary łańcuch budzi się po wait i musi umrzeć bez efektów (epoka/abort).
    await flush(60);
    expect(engine.getState().status).toBe('idle');
    expect(buildSessionViewModel(engine.getState()).showRatingButtons).toBe(false);

    engine.resume();
    await flush(60);
    expect(engine.getState().status).toBe('revealed');
  });

  it('does not pause while waiting for a manual rating', async () => {
    const cards = makeCards(1);
    const { engine } = makeEngine(
      makeGroup(1, 1, cards),
      makeSimpleSteps(['show_all_pages', 'show_ratings']),
    );
    engine.start(cards);
    await flush();
    expect(engine.getState().status).toBe('revealed');

    engine.pause();
    engine.resume();
    await flush();
    // Pauza była no-opem — ratingi dalej widoczne, karta nie została powtórzona.
    expect(engine.getState().status).toBe('revealed');
  });

  it('end finishes the session immediately and stops audio', async () => {
    const cards = [makeCard('c1', ['a'])];
    const { engine, stopAudio } = makeEngine(makeGroup(1, 1, cards), [
      { type: 'show_all_pages' },
      { type: 'wait', ms: 1000 },
      { type: 'show_ratings' },
    ]);
    engine.start(cards);
    await flush();

    engine.end();
    expect(engine.getState().status).toBe('finished');
    expect(stopAudio).toHaveBeenCalled();
  });

  it('speaks all active pages via injected TTS', async () => {
    const cards = [makeCard('c1', ['front', 'middle', 'hidden'])];
    const { engine } = makeEngine(makeGroup(3, 2, cards), [
      { type: 'speak_all_pages' },
      { type: 'show_all_pages' },
      { type: 'show_ratings' },
    ]);
    engine.start(cards);
    await flush();

    expect(engine.getState().status).toBe('revealed');
    expect(ttsService.speak).toHaveBeenCalledTimes(2);
    expect(ttsService.speak).toHaveBeenNthCalledWith(1, { text: 'front', lang: 'en-US', rate: 1.0 });
    expect(ttsService.speak).toHaveBeenNthCalledWith(2, { text: 'middle', lang: 'en-US', rate: 1.0 });
  });

  it('restartFailed replays only failed cards', async () => {
    const cards = [makeCard('c1', ['a']), makeCard('c2', ['b'])];
    const { engine, reviews } = makeEngine(
      makeGroup(1, 1, cards),
      makeSimpleSteps(['show_all_pages', 'show_ratings']),
    );
    engine.start(cards);
    await flush();
    await engine.rate(1); // c1 failed
    await flush();
    await engine.rate(3);
    await flush();
    expect(engine.getState().status).toBe('finished');

    engine.restartFailed();
    await flush();
    expect(engine.getDueCards().map((card) => card.id)).toEqual(['c1']);
    expect(engine.getState().status).toBe('revealed');
    await engine.rate(3);
    await flush();
    expect(engine.getState().status).toBe('finished');
    // Nowa próba sesji pozwala ocenić tę samą kartę ponownie.
    expect(reviews).toEqual([
      ['g1', 'c1', 1],
      ['g1', 'c2', 3],
      ['g1', 'c1', 3],
    ]);
  });

  it('ignores dispatch after unmount', () => {
    const { engine } = makeEngine(makeGroup(1, 1, []), []);
    engine.markUnmounted();
    engine.dispatch({ type: 'SHOW_RATINGS' });
    expect(engine.getState().status).toBe('idle');
  });

  it('finishes immediately when started with an empty card list', async () => {
    const { engine } = makeEngine(
      makeGroup(1, 1, []),
      makeSimpleSteps(['show_all_pages', 'show_ratings']),
    );
    engine.start([]);
    await flush();
    expect(engine.getState().status).toBe('finished');
  });

  it('skipToNextCard interrupts active chain and advances', async () => {
    const cards = [makeCard('c1', ['a']), makeCard('c2', ['b'])];
    const { engine, reviews } = makeEngine(makeGroup(1, 1, cards), [
      { type: 'speak_page', pageIndex: 0 },
      ...makeSimpleSteps(['show_all_pages', 'show_ratings']),
    ]);
    engine.start(cards);
    await flush();

    // TTS jest w toku (status speaking) — skip do następnej karty
    engine.skipToNextCard();
    await flush(50);

    // Karta c1 nie została oceniona (pominięta), c2 czeka na rating
    expect(engine.getState().currentCardIndex).toBe(1);
    expect(reviews).toEqual([]);
    // Upewniamy się, że po przeskoku, krok speak_page dla karty c2 faktycznie wystartował
    expect(ttsService.speak).toHaveBeenCalledWith(
      expect.objectContaining({ text: 'b' })
    );
  });

  it('goToPreviousCard on the first card replays it from step 0 without re-rating', async () => {
    const cards = makeCards(1);
    const { engine, reviews } = makeEngine(makeGroup(1, 1, cards), [
      { type: 'speak_page', pageIndex: 0 },
      ...makeSimpleSteps(['show_all_pages', 'show_ratings']),
    ]);
    engine.start(cards);
    await flush();
    expect(engine.getState().status).toBe('revealed');

    // Brak poprzedniej karty (index 0) — "poprzednia" restartuje bieżącą od kroku 0
    engine.goToPreviousCard();
    await flush();

    // Karta replayowana — silnik przechodzi przez speak_page → show_all_pages → show_ratings
    expect(engine.getState().status).toBe('revealed');
    expect(engine.getState().currentCardIndex).toBe(0);
    // Nadal zero wpisów SRS — restart nie wywołał oceny
    expect(reviews).toEqual([]);
  });

  it('goToPreviousCard right after a card starts goes back to the previous card', async () => {
    const cards = [makeCard('c1', ['a']), makeCard('c2', ['b'])];
    const { engine, reviews } = makeEngine(
      makeGroup(1, 1, cards),
      makeSimpleSteps(['show_all_pages', 'show_ratings']),
    );
    engine.start(cards);
    await flush();
    await engine.rate(3);
    await flush();
    // Jesteśmy na c2, tuż po jej starcie (w oknie "cofnij do poprzedniej")
    expect(engine.getState().currentCardIndex).toBe(1);

    engine.goToPreviousCard();
    await flush();

    // Cofnięci do c1, karta odtwarza się od kroku 0; samo cofnięcie nie ocenia
    expect(engine.getState().currentCardIndex).toBe(0);
    expect(engine.getState().status).toBe('revealed');
    expect(reviews).toEqual([['g1', 'c1', 3]]);
  });

  it('goToPreviousCard after the window replays the current card instead of going back', async () => {
    const cards = [makeCard('c1', ['a']), makeCard('c2', ['b'])];
    const { engine } = makeEngine(
      makeGroup(1, 1, cards),
      makeSimpleSteps(['show_all_pages', 'show_ratings']),
    );
    engine.start(cards);
    await flush();
    await engine.rate(3);
    await flush();
    expect(engine.getState().currentCardIndex).toBe(1);

    // Po upływie okna (jak w odtwarzaczu muzyki) "poprzednia" = restart bieżącej
    const realNow = Date.now();
    const nowSpy = jest.spyOn(Date, 'now').mockReturnValue(realNow + 10_000);
    engine.goToPreviousCard();
    nowSpy.mockRestore();
    await flush();

    expect(engine.getState().currentCardIndex).toBe(1);
    expect(engine.getState().status).toBe('revealed');
  });

  it('re-rating a card after going back replaces the rating (newest wins)', async () => {
    const cards = [makeCard('c1', ['a']), makeCard('c2', ['b'])];
    const { engine, reviews, reReviews } = makeEngine(
      makeGroup(1, 1, cards),
      makeSimpleSteps(['show_all_pages', 'show_ratings']),
    );
    engine.start(cards);
    await flush();
    await engine.rate(1); // c1 oceniona jako "again" → failed
    await flush();
    expect(engine.getFailedCount()).toBe(1);

    // Cofnięcie do c1 tuż po starcie c2 i ponowna ocena na 3
    engine.goToPreviousCard();
    await flush();
    expect(engine.getState().currentCardIndex).toBe(0);
    await engine.rate(3);
    await flush();

    // Pierwsza ocena poszła normalną ścieżką, druga jako nadpisanie od stanu bazowego
    expect(reviews).toEqual([['g1', 'c1', 1]]);
    expect(reReviews).toEqual([['g1', 'c1', 3]]);
    // Najnowsza ocena liczy się też dla listy failed
    expect(engine.getFailedCount()).toBe(0);
    // Sesja wróciła na c2
    expect(engine.getState().currentCardIndex).toBe(1);
  });

  it('replayCurrentCard restarts the current card without touching the index', async () => {
    const cards = [makeCard('c1', ['a']), makeCard('c2', ['b'])];
    const { engine, reviews } = makeEngine(
      makeGroup(1, 1, cards),
      makeSimpleSteps(['show_all_pages', 'show_ratings']),
    );
    engine.start(cards);
    await flush();
    await engine.rate(3);
    await flush();
    expect(engine.getState().currentCardIndex).toBe(1);

    engine.replayCurrentCard();
    await flush();

    expect(engine.getState().currentCardIndex).toBe(1);
    expect(engine.getState().status).toBe('revealed');
    expect(reviews).toEqual([['g1', 'c1', 3]]);
  });

  it('backgroundMode substitutes wait_for_tap with auto-reveal', async () => {
    const cards = [makeCard('c1', ['front'])];
    const { engine } = makeEngine(makeGroup(1, 1, cards), [
      { type: 'speak_page', pageIndex: 0 },
      { type: 'wait_for_tap' },
      { type: 'show_all_pages' },
    ]);
    engine.setBackgroundMode(true);
    engine.start(cards);
    // W tle speak_page → wait_for_tap (substytucja: pauza 1.5s + auto-reveal) → show_all_pages
    await flush(1600);

    // Wszystkie strony odsłonięte po substytucji
    expect(engine.getState().revealedPages).toEqual([0]);
  });

  it('backgroundMode: wait_for_tap_to_reveal auto-reveals and rates', async () => {
    const cards = [makeCard('c1', ['front', 'back'])];
    const { engine, reviews } = makeEngine(makeGroup(2, 2, cards), [
      { type: 'show_page', pageIndex: 0 },
      { type: 'speak_page', pageIndex: 0 },
      { type: 'wait_for_tap_to_reveal' },
      { type: 'auto_rate_fixed', rating: 3 },
      { type: 'next_card' },
    ]);
    engine.setBackgroundMode(true);
    engine.start(cards);
    await flush(1600);

    // W tle: karta auto-odsłonięta, oceniona, sesja zakończona
    expect(engine.getState().status).toBe('finished');
    expect(reviews).toEqual([['g1', 'c1', 3]]);
  });

  it('does not rate a card twice on restartFailed after backgroundMode replay', async () => {
    const cards = makeCards(1);
    const { engine, reviews } = makeEngine(
      makeGroup(1, 1, cards),
      makeSimpleSteps(['show_all_pages', 'show_ratings']),
    );
    engine.setBackgroundMode(true);
    engine.start(cards);
    await flush();
    // W tle show_ratings → auto-pauza, nie oceniamy
    expect(reviews).toEqual([]);
  });

  it('backgroundMode pause scales with text length', async () => {
    const longText = 'a'.repeat(50); // 50 chars → waitMs = max(1500, 50*60*2) = 6000
    const cards = [makeCard('c1', [longText])];
    const { engine } = makeEngine(makeGroup(1, 1, cards), [
      { type: 'speak_page', pageIndex: 0 },
      { type: 'wait_for_tap' },
    ]);
    engine.setBackgroundMode(true);
    engine.start(cards);
    // Po 3s: speak_page skończyło się, ale wait_for_tap wciąż czeka (6000ms)
    await flush(3000);
    expect(engine.getState().status).toBe('idle');
    expect(engine.getState().revealedPages).toEqual([]);
  });
});

describe('buildSessionViewModel', () => {
  it('maps reducer state to UI fields', () => {
    const vm = buildSessionViewModel({
      ...INITIAL_STUDY_SESSION_STATE,
      status: 'speaking',
      audioPageIndex: 1,
      revealedPages: [0],
      interactionGate: { kind: 'tap_to_reveal', revealMode: 'remaining', continueStepIndex: 2 },
      lastAnswerResult: {
        status: 'correct',
        text: 'hi',
        percent: 90,
        threshold: 70,
        pageIndex: 1,
        suggestedRating: null,
      },
    });
    expect(vm.isTtsPlaying).toBe(true);
    expect(vm.isSttListening).toBe(false);
    expect(vm.showRatingButtons).toBe(false);
    expect(vm.isSessionFinished).toBe(false);
    expect(vm.waitingForTap).toBe(true);
    expect(vm.audioPageIndex).toBe(1);
    expect(vm.revealedPages).toEqual([0]);
    expect(vm.sttResultText).toBe('hi');
    expect(vm.sttMatchPercent).toBe(90);
    expect(vm.sttSuccessThreshold).toBe(70);
    expect(vm.answerStatus).toBe('correct');
  });

  it('defaults sttMatchPercent to 0 when there is no answer', () => {
    const vm = buildSessionViewModel(INITIAL_STUDY_SESSION_STATE);
    expect(vm.sttMatchPercent).toBe(0);
    expect(vm.answerStatus).toBe('none');
    expect(vm.waitingForTap).toBe(false);
  });
});
