import type { Href } from 'expo-router';

export const STUDY_MODE_NEW_ID = 'new';

export const ROUTES = {
  HOME: '/',
  APP_SETTINGS: '/app-settings',
  IMPORT: '/import',
  STATS: '/stats',
  ARCHIVE: '/archive',
  STUDY_MODES: '/study-modes',
  studyMode: (modeId: string, selectForGroup?: string): Href => ({
    pathname: '/study-modes/[modeId]',
    params: selectForGroup
      ? { modeId, selectForGroup }
      : { modeId },
  }),
  createStudyMode: (selectForGroup?: string): Href => ({
    pathname: '/study-modes/[modeId]',
    params: selectForGroup
      ? { modeId: STUDY_MODE_NEW_ID, selectForGroup }
      : { modeId: STUDY_MODE_NEW_ID },
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
