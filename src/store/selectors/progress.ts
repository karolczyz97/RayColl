import { Flashcard } from '../../types/models';

export function getGroupProgress(cards: Flashcard[]): number {
  if (cards.length === 0) return 0;
  const learned = cards.filter((c) => c.srsState.repetitions >= 1).length;
  return Math.round((learned / cards.length) * 100);
}
