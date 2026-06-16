export const STORAGE_KEYS = {
  // Theme settings
  THEME_PREF: 'td-theme',
  USE_SYSTEM_COLORS: 'td-use-system-colors',

  // Speech settings
  TTS_RATE: 'td-tts-rate',

  // Navigation rail (persisted user preference, ≥600px widths)
  NAV_RAIL_EXPANDED: 'td-nav-rail-expanded',

  // Language settings
  LANGUAGE: 'td-lang',

  // Flashcard store persistence keys
  LOCAL_GROUPS: 'fiszki-local-groups',
  LOCAL_MODES: 'fiszki-local-modes',
  LOCAL_HEATMAP: 'fiszki-local-heatmap',

  // User specific namespaces
  USER_GROUPS: (userId: string) => `fiszki-user-groups-${userId}`,
  USER_MODES: (userId: string) => `fiszki-user-modes-${userId}`,
  USER_HEATMAP: (userId: string) => `fiszki-user-heatmap-${userId}`,

  // Update notification
  LAST_SEEN_BUILD: 'td-last-seen-build',
};
