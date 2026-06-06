import type { Href } from 'expo-router';

import { ROUTES } from '@/constants/routes';

export type TopLevelDestinationKey = 'dashboard' | 'stats' | 'archive' | 'settings';

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
    key: 'archive',
    href: ROUTES.ARCHIVE,
    labelKey: 'archive.title',
    focusedIcon: 'archive',
    unfocusedIcon: 'archive-outline',
  },
  {
    key: 'settings',
    href: ROUTES.APP_SETTINGS,
    labelKey: 'app_settings.title',
    focusedIcon: 'cog',
    unfocusedIcon: 'cog-outline',
  },
];

export function getActiveDestination(pathname: string): TopLevelDestinationKey | null {
  if (pathname === ROUTES.STATS || pathname.startsWith(`${ROUTES.STATS}/`)) {
    return 'stats';
  }

  if (pathname === ROUTES.ARCHIVE || pathname.startsWith(`${ROUTES.ARCHIVE}/`)) {
    return 'archive';
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
