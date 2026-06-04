import type { FlashcardGroup } from '@/types/models';
import { swapElements } from '@/utils/array';

export function reorderDeckPages(
  group: FlashcardGroup,
  index: number,
  target: number,
): FlashcardGroup {
  const nextNames = swapElements(group.pageNames, index, target);
  const nextLanguages = swapElements(group.pageLanguages, index, target);

  const cards = group.cards.map((card) => ({
    ...card,
    pages: swapElements(card.pages, index, target),
  }));

  return {
    ...group,
    pageNames: nextNames,
    pageLanguages: nextLanguages,
    cards,
  };
}
