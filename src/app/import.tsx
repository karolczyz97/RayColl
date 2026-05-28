import React from 'react';
import { AppErrorBoundary } from '../components/feedback/AppErrorBoundary';
import { ImportScreen } from '../features/import/ImportScreen';

export default function ImportPage() {
  return (
    <AppErrorBoundary title="Import screen crashed">
      <ImportScreen />
    </AppErrorBoundary>
  );
}
