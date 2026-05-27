import React from 'react';
import { GroupNotFound } from '../../components/GroupNotFound';
import { LoadingState } from '../../components/layout/LoadingState';
import { StudyScreen } from '../../features/study/components/StudyScreen';
import { useStudyPageController } from '../../features/study/hooks/useStudyPageController';

export default function StudyPage() {
  const controller = useStudyPageController();

  if (controller.isLoading) {
    return <LoadingState />;
  }

  if (!controller.activeGroup) {
    return <GroupNotFound onBack={controller.handleBack} />;
  }

  return <StudyScreen {...controller} activeGroup={controller.activeGroup} />;
}
