import React, { useCallback, useEffect, useRef } from 'react';
import { useLocalSearchParams, useNavigation } from 'expo-router';
import { AppErrorBoundary } from '@/components/feedback/AppErrorBoundary';
import { GroupNotFound } from '@/components/GroupNotFound';
import { LoadingState } from '@/components/layout/LoadingState';
import { StudyScreen } from '@/features/study/components/StudyScreen';
import { useStudyPageController } from '@/features/study/hooks/useStudyPageController';
import { useStudyBrowserBackGuard } from '@/features/study/hooks/useStudyBrowserBackGuard';
import { useI18n } from '@/i18n';
import { useStudyNavigationGuard } from '@/features/study/StudyNavigationGuardContext';
import { safeBack } from '@/utils/navigation';

function StudyPageContent() {
  const navigation = useNavigation();
  const { isStudyActive, setStudyExitHandler } = useStudyNavigationGuard();
  const allowNextBeforeRemoveRef = useRef(false);
  const navigateBackRef = useRef<() => void>(safeBack);
  const navigateBack = useCallback(() => {
    navigateBackRef.current();
  }, []);

  const controller = useStudyPageController({ navigateBack });
  const { requestExit } = controller;
  const navigateBackWithBrowserGuard = useStudyBrowserBackGuard({
    active: isStudyActive,
    onBackBlocked: controller.handleBack,
  });

  useEffect(() => {
    navigateBackRef.current = navigateBackWithBrowserGuard;
  }, [navigateBackWithBrowserGuard]);

  useEffect(() => {
    setStudyExitHandler(requestExit);
    return () => setStudyExitHandler(null);
  }, [requestExit, setStudyExitHandler]);

  useEffect(() => {
    const unsubscribe = navigation.addListener('beforeRemove', (e: {
      preventDefault: () => void;
      data: { action: Parameters<typeof navigation.dispatch>[0] };
    }) => {
      if (allowNextBeforeRemoveRef.current) {
        allowNextBeforeRemoveRef.current = false;
        return;
      }

      if (!isStudyActive) return;
      e.preventDefault();
      requestExit(() => {
        allowNextBeforeRemoveRef.current = true;
        navigation.dispatch(e.data.action);
      });
    });
    return unsubscribe;
  }, [isStudyActive, navigation, requestExit]);

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
