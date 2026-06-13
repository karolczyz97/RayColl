import type { MutableRefObject } from 'react';
import type { Flashcard, FlashcardGroup, ModeStep } from '@/types/models';
import { mapMatchToRating, matchSpeech } from '@/srs/srsEngine';
import { playErrorSound, playSuccessSound } from '@/services/audioFeedback';
import { playErrorHaptic, playSuccessHaptic } from '@/services/hapticFeedback';
import type { StudySkipState, SpeechRecognitionOutcome } from '@/features/study/hooks/useStudyAudio';
import type { SessionAction } from './sessionTypes';
import { getActivePageIndexes, sleep } from './sessionUtils';

const CORRECTION_INTERRUPT_PAUSE_MS = 2000;

interface StepExecutorContext {
  abortRef: MutableRefObject<boolean>;
  // Bumped on pause/resume/restart. A step chain captures the epoch when it starts;
  // a mismatch means the chain was superseded and must die without side effects.
  runEpochRef: MutableRefObject<number>;
  // Wynik ostatniego kroku listen_and_check na bieżącej karcie (null = brak).
  lastCheckPassedRef: MutableRefObject<boolean | null>;
  activeStepsRef: MutableRefObject<ModeStep[]>;
  groupRef: MutableRefObject<FlashcardGroup | null>;
  skipRef: MutableRefObject<StudySkipState>;
  lastTtsDurationRef: MutableRefObject<number>;
  lastPartialTextRef: MutableRefObject<string>;
  dispatchIfMounted: (action: SessionAction) => void;
  guardedAwait: <T>(promise: Promise<T>) => Promise<T | undefined>;
  playTts: (text: string, lang: string) => Promise<void>;
  runSpeechRecognition: (lang: string, timeoutMs: number) => Promise<SpeechRecognitionOutcome>;
  processCardReview: (card: Flashcard, rating: number) => void;
  advanceToNextCard: () => Promise<void>;
  markCardFailed: (card: Flashcard) => void;
  executeNext: (card: Flashcard, stepIndex: number) => Promise<void>;
}

type StepRunContext = StepExecutorContext & {
  isStale: () => boolean;
};

async function continueIfActive(
  card: Flashcard,
  stepIndex: number,
  context: StepRunContext,
) {
  if (!context.isStale()) {
    await context.executeNext(card, stepIndex);
  }
}

async function skipableSleep(context: StepRunContext, ms: number) {
  context.skipRef.current.requested = false;
  context.skipRef.current.armed = true;
  await context.guardedAwait(sleep(ms));
  context.skipRef.current.armed = false;
}

async function executeSpeakPageStep(
  card: Flashcard,
  stepIndex: number,
  step: Extract<ModeStep, { type: 'speak_page' }>,
  group: FlashcardGroup,
  context: StepRunContext,
) {
  context.skipRef.current.armed = true;
  context.dispatchIfMounted({ type: 'START_SPEAKING', stepIndex, pageIndex: step.pageIndex });
  const pageText = card.pages[step.pageIndex] || '';
  await context.guardedAwait(
    context.playTts(pageText, group.pageLanguages[step.pageIndex] || 'en-US'),
  );
  if (context.isStale()) {
    context.dispatchIfMounted({ type: 'END_SPEAKING' });
    return;
  }
  if (!context.skipRef.current.requested && step.pauseMultiplier > 0) {
    // Pauza = N × czas odsłuchu strony; zmierzony czas TTS, a gdy go brak
    // (błąd TTS), ta sama estymata co w kroku dynamic_pause.
    const listenDuration =
      context.lastTtsDurationRef.current > 0
        ? context.lastTtsDurationRef.current
        : pageText.length * 60;
    await context.guardedAwait(sleep(listenDuration * step.pauseMultiplier));
  }
  context.dispatchIfMounted({ type: 'END_SPEAKING' });

  await continueIfActive(card, stepIndex + 1, context);
}

async function executeTimedWaitStep(
  card: Flashcard,
  stepIndex: number,
  waitMs: number,
  context: StepRunContext,
) {
  context.skipRef.current.armed = true;
  context.dispatchIfMounted({ type: 'SET_CURRENT_STEP', stepIndex });
  await context.guardedAwait(sleep(waitMs));

  await continueIfActive(card, stepIndex + 1, context);
}

async function executeListenAndBranchStep(
  card: Flashcard,
  stepIndex: number,
  step: Extract<ModeStep, { type: 'listen_and_branch' }>,
  group: FlashcardGroup,
  context: StepRunContext,
) {
  context.skipRef.current.armed = true;
  context.dispatchIfMounted({ type: 'START_LISTENING', stepIndex, pageIndex: step.pageIndex });
  const lang = group.pageLanguages[step.pageIndex] || 'en-US';
  const softTimeout = Math.max(5000, context.lastTtsDurationRef.current * 3);
  const recognitionPromise = context.runSpeechRecognition(lang, softTimeout + 10000);
  const result = await context.guardedAwait(recognitionPromise);
  context.skipRef.current.armed = false;

  if (context.isStale()) return;

  if (context.skipRef.current.requested) {
    const late = await Promise.race([
      recognitionPromise.catch(() => undefined),
      sleep(700).then(() => undefined),
    ]);
    const harvested = late?.status === 'ok' ? late.text : context.lastPartialTextRef.current;

    if (context.isStale()) return;

    if (!harvested) {
      context.dispatchIfMounted({ type: 'END_LISTENING', text: '', matchPercent: 0 });
      await continueIfActive(card, stepIndex + 1, context);
      return;
    }

    const original = card.pages[step.pageIndex] || '';
    const percent = matchSpeech(harvested, original);
    context.dispatchIfMounted({ type: 'END_LISTENING', text: harvested, matchPercent: percent });
    await evaluateRecognition(card, stepIndex, step, group, percent, context, true);
    return;
  }

  if (!result || result.status === 'error') {
    context.dispatchIfMounted({
      type: 'REVEAL_PAGES',
      revealedPages: getActivePageIndexes(group),
    });
    context.dispatchIfMounted({ type: 'SHOW_RATINGS' });
    return;
  }

  const recognized = result.text;
  const original = card.pages[step.pageIndex] || '';
  const percent = matchSpeech(recognized, original);

  context.dispatchIfMounted({
    type: 'END_LISTENING',
    text: recognized || '',
    matchPercent: percent,
  });

  await evaluateRecognition(card, stepIndex, step, group, percent, context, false);
}

async function executeListenAndCheckStep(
  card: Flashcard,
  stepIndex: number,
  step: Extract<ModeStep, { type: 'listen_and_check' }>,
  group: FlashcardGroup,
  context: StepRunContext,
) {
  context.skipRef.current.armed = true;
  context.dispatchIfMounted({ type: 'START_LISTENING', stepIndex, pageIndex: step.pageIndex });
  const lang = group.pageLanguages[step.pageIndex] || 'en-US';
  const softTimeout = Math.max(5000, context.lastTtsDurationRef.current * 3);
  const recognitionPromise = context.runSpeechRecognition(lang, softTimeout + 10000);
  const result = await context.guardedAwait(recognitionPromise);
  context.skipRef.current.armed = false;

  if (context.isStale()) return;

  let recognized = '';
  if (context.skipRef.current.requested) {
    // Tap = fast-forward: zbierz spóźniony/częściowy wynik zamiast go odrzucać.
    const late = await Promise.race([
      recognitionPromise.catch(() => undefined),
      sleep(700).then(() => undefined),
    ]);
    recognized = late?.status === 'ok' ? late.text : context.lastPartialTextRef.current;
    if (context.isStale()) return;
  } else if (result?.status === 'ok') {
    recognized = result.text;
  }

  const original = card.pages[step.pageIndex] || '';
  const percent = recognized ? matchSpeech(recognized, original) : 0;
  const passed = percent >= step.successThreshold;
  context.lastCheckPassedRef.current = passed;
  context.dispatchIfMounted({ type: 'END_LISTENING', text: recognized, matchPercent: percent });

  if (passed) {
    playSuccessSound();
    playSuccessHaptic();
  } else {
    playErrorSound();
    playErrorHaptic();
  }

  // Krótka pauza, żeby wynik był widoczny (jak w listen_and_branch).
  await skipableSleep(context, 1200);
  if (context.isStale()) return;

  await continueIfActive(card, stepIndex + 1, context);
}

async function evaluateRecognition(
  card: Flashcard,
  stepIndex: number,
  step: Extract<ModeStep, { type: 'listen_and_branch' }>,
  group: FlashcardGroup,
  percent: number,
  context: StepRunContext,
  fromSkip: boolean,
) {
  if (!fromSkip) {
    await skipableSleep(context, 1200);
    if (context.isStale()) return;
  }

  if (percent >= step.successThreshold) {
    playSuccessSound();
    playSuccessHaptic();
    
    context.dispatchIfMounted({
      type: 'REVEAL_PAGES',
      revealedPages: getActivePageIndexes(group),
    });

    await skipableSleep(context, 1200);
    if (context.isStale()) return;
    const autoRating = mapMatchToRating(percent);
    context.processCardReview(card, autoRating);
    await context.advanceToNextCard();
    return;
  }

  playErrorSound();
  playErrorHaptic();
  context.markCardFailed(card);

  context.dispatchIfMounted({
    type: 'REVEAL_PAGES',
    revealedPages: getActivePageIndexes(group),
  });

  context.skipRef.current.requested = false;
  context.skipRef.current.armed = true;
  if (step.incorrectTtsPageIndex !== undefined) {
    context.dispatchIfMounted({
      type: 'START_SPEAKING',
      stepIndex,
      pageIndex: step.incorrectTtsPageIndex,
    });
    const correctionStart = Date.now();
    await context.guardedAwait(
      context.playTts(
        card.pages[step.incorrectTtsPageIndex] || '',
        group.pageLanguages[step.incorrectTtsPageIndex] || 'en-US',
      ),
    );
    const correctionDuration = Date.now() - correctionStart;
    context.dispatchIfMounted({ type: 'END_SPEAKING' });

    const wasTtsCut = context.skipRef.current.requested;
    context.skipRef.current.requested = false;
    context.skipRef.current.armed = true;

    const pauseMs = wasTtsCut ? CORRECTION_INTERRUPT_PAUSE_MS : correctionDuration * 2;
    await context.guardedAwait(sleep(pauseMs));
  } else {
    await skipableSleep(context, 2000);
  }
  context.skipRef.current.armed = false;

  if (!context.isStale()) {
    context.processCardReview(card, 1);
    await context.advanceToNextCard();
  }
}

export async function executeStudyStep(
  card: Flashcard,
  stepIndex: number,
  executorContext: StepExecutorContext,
) {
  const epoch = executorContext.runEpochRef.current;
  const context: StepRunContext = {
    ...executorContext,
    isStale: () =>
      executorContext.abortRef.current || executorContext.runEpochRef.current !== epoch,
  };
  const currentGroup = context.groupRef.current;
  const currentSteps = context.activeStepsRef.current;

  context.skipRef.current.requested = false;
  context.skipRef.current.armed = false;

  // Nowa karta zaczyna bez wyniku sprawdzenia wymowy.
  if (stepIndex === 0) {
    context.lastCheckPassedRef.current = null;
  }

  if (context.abortRef.current || !currentGroup || stepIndex >= currentSteps.length) {
    context.dispatchIfMounted({ type: 'SHOW_RATINGS' });
    return;
  }

  const step = currentSteps[stepIndex];

  // Krok warunkowy wykonuje się tylko przy pasującym wyniku ostatniego
  // "sprawdź wymowę"; bez sprawdzenia (null) kroki warunkowe są pomijane.
  if (step.condition) {
    const passed = context.lastCheckPassedRef.current;
    const conditionMet = step.condition === 'correct' ? passed === true : passed === false;
    if (!conditionMet) {
      await continueIfActive(card, stepIndex + 1, context);
      return;
    }
  }

  switch (step.type) {
    case 'show_page':
      context.dispatchIfMounted({ type: 'REVEAL_PAGE', stepIndex, pageIndex: step.pageIndex });
      await continueIfActive(card, stepIndex + 1, context);
      break;

    case 'reveal_on_tap':
      context.dispatchIfMounted({ type: 'SET_CURRENT_STEP', stepIndex, waitingForTap: true });
      break;

    case 'rate':
      context.dispatchIfMounted({ type: 'SHOW_RATINGS' });
      break;

    case 'next_card':
      // Tryb osłuchowy: karta kończy się bez oceny — SRS zostaje nietknięty.
      await context.advanceToNextCard();
      break;

    case 'speak_page':
      await executeSpeakPageStep(card, stepIndex, step, currentGroup, context);
      break;

    case 'dynamic_pause': {
      const text = card.pages[step.nextPageIndex] || '';
      await executeTimedWaitStep(card, stepIndex, text.length * 60 * step.pauseMultiplier, context);
      break;
    }

    case 'wait':
      await executeTimedWaitStep(card, stepIndex, step.ms, context);
      break;

    case 'listen_and_branch':
      await executeListenAndBranchStep(card, stepIndex, step, currentGroup, context);
      break;

    case 'listen_and_check':
      await executeListenAndCheckStep(card, stepIndex, step, currentGroup, context);
      break;

    default: {
      const _exhaustive: never = step;
      throw new Error(`Unknown step type: ${(_exhaustive as ModeStep).type}`);
    }
  }
}
