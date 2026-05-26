import { StudyMode } from '../../types/models';

export function addStudyModeAction(modes: StudyMode[], mode: StudyMode): StudyMode[] {
  return [...modes, mode];
}

export function updateStudyModeAction(modes: StudyMode[], mode: StudyMode): StudyMode[] {
  return modes.map((m) => (m.id === mode.id ? mode : m));
}

export function deleteStudyModeAction(modes: StudyMode[], modeId: string): StudyMode[] {
  return modes.filter((m) => m.id !== modeId);
}
