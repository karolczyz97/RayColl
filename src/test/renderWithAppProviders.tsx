import React from 'react';
import { PaperProvider } from 'react-native-paper';

import { AppThemeProvider } from '@/contexts/UserPreferencesContext';
import {
  NavigationShellProvider,
  type NavigationShellContextValue,
} from '@/contexts/NavigationShellContext';
import { NavigationBlockerProvider } from '@/contexts/NavigationBlockerContext';
import { I18nProvider } from '@/i18n';
import { createAppTheme } from '@/theme/createAppTheme';

const defaultShellContext: NavigationShellContextValue = {
  isCompact: true,
  isMedium: false,
  isExpanded: false,
  contentWidth: 400,
  showPersistentNavigation: false,
  navWidth: 0,
};

interface TestProvidersProps {
  children: React.ReactNode;
  shellContext?: Partial<NavigationShellContextValue>;
}

export function TestProviders({ children, shellContext }: TestProvidersProps) {
  const theme = createAppTheme({
    isDark: false,
    useSystemColors: false,
  });

  const shellValue: NavigationShellContextValue = {
    ...defaultShellContext,
    ...shellContext,
  };

  return (
    <I18nProvider>
      <AppThemeProvider>
        <NavigationShellProvider value={shellValue}>
          <NavigationBlockerProvider>
            <PaperProvider theme={theme}>{children}</PaperProvider>
          </NavigationBlockerProvider>
        </NavigationShellProvider>
      </AppThemeProvider>
    </I18nProvider>
  );
}
