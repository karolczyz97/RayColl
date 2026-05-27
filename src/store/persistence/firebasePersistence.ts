import { saveUserData, loadUserData, UserData } from '../../services/firebase';
import { validateBackupData } from '../../utils/backupValidation';
import { cloneUserData } from './firestoreSchema';
import {
  deleteActivityDay,
  deleteCard,
  deleteDeck,
  deleteStudyMode,
  saveActivityDay,
  saveCard,
  saveDeck,
  saveStudyMode,
} from './firestoreV2Persistence';

const cloudSnapshotCache = new Map<string, UserData>();

function isEqual(a: unknown, b: unknown): boolean {
  return JSON.stringify(a) === JSON.stringify(b);
}

export async function saveCloudData(userId: string, data: UserData): Promise<void> {
  try {
    validateBackupData(data);
    const previous = cloudSnapshotCache.get(userId);

    if (!previous) {
      await saveUserData(userId, data);
      cloudSnapshotCache.set(userId, cloneUserData(data));
      return;
    }

    const previousGroups = new Map(previous.groups.map((group) => [group.id, group]));
    for (const group of data.groups) {
      const previousGroup = previousGroups.get(group.id);
      previousGroups.delete(group.id);

      if (!previousGroup || !isEqual({ ...previousGroup, cards: [] }, { ...group, cards: [] })) {
        await saveDeck(userId, group);
      }

      const previousCards = new Map(previousGroup?.cards.map((card) => [card.id, card]) ?? []);
      for (const card of group.cards) {
        const previousCard = previousCards.get(card.id);
        previousCards.delete(card.id);

        if (!previousCard || !isEqual(previousCard, card)) {
          await saveCard(userId, group.id, card);
        }
      }

      for (const removedCardId of previousCards.keys()) {
        await deleteCard(userId, group.id, removedCardId);
      }
    }

    for (const removedGroup of previousGroups.values()) {
      await deleteDeck(userId, removedGroup);
    }

    const previousModes = new Map(previous.studyModes.map((mode) => [mode.id, mode]));
    for (const mode of data.studyModes) {
      const previousMode = previousModes.get(mode.id);
      previousModes.delete(mode.id);

      if (!previousMode || !isEqual(previousMode, mode)) {
        await saveStudyMode(userId, mode);
      }
    }

    for (const removedModeId of previousModes.keys()) {
      await deleteStudyMode(userId, removedModeId);
    }

    const previousActivity = new Map(Object.entries(previous.activityHeatmap));
    for (const [date, count] of Object.entries(data.activityHeatmap)) {
      const previousCount = previousActivity.get(date);
      previousActivity.delete(date);

      if (previousCount !== count) {
        await saveActivityDay(userId, date, count);
      }
    }

    for (const removedDate of previousActivity.keys()) {
      await deleteActivityDay(userId, removedDate);
    }

    cloudSnapshotCache.set(userId, cloneUserData(data));
  } catch (err) {
    console.error('Failed to save cloud data:', err);
    throw err;
  }
}

export async function loadCloudData(userId: string): Promise<UserData | null> {
  try {
    const data = await loadUserData(userId);
    if (!data) return null;
    validateBackupData(data);
    cloudSnapshotCache.set(userId, cloneUserData(data));
    return data;
  } catch (err) {
    console.error('Failed to load cloud data:', err);
    return null;
  }
}
