import { FlashcardGroup, Flashcard } from '../../types/models';
import { computeCardStats, CardStats } from '../../components/SegmentedProgressBar';

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
  getDueCards: (groupId: string) => Flashcard[]
): number {
  return groups.reduce((a, g) => a + getDueCards(g.id).length, 0);
}

export function getActiveDaysCount(heatmap: Record<string, number>): number {
  return Object.keys(heatmap).length;
}

export function getGlobalStats(groups: FlashcardGroup[]): CardStats {
  const allCards = groups.flatMap((g) => g.cards);
  return computeCardStats(allCards);
}
