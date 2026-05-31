import { Flashcard, FlashcardGroup } from '../../types/models';
import { padArray } from '../../utils/array';

export function getVisiblePages(card: Flashcard, group: FlashcardGroup): string[] {
  const count = group.activePageCount;
  return padArray([...card.pages], count, '').slice(0, count);
}

export function getVisiblePageNames(group: FlashcardGroup): string[] {
  return group.pageNames.slice(0, group.activePageCount);
}

export function getVisiblePageLanguages(group: FlashcardGroup): string[] {
  return group.pageLanguages.slice(0, group.activePageCount);
}

export function ensureCardHasPageSlots(card: Flashcard, minPageCount: number): Flashcard {
  const pages = padArray([...card.pages], minPageCount, '');
  return { ...card, pages };
}

export function normalizeGroupPageConfig(group: FlashcardGroup): FlashcardGroup {
  const activeCount = group.activePageCount;
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
