import { Flashcard } from '../../types/models';
import { CardFilter } from '../../constants/cardFilters';

export function filterCards(cards: Flashcard[], filter: CardFilter): Flashcard[] {
  const now = Date.now();
  switch (filter) {
    case 'all':
      return [...cards];
    case 'new':
      return cards.filter((c) => c.srsState.state === 0);
    case 'review':
      return cards.filter((c) => c.srsState.state > 0 && c.srsState.nextReviewTimestamp <= now);
    case 'new+review':
    default:
      return cards.filter((c) => c.srsState.state === 0 || c.srsState.nextReviewTimestamp <= now);
  }
}

export function getDueCards(cards: Flashcard[], filter: CardFilter): Flashcard[] {
  return filterCards(cards, filter);
}
