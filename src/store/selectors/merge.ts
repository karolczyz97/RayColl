import { UserData } from '../../services/firebase';

/**
 * Merges local cached user data with cloud backup user data.
 * Merges groups, merges flashcards within groups based on SRS repetition counts,
 * combines custom study modes, and takes the maximum of activity counts per day.
 */
export function mergeUserData(local: UserData, cloud: UserData): UserData {
  const mergedGroups = [...cloud.groups];

  for (const localGroup of local.groups) {
    const cloudGroupIdx = mergedGroups.findIndex((g) => g.id === localGroup.id);
    if (cloudGroupIdx === -1) {
      // Group only exists locally, add it
      mergedGroups.push(localGroup);
    } else {
      // Group exists in both, merge cards
      const cloudGroup = mergedGroups[cloudGroupIdx];
      const mergedCards = [...cloudGroup.cards];

      for (const localCard of localGroup.cards) {
        const cloudCardIdx = mergedCards.findIndex((c) => c.id === localCard.id);
        if (cloudCardIdx === -1) {
          // Card only exists locally, add it
          mergedCards.push(localCard);
        } else {
          // Card exists in both, keep the one with more SRS reviews/repetitions
          const cloudCard = mergedCards[cloudCardIdx];
          const localReps = localCard.srsState.repetitions ?? 0;
          const cloudReps = cloudCard.srsState.repetitions ?? 0;
          if (localReps > cloudReps) {
            mergedCards[cloudCardIdx] = localCard;
          }
        }
      }

      mergedGroups[cloudGroupIdx] = {
        ...cloudGroup,
        cards: mergedCards,
        activeModeId: cloudGroup.activeModeId || localGroup.activeModeId,
        studyFilter: cloudGroup.studyFilter || localGroup.studyFilter,
        activePageCount: cloudGroup.activePageCount ?? localGroup.activePageCount,
      };
    }
  }

  // Merge custom study modes
  const mergedModes = [...cloud.studyModes];
  for (const localMode of local.studyModes) {
    if (!mergedModes.some((m) => m.id === localMode.id)) {
      mergedModes.push(localMode);
    }
  }

  // Merge activity heatmap
  const mergedHeatmap = { ...cloud.activityHeatmap };
  for (const [date, count] of Object.entries(local.activityHeatmap)) {
    mergedHeatmap[date] = Math.max(mergedHeatmap[date] || 0, count);
  }

  return {
    groups: mergedGroups,
    studyModes: mergedModes,
    activityHeatmap: mergedHeatmap,
  };
}
