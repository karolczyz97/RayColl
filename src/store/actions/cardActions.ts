import { FlashcardGroup, Flashcard } from '../../types/models';
import { createNewSrsState } from '../../srs/srsEngine';
import { uid } from '../../utils/id';

export function addFlashcardAction(
  groups: FlashcardGroup[],
  groupId: string,
  pages: string[],
): { nextGroups: FlashcardGroup[]; newCardId: string } {
  const newCardId = uid();
  const card: Flashcard = {
    id: newCardId,
    pages,
    srsState: createNewSrsState(),
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
  return groups.map((g) =>
    g.id === groupId
      ? {
          ...g,
          cards: g.cards.map((c) => (c.id === card.id ? card : c)),
        }
      : g,
  );
}

export function deleteFlashcardAction(
  groups: FlashcardGroup[],
  groupId: string,
  cardId: string,
): FlashcardGroup[] {
  return groups.map((g) =>
    g.id === groupId
      ? {
          ...g,
          cards: g.cards.filter((c) => c.id !== cardId),
        }
      : g,
  );
}

export function addFlashcardsBulkAction(
  groups: FlashcardGroup[],
  groupId: string,
  cards: Flashcard[],
): FlashcardGroup[] {
  return groups.map((g) =>
    g.id === groupId
      ? {
          ...g,
          cards: [...g.cards, ...cards],
        }
      : g,
  );
}
