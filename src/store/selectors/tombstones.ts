import { FlashcardGroup, StudyMode } from '../../types/models';

export function selectLiveGroups(groups: FlashcardGroup[]): FlashcardGroup[] {
  return groups
    .filter((g) => g.deletedAt == null)
    .map((g) => ({
      ...g,
      cards: g.cards.filter((c) => c.deletedAt == null),
    }));
}

export function selectLiveStudyModes(modes: StudyMode[]): StudyMode[] {
  return modes.filter((m) => m.deletedAt == null);
}
