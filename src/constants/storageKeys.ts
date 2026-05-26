export const STORAGE_KEYS = {
  // Theme settings
  THEME_PREF: 'td-theme',
  USE_SYSTEM_COLORS: 'td-use-system-colors',
  
  // Speech settings
  TTS_RATE: 'td-tts-rate',
  
  // Language settings
  LANGUAGE: 'td-lang',
  
  // Flashcard store persistence keys
  SEED_VERSION: 'fiszki-seed-ver',
  LOCAL_GROUPS: 'fiszki-local-groups',
  LOCAL_MODES: 'fiszki-local-modes',
  LOCAL_HEATMAP: 'fiszki-local-heatmap',
  
  // User specific namespaces
  USER_GROUPS: (userId: string) => `fiszki-user-groups-${userId}`,
  USER_MODES: (userId: string) => `fiszki-user-modes-${userId}`,
  USER_HEATMAP: (userId: string) => `fiszki-user-heatmap-${userId}`,
};
