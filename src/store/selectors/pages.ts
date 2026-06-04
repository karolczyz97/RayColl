import { Flashcard, FlashcardGroup } from '@/types/models';
import { padArray } from '@/utils/array';

export function getVisiblePages(card: Flashcard, group: FlashcardGroup): string[] {
  const count = group.activePageCount;
  return padArray([...card.pages], count, '').slice(0, count);
}

export function getVisiblePageNames(group: FlashcardGroup): string[] {
  return group.pageNames.slice(0, group.activePageCount);
}
