import type { Flashcard, FlashcardGroup, StudyMode } from '@/types/models';
import type { UserData } from '@/store/persistence/firestoreSchema';
import { normalizeStoreData } from '@/store/storeDataNormalization';
import { isBuiltInModeSourceId } from '@/store/seed/seedModes';

function latestDelete(aDeletedAt: number | null | undefined, bDeletedAt: number | null | undefined): number {
  return Math.max(aDeletedAt ?? 0, bDeletedAt ?? 0);
}

function isAlive(latestEdit: number, deletedAt: number): boolean {
  return deletedAt <= 0 || latestEdit > deletedAt;
}

export function getLatestEdit(card: Flashcard): number {
  return Math.max(card.contentUpdatedAt ?? 0, card.srsUpdatedAt ?? 0);
}

export function mergeSrsStateNeverRegress(
  localReps: number,
  localSrsAt: number,
  cloudReps: number,
  cloudSrsAt: number,
): 'local' | 'cloud' {
  if (localReps > cloudReps) return 'local';
  if (cloudReps > localReps) return 'cloud';
  return localSrsAt > cloudSrsAt ? 'local' : 'cloud';
}

export function mergeActivityHeatmap(
  local: Record<string, number>,
  cloud: Record<string, number>,
): Record<string, number> {
  const merged = { ...cloud };
  for (const [date, count] of Object.entries(local)) {
    merged[date] = Math.max(merged[date] || 0, count);
  }
  return merged;
}

export function mergeCards(localCards: Flashcard[], cloudCards: Flashcard[]): Flashcard[] {
  const allIds = new Set([
    ...localCards.map((c) => c.id),
    ...cloudCards.map((c) => c.id),
  ]);
  const localMap = new Map(localCards.map((c) => [c.id, c]));
  const cloudMap = new Map(cloudCards.map((c) => [c.id, c]));

  const merged: Flashcard[] = [];

  for (const id of allIds) {
    const local = localMap.get(id);
    const cloud = cloudMap.get(id);

    if (!local && cloud) {
      const latestEdit = getLatestEdit(cloud);
      const delAt = cloud.deletedAt ?? 0;
      if (delAt > 0 && latestEdit > delAt) {
        merged.push({ ...cloud, deletedAt: undefined });
      } else {
        merged.push(cloud);
      }
      continue;
    }
    if (!cloud && local) {
      const latestEdit = getLatestEdit(local);
      const delAt = local.deletedAt ?? 0;
      if (delAt > 0 && latestEdit > delAt) {
        merged.push({ ...local, deletedAt: undefined });
      } else {
        merged.push(local);
      }
      continue;
    }
    const l = local!;
    const c = cloud!;

    const localContentAt = l.contentUpdatedAt ?? 0;
    const cloudContentAt = c.contentUpdatedAt ?? 0;
    const localSrsAt = l.srsUpdatedAt ?? 0;
    const cloudSrsAt = c.srsUpdatedAt ?? 0;
    const localReps = l.srsState.repetitions ?? 0;
    const cloudReps = c.srsState.repetitions ?? 0;

    const deletedAt = latestDelete(l.deletedAt, c.deletedAt);
    const latestEdit = Math.max(getLatestEdit(l), getLatestEdit(c));

    if (!isAlive(latestEdit, deletedAt)) {
      merged.push({ ...c, deletedAt });
      continue;
    }

    const srsSide = mergeSrsStateNeverRegress(localReps, localSrsAt, cloudReps, cloudSrsAt);
    const contentSide = localContentAt > cloudContentAt ? l
      : cloudContentAt > localContentAt ? c
      : srsSide === 'local' ? l : c;

    merged.push({
      id,
      pages: contentSide.pages,
      srsState: srsSide === 'local' ? l.srsState : c.srsState,
      contentUpdatedAt: contentSide.contentUpdatedAt ?? 0,
      srsUpdatedAt: srsSide === 'local' ? (l.srsUpdatedAt ?? 0) : (c.srsUpdatedAt ?? 0),
      deletedAt: deletedAt || undefined,
    });
  }

  return merged;
}

export function mergeGroups(localGroups: FlashcardGroup[], cloudGroups: FlashcardGroup[]): FlashcardGroup[] {
  const allIds = new Set([
    ...localGroups.map((g) => g.id),
    ...cloudGroups.map((g) => g.id),
  ]);
  const localMap = new Map(localGroups.map((g) => [g.id, g]));
  const cloudMap = new Map(cloudGroups.map((g) => [g.id, g]));

  const merged: FlashcardGroup[] = [];

  for (const id of allIds) {
    const local = localMap.get(id);
    const cloud = cloudMap.get(id);

    if (!local) {
      merged.push({
        ...cloud!,
        cards: mergeCards([], cloud!.cards),
      });
      continue;
    }
    if (!cloud) {
      merged.push({
        ...local,
        cards: mergeCards(local.cards, []),
      });
      continue;
    }

    const localUpdatedAt = local.updatedAt ?? 0;
    const cloudUpdatedAt = cloud.updatedAt ?? 0;
    const deletedAt = latestDelete(local.deletedAt, cloud.deletedAt);
    const latestEdit = Math.max(localUpdatedAt, cloudUpdatedAt);

    if (!isAlive(latestEdit, deletedAt)) {
      const mergedCards = mergeCards(local.cards, cloud.cards);
      merged.push({
        ...cloud,
        cards: mergedCards,
        deletedAt,
      });
      continue;
    }

    const winner = localUpdatedAt > cloudUpdatedAt ? local : cloud;
    const mergedCards = mergeCards(local.cards, cloud.cards);

    merged.push({
      ...winner,
      cards: mergedCards,
      updatedAt: latestEdit,
      deletedAt: deletedAt || undefined,
    });
  }

  return merged;
}

export function mergeStudyModes(
  localModes: StudyMode[],
  cloudModes: StudyMode[],
): StudyMode[] {
  const allIds = new Set([
    ...localModes.map((m) => m.id),
    ...cloudModes.map((m) => m.id),
  ]);
  const localMap = new Map(localModes.map((m) => [m.id, m]));
  const cloudMap = new Map(cloudModes.map((m) => [m.id, m]));

  const merged: StudyMode[] = [];

  for (const id of allIds) {
    const local = localMap.get(id);
    const cloud = cloudMap.get(id);

    if (!local) {
      merged.push(cloud!);
      continue;
    }
    if (!cloud) {
      merged.push(local);
      continue;
    }

    const localUpdatedAt = local.updatedAt ?? 0;
    const cloudUpdatedAt = cloud.updatedAt ?? 0;
    const deletedAt = latestDelete(local.deletedAt, cloud.deletedAt);
    const latestEdit = Math.max(localUpdatedAt, cloudUpdatedAt);

    if (!isAlive(latestEdit, deletedAt)) {
      if (isBuiltInModeSourceId(id)) {
        const winner = localUpdatedAt > cloudUpdatedAt ? local : cloud;
        merged.push({ ...winner, updatedAt: latestEdit, deletedAt: undefined });
        continue;
      }
      merged.push({ ...cloud, deletedAt });
      continue;
    }

    const winner = localUpdatedAt > cloudUpdatedAt ? local : cloud;

    merged.push({
      ...winner,
      updatedAt: latestEdit,
      deletedAt: deletedAt || undefined,
    });
  }

  return merged;
}

export function mergeUserData(local: UserData, cloud: UserData): UserData {
  const mergedGroups = mergeGroups(local.groups, cloud.groups);
  const mergedModes = mergeStudyModes(local.studyModes, cloud.studyModes);
  const mergedHeatmap = mergeActivityHeatmap(local.activityHeatmap, cloud.activityHeatmap);

  return normalizeStoreData({
    groups: mergedGroups,
    studyModes: mergedModes,
    activityHeatmap: mergedHeatmap,
  });
}
