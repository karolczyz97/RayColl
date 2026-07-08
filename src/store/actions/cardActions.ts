import { FlashcardGroup, Flashcard, SrsState } from '@/types/models';
import { createNewSrsState, calculateFsrs } from '@/srs/srsEngine';
import { uid } from '@/utils/id';
import { getLocalDateString } from '@/utils/date';

export function recordActivityAction(heatmap: Record<string, number>): {
  nextHeatmap: Record<string, number>;
  todayKey: string;
} {
  const todayKey = getLocalDateString(new Date());

  const nextHeatmap = {
    ...heatmap,
    [todayKey]: (heatmap[todayKey] || 0) + 1,
  };

  return { nextHeatmap, todayKey };
}

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

function reviewFlashcardAction(
  groups: FlashcardGroup[],
  groupId: string,
  cardId: string,
  rating: number,
): FlashcardGroup[] {
  const now = Date.now();
  return groups.map((g) =>
    g.id === groupId
      ? {
          ...g,
          cards: g.cards.map((c) =>
            c.id === cardId
              ? { ...c, srsState: calculateFsrs(c.srsState, rating), srsUpdatedAt: now }
              : c,
          ),
        }
      : g,
  );
}

/**
 * Atomic review: recomputes the card's SRS state from the *currently stored* card
 * (identified by id) and stamps `srsUpdatedAt`, bumping today's activity heatmap as
 * one unit. Only `srsState`/`srsUpdatedAt` change, so a stale in-session card snapshot
 * can never clobber freshly edited `pages`/`contentUpdatedAt` or a `deletedAt` flag.
 * Returns `todayKey` so callers/tests can assert the heatmap day.
 */
export function reviewCardAction(
  groups: FlashcardGroup[],
  groupId: string,
  cardId: string,
  rating: number,
  heatmap: Record<string, number>,
): { nextGroups: FlashcardGroup[]; nextHeatmap: Record<string, number>; todayKey: string } {
  const nextGroups = reviewFlashcardAction(groups, groupId, cardId, rating);
  const { nextHeatmap, todayKey } = recordActivityAction(heatmap);
  return { nextGroups, nextHeatmap, todayKey };
}

/**
 * Replacement review ("newest rating wins"): when a card gets re-rated within the
 * same session attempt (user navigated back to it), the new rating must *replace*
 * the earlier one, not stack on top of it. SRS is recomputed from `baseSrsState` —
 * the card's state from before the first rating (session snapshot) — and today's
 * activity heatmap is NOT bumped again (the first review already counted).
 * Like `reviewCardAction`, only `srsState`/`srsUpdatedAt` change on the stored card.
 */
export function reviewCardAgainAction(
  groups: FlashcardGroup[],
  groupId: string,
  cardId: string,
  rating: number,
  baseSrsState: SrsState,
): FlashcardGroup[] {
  const now = Date.now();
  return groups.map((g) =>
    g.id === groupId
      ? {
          ...g,
          cards: g.cards.map((c) =>
            c.id === cardId
              ? { ...c, srsState: calculateFsrs(baseSrsState, rating), srsUpdatedAt: now }
              : c,
          ),
        }
      : g,
  );
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
  const stampedCards = cards.map((c) => ({
    ...c,
    contentUpdatedAt: c.contentUpdatedAt,
    srsUpdatedAt: c.srsUpdatedAt,
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
