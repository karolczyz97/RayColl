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

function makeCard(id: string, pages: string[]): Flashcard {
  return { id, pages, srsState: createNewSrsState(), contentUpdatedAt: 0, srsUpdatedAt: 0 };
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
  playTts: jest.Mock;
  stopAudio: jest.Mock;
  skip: StudySkipState;
}

function makeEngine(group: FlashcardGroup, steps: AtomicStep[]): EngineHarness {
  const engine = new SessionEngine();
  const reviews: [string, string, number][] = [];
  const skip: StudySkipState = { requested: false, armed: false, signalResolve: () => {} };
  const playTts = jest.fn(() => Promise.resolve());
  const stopAudio = jest.fn();
  engine.configure({
    playTts: playTts as unknown as (text: string, lang: string) => Promise<void>,
    runSpeechRecognition: () => Promise.resolve({ status: 'ok', text: 'hello' }),
    guardedAwait: async <T>(promise: Promise<T>) =>
      (await promise.catch(() => undefined)) as T | undefined,
    stopAudio,
    skip,
    getLastTtsDuration: () => 0,
    onCardReviewed: (groupId, cardId, rating) => {
      reviews.push([groupId, cardId, rating]);
    },
  });
  engine.setGroup(group);
  engine.setActiveSteps(steps);
  return { engine, reviews, playTts, stopAudio, skip };
}

describe('SessionEngine', () => {
  it('runs the step chain after start and finishes after rating every card', async () => {
    const cards = [makeCard('c1', ['a']), makeCard('c2', ['b'])];
    const { engine, reviews } = makeEngine(makeGroup(1, 1, cards), [
      { type: 'show_all_pages' },
      { type: 'show_ratings' },
    ]);

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
    const cards = [makeCard('c1', ['a'])];
    const { engine, reviews } = makeEngine(makeGroup(1, 1, cards), [
      { type: 'show_all_pages' },
      { type: 'show_ratings' },
    ]);
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
    const cards = [makeCard('c1', ['a'])];
    const { engine } = makeEngine(makeGroup(1, 1, cards), [
      { type: 'show_all_pages' },
      { type: 'show_ratings' },
    ]);
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
    const { engine, playTts } = makeEngine(makeGroup(3, 2, cards), [
      { type: 'speak_all_pages' },
      { type: 'show_all_pages' },
      { type: 'show_ratings' },
    ]);
    engine.start(cards);
    await flush();

    expect(engine.getState().status).toBe('revealed');
    expect(playTts).toHaveBeenCalledTimes(2);
    expect(playTts).toHaveBeenNthCalledWith(1, 'front', 'en-US');
    expect(playTts).toHaveBeenNthCalledWith(2, 'middle', 'en-US');
  });

  it('restartFailed replays only failed cards', async () => {
    const cards = [makeCard('c1', ['a']), makeCard('c2', ['b'])];
    const { engine, reviews } = makeEngine(makeGroup(1, 1, cards), [
      { type: 'show_all_pages' },
      { type: 'show_ratings' },
    ]);
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
    const { engine } = makeEngine(makeGroup(1, 1, []), [
      { type: 'show_all_pages' },
      { type: 'show_ratings' },
    ]);
    engine.start([]);
    await flush();
    expect(engine.getState().status).toBe('finished');
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
