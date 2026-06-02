import { FlashcardGroup, Flashcard } from '../../types/models';
import { createNewSrsState } from '../../srs/srsEngine';
import { uid } from '../../utils/id';
import { recordActivityAction } from './activityActions';

export function addFlashcardAction(
  groups: FlashcardGroup[],
  groupId: string,
  pages: string[],
): { nextGroups: FlashcardGroup[]; newCardId: string } {
  const now = Date.now();
  const newCardId = uid();
  const card: Flashcard = {
    id: newCardId,
    pages,
    srsState: createNewSrsState(),
    contentUpdatedAt: now,
    srsUpdatedAt: 0,
  };
  const nextGroups = groups.map((g) =>
    g.id === groupId ? { ...g, cards: [...g.cards, card] } : g,
  );
  return { nextGroups, newCardId };
}

export function updateFlashcardAction(
  groups: FlashcardGroup[],
  groupId: string,
  card: Flashcard,
): FlashcardGroup[] {
  const now = Date.now();
  return groups.map((g) =>
    g.id === groupId
      ? {
          ...g,
          cards: g.cards.map((c) =>
            c.id === card.id ? { ...card, contentUpdatedAt: now } : c,
          ),
        }
      : g,
  );
}

export function reviewFlashcardAction(
  groups: FlashcardGroup[],
  groupId: string,
  card: Flashcard,
): FlashcardGroup[] {
  const now = Date.now();
  return groups.map((g) =>
    g.id === groupId
      ? {
          ...g,
          cards: g.cards.map((c) =>
            c.id === card.id ? { ...card, srsUpdatedAt: now } : c,
          ),
        }
      : g,
  );
}

/**
 * Atomic review: stamps the card's SRS update time and bumps today's activity heatmap
 * as one unit. The SRS state itself is computed in the study session (needs the rating)
 * and passed in via `card`. Returns `todayKey` so callers/tests can assert the heatmap day.
 * Argument order matches `reviewFlashcardAction(groups, groupId, card)`; heatmap last.
 */
export function reviewCardAction(
  groups: FlashcardGroup[],
  groupId: string,
  card: Flashcard,
  heatmap: Record<string, number>,
): { nextGroups: FlashcardGroup[]; nextHeatmap: Record<string, number>; todayKey: string } {
  const nextGroups = reviewFlashcardAction(groups, groupId, card);
  const { nextHeatmap, todayKey } = recordActivityAction(heatmap);
  return { nextGroups, nextHeatmap, todayKey };
}

export function deleteFlashcardAction(
  groups: FlashcardGroup[],
  groupId: string,
  cardId: string,
): FlashcardGroup[] {
  const now = Date.now();
  return groups.map((g) =>
    g.id === groupId
      ? {
          ...g,
          cards: g.cards.map((c) =>
            c.id === cardId ? { ...c, deletedAt: now } : c,
          ),
        }
      : g,
  );
}

export function addFlashcardsBulkAction(
  groups: FlashcardGroup[],
  groupId: string,
  cards: Flashcard[],
): FlashcardGroup[] {
  const now = Date.now();
  const stampedCards = cards.map((c) => ({
    ...c,
    contentUpdatedAt: c.contentUpdatedAt ?? now,
    srsUpdatedAt: c.srsUpdatedAt ?? 0,
  }));
  return groups.map((g) =>
    g.id === groupId
      ? {
          ...g,
          cards: [...g.cards, ...stampedCards],
        }
      : g,
  );
}
