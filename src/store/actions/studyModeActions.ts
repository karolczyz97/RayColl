import { StudyMode } from '@/types/models';
import { normalizeStudyMode } from '@/store/storeDataNormalization';

export function addStudyModeAction(modes: StudyMode[], mode: StudyMode): StudyMode[] {
  const now = Date.now();
  return [...modes, normalizeStudyMode({ ...mode, updatedAt: now })];
}

export function updateStudyModeAction(modes: StudyMode[], mode: StudyMode): StudyMode[] {
  const now = Date.now();
  const normalizedMode = normalizeStudyMode({ ...mode, updatedAt: now });
  return modes.map((m) => (m.id === normalizedMode.id ? normalizedMode : m));
}

export function deleteStudyModeAction(modes: StudyMode[], modeId: string): StudyMode[] {
  const now = Date.now();
  return modes.map((m) => (m.id === modeId ? { ...m, deletedAt: now } : m));
}
