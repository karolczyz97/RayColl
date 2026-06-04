import React from 'react';
import { useLocalSearchParams } from 'expo-router';
import { AppErrorBoundary } from '@/components/feedback/AppErrorBoundary';
import { BrowseScreen } from '@/features/browse/BrowseScreen';
import { useI18n } from '@/i18n';

export default function BrowsePage() {
  const { groupId } = useLocalSearchParams<{ groupId: string }>();
  const { t } = useI18n();

  return (
    <AppErrorBoundary resetKey={groupId} title={t('errors.browse_crashed')}>
      <BrowseScreen />
    </AppErrorBoundary>
  );
}
