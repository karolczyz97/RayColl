import type { FlashcardGroup, StudyMode } from '@/types/models';
import { isRecord } from './types';

export interface BackupData {
  groups: FlashcardGroup[];
  studyModes: StudyMode[];
  activityHeatmap: Record<string, number>;
  exportedAt?: string;
}

function assertNumber(value: unknown, message: string): asserts value is number {
  if (typeof value !== 'number' || Number.isNaN(value)) {
    throw new Error(message);
  }
}

function assertNonNegativeNumber(value: unknown, message: string): asserts value is number {
  assertNumber(value, message);
  if (value < 0) {
    throw new Error(message);
  }
}

export function assertStudyModeStep(step: unknown, modeId: string, index: number): void {
  if (!isRecord(step)) {
    throw new Error(`Step ${index + 1} in study mode ${modeId} must be a valid object.`);
  }

  switch (step.type) {
    case 'show_page':
      assertNonNegativeNumber(step.pageIndex, `Step ${index + 1} in study mode ${modeId} has invalid pageIndex.`);
      return;
    case 'speak_page':
      assertNonNegativeNumber(step.pageIndex, `Step ${index + 1} in study mode ${modeId} has invalid pageIndex.`);
      assertNonNegativeNumber(
        step.extraPauseMs,
        `Step ${index + 1} in study mode ${modeId} has invalid extraPauseMs.`,
      );
      return;
    case 'dynamic_pause':
      assertNonNegativeNumber(
        step.nextPageIndex,
        `Step ${index + 1} in study mode ${modeId} has invalid nextPageIndex.`,
      );
      assertNonNegativeNumber(
        step.extraPauseMs,
        `Step ${index + 1} in study mode ${modeId} has invalid extraPauseMs.`,
      );
      return;
    case 'wait':
      assertNonNegativeNumber(step.ms, `Step ${index + 1} in study mode ${modeId} has invalid ms.`);
      return;
    case 'listen_and_branch':
      assertNonNegativeNumber(step.pageIndex, `Step ${index + 1} in study mode ${modeId} has invalid pageIndex.`);
      assertNonNegativeNumber(
        step.successThreshold,
        `Step ${index + 1} in study mode ${modeId} has invalid successThreshold.`,
      );
      if (step.incorrectTtsPageIndex !== undefined) {
        assertNonNegativeNumber(
          step.incorrectTtsPageIndex,
          `Step ${index + 1} in study mode ${modeId} has invalid incorrectTtsPageIndex.`,
        );
      }
      return;
    case 'reveal_on_tap':
    case 'rate':
      return;
    default:
      throw new Error(`Step ${index + 1} in study mode ${modeId} has an unsupported type.`);
  }
}

export function validateSrsState(cardId: string, groupName: string, srsState: unknown): void {
  if (!isRecord(srsState)) {
    throw new Error(`Card ${cardId} in group ${groupName} is missing a valid "srsState" object.`);
  }
  assertNumber(srsState.difficulty, `Card ${cardId} in group ${groupName} has an invalid difficulty.`);
  assertNumber(srsState.stability, `Card ${cardId} in group ${groupName} has an invalid stability.`);
  assertNumber(srsState.repetitions, `Card ${cardId} in group ${groupName} has invalid repetitions.`);
  assertNumber(srsState.state, `Card ${cardId} in group ${groupName} has invalid state.`);
  assertNumber(
    srsState.lastReviewTimestamp,
    `Card ${cardId} in group ${groupName} has an invalid last review timestamp.`,
  );
  assertNumber(
    srsState.nextReviewTimestamp,
    `Card ${cardId} in group ${groupName} has an invalid next review timestamp.`,
  );
}

export function validateCard(card: unknown, groupName: string): void {
  if (!isRecord(card)) {
    throw new Error(`Card in group ${groupName} is not a valid object.`);
  }
  if (typeof card.id !== 'string') {
    throw new Error(`Card in group ${groupName} is missing a string ID.`);
  }
  if (!Array.isArray(card.pages)) {
    throw new Error(`Card ${card.id} in group ${groupName} must have a "pages" array.`);
  }
  validateSrsState(card.id, groupName, card.srsState);
}

export function validateGroup(group: unknown): void {
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
    validateCard(card, group.name);
  }
}

export function validateStudyMode(mode: unknown): void {
  if (!isRecord(mode)) {
    throw new Error('Each study mode must be a valid object.');
  }
  if (typeof mode.id !== 'string') {
    throw new Error('Each study mode must have a string id.');
  }
  const modeId = mode.id;
  if (typeof mode.name !== 'string') {
    throw new Error(`Study mode with ID ${modeId} must have a name.`);
  }
  if (!Array.isArray(mode.steps)) {
    throw new Error(`Study mode with ID ${modeId} must have a "steps" array.`);
  }
  if (mode.isBuiltIn !== undefined && typeof mode.isBuiltIn !== 'boolean') {
    throw new Error(`Study mode with ID ${modeId} has an invalid isBuiltIn flag.`);
  }
  if (mode.builtInSourceId !== undefined && typeof mode.builtInSourceId !== 'string') {
    throw new Error(`Study mode with ID ${modeId} has an invalid builtInSourceId.`);
  }
  mode.steps.forEach((step, index) => {
    assertStudyModeStep(step, modeId, index);
  });
}

export function validateBackupData(data: unknown): asserts data is BackupData {
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
    validateGroup(group);
  }

  for (const mode of data.studyModes) {
    validateStudyMode(mode);
  }
}
