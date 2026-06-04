import type { StoreData } from '@/types/models';

export function captureSnapshot(
  groupsRef: { current: StoreData['groups'] },
  studyModesRef: { current: StoreData['studyModes'] },
  heatmapRef: { current: StoreData['activityHeatmap'] },
): StoreData {
  return {
    groups: groupsRef.current,
    studyModes: studyModesRef.current,
    activityHeatmap: heatmapRef.current,
  };
}

export async function persistWithRollback(
  applySnapshot: (snapshot: StoreData) => void,
  persistNow: (snapshot: StoreData & { uid: string | null }) => Promise<void>,
  previousSnapshot: StoreData,
  newSnapshot: StoreData,
  uid: string | null,
  label: string,
): Promise<void> {
  try {
    await persistNow({ uid, ...newSnapshot });
  } catch (err) {
    applySnapshot(previousSnapshot);
    try {
      await persistNow({ uid, ...previousSnapshot });
    } catch (rollbackErr) {
      console.error(`${label} rollback persistence failed:`, rollbackErr);
    }
    throw err;
  }
}
