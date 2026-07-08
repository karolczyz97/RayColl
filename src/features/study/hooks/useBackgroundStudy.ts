import { useEffect, useRef } from 'react';
import { AppState, AppStateStatus, Platform } from 'react-native';
import { activateKeepAwakeAsync, deactivateKeepAwake } from 'expo-keep-awake';
import type { AtomicStep, Flashcard } from '@/types/models';
import type { SessionEngine } from '@/features/study/session/SessionEngine';
import { isModeHandsFreeCapable } from '@/features/study/session/sessionBackground';
import { audioSessionManager } from '@/services/mediaSession';
import { useI18n } from '@/i18n';

interface UseBackgroundStudyParams {
  engine: SessionEngine;
  groupName: string;
  steps: AtomicStep[];
  dueCards: Flashcard[];
  currentCardIndex: number;
  // isSessionFinished pochodzi z sessionViewModel — true gdy status === 'finished'
  isSessionFinished: boolean;
  backgroundPlaybackEnabled: boolean;
}

/**
 * Klej między AppState, SessionEngine i AudioSessionManager.
 *
 * - useKeepAwake: ekran nie gaśnie podczas aktywnej sesji.
 * - AppState: przy toggle OFF → engine.pause(); przy toggle ON i trybie
 *   hands-free → aktywuje mediaSession; w przeciwnym razie → engine.pause() + stopAudio.
 * - backgroundMode: ustawia flagę w silniku, która podmienia wait_for_tap na
 *   dynamiczne pauzy.
 * - FINISH_SESSION: zwalnia FGS i dezaktywuje media sesję.
 */
export function useBackgroundStudy({
  engine,
  groupName,
  steps,
  dueCards,
  currentCardIndex,
  isSessionFinished,
  backgroundPlaybackEnabled,
}: UseBackgroundStudyParams): void {
  const handsFreeCapable = isModeHandsFreeCapable(steps);
  const shouldUseBackground = backgroundPlaybackEnabled && handsFreeCapable;
  // currentState potrafi być null/undefined tuż po starcie procesu.
  const appStateRef = useRef<AppStateStatus>(AppState.currentState ?? 'active');
  const backgroundActivatedRef = useRef(false);
  const { t } = useI18n();
  // Zależność efektów od STRINGA, nie od funkcji t — niestabilne t między
  // renderami restartowałoby media sesję (deactivate+activate = miganie
  // notyfikacji i restart cichego playbacku).
  const pausedTitle = t('study.background_paused');

  // useKeepAwake: ekran nie wygasza się w trakcie sesji
  useEffect(() => {
    if (Platform.OS === 'android') {
      void activateKeepAwakeAsync();
      return () => {
        deactivateKeepAwake();
      };
    }
    return undefined;
  }, []);

  // Ustaw backgroundMode w silniku
  useEffect(() => {
    engine.setBackgroundMode(shouldUseBackground);
  }, [engine, shouldUseBackground]);

  // Aktywuj/dezaktywuj AudioSessionManager
  useEffect(() => {
    if (shouldUseBackground && !backgroundActivatedRef.current) {
      backgroundActivatedRef.current = true;
      void audioSessionManager.activate(engine, groupName, pausedTitle);
    }

    return () => {
      if (backgroundActivatedRef.current) {
        backgroundActivatedRef.current = false;
        void audioSessionManager.deactivate();
      }
    };
  }, [pausedTitle, groupName, shouldUseBackground, engine]);

  // Zwolnij FGS gdy sesja się kończy (wysłuchano wszystkie karty)
  useEffect(() => {
    if (isSessionFinished && backgroundActivatedRef.current) {
      backgroundActivatedRef.current = false;
      void audioSessionManager.deactivate();
    }
  }, [isSessionFinished]);

  // Aktualizuj metadane na lockscreenie przy każdej zmianie karty
  useEffect(() => {
    if (!shouldUseBackground) return;
    const card = dueCards[currentCardIndex];
    if (card) {
      audioSessionManager.updateNowPlaying(
        card.pages[0] || '',
        currentCardIndex,
        dueCards.length,
      );
    }
  }, [shouldUseBackground, dueCards, currentCardIndex]);

  // AppState listener
  useEffect(() => {
    const handleAppStateChange = (nextState: AppStateStatus) => {
      // "Wejście w tło" = poprzedni stan NIE był już tłem (start bywa 'unknown';
      // iOS raportuje inactive→background jako dwa zdarzenia — reaguj raz).
      const wasBackground =
        appStateRef.current === 'inactive' || appStateRef.current === 'background';
      if (!wasBackground && (nextState === 'inactive' || nextState === 'background')) {
        // Zejście w tło w trakcie trzymania karty: "puszczono" może nigdy nie
        // przyjść pod lockiem — zwolnij hold, żeby advance nie wisiał w tle.
        engine.setHolding(false);
        if (!shouldUseBackground) {
          // Niekompatybilny tryb: pauza + stopAudio (zabij STT)
          engine.pause();
        }
      }
      if (nextState === 'active' && shouldUseBackground && !isSessionFinished) {
        // Powrót do aplikacji po "Stop" z notyfikacji: media sesja umarła, ale
        // sesja nauki żyje — reaktywuj, żeby kolejne zablokowanie miało FGS.
        if (audioSessionManager.getCurrentEngine() !== engine) {
          backgroundActivatedRef.current = true;
          void audioSessionManager.activate(engine, groupName, pausedTitle);
        }
      }
      appStateRef.current = nextState;
    };

    const subscription = AppState.addEventListener('change', handleAppStateChange);
    return () => {
      subscription.remove();
    };
  }, [engine, shouldUseBackground, isSessionFinished, groupName, pausedTitle]);
}
