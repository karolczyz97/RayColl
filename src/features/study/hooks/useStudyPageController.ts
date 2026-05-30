import { useCallback, useEffect, useMemo, useRef } from 'react';
import { useWindowDimensions } from 'react-native';
import { router, useLocalSearchParams } from 'expo-router';
import type { Flashcard } from '../../../types/models';
import { useFlashcardStore } from '../../../hooks/useFlashcardStore';
import { useStudySession } from '../../../hooks/useStudySession';
import { useI18n } from '../../../i18n';
import { buildSessionProgressItems } from '../session/sessionProgress';

export function useStudyPageController() {
  const { groupId } = useLocalSearchParams<{ groupId: string }>();
  const { t } = useI18n();
  const store = useFlashcardStore();
  const { width } = useWindowDimensions();
  const isNarrow = width < 480;

  const {
    groups,
    studyModes,
    isLoading,
    reviewFlashcard,
    getDueCards,
    flushPersistence,
  } = store;

  const activeGroup = groups.find((group) => group.id === groupId) || null;
  const mode =
    studyModes.find((studyMode) => studyMode.id === activeGroup?.activeModeId) ||
    studyModes[0];
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
      reviewFlashcard(activeGroupId, card);
    },
    [reviewFlashcard],
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
    if (isLoading) return;
    if (startedRef.current === groupId) return;

    const currentGroup = groups.find((group) => group.id === groupId);
    if (currentGroup) {
      const due = getDueCards(currentGroup.id);
      if (due.length > 0) {
        startSession(due);
        startedRef.current = groupId;
      }
    }
  }, [groupId, startSession, isLoading, groups, getDueCards]);

  const handleBack = useCallback(() => {
    stopSession();
    void flushPersistence();
    router.back();
  }, [stopSession, flushPersistence]);

  useEffect(() => {
    return () => {
      stopSession();
      void flushPersistence();
    };
  }, [stopSession, flushPersistence]);

  useEffect(() => {
    if (sessionState.isSessionFinished) {
      void flushPersistence();
    }
  }, [sessionState.isSessionFinished, flushPersistence]);

  const sessionProgressItems = useMemo(
    () => buildSessionProgressItems(dueCards, activeGroup?.cards ?? []),
    [activeGroup?.cards, dueCards],
  );

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
    isLoading,
    isNarrow,
    restartFailed,
    restartSession,
    sessionProgressItems,
    sessionState,
    setHolding,
    t,
  };
}
