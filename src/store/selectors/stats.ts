import { Flashcard, FlashcardGroup } from '@/types/models';
import { getDueCards } from './cardSelectors';
import { getCardCategory } from '@/srs/srsEngine';
import { getLocalDateString } from '@/utils/date';

export const MAX_STREAK_DAYS = 365;

export interface CardStats {
  total: number;
  newCount: number;
  learning: number;
  review: number;
  mastered: number;
}

export function computeStreak(heatmap: Record<string, number>): number {
  const today = new Date();
  let streak = 0;

  const todayKey = getLocalDateString(today);
  const hasToday = Boolean(heatmap[todayKey]);

  if (hasToday) streak++;

  for (let i = 1; i < MAX_STREAK_DAYS; i++) {
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

export function getTotalDueCardsCount(groups: FlashcardGroup[]): number {
  return groups.reduce((a, g) => a + getDueCards(g.cards, g.studyFilter, g.cardOrder).length, 0);
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
