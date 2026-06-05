import type { MutableRefObject } from 'react';
import type { Flashcard, FlashcardGroup, ModeStep } from '@/types/models';
import { mapMatchToRating, matchSpeech } from '@/srs/srsEngine';
import { playErrorSound, playSuccessSound } from '@/services/audioFeedback';
import type { StudySkipState } from '@/features/study/hooks/useStudyAudio';
import type { SessionAction } from './sessionTypes';
import { getActivePageIndexes, sleep } from './sessionUtils';

interface StepExecutorContext {
  abortRef: MutableRefObject<boolean>;
  activeStepsRef: MutableRefObject<ModeStep[]>;
  groupRef: MutableRefObject<FlashcardGroup | null>;
  skipRef: MutableRefObject<StudySkipState>;
  lastTtsDurationRef: MutableRefObject<number>;
  dispatchIfMounted: (action: SessionAction) => void;
  guardedAwait: <T>(promise: Promise<T>) => Promise<T | undefined>;
  playTts: (text: string, lang: string) => Promise<void>;
  runSpeechRecognition: (lang: string, timeoutMs: number) => Promise<string>;
  processCardReview: (card: Flashcard, rating: number) => void;
  advanceToNextCard: () => Promise<void>;
  markCardFailed: (card: Flashcard) => void;
  unmarkCardFailed: (cardId: string) => void;
  executeNext: (card: Flashcard, stepIndex: number) => Promise<void>;
}

async function continueIfActive(
  card: Flashcard,
  stepIndex: number,
  context: StepExecutorContext,
) {
  if (!context.abortRef.current) {
    await context.executeNext(card, stepIndex);
  }
}

async function executeSpeakPageStep(
  card: Flashcard,
  stepIndex: number,
  step: Extract<ModeStep, { type: 'speak_page' }>,
  group: FlashcardGroup,
  context: StepExecutorContext,
) {
  context.skipRef.current.armed = true;
  context.dispatchIfMounted({ type: 'START_SPEAKING', stepIndex });
  await context.guardedAwait(
    context.playTts(card.pages[step.pageIndex] || '', group.pageLanguages[step.pageIndex] || 'en-US'),
  );
  if (!context.skipRef.current.requested && step.extraPauseMs > 0) {
    await context.guardedAwait(sleep(step.extraPauseMs));
  }
  context.dispatchIfMounted({ type: 'END_SPEAKING' });

  await continueIfActive(card, stepIndex + 1, context);
}

async function executeTimedWaitStep(
  card: Flashcard,
  stepIndex: number,
  waitMs: number,
  context: StepExecutorContext,
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
  context: StepExecutorContext,
) {
  context.skipRef.current.armed = true;
  context.dispatchIfMounted({ type: 'START_LISTENING', stepIndex });
  const lang = group.pageLanguages[step.pageIndex] || 'en-US';
  const softTimeout = Math.max(5000, context.lastTtsDurationRef.current * 3);
  const recognized =
    (await context.guardedAwait(context.runSpeechRecognition(lang, softTimeout + 10000))) || '';
  context.skipRef.current.armed = false;

  if (context.skipRef.current.requested) {
    context.dispatchIfMounted({
      type: 'END_LISTENING',
      text: '',
      matchPercent: 0,
    });
    await continueIfActive(card, stepIndex + 1, context);
    return;
  }

  const original = card.pages[step.pageIndex] || '';
  const percent = matchSpeech(recognized, original);

  context.dispatchIfMounted({
    type: 'END_LISTENING',
    text: recognized || '',
    matchPercent: percent,
  });

  await sleep(1200);

  if (percent >= step.successThreshold) {
    playSuccessSound();
    const autoRating = mapMatchToRating(percent);
    await sleep(600);
    if (!context.abortRef.current) {
      context.processCardReview(card, autoRating);
      await context.advanceToNextCard();
    }
    return;
  }

  playErrorSound();
  context.markCardFailed(card);

  context.dispatchIfMounted({
    type: 'REVEAL_PAGES',
    revealedPages: getActivePageIndexes(group),
  });

  context.skipRef.current.armed = true;
  if (step.incorrectTtsPageIndex !== undefined) {
    context.dispatchIfMounted({ type: 'START_SPEAKING', stepIndex });
    const correctionStart = Date.now();
    await context.guardedAwait(
      context.playTts(
        card.pages[step.incorrectTtsPageIndex] || '',
        group.pageLanguages[step.incorrectTtsPageIndex] || 'en-US',
      ),
    );
    const correctionDuration = Date.now() - correctionStart;
    context.dispatchIfMounted({ type: 'END_SPEAKING' });
    if (!context.skipRef.current.requested) {
      await context.guardedAwait(sleep(correctionDuration * 2));
    }
  } else {
    await context.guardedAwait(sleep(2000));
  }
  context.skipRef.current.armed = false;

  if (context.skipRef.current.requested) {
    context.unmarkCardFailed(card.id);
    await continueIfActive(card, stepIndex + 1, context);
    return;
  }

  if (!context.abortRef.current) {
    context.processCardReview(card, 1);
    await context.advanceToNextCard();
  }
}

export async function executeStudyStep(
  card: Flashcard,
  stepIndex: number,
  context: StepExecutorContext,
) {
  const currentGroup = context.groupRef.current;
  const currentSteps = context.activeStepsRef.current;

  context.skipRef.current.requested = false;
  context.skipRef.current.armed = false;

  if (context.abortRef.current || !currentGroup || stepIndex >= currentSteps.length) {
    context.dispatchIfMounted({ type: 'SHOW_RATINGS' });
    return;
  }

  const step = currentSteps[stepIndex];

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

    case 'speak_page':
      await executeSpeakPageStep(card, stepIndex, step, currentGroup, context);
      break;

    case 'dynamic_pause': {
      const text = card.pages[step.nextPageIndex] || '';
      await executeTimedWaitStep(card, stepIndex, text.length * 60 + (step.extraPauseMs || 0), context);
      break;
    }

    case 'wait':
      await executeTimedWaitStep(card, stepIndex, step.ms, context);
      break;

    case 'listen_and_branch':
      await executeListenAndBranchStep(card, stepIndex, step, currentGroup, context);
      break;

    default: {
      const _exhaustive: never = step;
      throw new Error(`Unknown step type: ${(_exhaustive as ModeStep).type}`);
    }
  }
}
