import { Flashcard } from '@/types/models';
import { CardFilter } from '@/constants/cardFilters';
import { CARD_ORDERS, normalizeCardOrder, type CardOrder } from '@/constants/cardOrder';
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
      return liveCards.filter(
        (c) => c.srsState.state === 0 || c.srsState.nextReviewTimestamp <= now,
      );
  }
}

/** Public store API: semantic alias for filtering live cards by the active study filter. */
export function getDueCards(
  cards: Flashcard[],
  filter: CardFilter,
  order?: CardOrder,
): Flashcard[] {
  return orderCards(filterCards(cards, filter), order);
}

export function orderCards(cards: Flashcard[], order?: CardOrder): Flashcard[] {
  const normalizedOrder = normalizeCardOrder(order);
  if (normalizedOrder === CARD_ORDERS.random) {
    return shuffleCards(cards);
  }

  if (normalizedOrder === CARD_ORDERS.hardest) {
    return cards
      .map((card, index) => ({ card, index }))
      .sort((a, b) => {
        const difficultyDelta = b.card.srsState.difficulty - a.card.srsState.difficulty;
        if (difficultyDelta !== 0) return difficultyDelta;
        const dueDelta = a.card.srsState.nextReviewTimestamp - b.card.srsState.nextReviewTimestamp;
        return dueDelta !== 0 ? dueDelta : a.index - b.index;
      })
      .map((item) => item.card);
  }

  return [...cards];
}

function shuffleCards(cards: Flashcard[]): Flashcard[] {
  const shuffled = [...cards];
  for (let index = shuffled.length - 1; index > 0; index -= 1) {
    const target = Math.floor(Math.random() * (index + 1));
    [shuffled[index], shuffled[target]] = [shuffled[target], shuffled[index]];
  }
  return shuffled;
}

export function getGroupProgress(cards: Flashcard[]): number {
  const liveCards = filterLive(cards);
  if (liveCards.length === 0) return 0;
  const learned = liveCards.filter((c) => c.srsState.repetitions >= 1).length;
  return Math.round((learned / liveCards.length) * 100);
}
