import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useWindowDimensions } from 'react-native';
import { useLocalSearchParams } from 'expo-router';
import { safeBack } from '@/utils/navigation';
import { useFlashcardStore } from '@/store/FlashcardStoreContext';
import { useStudySession } from '@/hooks/useStudySession';
import { buildSessionProgressItems } from '@/features/study/session/sessionProgress';

const NARROW_CONTROLS_WIDTH = 480;

interface UseStudyPageControllerOptions {
  navigateBack?: () => void;
}

export function useStudyPageController({
  navigateBack = safeBack,
}: UseStudyPageControllerOptions = {}) {
  const { groupId } = useLocalSearchParams<{ groupId: string }>();
  const store = useFlashcardStore();
  const { width } = useWindowDimensions();
  const isNarrow = width < NARROW_CONTROLS_WIDTH;

  const [showExitConfirm, setShowExitConfirm] = useState(false);
  const pendingExitNavigationRef = useRef<(() => void) | null>(null);

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
    (activeGroupId: string, cardId: string, rating: number) => {
      reviewFlashcard(activeGroupId, cardId, rating);
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
      }
    }
  }, [getDueCards, groupId, groups, isLoading, startSession]);

  const leaveStudy = useCallback(
    (navigate: () => void) => {
      stopSession();
      void flushPersistence().catch(() => {});
      navigate();
    },
    [flushPersistence, stopSession],
  );

  const requestExit = useCallback(
    (navigate: () => void = navigateBack) => {
      if (sessionState.isSessionFinished) {
        leaveStudy(navigate);
        return;
      }

      pendingExitNavigationRef.current = navigate;
      setShowExitConfirm(true);
    },
    [leaveStudy, navigateBack, sessionState.isSessionFinished],
  );

  const handleBack = useCallback(() => {
    requestExit(navigateBack);
  }, [navigateBack, requestExit]);

  const confirmExit = useCallback(() => {
    setShowExitConfirm(false);
    const navigate = pendingExitNavigationRef.current ?? navigateBack;
    pendingExitNavigationRef.current = null;
    leaveStudy(navigate);
  }, [leaveStudy, navigateBack]);

  const cancelExit = useCallback(() => {
    setShowExitConfirm(false);
    pendingExitNavigationRef.current = null;
  }, []);

  useEffect(() => {
    return () => {
      stopSession();
      void flushPersistence().catch(() => {});
    };
  }, [flushPersistence, stopSession]);

  useEffect(() => {
    if (sessionState.isSessionFinished) {
      void flushPersistence().catch(() => {});
    }
  }, [flushPersistence, sessionState.isSessionFinished]);

  const isExitBlocked = dueCards.length > 0 && !sessionState.isSessionFinished;

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
    isExitBlocked,
    isLoading,
    isNarrow,
    restartFailed,
    restartSession,
    requestExit,
    sessionProgressItems,
    sessionState,
    setHolding,
    showExitConfirm,
  };
}
