import React from 'react';
import { useLocalSearchParams } from 'expo-router';
import { AppErrorBoundary } from '../../components/feedback/AppErrorBoundary';
import { BrowseScreen } from '../../features/browse/BrowseScreen';

export default function BrowsePage() {
  const { groupId } = useLocalSearchParams<{ groupId: string }>();

  return (
    <AppErrorBoundary resetKey={groupId} title="Browse screen crashed">
      <BrowseScreen />
    </AppErrorBoundary>
  );
}
