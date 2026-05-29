import { StudyMode } from '../../types/models';
import { normalizeStudyMode } from '../storeDataNormalization';

export function addStudyModeAction(modes: StudyMode[], mode: StudyMode): StudyMode[] {
  return [...modes, normalizeStudyMode(mode)];
}

export function updateStudyModeAction(modes: StudyMode[], mode: StudyMode): StudyMode[] {
  const normalizedMode = normalizeStudyMode(mode);
  return modes.map((m) => (m.id === normalizedMode.id ? normalizedMode : m));
}

export function deleteStudyModeAction(modes: StudyMode[], modeId: string): StudyMode[] {
  return modes.filter((m) => m.id !== modeId);
}
