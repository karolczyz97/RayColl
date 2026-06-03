import { Flashcard, FlashcardGroup } from '../../types/models';
import { padArray } from '../../utils/array';
import { padPageMetadata } from '../storeDataNormalization';

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
  const { pageNames, pageLanguages } = padPageMetadata(
    group.pageNames,
    group.pageLanguages,
    group.activePageCount,
  );
  return {
    ...group,
    activePageCount: group.activePageCount,
    pageNames,
    pageLanguages,
  };
}
