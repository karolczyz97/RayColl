import type { FlashcardGroup, StudyMode } from '@/types/models';
import { APP_NAME } from '@/constants/app';
import { ROUTES, STUDY_MODE_NEW_ID } from '@/constants/routes';
import { getModeName } from '@/i18n/modeHelpers';
import type { TranslationFn } from '@/i18n';

function getRouteId(pathname: string, prefix: string): string | null {
  if (!pathname.startsWith(prefix)) return null;
  const id = pathname.slice(prefix.length).split('/')[0];
  return id ? decodeURIComponent(id) : null;
}

export function getWebDocumentTitle(
  pathname: string,
  groups: FlashcardGroup[],
  studyModes: StudyMode[],
  t: TranslationFn,
): string {
  return getPageTitle(pathname, groups, studyModes, t);
}

function getPageTitle(
  pathname: string,
  groups: FlashcardGroup[],
  studyModes: StudyMode[],
  t: TranslationFn,
): string {
  if (pathname === ROUTES.HOME) return t('nav.dashboard');
  if (pathname === ROUTES.STATS) return t('stats.title');
  if (pathname === ROUTES.ARCHIVE) return t('archive.title');
  if (pathname === ROUTES.APP_SETTINGS) return t('app_settings.title');
  if (pathname === ROUTES.IMPORT) return t('import.title');
  if (pathname === ROUTES.STUDY_MODES) return t('study_modes.title');

  const studyModeId = getRouteId(pathname, `${ROUTES.STUDY_MODES}/`);
  if (studyModeId) {
    if (studyModeId === STUDY_MODE_NEW_ID) return t('settings.create_mode_btn');
    const mode = studyModes.find((item) => item.id === studyModeId);
    return mode ? getModeName(t, mode.id, mode.name) : t('route.study_mode_detail');
  }

  const settingsGroupId = getRouteId(pathname, '/settings/');
  if (settingsGroupId) {
    const group = groups.find((item) => item.id === settingsGroupId);
    return group ? t('settings.title', { name: group.name }) : t('route.deck_settings');
  }

  const studyGroupId = getRouteId(pathname, '/study/');
  if (studyGroupId) {
    const group = groups.find((item) => item.id === studyGroupId);
    return group ? `${group.name} - ${t('route.study')}` : t('route.study');
  }

  const browseGroupId = getRouteId(pathname, '/browse/');
  if (browseGroupId) {
    const group = groups.find((item) => item.id === browseGroupId);
    return group ? `${group.name} - ${t('route.browse')}` : t('route.browse');
  }

  return APP_NAME;
}
