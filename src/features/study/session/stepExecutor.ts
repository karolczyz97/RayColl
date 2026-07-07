import type { Flashcard, FlashcardGroup, AtomicStep } from '@/types/models';
import { mapMatchToRating, matchSpeech } from '@/srs/srsEngine';
import { playErrorSound, playSuccessSound } from '@/services/audioFeedback';
import { playErrorHaptic, playSuccessHaptic } from '@/services/hapticFeedback';
import type { SessionEngine } from './SessionEngine';
import type { AnswerStatus, LastAnswerResult } from './sessionTypes';
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
 *
 * Kroki czytają/piszą stan sesji przez akcesory SessionEngine — synchroniczne,
 * zawsze świeże (silnik dispatchuje reducer synchronicznie).
 */
interface StepRun {
  engine: SessionEngine;
  // Łańcuch łapie epokę na starcie; mismatch = łańcuch wyprzedzony (pauza,
  // restart) i musi umrzeć bez efektów ubocznych.
  isStale: () => boolean;
}

async function continueIfActive(card: Flashcard, stepIndex: number, run: StepRun) {
  if (!run.isStale()) {
    await run.engine.executeStep(card, stepIndex);
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

async function speakSinglePage(
  card: Flashcard,
  stepIndex: number,
  pageIndex: number,
  group: FlashcardGroup,
  run: StepRun,
) {
  const { engine } = run;
  const skip = engine.getSkip();
  if (skip) skip.armed = true;
  engine.dispatch({ type: 'START_SPEAKING', stepIndex, pageIndex });
  const pageText = card.pages[pageIndex] || '';
  // TTS tylko mówi — żadnej pauzy. Pauza po TTS to osobny krok (dynamic_pause/wait).
  await engine.guardedAwait(engine.playTts(pageText, group.pageLanguages[pageIndex] || 'en-US'));
  if (skip) skip.armed = false;
  engine.dispatch({ type: 'END_SPEAKING' });
}

async function executeSpeakPageStep(
  card: Flashcard,
  stepIndex: number,
  step: Extract<AtomicStep, { type: 'speak_page' }>,
  group: FlashcardGroup,
  run: StepRun,
) {
  await speakSinglePage(card, stepIndex, step.pageIndex, group, run);
  if (run.isStale()) return;
  // Tap podczas TTS tylko przerwał odtwarzanie (guardedAwait wrócił wcześniej);
  // nie odsłania strony, nie pokazuje ratingów — po prostu idziemy dalej.
  await continueIfActive(card, stepIndex + 1, run);
}

async function executeTimedWaitStep(
  card: Flashcard,
  stepIndex: number,
  waitMs: number,
  run: StepRun,
) {
  const { engine } = run;
  const skip = engine.getSkip();
  if (skip) skip.armed = true;
  engine.dispatch({ type: 'SET_CURRENT_STEP', stepIndex });
  await engine.guardedAwait(sleep(waitMs));
  if (skip) skip.armed = false;
  if (run.isStale()) return;
  await continueIfActive(card, stepIndex + 1, run);
}

async function executeListenAndCheckStep(
  card: Flashcard,
  stepIndex: number,
  step: Extract<AtomicStep, { type: 'listen_and_check' }>,
  group: FlashcardGroup,
  run: StepRun,
) {
  const { engine } = run;
  const skip = engine.getSkip();
  if (skip) skip.armed = true;
  engine.dispatch({ type: 'START_LISTENING', stepIndex, pageIndex: step.pageIndex });
  const lang = group.pageLanguages[step.pageIndex] || 'en-US';
  const softTimeout = Math.max(5000, engine.getLastTtsDuration() * 3);
  const result = await engine.guardedAwait(
    engine.runSpeechRecognition(lang, softTimeout + 10000),
  );
  if (skip) skip.armed = false;

  if (run.isStale()) return;

  const threshold = step.successThreshold;
  const pageIndex = step.pageIndex;
  let answer: LastAnswerResult;

  if (skip?.requested) {
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

  // Silnik najpierw (kolejne kroki czytają warunek synchronicznie), potem mirror UI.
  engine.setLastAnswerResult(answer);
  engine.dispatch({ type: 'SET_LAST_ANSWER_RESULT', result: answer });

  // STT samo: nie robi feedbacku, pauzy, odsłaniania, oceny, mark failed ani next card.
  await continueIfActive(card, stepIndex + 1, run);
}

function executeAutoRateFromAnswer(card: Flashcard, run: StepRun) {
  const { engine } = run;
  const result = engine.getLastAnswerResult();
  // No-op dla skipped/error/none — nawet przy condition "zawsze".
  if (result.status !== 'correct' && result.status !== 'incorrect') return;
  if (result.percent === null || result.threshold === null) return;
  if (engine.getCardReviewState() !== 'none') return;
  const rating = mapMatchToRating(result.percent, result.threshold);
  engine.processCardReview(card, rating);
  engine.setCardReviewState('autoRated');
}

export async function executeStudyStep(
  card: Flashcard,
  stepIndex: number,
  engine: SessionEngine,
) {
  const epoch = engine.getRunEpoch();
  const run: StepRun = {
    engine,
    isStale: () => engine.isAborted() || engine.getRunEpoch() !== epoch,
  };
  const currentGroup = engine.getGroup();
  const currentSteps = engine.getActiveSteps();

  const skip = engine.getSkip();
  if (skip) {
    skip.requested = false;
    skip.armed = false;
  }

  // Nowa karta (wejście od kroku 0): zeruj wynik odpowiedzi, stan oceny i
  // runnerowy zestaw odsłoniętych stron. Re-entry po gate (stepIndex != 0)
  // nie resetuje — wznowienie po gate synchronizuje strony ze świeżego stanu.
  if (stepIndex === 0) {
    engine.setLastAnswerResult(NO_ANSWER_RESULT);
    engine.setCardReviewState('none');
    engine.setRevealedPages([]);
  }

  // Koniec listy / brak grupy / abort => STOP. Bez fallbacku SHOW_RATINGS —
  // runner nie zgaduje intencji. Niepełna konfiguracja to błąd konfiguracji.
  if (engine.isAborted() || !currentGroup || stepIndex >= currentSteps.length) {
    return;
  }

  const step = currentSteps[stepIndex];

  // W tle: substytucja kroków wymagających interakcji na automatyczne
  if (engine.isBackgroundMode()) {
    if (
      step.type === 'wait_for_tap' ||
      step.type === 'wait_for_tap_to_reveal' ||
      step.type === 'wait_for_tap_to_reveal_next'
    ) {
      // Dynamiczna pauza zależna od długości tekstu (wzór jak w dynamic_pause)
      const totalTextLength = getActivePageIndexes(currentGroup).reduce(
        (sum, pi) => sum + (card.pages[pi] || '').length,
        0,
      );
      const waitMs = Math.max(1500, Math.min(totalTextLength * 60 * 2, 15000));
      if (!run.isStale()) {
        engine.dispatch({ type: 'SET_CURRENT_STEP', stepIndex });
        await engine.guardedAwait(sleep(waitMs));
      }
      if (run.isStale()) return;
      // Auto-odsłoń wszystkie aktywne strony
      const all = getActivePageIndexes(currentGroup);
      engine.setRevealedPages(all);
      engine.dispatch({ type: 'REVEAL_PAGES', revealedPages: all });
      await continueIfActive(card, stepIndex + 1, run);
      return;
    }

    // W tle: show_ratings = auto-pauza (teoretycznie nie powinno dojść, bo
    // isModeHandsFreeCapable filtruje tryby z show_ratings, ale defensywnie)
    if (step.type === 'show_ratings') {
      engine.pause();
      return;
    }
  }

  // Warunek kroku: 'correct' -> status correct; 'wrong' -> status incorrect.
  // skipped/error/none NIGDY nie pasują (tap w STT nie odpala correct/wrong).
  if (step.condition) {
    const status = engine.getLastAnswerResult().status;
    const conditionMet =
      step.condition === 'correct' ? status === 'correct' : status === 'incorrect';
    if (!conditionMet) {
      await continueIfActive(card, stepIndex + 1, run);
      return;
    }
  }

  switch (step.type) {
    case 'show_page': {
      engine.setRevealedPages(
        uniquePageIndexes([...engine.getRevealedPages(), step.pageIndex]),
      );
      engine.dispatch({ type: 'REVEAL_PAGE', stepIndex, pageIndex: step.pageIndex });
      await continueIfActive(card, stepIndex + 1, run);
      break;
    }

    case 'show_all_pages': {
      const all = getActivePageIndexes(currentGroup);
      engine.setRevealedPages(all);
      engine.dispatch({ type: 'REVEAL_PAGES', revealedPages: all });
      await continueIfActive(card, stepIndex + 1, run);
      break;
    }

    case 'wait_for_tap_to_reveal_next':
      if (areAllActivePagesRevealed(currentGroup, engine.getRevealedPages())) {
        await continueIfActive(card, stepIndex + 1, run);
      } else {
        engine.dispatch({
          type: 'START_TAP_REVEAL_GATE',
          revealMode: 'single',
          continueStepIndex: stepIndex + 1,
        });
      }
      break;

    case 'wait_for_tap_to_reveal':
      // Auto-complete: jeśli wszystko już odkryte, nie otwieraj gate — idź dalej.
      if (areAllActivePagesRevealed(currentGroup, engine.getRevealedPages())) {
        await continueIfActive(card, stepIndex + 1, run);
      } else {
        // STOP: runner czeka na tapy. Każdy tap (w gestach) odsłania jedną stronę;
        // ostatni domyka gate i ustawia pendingStepIndexToRun = stepIndex + 1.
        engine.dispatch({
          type: 'START_TAP_REVEAL_GATE',
          revealMode: 'remaining',
          continueStepIndex: stepIndex + 1,
        });
      }
      break;

    case 'show_ratings':
      // Pokaż ratingi tylko jeśli karta jeszcze nieoceniona; inaczej no-op -> dalej.
      if (engine.getCardReviewState() === 'none') {
        engine.dispatch({ type: 'SHOW_RATINGS' });
        // STOP: czekamy na manualną ocenę (handleRating).
      } else {
        await continueIfActive(card, stepIndex + 1, run);
      }
      break;

    case 'speak_page':
      await executeSpeakPageStep(card, stepIndex, step, currentGroup, run);
      break;

    case 'speak_all_pages': {
      // Czyta kolejno wszystkie aktywne strony. Tap przerywa TTS i pomija
      // odtwarzanie kolejnych stron tej karty (break pętli).
      const skip = engine.getSkip();
      for (const pageIndex of getActivePageIndexes(currentGroup)) {
        await speakSinglePage(card, stepIndex, pageIndex, currentGroup, run);
        if (run.isStale()) return;
        if (skip?.requested) break;
      }
      await continueIfActive(card, stepIndex + 1, run);
      break;
    }

    case 'dynamic_pause': {
      const text = card.pages[step.nextPageIndex] || '';
      await executeTimedWaitStep(card, stepIndex, text.length * 60 * step.pauseMultiplier, run);
      break;
    }

    case 'wait':
      await executeTimedWaitStep(card, stepIndex, step.ms, run);
      break;

    case 'listen_and_check':
      await executeListenAndCheckStep(card, stepIndex, step, currentGroup, run);
      break;

    case 'feedback_success':
      // Sam feedback — nie sprawdza wyniku (to robi condition), nie branchuje.
      playSuccessSound();
      playSuccessHaptic();
      await continueIfActive(card, stepIndex + 1, run);
      break;

    case 'feedback_error':
      playErrorSound();
      playErrorHaptic();
      await continueIfActive(card, stepIndex + 1, run);
      break;

    case 'auto_rate_from_answer':
      executeAutoRateFromAnswer(card, run);
      // Nie przechodzi do następnej karty, nie odsłania, nie pokazuje ratingów.
      await continueIfActive(card, stepIndex + 1, run);
      break;

    case 'auto_rate_fixed':
      if (engine.getCardReviewState() === 'none') {
        engine.processCardReview(card, step.rating);
        engine.setCardReviewState('autoRated');
      }
      await continueIfActive(card, stepIndex + 1, run);
      break;

    case 'mark_failed':
      // Failed list != ocena SRS. Sam dodaje kartę do failed, nic więcej.
      engine.markCardFailed(card);
      await continueIfActive(card, stepIndex + 1, run);
      break;

    case 'wait_for_tap':
      // STOP: zawsze czeka na tap (bez odsłaniania stron i bez auto-complete).
      // Tap przy revealMode 'none' tylko domyka gate i wznawia od kolejnego kroku.
      engine.dispatch({
        type: 'START_TAP_REVEAL_GATE',
        revealMode: 'none',
        continueStepIndex: stepIndex + 1,
      });
      break;

    case 'next_card':
      // Przechodzi do następnej karty bez własnej oceny. advanceToNextCard
      // respektuje trzymanie karty (czeka na puszczenie).
      await engine.advanceToNextCard();
      break;

    default: {
      const _exhaustive: never = step;
      throw new Error(`Unknown step type: ${(_exhaustive as AtomicStep).type}`);
    }
  }
}
