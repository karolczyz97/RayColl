import { FlashcardGroup, StudyMode } from '@/types/models';
import { filterLive } from '@/utils/array';

export function selectLiveStudyModes(modes: StudyMode[]): StudyMode[] {
  return filterLive(modes);
}

export function isArchived(group: FlashcardGroup): boolean {
  return (group.archivedAt ?? 0) > 0;
}

export function selectActiveGroups(groups: FlashcardGroup[]): FlashcardGroup[] {
  return filterLive(groups)
    .filter((g) => !isArchived(g))
    .map((g) => ({ ...g, cards: filterLive(g.cards) }));
}

export function selectArchivedGroups(groups: FlashcardGroup[]): FlashcardGroup[] {
  return filterLive(groups)
    .filter(isArchived)
    .map((g) => ({ ...g, cards: filterLive(g.cards) }))
    .sort((a, b) => (b.archivedAt ?? 0) - (a.archivedAt ?? 0));
}
