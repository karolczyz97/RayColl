import React, { useCallback, useEffect, useRef } from 'react';
import { useLocalSearchParams, useNavigation } from 'expo-router';
// usePreventRemove isn't re-exported from the expo-router entry. It drives the stack's native
// preventNativeDismiss, the only reliable way to intercept Android's native back gesture.
// Imported from the bundled react-navigation core since there's no public alias.
import { usePreventRemove } from 'expo-router/build/react-navigation/core';
import { AppErrorBoundary } from '@/components/feedback/AppErrorBoundary';
import { GroupNotFound } from '@/components/GroupNotFound';
import { LoadingState } from '@/components/layout/LoadingState';
import { StudyScreen } from '@/features/study/components/StudyScreen';
import { useStudyPageController } from '@/features/study/hooks/useStudyPageController';
import { useNavigationBlocker } from '@/contexts/NavigationBlockerContext';
import { useBrowserBackBlocker } from '@/hooks/useBrowserBackBlocker';
import { useI18n } from '@/i18n';
import { navigateUp } from '@/utils/navigation';

function StudyPageContent() {
  const navigation = useNavigation();
  const navigateBackRef = useRef<() => void>(navigateUp);
  const navigateBack = useCallback(() => {
    navigateBackRef.current();
  }, []);

  const controller = useStudyPageController({ navigateBack });
  const { isExitBlocked, requestExit } = controller;
  const navigateBackWithBrowserBlocker = useBrowserBackBlocker({
    active: isExitBlocked,
    onBackBlocked: controller.handleBack,
  });

  useNavigationBlocker({
    active: isExitBlocked,
    requestLeave: requestExit,
  });

  useEffect(() => {
    navigateBackRef.current = navigateBackWithBrowserBlocker;
  }, [navigateBackWithBrowserBlocker]);

  // Intercept Android's native back/gesture while a session is in progress. Setting
  // preventRemove flips the screen's native preventNativeDismiss so the gesture can't pop the
  // screen out from under us; requestExit then surfaces the confirmation dialog (confirming
  // ends the session in place).
  usePreventRemove(isExitBlocked, () => {
    requestExit();
  });

  // Bridge for JS-driven removals (like the header back button, and Expo Router Web's browser back).
  // usePreventRemove (above) ensures native gestures are blocked, but on Web it doesn't correctly
  // intercept the popstate routing change, causing the screen to unmount without warning.
  useEffect(() => {
    if (!isExitBlocked) return;
    const unsubscribe = navigation.addListener('beforeRemove', (e: {
      preventDefault: () => void;
    }) => {
      e.preventDefault();
      requestExit();
    });
    return unsubscribe;
  }, [isExitBlocked, navigation, requestExit]);

  // usePreventRemove blocks the native swipe-back, but the stack reports a *blocked* swipe as a
  // 'gestureCancel' event rather than invoking the callback above (which only fires for JS-driven
  // removals). Without this an edge-swipe is silently swallowed with no dialog, so bridge it to
  // the same confirm flow as the in-app back button. useNavigation()'s base typing doesn't
  // surface this stack-specific event, hence the cast.
  useEffect(() => {
    if (!isExitBlocked) return;
    const addGestureListener = navigation.addListener as unknown as (
      type: 'gestureCancel',
      callback: () => void,
    ) => () => void;
    return addGestureListener('gestureCancel', () => {
      requestExit();
    });
  }, [isExitBlocked, navigation, requestExit]);

  if (controller.isLoading) {
    return <LoadingState />;
  }

  if (!controller.activeGroup) {
    return <GroupNotFound onBack={controller.handleBack} />;
  }

  return (
    <StudyScreen {...controller} activeGroup={controller.activeGroup} />
  );
}

export default function StudyPage() {
  const { groupId } = useLocalSearchParams<{ groupId: string }>();
  const { t } = useI18n();

  return (
    <AppErrorBoundary resetKey={groupId} title={t('errors.study_crashed')}>
      <StudyPageContent />
    </AppErrorBoundary>
  );
}
