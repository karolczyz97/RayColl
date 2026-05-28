import React from 'react';
import { useLocalSearchParams } from 'expo-router';
import { AppErrorBoundary } from '../../components/feedback/AppErrorBoundary';
import { GroupNotFound } from '../../components/GroupNotFound';
import { LoadingState } from '../../components/layout/LoadingState';
import { StudyScreen } from '../../features/study/components/StudyScreen';
import { useStudyPageController } from '../../features/study/hooks/useStudyPageController';

function StudyPageContent() {
  const controller = useStudyPageController();

  if (controller.isLoading) {
    return <LoadingState />;
  }

  if (!controller.activeGroup) {
    return <GroupNotFound onBack={controller.handleBack} />;
  }

  return <StudyScreen {...controller} activeGroup={controller.activeGroup} />;
}

export default function StudyPage() {
  const { groupId } = useLocalSearchParams<{ groupId: string }>();

  return (
    <AppErrorBoundary resetKey={groupId} title="Study screen crashed">
      <StudyPageContent />
    </AppErrorBoundary>
  );
}
