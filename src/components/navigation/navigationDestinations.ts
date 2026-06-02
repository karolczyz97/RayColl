import type { Href } from 'expo-router';

import { ROUTES } from '../../constants/routes';

export type TopLevelDestinationKey = 'dashboard' | 'stats' | 'settings';

export interface NavigationDestination {
  key: TopLevelDestinationKey;
  href: Href;
  labelKey: string;
  focusedIcon: string;
  unfocusedIcon: string;
}

export const NAVIGATION_DESTINATIONS: NavigationDestination[] = [
  {
    key: 'dashboard',
    href: ROUTES.HOME,
    labelKey: 'nav.dashboard',
    focusedIcon: 'view-dashboard',
    unfocusedIcon: 'view-dashboard-outline',
  },
  {
    key: 'stats',
    href: ROUTES.STATS,
    labelKey: 'stats.title',
    focusedIcon: 'chart-bar',
    unfocusedIcon: 'chart-bar',
  },
  {
    key: 'settings',
    href: ROUTES.APP_SETTINGS,
    labelKey: 'app_settings.title',
    focusedIcon: 'cog',
    unfocusedIcon: 'cog-outline',
  },
];

export const IMPORT_ACTION_HREF: Href = ROUTES.IMPORT;

export function getActiveDestination(pathname: string): TopLevelDestinationKey | null {
  if (pathname === ROUTES.STATS || pathname.startsWith(`${ROUTES.STATS}/`)) {
    return 'stats';
  }

  if (pathname === ROUTES.APP_SETTINGS || pathname.startsWith(`${ROUTES.APP_SETTINGS}/`)) {
    return 'settings';
  }

  if (
    pathname === ROUTES.HOME ||
    pathname.startsWith('/browse/') ||
    pathname.startsWith('/settings/') ||
    pathname.startsWith('/study/')
  ) {
    return 'dashboard';
  }

  return null;
}

export function isImmersivePathname(pathname: string): boolean {
  return (
    pathname === ROUTES.IMPORT ||
    pathname.startsWith(`${ROUTES.IMPORT}/`) ||
    pathname.startsWith('/browse/') ||
    pathname.startsWith('/settings/') ||
    pathname.startsWith('/study/')
  );
}
