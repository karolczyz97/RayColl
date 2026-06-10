import { FlashcardGroup, StudyMode } from '@/types/models';
import { filterLive } from '@/utils/array';

export function selectLiveStudyModes(modes: StudyMode[]): StudyMode[] {
  return filterLive(modes);
}

export function isArchived(group: FlashcardGroup): boolean {
  return (group.archivedAt ?? 0) > 0;
}

// Keeps the group's identity when no card is tombstoned, so a deck untouched by
// an update flows through with a stable reference (GroupCard is memoized on it).
function withLiveCards(group: FlashcardGroup): FlashcardGroup {
  const liveCards = filterLive(group.cards);
  return liveCards === group.cards ? group : { ...group, cards: liveCards };
}

export function selectActiveGroups(groups: FlashcardGroup[]): FlashcardGroup[] {
  return filterLive(groups)
    .filter((g) => !isArchived(g))
    .map(withLiveCards);
}

export function selectArchivedGroups(groups: FlashcardGroup[]): FlashcardGroup[] {
  return filterLive(groups)
    .filter(isArchived)
    .map(withLiveCards)
    .sort((a, b) => (b.archivedAt ?? 0) - (a.archivedAt ?? 0));
}
