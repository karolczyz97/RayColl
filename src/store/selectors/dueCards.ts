import { Flashcard } from '@/types/models';
import { CardFilter } from '@/constants/cardFilters';
import { filterLive } from '@/utils/array';

export function filterCards(cards: Flashcard[], filter: CardFilter): Flashcard[] {
  const liveCards = filterLive(cards);
  const now = Date.now();
  switch (filter) {
    case 'all':
      return [...liveCards];
    case 'new':
      return liveCards.filter((c) => c.srsState.state === 0);
    case 'review':
      return liveCards.filter((c) => c.srsState.state > 0 && c.srsState.nextReviewTimestamp <= now);
    case 'new+review':
    default:
      return liveCards.filter((c) => c.srsState.state === 0 || c.srsState.nextReviewTimestamp <= now);
  }
}

export function getDueCards(cards: Flashcard[], filter: CardFilter): Flashcard[] {
  return filterCards(cards, filter);
}
