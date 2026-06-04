import React from 'react';
import { PaperProvider } from 'react-native-paper';

import { AppThemeProvider } from '@/contexts/ThemeContext';
import { I18nProvider } from '@/i18n';
import { createAppTheme } from '@/theme/createAppTheme';

interface TestProvidersProps {
  children: React.ReactNode;
}

export function TestProviders({ children }: TestProvidersProps) {
  const theme = createAppTheme({
    isDark: false,
    useSystemColors: false,
  });

  return (
    <I18nProvider>
      <AppThemeProvider>
        <PaperProvider theme={theme}>{children}</PaperProvider>
      </AppThemeProvider>
    </I18nProvider>
  );
}

