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
  AtomicStep,
  SrsState,
  StepCondition,
  StudyMode,
  StoreData,
} from '@/types/models';
import { isBuiltInModeSourceId } from './seed/seedModes';
import { coerceStringArray } from '@/utils/array';
import { isRecord } from '@/utils/types';
import { normalizeCompoundStep } from '@/features/settings/compoundSteps';
import { ATOMIC_STEP_TYPE_ORDER, clampStepNumberField } from '@/features/settings/stepRegistry';

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
    contentUpdatedAt: card.contentUpdatedAt,
    srsUpdatedAt: card.srsUpdatedAt,
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
    updatedAt: group.updatedAt,
    ...(deletedAt != null ? { deletedAt } : {}),
    ...(archivedAt != null ? { archivedAt } : {}),
  };
}

// Known primitive steps. Unknown types are stripped defensively so the runner
// never receives a step it cannot execute.
const VALID_STEP_TYPES = new Set<ModeStep['type']>([...ATOMIC_STEP_TYPE_ORDER, 'compound']);

function clampStepRating(value: unknown): number {
  return clampStepNumberField('auto_rate_fixed', 'rating', value);
}

// `pauseMultiplier` = multiplier for the next page listen duration.
function normalizePauseMultiplier(step: AtomicStep): number {
  return clampStepNumberField(
    'dynamic_pause',
    'pauseMultiplier',
    (step as { pauseMultiplier?: unknown }).pauseMultiplier,
  );
}

function normalizePageIndex(value: unknown): number {
  return typeof value === 'number' && Number.isFinite(value)
    ? Math.max(0, Math.trunc(value))
    : 0;
}

// Nieznane wartości warunku (np. z uszkodzonego backupu) są usuwane.
function normalizeStepCondition(step: AtomicStep): { condition?: StepCondition } {
  const raw = (step as { condition?: unknown }).condition;
  return raw === 'correct' || raw === 'wrong' ? { condition: raw } : {};
}

function normalizeModeStep(step: ModeStep): ModeStep | null {
  if (step.type === 'compound') {
    return normalizeCompoundStep(step);
  }

  const base = normalizeStepCondition(step);
  if (step.type === 'speak_page') {
    return {
      ...base,
      type: 'speak_page',
      pageIndex: normalizePageIndex((step as { pageIndex?: unknown }).pageIndex),
    };
  }
  if (step.type === 'dynamic_pause') {
    return {
      ...base,
      type: 'dynamic_pause',
      nextPageIndex: normalizePageIndex((step as { nextPageIndex?: unknown }).nextPageIndex),
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
  if (step.type === 'listen_and_check') {
    return {
      ...base,
      type: 'listen_and_check',
      pageIndex: normalizePageIndex((step as { pageIndex?: unknown }).pageIndex),
      successThreshold: clampStepNumberField(
        'listen_and_check',
        'successThreshold',
        (step as { successThreshold?: unknown }).successThreshold,
      ),
    };
  }
  if (step.type === 'show_page') {
    return {
      ...base,
      type: 'show_page',
      pageIndex: normalizePageIndex((step as { pageIndex?: unknown }).pageIndex),
    };
  }
  if (step.type === 'wait') {
    return {
      ...base,
      type: 'wait',
      ms: clampStepNumberField('wait', 'ms', (step as { ms?: unknown }).ms),
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
    rawMode.isBuiltIn === true &&
    typeof rawMode.builtInSourceId === 'string' &&
    isBuiltInModeSourceId(rawMode.builtInSourceId)
      ? rawMode.builtInSourceId
      : undefined;
  const isBuiltIn = rawSourceId !== undefined;

  return {
    id: mode.id,
    name: mode.name,
    // Guard against corrupt records where `steps` is not an array; an empty
    // list is normalized rather than crashing the whole load.
    // Step IDs are only used as React keys (with ?? index fallback) — stripping
    // them keeps normalization idempotent so deepEqual across data sources works.
    // Keep only known primitive steps so the runner never receives an unsupported
    // step after boundary validation has accepted the payload.
    steps: (Array.isArray(mode.steps) ? mode.steps : [])
      .filter((step) => VALID_STEP_TYPES.has((step as { type?: unknown }).type as ModeStep['type']))
      .map(({ id: _id, ...step }) => normalizeModeStep(step as ModeStep))
      .filter((step): step is ModeStep => step !== null),
    isBuiltIn,
    ...(rawSourceId ? { builtInSourceId: rawSourceId } : {}),
    updatedAt: mode.updatedAt,
    ...(mode.deletedAt != null ? { deletedAt: mode.deletedAt } : {}),
  };
}

export function normalizeStudyModes(modes: StudyMode[]): StudyMode[] {
  return modes.map(normalizeStudyMode);
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
