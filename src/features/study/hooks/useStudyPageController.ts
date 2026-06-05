import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useWindowDimensions } from 'react-native';
import { useLocalSearchParams } from 'expo-router';
import { safeBack } from '@/utils/navigation';
import type { Flashcard } from '@/types/models';
import { useFlashcardStore } from '@/store/FlashcardStoreContext';
import { useStudySession } from '@/hooks/useStudySession';
import { buildSessionProgressItems } from '@/features/study/session/sessionProgress';
import { useStudyNavigationGuard } from '@/features/study/StudyNavigationGuardContext';

const NARROW_CONTROLS_WIDTH = 480;

export function useStudyPageController() {
  const { groupId } = useLocalSearchParams<{ groupId: string }>();
  const store = useFlashcardStore();
  const { setStudyActive } = useStudyNavigationGuard();
  const { width } = useWindowDimensions();
  const isNarrow = width < NARROW_CONTROLS_WIDTH;

  const [showExitConfirm, setShowExitConfirm] = useState(false);

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
    handleCardPress,
    startSession,
    stopSession,
    setHolding,
    restartSession,
    restartFailed,
    failedCount,
    clearError,
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
        setStudyActive(true);
      }
    }
  }, [getDueCards, groupId, groups, isLoading, setStudyActive, startSession]);

  const handleBack = useCallback(() => {
    if (sessionState.isSessionFinished) {
      stopSession();
      void flushPersistence();
      setStudyActive(false);
      safeBack();
      return;
    }
    setShowExitConfirm(true);
  }, [flushPersistence, sessionState.isSessionFinished, setStudyActive, stopSession]);

  const confirmExit = useCallback(() => {
    setShowExitConfirm(false);
    stopSession();
    void flushPersistence();
    setStudyActive(false);
    safeBack();
  }, [flushPersistence, setStudyActive, stopSession]);

  const cancelExit = useCallback(() => {
    setShowExitConfirm(false);
  }, []);

  useEffect(() => {
    return () => {
      stopSession();
      void flushPersistence();
      setStudyActive(false);
    };
  }, [flushPersistence, setStudyActive, stopSession]);

  useEffect(() => {
    if (sessionState.isSessionFinished) {
      void flushPersistence();
      setStudyActive(false);
    }
  }, [flushPersistence, sessionState.isSessionFinished, setStudyActive]);

  const sessionProgressItems = useMemo(
    () => buildSessionProgressItems(dueCards, activeGroup?.cards ?? []),
    [activeGroup?.cards, dueCards],
  );

  return {
    activeGroup,
    cancelExit,
    clearError,
    confirmExit,
    currentCard: dueCards[sessionState.currentCardIndex] || null,
    dueCards,
    failedCount,
    groupId,
    handleBack,
    handleCardPress,
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
    showExitConfirm,
  };
}
