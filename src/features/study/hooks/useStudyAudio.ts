import { useCallback, useRef } from 'react';
import type { MutableRefObject } from 'react';
import type { SessionAction } from '@/features/study/session/sessionTypes';
import { ttsService } from '@/services/ttsService';
import { getSttService } from '@/services/sttService';
import {
  playMicOffSound,
  playMicOnSound,
} from '@/services/audioFeedback';
import { playErrorHaptic, playStudyActionHaptic } from '@/services/hapticFeedback';
import { getErrorMessage } from '@/utils/errors';

export interface StudySkipState {
  requested: boolean;
  armed: boolean;
  signalResolve: () => void;
}

/**
 * Distinguishes a recognized utterance (even an empty one, which is a legitimate
 * 0% answer) from a speech-service failure (mic/permission/network), so the caller
 * can fail the card on the former but offer a manual rating on the latter.
 */
export type SpeechRecognitionOutcome =
  | { status: 'ok'; text: string }
  | { status: 'error' };

export function useStudyAudio(
  dispatchIfMounted: (action: SessionAction) => void,
  ttsRateRef: MutableRefObject<number>,
) {
  const sttServiceRef = useRef(getSttService());
  const lastTtsDurationRef = useRef(0);
  const skipRef = useRef<StudySkipState>({
    requested: false,
    armed: false,
    signalResolve: () => {},
  });
  // Bumped whenever a recognition run starts or audio is stopped/skipped, so we can
  // discard partial-result callbacks that belong to a previous (zombie) run.
  const sttTokenRef = useRef(0);
  const lastPartialTextRef = useRef('');

  const stopAudio = useCallback(() => {
    sttTokenRef.current += 1;
    ttsService.cancel();
    void sttServiceRef.current.stopListening().catch(() => {});
  }, []);

  const playTts = useCallback(
    async (text: string, lang: string) => {
      const startTime = Date.now();
      try {
        await ttsService.speak({ text, lang, rate: ttsRateRef.current });
      } catch (err) {
        console.error('TTS Speak Error:', getErrorMessage(err));
        dispatchIfMounted({ type: 'SET_ERROR', errorMsg: 'study.error.tts' });
      }
      lastTtsDurationRef.current = Date.now() - startTime;
    },
    [dispatchIfMounted, ttsRateRef],
  );

  const runSpeechRecognition = useCallback(
    async (lang: string, timeoutMs: number): Promise<SpeechRecognitionOutcome> => {
      const token = (sttTokenRef.current += 1);
      lastPartialTextRef.current = '';
      playStudyActionHaptic();
      playMicOnSound();
      try {
        const text = await sttServiceRef.current.startListening({
          language: lang,
          timeoutMs,
          onPartialResult: (partial) => {
            // Drop partials from a superseded/cancelled run (zombie callback).
            if (token !== sttTokenRef.current) return;
            lastPartialTextRef.current = partial;
            dispatchIfMounted({ type: 'UPDATE_PARTIAL_STT', text: partial });
          },
          onListeningStateChange: (listening) => {
            if (!listening) playMicOffSound();
          },
        });
        return { status: 'ok', text };
      } catch (err) {
        playErrorHaptic();
        console.error('STT Listen Error:', getErrorMessage(err));
        dispatchIfMounted({ type: 'SET_ERROR', errorMsg: 'study.error.stt' });
        return { status: 'error' };
      }
    },
    [dispatchIfMounted],
  );

  const requestSkip = useCallback(() => {
    const skip = skipRef.current;
    if (!skip.armed) return;
    skip.requested = true;
    sttTokenRef.current += 1;
    ttsService.cancel();
    void sttServiceRef.current.stopListening().catch(() => {});
    skip.signalResolve();
  }, []);

  const guardedAwait = useCallback(
    async <T>(promise: Promise<T>): Promise<T | undefined> => {
      const skip = skipRef.current;
      const skipSignal = new Promise<void>((resolve) => {
        skip.signalResolve = resolve;
      });
      return (await Promise.race([promise, skipSignal]).catch(() => undefined)) as
        | T
        | undefined;
    },
    [],
  );

  return {
    playTts,
    runSpeechRecognition,
    requestSkip,
    guardedAwait,
    stopAudio,
    lastTtsDurationRef,
    skipRef,
    lastPartialTextRef,
  };
}
