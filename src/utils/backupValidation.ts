import type { FlashcardGroup, StudyMode } from '@/types/models';
import { normalizeCardOrder } from '@/constants/cardOrder';
import { isCompoundStepKind } from '@/features/settings/compoundSteps';
import { isRecord } from './types';

export interface BackupData {
  groups: FlashcardGroup[];
  studyModes: StudyMode[];
  activityHeatmap: Record<string, number>;
  exportedAt?: string;
}

function assertNumber(value: unknown, message: string): asserts value is number {
  // Number.isFinite rejects NaN and ±Infinity (and non-numbers); a bare NaN check
  // would let Infinity slip through into timestamps/counts.
  if (typeof value !== 'number' || !Number.isFinite(value)) {
    throw new Error(message);
  }
}

function assertNonNegativeNumber(value: unknown, message: string): asserts value is number {
  assertNumber(value, message);
  if (value < 0) {
    throw new Error(message);
  }
}

function assertBoolean(value: unknown, message: string): asserts value is boolean {
  if (typeof value !== 'boolean') {
    throw new Error(message);
  }
}

function assertOptionalPage(value: unknown, message: string): void {
  if (value !== null) {
    assertNonNegativeNumber(value, message);
  }
}

function assertCompoundPause(value: unknown, modeId: string, index: number): void {
  if (value === null) return;
  if (!isRecord(value)) {
    throw new Error(`Step ${index + 1} in study mode ${modeId} has invalid compound pause.`);
  }
  if (value.kind === 'fixed') {
    assertNonNegativeNumber(
      value.ms,
      `Step ${index + 1} in study mode ${modeId} has invalid compound pause.`,
    );
    return;
  }
  if (value.kind === 'dynamic') {
    assertNonNegativeNumber(
      value.page,
      `Step ${index + 1} in study mode ${modeId} has invalid compound pause.`,
    );
    assertNonNegativeNumber(
      value.multiplier,
      `Step ${index + 1} in study mode ${modeId} has invalid compound pause.`,
    );
    return;
  }
  throw new Error(`Step ${index + 1} in study mode ${modeId} has invalid compound pause.`);
}

function assertCompoundBranch(value: unknown, modeId: string, index: number): void {
  if (!isRecord(value)) {
    throw new Error(`Step ${index + 1} in study mode ${modeId} has invalid compound branch.`);
  }
  assertBoolean(
    value.feedback,
    `Step ${index + 1} in study mode ${modeId} has invalid compound branch.`,
  );
  assertOptionalPage(
    value.speakPage,
    `Step ${index + 1} in study mode ${modeId} has invalid compound branch.`,
  );
  assertCompoundPause(value.pause, modeId, index);
  assertBoolean(
    value.revealAll,
    `Step ${index + 1} in study mode ${modeId} has invalid compound branch.`,
  );
  assertBoolean(
    value.markFailed,
    `Step ${index + 1} in study mode ${modeId} has invalid compound branch.`,
  );
  if (value.rate !== 'fromAnswer' && value.rate !== 'fixed' && value.rate !== 'none') {
    throw new Error(`Step ${index + 1} in study mode ${modeId} has invalid compound branch.`);
  }
  assertNonNegativeNumber(
    value.fixedRating,
    `Step ${index + 1} in study mode ${modeId} has invalid compound branch.`,
  );
  assertBoolean(
    value.advance,
    `Step ${index + 1} in study mode ${modeId} has invalid compound branch.`,
  );
}

function assertCompoundParams(params: unknown, modeId: string, index: number): void {
  if (!isRecord(params) || !isCompoundStepKind(params.kind)) {
    throw new Error(`Step ${index + 1} in study mode ${modeId} has invalid compound params.`);
  }

  switch (params.kind) {
    case 'present_front':
      assertNonNegativeNumber(params.page, `Step ${index + 1} in study mode ${modeId} has invalid pageIndex.`);
      assertBoolean(params.speak, `Step ${index + 1} in study mode ${modeId} has invalid compound params.`);
      return;
    case 'flip_reveal':
      if (params.revealStyle !== 'all' && params.revealStyle !== 'next') {
        throw new Error(`Step ${index + 1} in study mode ${modeId} has invalid compound params.`);
      }
      return;
    case 'show_all_grade':
    case 'fail_next':
      return;
    case 'speak_pause_next':
      assertNonNegativeNumber(params.page, `Step ${index + 1} in study mode ${modeId} has invalid pageIndex.`);
      assertNonNegativeNumber(params.nextPage, `Step ${index + 1} in study mode ${modeId} has invalid nextPageIndex.`);
      assertNonNegativeNumber(params.multiplier, `Step ${index + 1} in study mode ${modeId} has invalid pauseMultiplier.`);
      return;
    case 'auto_flip':
      assertNonNegativeNumber(params.questionPage, `Step ${index + 1} in study mode ${modeId} has invalid pageIndex.`);
      assertNonNegativeNumber(params.answerPage, `Step ${index + 1} in study mode ${modeId} has invalid pageIndex.`);
      assertNonNegativeNumber(params.multiplier, `Step ${index + 1} in study mode ${modeId} has invalid pauseMultiplier.`);
      assertBoolean(params.speakQuestion, `Step ${index + 1} in study mode ${modeId} has invalid compound params.`);
      assertBoolean(params.speakAnswer, `Step ${index + 1} in study mode ${modeId} has invalid compound params.`);
      return;
    case 'listen_grade':
      assertNonNegativeNumber(params.answerPage, `Step ${index + 1} in study mode ${modeId} has invalid pageIndex.`);
      assertNonNegativeNumber(params.threshold, `Step ${index + 1} in study mode ${modeId} has invalid successThreshold.`);
      if (params.threshold > 100) {
        throw new Error(`Step ${index + 1} in study mode ${modeId} has invalid successThreshold.`);
      }
      assertCompoundBranch(params.onCorrect, modeId, index);
      assertCompoundBranch(params.onWrong, modeId, index);
      assertBoolean(params.manualFallback, `Step ${index + 1} in study mode ${modeId} has invalid compound params.`);
      return;
    case 'grade_after_listen':
      assertCompoundBranch(params.onCorrect, modeId, index);
      assertCompoundBranch(params.onWrong, modeId, index);
      assertBoolean(params.manualFallback, `Step ${index + 1} in study mode ${modeId} has invalid compound params.`);
      return;
    case 'auto_pass_next':
      assertNonNegativeNumber(params.rating, `Step ${index + 1} in study mode ${modeId} has invalid rating.`);
      return;
  }
}

export function assertStudyModeStep(step: unknown, modeId: string, index: number): void {
  if (!isRecord(step)) {
    throw new Error(`Step ${index + 1} in study mode ${modeId} must be a valid object.`);
  }

  switch (step.type) {
    case 'show_page':
      assertNonNegativeNumber(
        step.pageIndex,
        `Step ${index + 1} in study mode ${modeId} has invalid pageIndex.`,
      );
      return;
    case 'speak_page':
      assertNonNegativeNumber(
        step.pageIndex,
        `Step ${index + 1} in study mode ${modeId} has invalid pageIndex.`,
      );
      // Legacy pola (pauseMultiplier/extraPauseMs) bywają w starych backupach —
      // akceptujemy i ignorujemy przy normalizacji (TTS już nie pauzuje).
      if (step.pauseMultiplier !== undefined) {
        assertNonNegativeNumber(
          step.pauseMultiplier,
          `Step ${index + 1} in study mode ${modeId} has invalid pauseMultiplier.`,
        );
      }
      if (step.extraPauseMs !== undefined) {
        assertNonNegativeNumber(
          step.extraPauseMs,
          `Step ${index + 1} in study mode ${modeId} has invalid extraPauseMs.`,
        );
      }
      return;
    case 'dynamic_pause':
      assertNonNegativeNumber(
        step.nextPageIndex,
        `Step ${index + 1} in study mode ${modeId} has invalid nextPageIndex.`,
      );
      if (step.pauseMultiplier !== undefined) {
        assertNonNegativeNumber(
          step.pauseMultiplier,
          `Step ${index + 1} in study mode ${modeId} has invalid pauseMultiplier.`,
        );
      }
      if (step.extraPauseMs !== undefined) {
        assertNonNegativeNumber(
          step.extraPauseMs,
          `Step ${index + 1} in study mode ${modeId} has invalid extraPauseMs.`,
        );
      }
      return;
    case 'wait':
      assertNonNegativeNumber(step.ms, `Step ${index + 1} in study mode ${modeId} has invalid ms.`);
      return;
    case 'listen_and_check':
      assertNonNegativeNumber(
        step.pageIndex,
        `Step ${index + 1} in study mode ${modeId} has invalid pageIndex.`,
      );
      assertNonNegativeNumber(
        step.successThreshold,
        `Step ${index + 1} in study mode ${modeId} has invalid successThreshold.`,
      );
      if (step.successThreshold > 100) {
        throw new Error(`Step ${index + 1} in study mode ${modeId} has invalid successThreshold.`);
      }
      return;
    case 'auto_rate_fixed':
      assertNonNegativeNumber(
        step.rating,
        `Step ${index + 1} in study mode ${modeId} has invalid rating.`,
      );
      return;
    case 'compound':
      if (step.version !== undefined) {
        assertNumber(
          step.version,
          `Step ${index + 1} in study mode ${modeId} has invalid compound version.`,
        );
      }
      assertCompoundParams(step.params, modeId, index);
      return;
    // Primitive steps bez parametrów.
    case 'show_all_pages':
    case 'wait_for_tap_to_reveal_next':
    case 'wait_for_tap_to_reveal':
    case 'show_ratings':
    case 'feedback_success':
    case 'feedback_error':
    case 'auto_rate_from_answer':
    case 'mark_failed':
    case 'next_card':
      return;
    // Legacy typy (reveal_on_tap, listen_and_branch, rate) — wciąż akceptowane,
    // żeby stare backupy się walidowały; normalizacja usuwa je przy imporcie.
    case 'reveal_on_tap':
    case 'listen_and_branch':
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
  assertNumber(
    srsState.difficulty,
    `Card ${cardId} in group ${groupName} has an invalid difficulty.`,
  );
  assertNumber(
    srsState.stability,
    `Card ${cardId} in group ${groupName} has an invalid stability.`,
  );
  assertNumber(
    srsState.repetitions,
    `Card ${cardId} in group ${groupName} has invalid repetitions.`,
  );
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
  if (!card.pages.every((page) => typeof page === 'string')) {
    throw new Error(`Card ${card.id} in group ${groupName} must have string pages.`);
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
  if (group.cardOrder !== undefined && normalizeCardOrder(group.cardOrder) !== group.cardOrder) {
    throw new Error(`Group with ID ${group.id} has an invalid card order.`);
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
    assertNonNegativeNumber(count, `Activity heatmap entry ${date} must be a non-negative number.`);
  }

  for (const group of data.groups) {
    validateGroup(group);
  }

  for (const mode of data.studyModes) {
    validateStudyMode(mode);
  }
}
