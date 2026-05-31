import { Flashcard } from '../../types/models';
import { filterLive } from '../../utils/array';

export function getGroupProgress(cards: Flashcard[]): number {
  const liveCards = filterLive(cards);
  if (liveCards.length === 0) return 0;
  const learned = liveCards.filter((c) => c.srsState.repetitions >= 1).length;
  return Math.round((learned / liveCards.length) * 100);
}
