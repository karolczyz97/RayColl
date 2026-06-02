import type { Href } from 'expo-router';

export const ROUTES = {
  HOME: '/',
  APP_SETTINGS: '/app-settings',
  IMPORT: '/import',
  STATS: '/stats',
  browseDeck: (groupId: string): Href => ({
    pathname: '/browse/[groupId]',
    params: { groupId },
  }),
  deckSettings: (groupId: string): Href => ({
    pathname: '/settings/[groupId]',
    params: { groupId },
  }),
  studyDeck: (groupId: string): Href => ({
    pathname: '/study/[groupId]',
    params: { groupId },
  }),
} as const;
