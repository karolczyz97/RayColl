import React from 'react';
import { GroupNotFound } from '../../components/GroupNotFound';
import { LoadingState } from '../../components/layout/LoadingState';
import { DeckSettingsScreen } from '../../features/settings/DeckSettingsScreen';
import { useDeckSettingsController } from '../../features/settings/useDeckSettingsController';

export default function SettingsPage() {
  const controller = useDeckSettingsController();

  if (controller.isLoading) {
    return <LoadingState />;
  }

  if (!controller.activeGroup) {
    return <GroupNotFound onBack={controller.handleBack} />;
  }

  return <DeckSettingsScreen {...controller} />;
}
