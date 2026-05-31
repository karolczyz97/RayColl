import { Flashcard } from '../../types/models';

export function getGroupProgress(cards: Flashcard[]): number {
  const liveCards = cards.filter((c) => c.deletedAt == null);
  if (liveCards.length === 0) return 0;
  const learned = liveCards.filter((c) => c.srsState.repetitions >= 1).length;
  return Math.round((learned / liveCards.length) * 100);
}
