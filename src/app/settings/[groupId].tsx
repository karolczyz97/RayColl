import React from 'react';
import { useLocalSearchParams } from 'expo-router';
import { AppErrorBoundary } from '../../components/feedback/AppErrorBoundary';
import { GroupNotFound } from '../../components/GroupNotFound';
import { LoadingState } from '../../components/layout/LoadingState';
import { DeckSettingsScreen } from '../../features/settings/DeckSettingsScreen';
import { useDeckSettingsController } from '../../features/settings/useDeckSettingsController';

function SettingsPageContent() {
  const controller = useDeckSettingsController();

  if (controller.isLoading) {
    return <LoadingState />;
  }

  if (!controller.activeGroup) {
    return <GroupNotFound onBack={controller.handleBack} />;
  }

  return <DeckSettingsScreen {...controller} />;
}

export default function SettingsPage() {
  const { groupId } = useLocalSearchParams<{ groupId: string }>();

  return (
    <AppErrorBoundary resetKey={groupId} title="Settings screen crashed">
      <SettingsPageContent />
    </AppErrorBoundary>
  );
}
