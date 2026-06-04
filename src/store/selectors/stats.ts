import { FlashcardGroup, Flashcard } from '@/types/models';
import { getCardCategory } from '@/srs/srsEngine';

export interface CardStats {
  total: number;
  newCount: number;
  learning: number;
  review: number;
  mastered: number;
}

export function getLocalDateString(d: Date): string {
  const year = d.getFullYear();
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

export function computeStreak(heatmap: Record<string, number>): number {
  const today = new Date();
  let streak = 0;
  for (let i = 0; i < 365; i++) {
    const d = new Date(today);
    d.setDate(d.getDate() - i);
    const key = getLocalDateString(d);
    if (heatmap[key]) streak++;
    else break;
  }
  return streak;
}

export function getTotalCardsCount(groups: FlashcardGroup[]): number {
  return groups.reduce((a, g) => a + g.cards.length, 0);
}

export function getTotalDueCardsCount(
  groups: FlashcardGroup[],
  getDueCards: (groupId: string) => Flashcard[],
): number {
  return groups.reduce((a, g) => a + getDueCards(g.id).length, 0);
}

export function getActiveDaysCount(heatmap: Record<string, number>): number {
  return Object.keys(heatmap).length;
}

export function computeCardStats(cards: Flashcard[]): CardStats {
  let newCount = 0;
  let learning = 0;
  let review = 0;
  let mastered = 0;

  for (const card of cards) {
    const category = getCardCategory(card.srsState);
    if (category === 'new') newCount++;
    else if (category === 'learning') learning++;
    else if (category === 'review') review++;
    else if (category === 'mastered') mastered++;
  }

  return { total: cards.length, newCount, learning, review, mastered };
}

export function getGlobalStats(groups: FlashcardGroup[]): CardStats {
  const allCards = groups.flatMap((g) => g.cards);
  return computeCardStats(allCards);
}
