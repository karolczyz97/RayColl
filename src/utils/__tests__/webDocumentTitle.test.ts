import { describe, expect, it } from '@jest/globals';
import type { FlashcardGroup, StudyMode } from '@/types/models';
import { getWebDocumentTitle } from '../webDocumentTitle';

const t = (key: string, replacements?: Record<string, string | number>) => {
  const labels: Record<string, string> = {
    'nav.dashboard': 'Dashboard',
    'route.browse': 'Browse cards',
    'route.study': 'Study',
    'route.deck_settings': 'Deck Settings',
    'route.study_mode_detail': 'Edit study mode',
    'settings.title': 'Deck Settings: {name}',
    'stats.title': 'Stats',
    'archive.title': 'Archive',
    'app_settings.title': 'App Settings',
    'import.title': 'Import Flashcards',
    'study_modes.title': 'Study modes',
  };
  let value = labels[key] ?? key;
  for (const [name, replacement] of Object.entries(replacements ?? {})) {
    value = value.split(`{${name}}`).join(String(replacement));
  }
  return value;
};

const groups = [
  {
    id: 'deck-1',
    name: 'German',
    cards: [],
    activeModeId: 'classic',
    studyFilter: 'all',
    pageLanguages: [],
    pageNames: [],
    activePageCount: 0,
  } satisfies FlashcardGroup,
];

const modes = [
  {
    id: 'custom',
    name: 'Listening',
    steps: [],
    isBuiltIn: false,
  } satisfies StudyMode,
];

describe('getWebDocumentTitle', () => {
  it('uses a readable title for dashboard instead of leaving the URL as the tab label', () => {
    expect(getWebDocumentTitle('/', groups, modes, t)).toBe('Dashboard | RayColl');
  });

  it('uses deck context for dynamic deck routes', () => {
    expect(getWebDocumentTitle('/browse/deck-1', groups, modes, t)).toBe(
      'German - Browse cards | RayColl',
    );
    expect(getWebDocumentTitle('/settings/deck-1', groups, modes, t)).toBe(
      'Deck Settings: German | RayColl',
    );
  });

  it('uses the study mode name for mode detail routes', () => {
    expect(getWebDocumentTitle('/study-modes/custom', groups, modes, t)).toBe(
      'Listening | RayColl',
    );
  });
});
