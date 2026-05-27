import { useCallback, useEffect, useMemo, useRef } from 'react';
import { useWindowDimensions } from 'react-native';
import { router, useLocalSearchParams } from 'expo-router';
import type { Flashcard } from '../../../types/models';
import { useFlashcardStore } from '../../../hooks/useFlashcardStore';
import { useStudySession } from '../../../hooks/useStudySession';
import { useI18n } from '../../../i18n';

export function useStudyPageController() {
  const { groupId } = useLocalSearchParams<{ groupId: string }>();
  const { t } = useI18n();
  const store = useFlashcardStore();
  const { width } = useWindowDimensions();
  const isNarrow = width < 480;

  const activeGroup = store.groups.find((group) => group.id === groupId) || null;
  const mode =
    store.studyModes.find((studyMode) => studyMode.id === activeGroup?.activeModeId) ||
    store.studyModes[0];
  const steps = useMemo(() => mode?.steps || [], [mode]);

  const getButtonText = useCallback(
    (key: string) => {
      if (isNarrow) return '';
      const text = t(key);
      return text.split(' (')[0];
    },
    [isNarrow, t],
  );

  const onCardReviewed = useCallback(
    (activeGroupId: string, card: Flashcard) => {
      store.reviewFlashcard(activeGroupId, card);
    },
    [store],
  );

  const {
    dueCards,
    sessionState,
    handleRating,
    handleCardTap,
    startSession,
    stopSession,
    setHolding,
    restartSession,
    restartFailed,
    failedCount,
  } = useStudySession(activeGroup, steps, onCardReviewed);

  const startedRef = useRef<string | null>(null);

  useEffect(() => {
    if (store.isLoading) return;
    if (startedRef.current === groupId) return;

    const currentGroup = store.groups.find((group) => group.id === groupId);
    if (currentGroup) {
      const due = store.getDueCards(currentGroup.id);
      if (due.length > 0) {
        startSession(due);
        startedRef.current = groupId;
      }
    }
  }, [groupId, startSession, store]);

  const handleBack = useCallback(() => {
    stopSession();
    void store.flushPersistence();
    router.back();
  }, [stopSession, store]);

  useEffect(() => {
    return () => {
      stopSession();
      void store.flushPersistence();
    };
  }, [stopSession, store]);

  useEffect(() => {
    if (sessionState.isSessionFinished) {
      void store.flushPersistence();
    }
  }, [sessionState.isSessionFinished, store]);

  return {
    activeGroup,
    currentCard: dueCards[sessionState.currentCardIndex] || null,
    dueCards,
    failedCount,
    getButtonText,
    groupId,
    handleBack,
    handleCardTap,
    handleRating,
    hasStt: steps.some((step) => step.type === 'listen_and_branch'),
    hasTts: steps.some((step) => step.type === 'speak_page'),
    isLoading: store.isLoading,
    isNarrow,
    progressPct: dueCards.length > 0 ? sessionState.currentCardIndex / dueCards.length : 0,
    restartFailed,
    restartSession,
    sessionState,
    setHolding,
    t,
  };
}
