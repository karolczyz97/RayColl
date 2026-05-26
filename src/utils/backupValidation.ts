import type { FlashcardGroup, StudyMode } from '../types/models';

export interface BackupData {
  groups: FlashcardGroup[];
  studyModes: StudyMode[];
  activityHeatmap: Record<string, number>;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === 'object' && !Array.isArray(value);
}

function assertNumber(value: unknown, message: string): asserts value is number {
  if (typeof value !== 'number' || Number.isNaN(value)) {
    throw new Error(message);
  }
}

export function validateBackupData(data: unknown): data is BackupData {
  if (!isRecord(data)) {
    throw new Error('Backup data is not a valid JSON object.');
  }
  if (!Array.isArray(data.groups)) {
    throw new Error('Backup data must contain a "groups" array.');
  }
  if (!Array.isArray(data.studyModes)) {
    throw new Error('Backup data must contain a "studyModes" array.');
  }
  if (!isRecord(data.activityHeatmap)) {
    throw new Error('Backup data must contain an "activityHeatmap" object.');
  }

  for (const [date, count] of Object.entries(data.activityHeatmap)) {
    assertNumber(count, `Activity heatmap entry ${date} must be a number.`);
  }

  for (const group of data.groups) {
    if (!isRecord(group)) {
      throw new Error('Each group must be a valid object.');
    }
    if (typeof group.id !== 'string') {
      throw new Error('Each group must have a string id.');
    }
    if (typeof group.name !== 'string') {
      throw new Error(`Group with ID ${group.id} must have a name.`);
    }
    if (!Array.isArray(group.cards)) {
      throw new Error(`Group with ID ${group.id} must contain a "cards" array.`);
    }
    if (!Array.isArray(group.pageLanguages)) {
      throw new Error(`Group with ID ${group.id} must contain a "pageLanguages" array.`);
    }
    if (!Array.isArray(group.pageNames)) {
      throw new Error(`Group with ID ${group.id} must contain a "pageNames" array.`);
    }
    if (typeof group.activeModeId !== 'string') {
      throw new Error(`Group with ID ${group.id} must have an active study mode.`);
    }
    for (const card of group.cards) {
      if (!isRecord(card)) {
        throw new Error(`Card in group ${group.name} is not a valid object.`);
      }
      if (typeof card.id !== 'string') {
        throw new Error(`Card in group ${group.name} is missing a string ID.`);
      }
      if (!Array.isArray(card.pages)) {
        throw new Error(`Card ${card.id} in group ${group.name} must have a "pages" array.`);
      }
      if (!isRecord(card.srsState)) {
        throw new Error(
          `Card ${card.id} in group ${group.name} is missing a valid "srsState" object.`,
        );
      }
      assertNumber(
        card.srsState.difficulty,
        `Card ${card.id} in group ${group.name} has an invalid difficulty.`,
      );
      assertNumber(
        card.srsState.stability,
        `Card ${card.id} in group ${group.name} has an invalid stability.`,
      );
      assertNumber(
        card.srsState.repetitions,
        `Card ${card.id} in group ${group.name} has invalid repetitions.`,
      );
      assertNumber(
        card.srsState.state,
        `Card ${card.id} in group ${group.name} has invalid state.`,
      );
      assertNumber(
        card.srsState.lastReviewTimestamp,
        `Card ${card.id} in group ${group.name} has an invalid last review timestamp.`,
      );
      assertNumber(
        card.srsState.nextReviewTimestamp,
        `Card ${card.id} in group ${group.name} has an invalid next review timestamp.`,
      );
    }
  }

  for (const mode of data.studyModes) {
    if (!isRecord(mode)) {
      throw new Error('Each study mode must be a valid object.');
    }
    if (typeof mode.id !== 'string') {
      throw new Error('Each study mode must have a string id.');
    }
    if (typeof mode.name !== 'string') {
      throw new Error(`Study mode with ID ${mode.id} must have a name.`);
    }
    if (!Array.isArray(mode.steps)) {
      throw new Error(`Study mode with ID ${mode.id} must have a "steps" array.`);
    }
  }

  return true;
}
