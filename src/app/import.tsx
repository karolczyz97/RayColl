import React from 'react';
import { AppErrorBoundary } from '../components/feedback/AppErrorBoundary';
import { ImportScreen } from '../features/import/ImportScreen';
import { useI18n } from '../i18n';

export default function ImportPage() {
  const { t } = useI18n();
  return (
    <AppErrorBoundary title={t('errors.import_crashed')}>
      <ImportScreen />
    </AppErrorBoundary>
  );
}
