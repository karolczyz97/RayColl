import { FlashcardGroup, StudyMode } from '../../types/models';
import { filterLive } from '../../utils/array';

export function selectLiveGroups(groups: FlashcardGroup[]): FlashcardGroup[] {
  return filterLive(groups).map((g) => ({
    ...g,
    cards: filterLive(g.cards),
  }));
}

export function selectLiveStudyModes(modes: StudyMode[]): StudyMode[] {
  return filterLive(modes);
}
