import { useCallback, useRef } from 'react';
import type { MutableRefObject } from 'react';
import type { SessionAction } from '@/features/study/session/sessionTypes';
import { ttsService } from '@/services/ttsService';
import { getSttService } from '@/services/sttService';
import {
  playMicOffSound,
  playMicOnSound,
} from '@/services/audioFeedback';
import { getErrorMessage } from '@/utils/errors';

export interface StudySkipState {
  requested: boolean;
  armed: boolean;
  signalResolve: () => void;
}

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

  const stopAudio = useCallback(() => {
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
    async (lang: string, timeoutMs: number) => {
      playMicOnSound();
      let recognized = '';
      try {
        recognized = await sttServiceRef.current.startListening({
          language: lang,
          timeoutMs,
          onPartialResult: (text) =>
            dispatchIfMounted({ type: 'UPDATE_PARTIAL_STT', text }),
          onListeningStateChange: (listening) => {
            if (!listening) playMicOffSound();
          },
        });
      } catch (err) {
        console.error('STT Listen Error:', getErrorMessage(err));
        dispatchIfMounted({ type: 'SET_ERROR', errorMsg: 'study.error.stt' });
      }
      return recognized;
    },
    [dispatchIfMounted],
  );

  const requestSkip = useCallback(() => {
    const skip = skipRef.current;
    if (!skip.armed) return;
    skip.requested = true;
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
  };
}
