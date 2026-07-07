import type { AtomicStep, StepCondition } from '@/types/models';
import { MAX_PAUSE_MULTIPLIER } from '@/constants/studySteps';
import { clamp } from '@/utils/math';

export type AtomicStepType = AtomicStep['type'];

// Parametry modelu danego typu kroku (bez type/id/condition).
type ParamsOf<T extends AtomicStepType> = Omit<
  Extract<AtomicStep, { type: T }>,
  'type' | 'id' | 'condition'
>;

export type StepCategory = 'display' | 'audio' | 'timing' | 'rating' | 'flow';

interface FieldBase {
  labelKey: string;
  accessibilityLabel: string;
  defaultValue: number;
}

export type StepFieldSpec =
  | (FieldBase & { kind: 'page' })
  | (FieldBase & { kind: 'number'; min: number; max?: number; summaryParam?: string });

export interface StepDefinition<T extends AtomicStepType = AtomicStepType> {
  type: T;
  category: StepCategory;
  labelKey: string;
  summaryKey: string;
  // Mapped type: kompilator wymusza dokładnie jedną spec per parametr modelu.
  fields: { [K in keyof ParamsOf<T> & string]: StepFieldSpec };
}

function defineStep<T extends AtomicStepType>(def: StepDefinition<T>): StepDefinition<T> {
  return def;
}

const PAGE_FIELD: StepFieldSpec = {
  kind: 'page',
  labelKey: 'settings.dialog.add_step.page_idx',
  accessibilityLabel: 'Page number input',
  defaultValue: 0,
};

// Kolejność wpisów = kolejność pickera (pogrupowana kategoriami).
export const ATOMIC_STEP_REGISTRY: readonly StepDefinition[] = [
  defineStep({
    type: 'show_page',
    category: 'display',
    labelKey: 'step.type.show_page',
    summaryKey: 'step.show_page',
    fields: { pageIndex: PAGE_FIELD },
  }),
  defineStep({
    type: 'show_all_pages',
    category: 'display',
    labelKey: 'step.type.show_all_pages',
    summaryKey: 'step.show_all_pages',
    fields: {},
  }),
  defineStep({
    type: 'wait_for_tap_to_reveal_next',
    category: 'display',
    labelKey: 'step.type.wait_for_tap_to_reveal_next',
    summaryKey: 'step.wait_for_tap_to_reveal_next',
    fields: {},
  }),
  defineStep({
    type: 'wait_for_tap_to_reveal',
    category: 'display',
    labelKey: 'step.type.wait_for_tap_to_reveal',
    summaryKey: 'step.wait_for_tap_to_reveal',
    fields: {},
  }),
  defineStep({
    type: 'speak_page',
    category: 'audio',
    labelKey: 'step.type.speak_page',
    summaryKey: 'step.speak_page',
    fields: { pageIndex: PAGE_FIELD },
  }),
  defineStep({
    type: 'speak_all_pages',
    category: 'audio',
    labelKey: 'step.type.speak_all_pages',
    summaryKey: 'step.speak_all_pages',
    fields: {},
  }),
  defineStep({
    type: 'listen_and_check',
    category: 'audio',
    labelKey: 'step.type.listen_and_check',
    summaryKey: 'step.listen_and_check',
    fields: {
      pageIndex: PAGE_FIELD,
      successThreshold: {
        kind: 'number',
        labelKey: 'settings.dialog.add_step.threshold',
        accessibilityLabel: 'Success threshold input',
        defaultValue: 70,
        min: 0,
        max: 100,
        summaryParam: 'threshold',
      },
    },
  }),
  defineStep({
    type: 'feedback_success',
    category: 'audio',
    labelKey: 'step.type.feedback_success',
    summaryKey: 'step.feedback_success',
    fields: {},
  }),
  defineStep({
    type: 'feedback_error',
    category: 'audio',
    labelKey: 'step.type.feedback_error',
    summaryKey: 'step.feedback_error',
    fields: {},
  }),
  defineStep({
    type: 'dynamic_pause',
    category: 'timing',
    labelKey: 'step.type.dynamic_pause',
    summaryKey: 'step.dynamic_pause',
    fields: {
      nextPageIndex: PAGE_FIELD,
      pauseMultiplier: {
        kind: 'number',
        labelKey: 'settings.dialog.add_step.pause_multiplier',
        accessibilityLabel: 'Pause multiplier input',
        defaultValue: 1,
        min: 0,
        max: MAX_PAUSE_MULTIPLIER,
        summaryParam: 'multiplier',
      },
    },
  }),
  defineStep({
    type: 'wait',
    category: 'timing',
    labelKey: 'step.type.wait',
    summaryKey: 'step.wait',
    fields: {
      ms: {
        kind: 'number',
        labelKey: 'settings.dialog.add_step.time',
        accessibilityLabel: 'Duration in milliseconds input',
        defaultValue: 500,
        min: 0,
        summaryParam: 'ms',
      },
    },
  }),
  defineStep({
    type: 'show_ratings',
    category: 'rating',
    labelKey: 'step.type.show_ratings',
    summaryKey: 'step.show_ratings',
    fields: {},
  }),
  defineStep({
    type: 'auto_rate_from_answer',
    category: 'rating',
    labelKey: 'step.type.auto_rate_from_answer',
    summaryKey: 'step.auto_rate_from_answer',
    fields: {},
  }),
  defineStep({
    type: 'auto_rate_fixed',
    category: 'rating',
    labelKey: 'step.type.auto_rate_fixed',
    summaryKey: 'step.auto_rate_fixed',
    fields: {
      rating: {
        kind: 'number',
        labelKey: 'settings.dialog.add_step.rating',
        accessibilityLabel: 'Rating input',
        defaultValue: 3,
        min: 1,
        max: 4,
        summaryParam: 'rating',
      },
    },
  }),
  defineStep({
    type: 'mark_failed',
    category: 'rating',
    labelKey: 'step.type.mark_failed',
    summaryKey: 'step.mark_failed',
    fields: {},
  }),
  defineStep({
    type: 'wait_for_tap',
    category: 'flow',
    labelKey: 'step.type.wait_for_tap',
    summaryKey: 'step.wait_for_tap',
    fields: {},
  }),
  defineStep({
    type: 'next_card',
    category: 'flow',
    labelKey: 'step.type.next_card',
    summaryKey: 'step.next_card',
    fields: {},
  }),
];

export const ATOMIC_STEP_TYPE_ORDER: AtomicStepType[] = ATOMIC_STEP_REGISTRY.map((d) => d.type);

export const ATOMIC_STEP_TYPE_SET: ReadonlySet<AtomicStepType> = new Set(ATOMIC_STEP_TYPE_ORDER);

const REGISTRY_BY_TYPE = new Map<AtomicStepType, StepDefinition>(
  ATOMIC_STEP_REGISTRY.map((def) => [def.type, def]),
);

export function isAtomicStepType(value: unknown): value is AtomicStepType {
  return typeof value === 'string' && ATOMIC_STEP_TYPE_SET.has(value as AtomicStepType);
}

export function getStepDefinition(type: AtomicStepType): StepDefinition {
  const def = REGISTRY_BY_TYPE.get(type);
  if (!def) {
    throw new Error(`Unknown atomic step type: ${type}`);
  }
  return def;
}

function fieldEntries(def: StepDefinition): [string, StepFieldSpec][] {
  return Object.entries(def.fields as Record<string, StepFieldSpec>);
}

function toFiniteInteger(value: unknown, fallback: number): number {
  return typeof value === 'number' && Number.isFinite(value) ? Math.trunc(value) : fallback;
}

function clampFieldValue(spec: StepFieldSpec, raw: unknown, pageCount: number): number {
  const value = toFiniteInteger(raw, spec.defaultValue);
  if (spec.kind === 'page') {
    return clamp(value, 0, Math.max(1, Math.trunc(pageCount)) - 1);
  }
  return clamp(value, spec.min, spec.max ?? Infinity);
}

// Zamiennik buildModeStep: null dla nieznanego typu.
export function buildAtomicStep(
  type: string,
  values: Record<string, number>,
  ctx: { pageCount: number },
  condition: 'always' | StepCondition,
): AtomicStep | null {
  if (!isAtomicStepType(type)) return null;
  const def = getStepDefinition(type);

  const params: Record<string, number> = {};
  for (const [param, spec] of fieldEntries(def)) {
    params[param] = clampFieldValue(spec, values[param], ctx.pageCount);
  }

  // Jedyny cast: bezpieczeństwo gwarantuje mapped type w StepDefinition.fields
  // (dokładnie jedna spec per parametr modelu danego typu).
  const step = { type: def.type, ...params } as AtomicStep;
  return condition === 'always' ? step : { ...step, condition };
}

// Parametry do t(def.summaryKey, params).
export function getStepSummaryParams(step: AtomicStep): Record<string, number> | undefined {
  const def = getStepDefinition(step.type);
  const entries = fieldEntries(def);
  if (entries.length === 0) return undefined;

  const params: Record<string, number> = {};
  for (const [param, spec] of entries) {
    const value = (step as unknown as Record<string, number>)[param];
    if (spec.kind === 'page') {
      params.index = value + 1;
    } else {
      params[spec.summaryParam ?? param] = value;
    }
  }
  return params;
}

// Wartość jedynego pola typu 'page' danego kroku albo null.
export function getStepPageIndex(step: AtomicStep): number | null {
  const def = REGISTRY_BY_TYPE.get(step.type);
  if (!def) return null;
  for (const [param, spec] of fieldEntries(def)) {
    if (spec.kind === 'page') {
      return (step as unknown as Record<string, number>)[param];
    }
  }
  return null;
}

// Clamp jednej wartości liczbowej wg spec z rejestru (używa go normalizacja danych).
export function clampStepNumberField(type: AtomicStepType, param: string, raw: unknown): number {
  const def = getStepDefinition(type);
  const spec = (def.fields as Record<string, StepFieldSpec | undefined>)[param];
  if (!spec || spec.kind !== 'number') {
    throw new Error(`Step type ${type} has no numeric field ${param}`);
  }
  const value = toFiniteInteger(raw, spec.defaultValue);
  return clamp(value, spec.min, spec.max ?? Infinity);
}
