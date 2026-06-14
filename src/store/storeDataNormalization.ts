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

// Znane primitive steps. Wszystko spoza tego zbioru (legacy reveal_on_tap,
// listen_and_branch, rate, albo nieznane typy z uszkodzonego backupu) jest
// usuwane przy normalizacji, żeby runner nigdy nie dostał kroku, którego nie zna.
const VALID_STEP_TYPES = new Set<ModeStep['type']>([
  'show_page',
  'show_all_pages',
  'wait_for_tap_to_reveal_next',
  'wait_for_tap_to_reveal',
  'show_ratings',
  'speak_page',
  'dynamic_pause',
  'wait',
  'listen_and_check',
  'feedback_success',
  'feedback_error',
  'auto_rate_from_answer',
  'auto_rate_fixed',
  'mark_failed',
  'next_card',
]);

function clampStepRating(value: unknown): number {
  const n = typeof value === 'number' && Number.isFinite(value) ? Math.trunc(value) : 3;
  return Math.max(1, Math.min(4, n));
}

// `pauseMultiplier` = wielokrotność czasu odsłuchu strony. Legacy `extraPauseMs`
// (stała pauza w ms) oraz brak pola mapują się na domyślne 1× (dynamic_pause zawsze
// trwał 1× odsłuch). Używane już tylko przez dynamic_pause — TTS nie pauzuje.
function normalizePauseMultiplier(step: ModeStep): number {
  const raw = step as { pauseMultiplier?: unknown };
  const multiplier =
    typeof raw.pauseMultiplier === 'number' && Number.isFinite(raw.pauseMultiplier)
      ? raw.pauseMultiplier
      : 1;
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
    // TTS już nie pauzuje — ewentualny legacy pauseMultiplier jest tu usuwany.
    return { ...base, type: 'speak_page', pageIndex: step.pageIndex };
  }
  if (step.type === 'dynamic_pause') {
    return {
      ...base,
      type: 'dynamic_pause',
      nextPageIndex: step.nextPageIndex,
      pauseMultiplier: normalizePauseMultiplier(step),
    };
  }
  if (step.type === 'auto_rate_fixed') {
    return {
      ...base,
      type: 'auto_rate_fixed',
      rating: clampStepRating((step as { rating?: unknown }).rating),
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
    // Zostaw tylko znane primitive steps — legacy/nieznane typy (reveal_on_tap,
    // listen_and_branch, rate, …) są usuwane przy load/import, żeby runner nigdy
    // nie dostał kroku, którego nie rozumie.
    steps: (Array.isArray(mode.steps) ? mode.steps : [])
      .filter((step) =>
        VALID_STEP_TYPES.has((step as { type?: unknown }).type as ModeStep['type']),
      )
      .map(({ id: _id, ...step }) => normalizeModeStep(step as ModeStep)),
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
