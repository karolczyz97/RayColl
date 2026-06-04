import React, { useCallback, useEffect, useRef, useState } from 'react';
import { useLocalSearchParams, useNavigation } from 'expo-router';
import { AppErrorBoundary } from '@/components/feedback/AppErrorBoundary';
import { ConfirmDialog } from '@/components/dialogs/ConfirmDialog';
import { GroupNotFound } from '@/components/GroupNotFound';
import { LoadingState } from '@/components/layout/LoadingState';
import { StudyScreen } from '@/features/study/components/StudyScreen';
import { useStudyPageController } from '@/features/study/hooks/useStudyPageController';
import { useI18n } from '@/i18n';
import { getIsStudyActive } from '@/features/study/studyGuard';

function StudyPageContent() {
  const controller = useStudyPageController();
  const navigation = useNavigation();
  const { t } = useI18n();

  const [showSystemBackGuard, setShowSystemBackGuard] = useState(false);
  const pendingSystemBackRef = useRef<(() => void) | null>(null);

  useEffect(() => {
    const unsubscribe = navigation.addListener('beforeRemove', (e: any) => {
      if (!getIsStudyActive()) return;
      e.preventDefault();
      pendingSystemBackRef.current = () => navigation.dispatch(e.data.action);
      setShowSystemBackGuard(true);
    });
    return unsubscribe;
  }, [navigation]);

  const confirmSystemBack = useCallback(() => {
    setShowSystemBackGuard(false);
    if (pendingSystemBackRef.current) {
      pendingSystemBackRef.current();
      pendingSystemBackRef.current = null;
    }
  }, []);

  const cancelSystemBack = useCallback(() => {
    setShowSystemBackGuard(false);
    pendingSystemBackRef.current = null;
  }, []);

  if (controller.isLoading) {
    return <LoadingState />;
  }

  if (!controller.activeGroup) {
    return <GroupNotFound onBack={controller.handleBack} />;
  }

  return (
    <>
      <StudyScreen {...controller} activeGroup={controller.activeGroup} />
      <ConfirmDialog
        visible={showSystemBackGuard}
        title={t('study.exit_confirm_title')}
        message={t('study.exit_confirm_message')}
        confirmLabel={t('study.exit_confirm_btn')}
        cancelLabel={t('btn.cancel')}
        onConfirm={confirmSystemBack}
        onDismiss={cancelSystemBack}
      />
    </>
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
