import type { MutableRefObject } from 'react';
import type { Flashcard, FlashcardGroup, AtomicStep } from '@/types/models';
import { mapMatchToRating, matchSpeech } from '@/srs/srsEngine';
import { playErrorSound, playSuccessSound } from '@/services/audioFeedback';
import { playErrorHaptic, playSuccessHaptic } from '@/services/hapticFeedback';
import type { StudySkipState, SpeechRecognitionOutcome } from '@/features/study/hooks/useStudyAudio';
import type {
  AnswerStatus,
  CardReviewState,
  LastAnswerResult,
  SessionAction,
  StudySessionState,
} from './sessionTypes';
import { NO_ANSWER_RESULT } from './sessionTypes';
import {
  areAllActivePagesRevealed,
  getActivePageIndexes,
  sleep,
  uniquePageIndexes,
} from './sessionUtils';

/**
 * Runner = głupi interpreter listy primitive steps. Nie zna trybów. Każdy krok
 * robi DOKŁADNIE jedną rzecz i kończy się jednym z:
 *   - continue stepIndex + 1
 *   - stop na interaction gate (wait_for_tap_to_reveal)
 *   - stop na show ratings (show_ratings)
 *   - next card (next_card)
 * Koniec listy / brak kroku => STOP. Runner nie zgaduje intencji.
 */
interface StepExecutorContext {
  abortRef: MutableRefObject<boolean>;
  // Bumped on pause/resume/restart. A step chain captures the epoch when it starts;
  // a mismatch means the chain was superseded and must die without side effects.
  runEpochRef: MutableRefObject<number>;
  // Wynik ostatniego "Sprawdź wymowę" na bieżącej karcie — synchroniczne źródło
  // prawdy dla warunków (correct/wrong). Mirror dla UI leci przez reducer.
  lastAnswerResultRef: MutableRefObject<LastAnswerResult>;
  // Stan oceny karty: gwarantuje jedną ocenę na kartę (auto albo manual).
  currentCardReviewStateRef: MutableRefObject<CardReviewState>;
  // Synchroniczny mirror revealedPages — reducer state jest stale w obrębie
  // jednego łańcucha kroków (brak re-renderu), a auto-complete tap-gate i show_*
  // muszą widzieć świeży zestaw odsłoniętych stron.
  revealedPagesRef: MutableRefObject<number[]>;
  activeStepsRef: MutableRefObject<AtomicStep[]>;
  groupRef: MutableRefObject<FlashcardGroup | null>;
  stateRef: MutableRefObject<StudySessionState>;
  skipRef: MutableRefObject<StudySkipState>;
  lastTtsDurationRef: MutableRefObject<number>;
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

function buildAnswerResult(
  status: AnswerStatus,
  text: string,
  percent: number | null,
  threshold: number | null,
  pageIndex: number,
): LastAnswerResult {
  // STT nie ocenia: suggestedRating zostaje null. Ocenę z wyniku liczy dopiero
  // krok auto_rate_from_answer (mapMatchToRating), nie ten krok.
  return { status, text, percent, threshold, pageIndex, suggestedRating: null };
}

async function executeSpeakPageStep(
  card: Flashcard,
  stepIndex: number,
  step: Extract<AtomicStep, { type: 'speak_page' }>,
  group: FlashcardGroup,
  context: StepRunContext,
) {
  context.skipRef.current.armed = true;
  context.dispatchIfMounted({ type: 'START_SPEAKING', stepIndex, pageIndex: step.pageIndex });
  const pageText = card.pages[step.pageIndex] || '';
  // TTS tylko mówi — żadnej pauzy. Pauza po TTS to osobny krok (dynamic_pause/wait).
  await context.guardedAwait(
    context.playTts(pageText, group.pageLanguages[step.pageIndex] || 'en-US'),
  );
  context.skipRef.current.armed = false;
  context.dispatchIfMounted({ type: 'END_SPEAKING' });
  if (context.isStale()) return;
  // Tap podczas TTS tylko przerwał odtwarzanie (guardedAwait wrócił wcześniej);
  // nie odsłania strony, nie pokazuje ratingów — po prostu idziemy dalej.
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
  context.skipRef.current.armed = false;
  if (context.isStale()) return;
  await continueIfActive(card, stepIndex + 1, context);
}

async function executeListenAndCheckStep(
  card: Flashcard,
  stepIndex: number,
  step: Extract<AtomicStep, { type: 'listen_and_check' }>,
  group: FlashcardGroup,
  context: StepRunContext,
) {
  context.skipRef.current.armed = true;
  context.dispatchIfMounted({ type: 'START_LISTENING', stepIndex, pageIndex: step.pageIndex });
  const lang = group.pageLanguages[step.pageIndex] || 'en-US';
  const softTimeout = Math.max(5000, context.lastTtsDurationRef.current * 3);
  const result = await context.guardedAwait(
    context.runSpeechRecognition(lang, softTimeout + 10000),
  );
  context.skipRef.current.armed = false;

  if (context.isStale()) return;

  const threshold = step.successThreshold;
  const pageIndex = step.pageIndex;
  let answer: LastAnswerResult;

  if (context.skipRef.current.requested) {
    // Tap podczas STT = skip. NIE zbieramy late/partial result, NIE liczymy
    // matchSpeech, NIE ustawiamy incorrect. To wyłącznie pominięcie kroku, więc
    // status `skipped` nie odpali kroków warunkowych correct/wrong.
    answer = buildAnswerResult('skipped', '', null, threshold, pageIndex);
  } else if (!result || result.status === 'error') {
    // Techniczny błąd STT (mic/permission/network). To nie jest "źle".
    answer = buildAnswerResult('error', '', null, threshold, pageIndex);
  } else {
    const recognized = result.text;
    const original = card.pages[pageIndex] || '';
    const percent = matchSpeech(recognized, original);
    const status: AnswerStatus = percent >= threshold ? 'correct' : 'incorrect';
    answer = buildAnswerResult(status, recognized, percent, threshold, pageIndex);
  }

  // Ref najpierw (kolejne kroki czytają warunek synchronicznie), potem mirror UI.
  context.lastAnswerResultRef.current = answer;
  context.dispatchIfMounted({ type: 'SET_LAST_ANSWER_RESULT', result: answer });

  // STT samo: nie robi feedbacku, pauzy, odsłaniania, oceny, mark failed ani next card.
  await continueIfActive(card, stepIndex + 1, context);
}

function executeAutoRateFromAnswer(card: Flashcard, context: StepRunContext) {
  const result = context.lastAnswerResultRef.current;
  // No-op dla skipped/error/none — nawet przy condition "zawsze".
  if (result.status !== 'correct' && result.status !== 'incorrect') return;
  if (result.percent === null || result.threshold === null) return;
  if (context.currentCardReviewStateRef.current !== 'none') return;
  const rating = mapMatchToRating(result.percent, result.threshold);
  context.processCardReview(card, rating);
  context.currentCardReviewStateRef.current = 'autoRated';
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

  // Nowa karta (wejście od kroku 0): zeruj wynik odpowiedzi, stan oceny i
  // synchroniczny mirror odsłoniętych stron. Re-entry po gate (stepIndex != 0)
  // nie resetuje — pendingStepIndexToRun synchronizuje ref ze świeżego state.
  if (stepIndex === 0) {
    context.lastAnswerResultRef.current = NO_ANSWER_RESULT;
    context.currentCardReviewStateRef.current = 'none';
    context.revealedPagesRef.current = [];
  }

  // Koniec listy / brak grupy / abort => STOP. Bez fallbacku SHOW_RATINGS —
  // runner nie zgaduje intencji. Niepełna konfiguracja to błąd konfiguracji.
  if (context.abortRef.current || !currentGroup || stepIndex >= currentSteps.length) {
    return;
  }

  const step = currentSteps[stepIndex];

  // Warunek kroku: 'correct' -> status correct; 'wrong' -> status incorrect.
  // skipped/error/none NIGDY nie pasują (tap w STT nie odpala correct/wrong).
  if (step.condition) {
    const status = context.lastAnswerResultRef.current.status;
    const conditionMet =
      step.condition === 'correct' ? status === 'correct' : status === 'incorrect';
    if (!conditionMet) {
      await continueIfActive(card, stepIndex + 1, context);
      return;
    }
  }

  switch (step.type) {
    case 'show_page': {
      context.revealedPagesRef.current = uniquePageIndexes([
        ...context.revealedPagesRef.current,
        step.pageIndex,
      ]);
      context.dispatchIfMounted({ type: 'REVEAL_PAGE', stepIndex, pageIndex: step.pageIndex });
      await continueIfActive(card, stepIndex + 1, context);
      break;
    }

    case 'show_all_pages': {
      const all = getActivePageIndexes(currentGroup);
      context.revealedPagesRef.current = all;
      context.dispatchIfMounted({ type: 'REVEAL_PAGES', revealedPages: all });
      await continueIfActive(card, stepIndex + 1, context);
      break;
    }

    case 'wait_for_tap_to_reveal_next':
      if (areAllActivePagesRevealed(currentGroup, context.revealedPagesRef.current)) {
        await continueIfActive(card, stepIndex + 1, context);
      } else {
        context.dispatchIfMounted({
          type: 'START_TAP_REVEAL_GATE',
          revealMode: 'single',
          continueStepIndex: stepIndex + 1,
        });
      }
      break;

    case 'wait_for_tap_to_reveal':
      // Auto-complete: jeśli wszystko już odkryte, nie otwieraj gate — idź dalej.
      if (areAllActivePagesRevealed(currentGroup, context.revealedPagesRef.current)) {
        await continueIfActive(card, stepIndex + 1, context);
      } else {
        // STOP: runner czeka na tapy. Każdy tap (w gestach) odsłania jedną stronę;
        // ostatni domyka gate i ustawia pendingStepIndexToRun = stepIndex + 1.
        context.dispatchIfMounted({
          type: 'START_TAP_REVEAL_GATE',
          revealMode: 'remaining',
          continueStepIndex: stepIndex + 1,
        });
      }
      break;

    case 'show_ratings':
      // Pokaż ratingi tylko jeśli karta jeszcze nieoceniona; inaczej no-op -> dalej.
      if (context.currentCardReviewStateRef.current === 'none') {
        context.dispatchIfMounted({ type: 'SHOW_RATINGS' });
        // STOP: czekamy na manualną ocenę (handleRating).
      } else {
        await continueIfActive(card, stepIndex + 1, context);
      }
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

    case 'listen_and_check':
      await executeListenAndCheckStep(card, stepIndex, step, currentGroup, context);
      break;

    case 'feedback_success':
      // Sam feedback — nie sprawdza wyniku (to robi condition), nie branchuje.
      playSuccessSound();
      playSuccessHaptic();
      await continueIfActive(card, stepIndex + 1, context);
      break;

    case 'feedback_error':
      playErrorSound();
      playErrorHaptic();
      await continueIfActive(card, stepIndex + 1, context);
      break;

    case 'auto_rate_from_answer':
      executeAutoRateFromAnswer(card, context);
      // Nie przechodzi do następnej karty, nie odsłania, nie pokazuje ratingów.
      await continueIfActive(card, stepIndex + 1, context);
      break;

    case 'auto_rate_fixed':
      if (context.currentCardReviewStateRef.current === 'none') {
        context.processCardReview(card, step.rating);
        context.currentCardReviewStateRef.current = 'autoRated';
      }
      await continueIfActive(card, stepIndex + 1, context);
      break;

    case 'mark_failed':
      // Failed list != ocena SRS. Sam dodaje kartę do failed, nic więcej.
      context.markCardFailed(card);
      await continueIfActive(card, stepIndex + 1, context);
      break;

    case 'next_card':
      // Przechodzi do następnej karty bez własnej oceny. advanceToNextCard
      // respektuje holdingRef (czeka na puszczenie karty).
      await context.advanceToNextCard();
      break;

    default: {
      const _exhaustive: never = step;
      throw new Error(`Unknown step type: ${(_exhaustive as AtomicStep).type}`);
    }
  }
}
