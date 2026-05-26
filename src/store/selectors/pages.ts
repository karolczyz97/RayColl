import { Flashcard, FlashcardGroup } from '../../types/models';

export function getVisiblePages(card: Flashcard, group: FlashcardGroup): string[] {
  const count = group.activePageCount ?? group.pageNames.length;
  const pages = [...card.pages];
  while (pages.length < count) {
    pages.push('');
  }
  return pages.slice(0, count);
}

export function getVisiblePageNames(group: FlashcardGroup): string[] {
  const count = group.activePageCount ?? group.pageNames.length;
  return group.pageNames.slice(0, count);
}

export function getVisiblePageLanguages(group: FlashcardGroup): string[] {
  const count = group.activePageCount ?? group.pageLanguages.length;
  return group.pageLanguages.slice(0, count);
}

export function ensureCardHasPageSlots(card: Flashcard, minPageCount: number): Flashcard {
  const pages = [...card.pages];
  while (pages.length < minPageCount) {
    pages.push('');
  }
  return { ...card, pages };
}

export function normalizeGroupPageConfig(group: FlashcardGroup): FlashcardGroup {
  const activeCount = group.activePageCount ?? group.pageNames.length;
  const names = [...group.pageNames];
  const langs = [...group.pageLanguages];
  while (names.length < activeCount) {
    names.push(`Page ${names.length + 1}`);
  }
  while (langs.length < activeCount) {
    langs.push('en-US');
  }
  return {
    ...group,
    activePageCount: activeCount,
    pageNames: names,
    pageLanguages: langs,
  };
}
