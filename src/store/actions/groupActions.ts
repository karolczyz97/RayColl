import { FlashcardGroup, Flashcard } from '../../types/models';
import { CardFilter } from '../../constants/cardFilters';
import { DEFAULT_STUDY_FILTER } from '../storeDataNormalization';
import { uid } from '../../utils/id';
import { createNewSrsState } from '../../srs/srsEngine';

export function addGroupAction(
  groups: FlashcardGroup[],
  name: string,
  languages: string[],
  pageNames: string[],
): { nextGroups: FlashcardGroup[]; newGroupId: string } {
  const newGroupId = uid();
  const g: FlashcardGroup = {
    id: newGroupId,
    name: name.trim(),
    cards: [],
    activeModeId: 'classic',
    studyFilter: DEFAULT_STUDY_FILTER,
    pageLanguages: languages,
    pageNames,
    activePageCount: pageNames.length,
  };
  return { nextGroups: [...groups, g], newGroupId };
}

export function updateGroupAction(
  groups: FlashcardGroup[],
  group: FlashcardGroup,
): FlashcardGroup[] {
  return groups.map((g) => (g.id === group.id ? group : g));
}

export function deleteGroupAction(groups: FlashcardGroup[], groupId: string): FlashcardGroup[] {
  return groups.filter((g) => g.id !== groupId);
}

export function setVisiblePageCountAction(
  groups: FlashcardGroup[],
  groupId: string,
  count: number,
): FlashcardGroup[] {
  return groups.map((g) => {
    if (g.id !== groupId) return g;
    const names = [...g.pageNames];
    const langs = [...g.pageLanguages];
    while (names.length < count) {
      names.push(`Page ${names.length + 1}`);
    }
    while (langs.length < count) {
      langs.push('en-US');
    }
    return {
      ...g,
      activePageCount: count,
      pageNames: names,
      pageLanguages: langs,
    };
  });
}

export function setStudyFilterAction(
  groups: FlashcardGroup[],
  groupId: string,
  filter: CardFilter,
): FlashcardGroup[] {
  return groups.map((g) => (g.id === groupId ? { ...g, studyFilter: filter } : g));
}

export function setActiveStudyModeAction(
  groups: FlashcardGroup[],
  groupId: string,
  modeId: string,
): FlashcardGroup[] {
  return groups.map((g) => (g.id === groupId ? { ...g, activeModeId: modeId } : g));
}

export function addGroupWithCardsAction(
  groups: FlashcardGroup[],
  name: string,
  languages: string[],
  pageNames: string[],
  cardsData: Omit<Flashcard, 'id' | 'srsState'>[],
): { nextGroups: FlashcardGroup[]; newGroupId: string } {
  const newGroupId = uid();
  const cards: Flashcard[] = cardsData.map((c) => ({
    id: uid(),
    pages: c.pages,
    srsState: createNewSrsState(),
  }));

  const g: FlashcardGroup = {
    id: newGroupId,
    name: name.trim(),
    cards,
    activeModeId: 'classic',
    studyFilter: DEFAULT_STUDY_FILTER,
    pageLanguages: languages,
    pageNames,
    activePageCount: pageNames.length,
  };
  return { nextGroups: [...groups, g], newGroupId };
}
