import {
  collection,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  serverTimestamp,
  setDoc,
  writeBatch,
} from 'firebase/firestore';
import type { Flashcard, FlashcardGroup, StudyMode } from '@/types/models';
import { db } from '@/services/firebaseClient';
import { normalizeStoreData } from '@/store/storeDataNormalization';
import {
  FIRESTORE_SCHEMA_VERSION,
  type UserData,
  deserializeActivityDayCount,
  deserializeCardDoc,
  deserializeDeckDoc,
  deserializeStudyModeDoc,
  serializeCardDoc,
  serializeDeckDoc,
  serializeStudyModeDoc,
} from './firestoreSchema';

const MAX_BATCH_OPS = 400;

function getUserRootRef(uid: string) {
  return doc(db!, 'users', uid);
}

function getDeckRef(uid: string, deckId: string) {
  return doc(db!, 'users', uid, 'decks', deckId);
}

function getCardRef(uid: string, deckId: string, cardId: string) {
  return doc(db!, 'users', uid, 'decks', deckId, 'cards', cardId);
}

function getStudyModeRef(uid: string, modeId: string) {
  return doc(db!, 'users', uid, 'studyModes', modeId);
}

function getActivityRef(uid: string, date: string) {
  return doc(db!, 'users', uid, 'activity', date);
}

async function touchUserRoot(uid: string, extra?: Record<string, unknown>) {
  if (!db) {
    return;
  }

  await setDoc(
    getUserRootRef(uid),
    {
      schemaVersion: FIRESTORE_SCHEMA_VERSION,
      updatedAt: serverTimestamp(),
      ...extra,
    },
    { merge: true },
  );
}

async function commitBatchOperations(
  operations: ((batch: ReturnType<typeof writeBatch>) => void)[],
) {
  if (!db || operations.length === 0) {
    return;
  }

  for (let index = 0; index < operations.length; index += MAX_BATCH_OPS) {
    const batch = writeBatch(db);
    const chunk = operations.slice(index, index + MAX_BATCH_OPS);
    chunk.forEach((operation) => operation(batch));
    await batch.commit();
  }
}

export async function loadUserDataV2(uid: string): Promise<UserData | null> {
  if (!db) {
    return null;
  }

  const rootSnap = await getDoc(getUserRootRef(uid));

  if (!rootSnap.exists()) {
    return null;
  }

  const decksSnap = await getDocs(collection(db, 'users', uid, 'decks'));
  const groups = (
    await Promise.all(
      decksSnap.docs.map(async (deckDoc) => {
        const cardsSnap = await getDocs(collection(db!, 'users', uid, 'decks', deckDoc.id, 'cards'));
        // Skip individual corrupt cards rather than failing the whole load.
        const cards = cardsSnap.docs.flatMap((cardDoc) => {
          try {
            return [deserializeCardDoc(cardDoc.id, cardDoc.data())];
          } catch (err) {
            console.warn(`Skipping corrupt Firestore card ${cardDoc.id} in deck ${deckDoc.id}:`, err);
            return [];
          }
        });
        try {
          return deserializeDeckDoc(deckDoc.id, deckDoc.data(), cards);
        } catch (err) {
          console.warn(`Skipping corrupt Firestore deck ${deckDoc.id}:`, err);
          return null;
        }
      }),
    )
  ).filter((group): group is FlashcardGroup => group !== null);

  const studyModesSnap = await getDocs(collection(db, 'users', uid, 'studyModes'));
  const studyModes = studyModesSnap.docs.flatMap((modeDoc) => {
    try {
      return [deserializeStudyModeDoc(modeDoc.id, modeDoc.data())];
    } catch (err) {
      console.warn(`Skipping corrupt Firestore study mode ${modeDoc.id}:`, err);
      return [];
    }
  });

  const activitySnap = await getDocs(collection(db, 'users', uid, 'activity'));
  const activityHeatmap: Record<string, number> = {};
  activitySnap.docs.forEach((dayDoc) => {
    try {
      activityHeatmap[dayDoc.id] = deserializeActivityDayCount(dayDoc.id, dayDoc.data());
    } catch (err) {
      console.warn(`Skipping corrupt Firestore activity day ${dayDoc.id}:`, err);
    }
  });

  return normalizeStoreData({
    groups,
    studyModes,
    activityHeatmap,
  });
}

export async function saveDeck(uid: string, group: FlashcardGroup): Promise<void> {
  if (!db) {
    return;
  }

  await touchUserRoot(uid);
  await setDoc(getDeckRef(uid, group.id), {
    ...serializeDeckDoc(group),
  });
}

export async function deleteDeck(uid: string, group: FlashcardGroup): Promise<void> {
  if (!db) {
    return;
  }

  const operations: ((batch: ReturnType<typeof writeBatch>) => void)[] = [];

  group.cards.forEach((card) => {
    operations.push((batch) => batch.delete(getCardRef(uid, group.id, card.id)));
  });
  operations.push((batch) => batch.delete(getDeckRef(uid, group.id)));

  await commitBatchOperations(operations);
  await touchUserRoot(uid);
}

export async function saveCard(uid: string, groupId: string, card: Flashcard): Promise<void> {
  if (!db) {
    return;
  }

  await touchUserRoot(uid);
  await setDoc(getCardRef(uid, groupId, card.id), {
    ...serializeCardDoc(card),
  });
}

export async function deleteCard(uid: string, groupId: string, cardId: string): Promise<void> {
  if (!db) {
    return;
  }

  await deleteDoc(getCardRef(uid, groupId, cardId));
  await touchUserRoot(uid);
}

export async function saveStudyMode(uid: string, mode: StudyMode): Promise<void> {
  if (!db) {
    return;
  }

  await touchUserRoot(uid);
  await setDoc(getStudyModeRef(uid, mode.id), {
    ...serializeStudyModeDoc(mode),
  });
}

export async function deleteStudyMode(uid: string, modeId: string): Promise<void> {
  if (!db) {
    return;
  }

  await deleteDoc(getStudyModeRef(uid, modeId));
  await touchUserRoot(uid);
}

export async function saveActivityDay(uid: string, date: string, count: number): Promise<void> {
  if (!db) {
    return;
  }

  await touchUserRoot(uid);
  await setDoc(getActivityRef(uid, date), {
    count,
    updatedAt: serverTimestamp(),
  });
}

export async function deleteActivityDay(uid: string, date: string): Promise<void> {
  if (!db) {
    return;
  }

  await deleteDoc(getActivityRef(uid, date));
  await touchUserRoot(uid);
}

export async function saveUserDataV2(
  uid: string,
  data: UserData,
  options?: { legacyBackup?: UserData },
): Promise<void> {
  if (!db) {
    return;
  }

  const existing = (await loadUserDataV2(uid)) ?? {
    groups: [],
    studyModes: [],
    activityHeatmap: {},
  };

  const operations: ((batch: ReturnType<typeof writeBatch>) => void)[] = [];

  operations.push((batch) =>
    batch.set(getUserRootRef(uid), {
      schemaVersion: FIRESTORE_SCHEMA_VERSION,
      updatedAt: serverTimestamp(),
      ...(options?.legacyBackup ? { legacyBackup: options.legacyBackup } : {}),
    }),
  );

  const existingGroups = new Map(existing.groups.map((group) => [group.id, group]));
  data.groups.forEach((group) => {
    operations.push((batch) =>
      batch.set(getDeckRef(uid, group.id), {
        ...serializeDeckDoc(group),
      }),
    );

    const existingCards = new Set(existingGroups.get(group.id)?.cards.map((card) => card.id) ?? []);
    group.cards.forEach((card) => {
      existingCards.delete(card.id);
      operations.push((batch) =>
        batch.set(getCardRef(uid, group.id, card.id), {
          ...serializeCardDoc(card),
        }),
      );
    });

    existingCards.forEach((cardId) => {
      operations.push((batch) => batch.delete(getCardRef(uid, group.id, cardId)));
    });

    existingGroups.delete(group.id);
  });

  existingGroups.forEach((group) => {
    group.cards.forEach((card) => {
      operations.push((batch) => batch.delete(getCardRef(uid, group.id, card.id)));
    });
    operations.push((batch) => batch.delete(getDeckRef(uid, group.id)));
  });

  const existingModes = new Set(existing.studyModes.map((mode) => mode.id));
  data.studyModes.forEach((mode) => {
    existingModes.delete(mode.id);
    operations.push((batch) =>
      batch.set(getStudyModeRef(uid, mode.id), {
        ...serializeStudyModeDoc(mode),
      }),
    );
  });
  existingModes.forEach((modeId) => {
    operations.push((batch) => batch.delete(getStudyModeRef(uid, modeId)));
  });

  const existingActivityKeys = new Set(Object.keys(existing.activityHeatmap));
  Object.entries(data.activityHeatmap).forEach(([date, count]) => {
    existingActivityKeys.delete(date);
    operations.push((batch) =>
      batch.set(getActivityRef(uid, date), {
        count,
        updatedAt: serverTimestamp(),
      }),
    );
  });
  existingActivityKeys.forEach((date) => {
    operations.push((batch) => batch.delete(getActivityRef(uid, date)));
  });

  await commitBatchOperations(operations);
}
