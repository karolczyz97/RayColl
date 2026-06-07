import React from 'react';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { fireEvent, renderAsync, screen, waitFor } from '@testing-library/react-native';
import { Text } from 'react-native';

import { STORAGE_KEYS } from '../../../constants/storageKeys';
import { useNavigationShell } from '../../../contexts/NavigationShellContext';
import { AppThemeProvider } from '../../../contexts/UserPreferencesContext';
import { useStudyNavigationGuard } from '../../../features/study/StudyNavigationGuardContext';
import { I18nProvider } from '../../../i18n';
import { UI_PREFERENCE_STORAGE_KEYS, clearUiPreferenceCache } from '../../../storage/uiPreferences';
import { createAppTheme } from '../../../theme/createAppTheme';
import { AppTopBar } from '../../layout/AppTopBar';
import { AppNavigationShell } from '../AppNavigationShell';
import { PaperProvider } from 'react-native-paper';

const mockResponsiveLayout = {
  width: 840,
  height: 900,
  windowSizeClass: 'expanded',
  contentWidth: 760,
  contentSizeClass: 'medium',
  isCompact: false,
  isMedium: false,
  isExpanded: true,
  isDesktop: true,
  contentMaxWidth: 1200,
  formMaxWidth: 800,
  cardMaxWidth: 450,
  useTwoColumnLayout: false,
  showNavigationRail: true,
  showPersistentNavigation: true,
  navWidth: 80,
};

jest.mock('../../../hooks/useResponsiveLayout', () => ({
  useResponsiveLayout: () => mockResponsiveLayout,
}));

jest.mock('../../../store/FlashcardStoreContext', () => ({
  useFlashcardStore: () => ({
    user: null,
    signIn: jest.fn(),
    signOut: jest.fn(),
  }),
}));

function setResponsiveLayout(overrides: Partial<typeof mockResponsiveLayout>) {
  Object.assign(mockResponsiveLayout, {
    width: 840,
    height: 900,
    windowSizeClass: 'expanded',
    contentWidth: 760,
    contentSizeClass: 'medium',
    isCompact: false,
    isMedium: false,
    isExpanded: true,
    isDesktop: true,
    contentMaxWidth: 1200,
    formMaxWidth: 800,
    cardMaxWidth: 450,
    useTwoColumnLayout: false,
    showNavigationRail: true,
    showPersistentNavigation: true,
    navWidth: 80,
    ...overrides,
  });
}

function ShellSizeProbe() {
  const { contentWidth, navWidth } = useNavigationShell();

  return <Text testID="shell-size">{`${contentWidth}:${navWidth}`}</Text>;
}

function StudyExitGuardProbe({
  onRequest,
}: {
  onRequest: (navigate: () => void) => void;
}) {
  const { setStudyActive, setStudyExitHandler } = useStudyNavigationGuard();

  React.useEffect(() => {
    setStudyActive(true);
    setStudyExitHandler(onRequest);
    return () => {
      setStudyActive(false);
      setStudyExitHandler(null);
    };
  }, [onRequest, setStudyActive, setStudyExitHandler]);

  return <Text>Study guard active</Text>;
}

async function renderShell(children?: React.ReactNode) {
  const theme = createAppTheme({
    isDark: false,
    useSystemColors: false,
  });

  return renderAsync(
    <I18nProvider>
      <AppThemeProvider>
        <PaperProvider theme={theme}>
          <AppNavigationShell>
            <AppTopBar title="Dashboard" />
            <ShellSizeProbe />
            {children}
          </AppNavigationShell>
        </PaperProvider>
      </AppThemeProvider>
    </I18nProvider>,
  );
}

describe('AppNavigationShell', () => {
  beforeEach(async () => {
    jest.clearAllMocks();
    await AsyncStorage.clear();
    clearUiPreferenceCache();
    global.__expoRouterMock.pathname = '/';
    setResponsiveLayout({});
  });

  it('provides the actual content width after applying the current rail state', async () => {
    await renderShell();

    await waitFor(() => expect(screen.getByTestId('shell-size')).toHaveTextContent('760:80'));
  });

  it('loads UI preferences with a single batched storage read', async () => {
    await renderShell();

    await waitFor(() => expect(AsyncStorage.multiGet).toHaveBeenCalledWith(UI_PREFERENCE_STORAGE_KEYS));
  });

  it('always shows the rail and offers no hide control on medium+ widths', async () => {
    await renderShell();

    // The rail is present (its expand control is shown) and can only be
    // collapsed/expanded — never fully hidden.
    await waitFor(() => expect(screen.getByLabelText('Expand navigation')).toBeOnTheScreen());
    expect(screen.queryByLabelText('Hide navigation')).toBeNull();
    expect(screen.queryByLabelText('Show navigation')).toBeNull();
  });

  it('does not render app branding inside the rail', async () => {
    await renderShell();

    await waitFor(() => expect(screen.getByLabelText('Expand navigation')).toBeOnTheScreen());
    expect(screen.queryByText('RayColl')).toBeNull();
    expect(screen.queryByLabelText('RayColl Logo Icon')).toBeNull();
  });

  it('routes rail navigation through the active study exit handler', async () => {
    const onRequest = jest.fn();

    await renderShell(<StudyExitGuardProbe onRequest={onRequest} />);

    await waitFor(() => expect(screen.getByLabelText('Study Statistics')).toBeOnTheScreen());
    await fireEvent.press(screen.getByLabelText('Study Statistics'));

    expect(onRequest).toHaveBeenCalledTimes(1);
    expect(global.__expoRouterMock.router.navigate).not.toHaveBeenCalled();

    onRequest.mock.calls[0][0]();

    expect(global.__expoRouterMock.router.navigate).toHaveBeenCalledWith('/stats');
  });

  it('expands the rail and persists the expanded preference', async () => {
    await renderShell();

    await waitFor(() => expect(screen.getByLabelText('Expand navigation')).toBeOnTheScreen());

    await fireEvent.press(screen.getByLabelText('Expand navigation'));

    await waitFor(() => expect(screen.getByLabelText('Collapse navigation')).toBeOnTheScreen());
    expect(AsyncStorage.setItem).toHaveBeenCalledWith(STORAGE_KEYS.NAV_RAIL_EXPANDED, 'true');
  });

  it('renders children directly on compact screens without rail or bottom bar', async () => {
    setResponsiveLayout({
      width: 390,
      windowSizeClass: 'compact',
      contentWidth: 390,
      contentSizeClass: 'compact',
      isCompact: true,
      isExpanded: false,
      isDesktop: false,
      showNavigationRail: false,
      showPersistentNavigation: false,
      navWidth: 0,
    });

    await renderShell();

    await waitFor(() => expect(screen.queryByLabelText('Hide navigation')).toBeNull());
    expect(screen.queryByLabelText('Show navigation')).toBeNull();
    expect(screen.getByText('Dashboard')).toBeOnTheScreen();
    expect(screen.getByTestId('shell-size')).toBeOnTheScreen();
  });
});
