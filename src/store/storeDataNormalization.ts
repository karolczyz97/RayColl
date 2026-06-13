import { CARD_FILTERS, DEFAULT_STUDY_FILTER, type CardFilter } from '@/constants/cardFilters';
import { DEFAULT_CARD_ORDER, normalizeCardOrder } from '@/constants/cardOrder';
import {
  MIN_PAGE_COUNT,
  MAX_VISIBLE_PAGE_COUNT,
  MAX_STORED_PAGE_COUNT,
  clampActivePageCount,
} from '@/constants/pages';
import type {
  Flashcard,
  FlashcardGroup,
  ModeStep,
  SrsState,
  StepCondition,
  StudyMode,
  StoreData,
} from '@/types/models';
import { createSeedModes, isBuiltInModeSourceId } from './seed/seedModes';
import { coerceStringArray } from '@/utils/array';
import { isRecord } from '@/utils/types';

const VALID_SRS_STATES: ReadonlySet<SrsState['state']> = new Set([0, 1, 2, 3]);

// Canonical definition lives in constants/cardFilters; re-exported here to keep
// existing import sites stable.
export { DEFAULT_STUDY_FILTER };
export { DEFAULT_CARD_ORDER };
const VALID_STUDY_FILTERS = new Set<CardFilter>(Object.values(CARD_FILTERS));

export function normalizeStudyFilter(filter: unknown): CardFilter {
  return VALID_STUDY_FILTERS.has(filter as CardFilter)
    ? (filter as CardFilter)
    : DEFAULT_STUDY_FILTER;
}

/**
 * Pads page names and languages up to `count`, filling gaps with the canonical
 * defaults ("Page N" / "en-US"). Single source of truth for page metadata
 * padding shared by normalization and the setVisiblePageCount action.
 */
export function padPageMetadata(
  pageNames: string[],
  pageLanguages: string[],
  count: number,
): { pageNames: string[]; pageLanguages: string[] } {
  const names = [...pageNames];
  const langs = [...pageLanguages];
  while (names.length < count) {
    names.push(`Page ${names.length + 1}`);
  }
  while (langs.length < count) {
    langs.push('en-US');
  }
  return { pageNames: names, pageLanguages: langs };
}

export function getStoredPageCount(
  group: FlashcardGroup,
  pageNames: string[],
  pageLanguages: string[],
): number {
  const maxCardPages = Math.max(0, ...group.cards.map((c) => c.pages.length));
  const rawStored = Math.max(pageNames.length, pageLanguages.length, maxCardPages, MIN_PAGE_COUNT);
  return Math.min(rawStored, MAX_STORED_PAGE_COUNT);
}

function normalizeCard(card: Flashcard): Flashcard {
  const { deletedAt, ...rest } = card;
  return {
    ...rest,
    srsState: VALID_SRS_STATES.has(card.srsState.state)
      ? card.srsState
      : { ...card.srsState, state: 0 as SrsState['state'] },
    contentUpdatedAt: card.contentUpdatedAt ?? 0,
    srsUpdatedAt: card.srsUpdatedAt ?? 0,
    ...(deletedAt != null ? { deletedAt } : {}),
  };
}

export function normalizeGroup(group: FlashcardGroup): FlashcardGroup {
  const { deletedAt, archivedAt, ...groupBase } = group;
  const rawNames = coerceStringArray((group as { pageNames?: unknown }).pageNames);
  const rawLanguages = coerceStringArray((group as { pageLanguages?: unknown }).pageLanguages);
  const storedPageCount = getStoredPageCount(group, rawNames, rawLanguages);
  const { pageNames, pageLanguages } = padPageMetadata(rawNames, rawLanguages, storedPageCount);

  const rawActivePageCount = (group as { activePageCount?: unknown }).activePageCount;
  const fallback = storedPageCount;
  const candidate =
    typeof rawActivePageCount === 'number' && Number.isFinite(rawActivePageCount)
      ? rawActivePageCount
      : fallback;
  const maxVisible = Math.min(MAX_VISIBLE_PAGE_COUNT, storedPageCount);
  const activePageCount = clampActivePageCount(candidate, maxVisible);

  return {
    ...groupBase,
    activePageCount,
    pageNames,
    pageLanguages,
    cards: group.cards.map(normalizeCard),
    studyFilter: normalizeStudyFilter(group.studyFilter),
    cardOrder: normalizeCardOrder(group.cardOrder),
    updatedAt: (group as { updatedAt?: number }).updatedAt ?? 0,
    ...(deletedAt != null ? { deletedAt } : {}),
    ...(archivedAt != null ? { archivedAt } : {}),
  };
}

export const MAX_PAUSE_MULTIPLIER = 5;

// Migracja kroków z legacy `extraPauseMs` (stała pauza w ms) na `pauseMultiplier`
// (wielokrotność czasu odsłuchu): dawna pauza > 0 → ×1, brak pauzy → ×0
// (dla dynamic_pause brak/0 → ×1, bo sama pauza zawsze trwała 1× odsłuch).
function normalizePauseMultiplier(step: ModeStep, legacyZeroMeans: 0 | 1): number {
  const raw = step as { pauseMultiplier?: unknown; extraPauseMs?: unknown };
  let multiplier: number;
  if (typeof raw.pauseMultiplier === 'number' && Number.isFinite(raw.pauseMultiplier)) {
    multiplier = raw.pauseMultiplier;
  } else if (typeof raw.extraPauseMs === 'number' && Number.isFinite(raw.extraPauseMs)) {
    multiplier = raw.extraPauseMs > 0 ? 1 : legacyZeroMeans;
  } else {
    multiplier = 1;
  }
  return Math.max(0, Math.min(MAX_PAUSE_MULTIPLIER, Math.trunc(multiplier)));
}

// Nieznane wartości warunku (np. z uszkodzonego backupu) są usuwane.
function normalizeStepCondition(step: ModeStep): { condition?: StepCondition } {
  const raw = (step as { condition?: unknown }).condition;
  return raw === 'correct' || raw === 'wrong' ? { condition: raw } : {};
}

function normalizeModeStep(step: ModeStep): ModeStep {
  const base = normalizeStepCondition(step);
  if (step.type === 'speak_page') {
    return {
      ...base,
      type: 'speak_page',
      pageIndex: step.pageIndex,
      pauseMultiplier: normalizePauseMultiplier(step, 0),
    };
  }
  if (step.type === 'dynamic_pause') {
    return {
      ...base,
      type: 'dynamic_pause',
      nextPageIndex: step.nextPageIndex,
      pauseMultiplier: normalizePauseMultiplier(step, 1),
    };
  }
  if ('condition' in step && base.condition === undefined) {
    const { condition: _invalid, ...rest } = step;
    return rest;
  }
  return step;
}

export function normalizeStudyMode(mode: StudyMode): StudyMode {
  const rawMode = mode as StudyMode & {
    isBuiltIn?: unknown;
    builtInSourceId?: unknown;
  };
  const rawSourceId =
    typeof rawMode.builtInSourceId === 'string' && isBuiltInModeSourceId(rawMode.builtInSourceId)
      ? rawMode.builtInSourceId
      : undefined;
  const sourceId = rawSourceId ?? (isBuiltInModeSourceId(mode.id) ? mode.id : undefined);
  const isBuiltIn = sourceId ? true : rawMode.isBuiltIn === true;

  return {
    id: mode.id,
    name: mode.name,
    // Guard against corrupt/legacy records where `steps` isn't an array; an empty
    // list is normalized rather than crashing the whole load.
    // Step IDs are only used as React keys (with ?? index fallback) — stripping
    // them keeps normalization idempotent so deepEqual across data sources works.
    steps: (Array.isArray(mode.steps) ? mode.steps : []).map(({ id: _id, ...step }) =>
      normalizeModeStep(step as ModeStep),
    ),
    isBuiltIn,
    ...(sourceId ? { builtInSourceId: sourceId } : {}),
    updatedAt: (mode as { updatedAt?: number }).updatedAt ?? 0,
    ...(mode.deletedAt != null ? { deletedAt: mode.deletedAt } : {}),
  };
}

export function normalizeStudyModes(modes: StudyMode[]): StudyMode[] {
  const normalizedModes = modes.map(normalizeStudyMode);
  const existingIds = new Set(normalizedModes.map((mode) => mode.id));
  const missingBuiltIns = createSeedModes().filter((mode) => !existingIds.has(mode.id));

  return [...normalizedModes, ...missingBuiltIns];
}

export function normalizeActivityHeatmap(value: unknown): Record<string, number> {
  if (!isRecord(value)) {
    return {};
  }

  return Object.fromEntries(
    Object.entries(value).filter((entry): entry is [string, number] => {
      const [, count] = entry;
      return typeof count === 'number' && Number.isFinite(count);
    }),
  );
}

export function normalizeStoreData(data: StoreData): StoreData {
  return {
    groups: data.groups.map(normalizeGroup),
    studyModes: normalizeStudyModes(data.studyModes),
    activityHeatmap: normalizeActivityHeatmap(data.activityHeatmap),
    ...(data.lastSyncedAt != null ? { lastSyncedAt: data.lastSyncedAt } : {}),
  };
}
