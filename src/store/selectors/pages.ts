import { Flashcard, FlashcardGroup } from '@/types/models';
import { padArray } from '@/utils/array';

export function getVisiblePages(card: Flashcard, group: FlashcardGroup): string[] {
  const count = group.activePageCount;
  // padArray copies before padding and the result is sliced, so card.pages is never
  // mutated — no defensive spread needed.
  return padArray(card.pages, count, '').slice(0, count);
}

export function getVisiblePageNames(group: FlashcardGroup): string[] {
  return group.pageNames.slice(0, group.activePageCount);
}
