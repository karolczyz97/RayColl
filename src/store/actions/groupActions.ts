import { FlashcardGroup, Flashcard } from '../../types/models';
import { CardFilter } from '../../constants/cardFilters';
import { MIN_PAGE_COUNT, MAX_VISIBLE_PAGE_COUNT } from '../../constants/pages';
import { DEFAULT_STUDY_FILTER } from '../storeDataNormalization';
import { uid } from '../../utils/id';
import { createNewSrsState } from '../../srs/srsEngine';

export function addGroupAction(
  groups: FlashcardGroup[],
  name: string,
  languages: string[],
  pageNames: string[],
): { nextGroups: FlashcardGroup[]; newGroupId: string } {
  const now = Date.now();
  const newGroupId = uid();
  const g: FlashcardGroup = {
    id: newGroupId,
    name: name.trim(),
    cards: [],
    activeModeId: 'classic',
    studyFilter: DEFAULT_STUDY_FILTER,
    pageLanguages: languages,
    pageNames,
    activePageCount: Math.max(MIN_PAGE_COUNT, Math.min(MAX_VISIBLE_PAGE_COUNT, pageNames.length)),
    updatedAt: now,
  };
  return { nextGroups: [...groups, g], newGroupId };
}

export function updateGroupAction(
  groups: FlashcardGroup[],
  group: FlashcardGroup,
): FlashcardGroup[] {
  const now = Date.now();
  return groups.map((g) => {
    if (g.id !== group.id) return g;
    const incomingCardIds = new Set(group.cards.map((c) => c.id));
    const tombstonedCards = g.cards.filter(
      (c) => c.deletedAt != null && !incomingCardIds.has(c.id),
    );
    return {
      ...group,
      updatedAt: now,
      cards: [...group.cards, ...tombstonedCards],
    };
  });
}

export function deleteGroupAction(groups: FlashcardGroup[], groupId: string): FlashcardGroup[] {
  const now = Date.now();
  return groups.map((g) => (g.id === groupId ? { ...g, deletedAt: now, archivedAt: null } : g));
}

export function archiveGroupAction(groups: FlashcardGroup[], groupId: string): FlashcardGroup[] {
  const now = Date.now();
  return groups.map((g) =>
    g.id === groupId ? { ...g, archivedAt: now, updatedAt: now } : g
  );
}

export function restoreGroupAction(groups: FlashcardGroup[], groupId: string): FlashcardGroup[] {
  const now = Date.now();
  return groups.map((g) =>
    g.id === groupId ? { ...g, archivedAt: null, updatedAt: now } : g
  );
}

export function purgeExpiredArchivesAction(
  groups: FlashcardGroup[],
  now: number,
  retentionMs: number,
): { groups: FlashcardGroup[]; changed: boolean } {
  let changed = false;
  const next = groups.map((g) => {
    const archivedAt = g.archivedAt ?? 0;
    const deletedAt = g.deletedAt ?? 0;
    if (archivedAt > 0 && deletedAt <= 0 && now - archivedAt >= retentionMs) {
      changed = true;
      return { ...g, deletedAt: now };
    }
    return g;
  });
  return changed ? { groups: next, changed } : { groups, changed };
}

export function setVisiblePageCountAction(
  groups: FlashcardGroup[],
  groupId: string,
  count: number,
): FlashcardGroup[] {
  const now = Date.now();
  const clampedCount = Math.max(MIN_PAGE_COUNT, Math.min(MAX_VISIBLE_PAGE_COUNT, count));
  return groups.map((g) => {
    if (g.id !== groupId) return g;
    const names = [...g.pageNames];
    const langs = [...g.pageLanguages];
    while (names.length < clampedCount) {
      names.push(`Page ${names.length + 1}`);
    }
    while (langs.length < clampedCount) {
      langs.push('en-US');
    }
    return {
      ...g,
      activePageCount: clampedCount,
      pageNames: names,
      pageLanguages: langs,
      updatedAt: now,
    };
  });
}

export function setStudyFilterAction(
  groups: FlashcardGroup[],
  groupId: string,
  filter: CardFilter,
): FlashcardGroup[] {
  const now = Date.now();
  return groups.map((g) => (g.id === groupId ? { ...g, studyFilter: filter, updatedAt: now } : g));
}

export function setActiveStudyModeAction(
  groups: FlashcardGroup[],
  groupId: string,
  modeId: string,
): FlashcardGroup[] {
  const now = Date.now();
  return groups.map((g) => (g.id === groupId ? { ...g, activeModeId: modeId, updatedAt: now } : g));
}

export function addGroupWithCardsAction(
  groups: FlashcardGroup[],
  name: string,
  languages: string[],
  pageNames: string[],
  cardsData: Omit<Flashcard, 'id' | 'srsState'>[],
): { nextGroups: FlashcardGroup[]; newGroupId: string } {
  const now = Date.now();
  const newGroupId = uid();
  const cards: Flashcard[] = cardsData.map((c) => ({
    id: uid(),
    pages: c.pages,
    srsState: createNewSrsState(),
    contentUpdatedAt: now,
    srsUpdatedAt: 0,
  }));

  const g: FlashcardGroup = {
    id: newGroupId,
    name: name.trim(),
    cards,
    activeModeId: 'classic',
    studyFilter: DEFAULT_STUDY_FILTER,
    pageLanguages: languages,
    pageNames,
    activePageCount: Math.max(MIN_PAGE_COUNT, Math.min(MAX_VISIBLE_PAGE_COUNT, pageNames.length)),
    updatedAt: now,
  };
  return { nextGroups: [...groups, g], newGroupId };
}
