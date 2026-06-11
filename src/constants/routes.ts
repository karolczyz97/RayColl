import type { Href } from 'expo-router';

export const ROUTES = {
  HOME: '/',
  APP_SETTINGS: '/app-settings',
  IMPORT: '/import',
  STATS: '/stats',
  ARCHIVE: '/archive',
  STUDY_MODES: '/study-modes',
  studyMode: (modeId: string): Href => ({
    pathname: '/study-modes/[modeId]',
    params: { modeId },
  }),
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
