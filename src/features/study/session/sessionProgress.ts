import { getCardCategory } from '@/srs/srsEngine';
import type { SrsCardCategory } from '@/srs/srsEngine';
import type { Flashcard } from '@/types/models';

export interface SessionProgressItem {
  id: string;
  category: SrsCardCategory;
}

export interface SessionProgressSegment extends SessionProgressItem {
  state: 'past' | 'current' | 'future';
}

export function buildSessionProgressItems(
  dueCards: Flashcard[],
  groupCards: Flashcard[],
): SessionProgressItem[] {
  const liveCardsById = new Map(groupCards.map((card) => [card.id, card]));

  return dueCards.map((card) => {
    const liveCard = liveCardsById.get(card.id) ?? card;
    return {
      id: card.id,
      category: getCardCategory(liveCard.srsState),
    };
  });
}

export function getSessionProgressSegments(
  items: SessionProgressItem[],
  currentIndex: number,
): SessionProgressSegment[] {
  if (items.length === 0) {
    return [];
  }

  if (currentIndex >= items.length) {
    return items.map((item) => ({ ...item, state: 'past' }));
  }

  const normalizedIndex = Math.max(0, currentIndex);
  return items.map((item, index) => ({
    ...item,
    state: index < normalizedIndex ? 'past' : index === normalizedIndex ? 'current' : 'future',
  }));
}
