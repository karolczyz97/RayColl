import { MAX_PAUSE_MULTIPLIER } from '@/constants/studySteps';
import type {
  AtomicStep,
  CompoundBranch,
  CompoundParams,
  CompoundPause,
  CompoundStep,
  CompoundStepKind,
  ModeStep,
  StepCondition,
} from '@/types/models';
import { clamp } from '@/utils/math';
import { isRecord } from '@/utils/types';

export interface CompoundDefaultContext {
  pageCount?: number;
  defaultQuestionPage?: number;
  defaultAnswerPage?: number;
}

export const COMPOUND_KINDS_ORDER: CompoundStepKind[] = [
  'present_front',
  'flip_reveal',
  'show_all_grade',
  'speak_pause_next',
  'auto_flip',
  'listen_grade',
  'grade_after_listen',
  'auto_pass_next',
  'fail_next',
];

export const COMPOUND_LABEL_KEYS: Record<CompoundStepKind, string> = {
  present_front: 'compound.present_front.name',
  flip_reveal: 'compound.flip_reveal.name',
  show_all_grade: 'compound.show_all_grade.name',
  speak_pause_next: 'compound.speak_pause_next.name',
  auto_flip: 'compound.auto_flip.name',
  listen_grade: 'compound.listen_grade.name',
  grade_after_listen: 'compound.grade_after_listen.name',
  auto_pass_next: 'compound.auto_pass_next.name',
  fail_next: 'compound.fail_next.name',
};

const COMPOUND_KIND_SET = new Set<CompoundStepKind>(COMPOUND_KINDS_ORDER);

export function isCompoundStepKind(value: unknown): value is CompoundStepKind {
  return COMPOUND_KIND_SET.has(value as CompoundStepKind);
}

function finiteInteger(value: unknown, fallback: number): number {
  return typeof value === 'number' && Number.isFinite(value) ? Math.trunc(value) : fallback;
}

function finiteBoolean(value: unknown, fallback: boolean): boolean {
  return typeof value === 'boolean' ? value : fallback;
}

function clampPage(value: unknown, fallback: number): number {
  return Math.max(0, finiteInteger(value, fallback));
}

function clampPageForContext(value: number, pageCount: number): number {
  return clamp(value, 0, Math.max(0, pageCount - 1));
}

function clampMultiplier(value: unknown, fallback: number): number {
  return clamp(finiteInteger(value, fallback), 0, MAX_PAUSE_MULTIPLIER);
}

function clampRating(value: unknown, fallback: number): number {
  return clamp(finiteInteger(value, fallback), 1, 4);
}

function clampThreshold(value: unknown, fallback: number): number {
  return clamp(finiteInteger(value, fallback), 0, 100);
}

function clampMs(value: unknown, fallback: number): number {
  return Math.max(0, finiteInteger(value, fallback));
}

function defaultQuestionPage(ctx?: CompoundDefaultContext): number {
  const pageCount = Math.max(1, finiteInteger(ctx?.pageCount, 2));
  return clampPageForContext(finiteInteger(ctx?.defaultQuestionPage, 0), pageCount);
}

function defaultAnswerPage(ctx?: CompoundDefaultContext): number {
  const pageCount = Math.max(1, finiteInteger(ctx?.pageCount, 2));
  return clampPageForContext(finiteInteger(ctx?.defaultAnswerPage, Math.min(1, pageCount - 1)), pageCount);
}

export function defaultBranch(kind: StepCondition): CompoundBranch {
  return kind === 'correct'
    ? {
        feedback: true,
        speakPage: null,
        pause: null,
        revealAll: true,
        markFailed: false,
        rate: 'fromAnswer',
        fixedRating: 3,
        advance: true,
      }
    : {
        feedback: true,
        speakPage: null,
        pause: null,
        revealAll: true,
        markFailed: true,
        rate: 'fixed',
        fixedRating: 1,
        advance: true,
      };
}

export function defaultCompoundParams(
  kind: CompoundStepKind,
  ctx?: CompoundDefaultContext,
): CompoundParams {
  const questionPage = defaultQuestionPage(ctx);
  const answerPage = defaultAnswerPage(ctx);

  switch (kind) {
    case 'present_front':
      return { kind, page: questionPage, speak: true };
    case 'flip_reveal':
      return { kind, revealStyle: 'all' };
    case 'show_all_grade':
      return { kind };
    case 'speak_pause_next':
      return { kind, page: questionPage, nextPage: answerPage, multiplier: 1 };
    case 'auto_flip':
      return {
        kind,
        questionPage,
        answerPage,
        multiplier: 1,
        speakQuestion: true,
        speakAnswer: false,
      };
    case 'listen_grade':
      return {
        kind,
        answerPage,
        threshold: 70,
        onCorrect: defaultBranch('correct'),
        onWrong: defaultBranch('wrong'),
        manualFallback: true,
      };
    case 'grade_after_listen':
      return {
        kind,
        onCorrect: defaultBranch('correct'),
        onWrong: defaultBranch('wrong'),
        manualFallback: true,
      };
    case 'auto_pass_next':
      return { kind, rating: 3 };
    case 'fail_next':
      return { kind };
  }
}

function normalizePause(value: unknown): CompoundPause | null {
  if (!isRecord(value)) return null;
  if (value.kind === 'fixed') {
    return { kind: 'fixed', ms: clampMs(value.ms, 1000) };
  }
  if (value.kind === 'dynamic') {
    return {
      kind: 'dynamic',
      page: clampPage(value.page, 0),
      multiplier: clampMultiplier(value.multiplier, 1),
    };
  }
  return null;
}

function normalizeRate(value: unknown, fallback: CompoundBranch['rate']): CompoundBranch['rate'] {
  return value === 'fromAnswer' || value === 'fixed' || value === 'none' ? value : fallback;
}

export function normalizeCompoundBranch(
  value: unknown,
  condition: StepCondition,
): CompoundBranch {
  const fallback = defaultBranch(condition);
  if (!isRecord(value)) return fallback;
  const speakPage =
    value.speakPage === null ? null : typeof value.speakPage === 'number' ? clampPage(value.speakPage, 0) : fallback.speakPage;

  return {
    feedback: finiteBoolean(value.feedback, fallback.feedback),
    speakPage,
    pause: normalizePause(value.pause),
    revealAll: finiteBoolean(value.revealAll, fallback.revealAll),
    markFailed: finiteBoolean(value.markFailed, fallback.markFailed),
    rate: normalizeRate(value.rate, fallback.rate),
    fixedRating: clampRating(value.fixedRating, fallback.fixedRating),
    advance: finiteBoolean(value.advance, fallback.advance),
  };
}

export function normalizeCompoundParams(value: unknown): CompoundParams | null {
  if (!isRecord(value) || !isCompoundStepKind(value.kind)) return null;

  switch (value.kind) {
    case 'present_front':
      return {
        kind: value.kind,
        page: clampPage(value.page, 0),
        speak: finiteBoolean(value.speak, true),
      };
    case 'flip_reveal':
      return {
        kind: value.kind,
        revealStyle: value.revealStyle === 'next' ? 'next' : 'all',
      };
    case 'show_all_grade':
      return { kind: value.kind };
    case 'speak_pause_next':
      return {
        kind: value.kind,
        page: clampPage(value.page, 0),
        nextPage: clampPage(value.nextPage, 1),
        multiplier: clampMultiplier(value.multiplier, 1),
      };
    case 'auto_flip':
      return {
        kind: value.kind,
        questionPage: clampPage(value.questionPage, 0),
        answerPage: clampPage(value.answerPage, 1),
        multiplier: clampMultiplier(value.multiplier, 1),
        speakQuestion: finiteBoolean(value.speakQuestion, true),
        speakAnswer: finiteBoolean(value.speakAnswer, false),
      };
    case 'listen_grade':
      return {
        kind: value.kind,
        answerPage: clampPage(value.answerPage, 1),
        threshold: clampThreshold(value.threshold, 70),
        onCorrect: normalizeCompoundBranch(value.onCorrect, 'correct'),
        onWrong: normalizeCompoundBranch(value.onWrong, 'wrong'),
        manualFallback: finiteBoolean(value.manualFallback, true),
      };
    case 'grade_after_listen':
      return {
        kind: value.kind,
        onCorrect: normalizeCompoundBranch(value.onCorrect, 'correct'),
        onWrong: normalizeCompoundBranch(value.onWrong, 'wrong'),
        manualFallback: finiteBoolean(value.manualFallback, true),
      };
    case 'auto_pass_next':
      return { kind: value.kind, rating: clampRating(value.rating, 3) };
    case 'fail_next':
      return { kind: value.kind };
  }
}

export function normalizeCompoundStep(step: unknown): CompoundStep | null {
  if (!isRecord(step) || step.type !== 'compound') return null;
  const params = normalizeCompoundParams(step.params);
  return params ? { type: 'compound', version: 1, params } : null;
}

function conditioned(step: AtomicStep, condition: StepCondition): AtomicStep {
  return { ...step, condition };
}

function expandBranch(
  branch: CompoundBranch,
  condition: StepCondition,
): AtomicStep[] {
  const steps: AtomicStep[] = [];

  if (branch.feedback) {
    steps.push(conditioned({ type: condition === 'correct' ? 'feedback_success' : 'feedback_error' }, condition));
  }
  if (branch.speakPage !== null) {
    steps.push(conditioned({ type: 'speak_page', pageIndex: branch.speakPage }, condition));
  }
  if (branch.pause?.kind === 'fixed') {
    steps.push(conditioned({ type: 'wait', ms: branch.pause.ms }, condition));
  }
  if (branch.pause?.kind === 'dynamic') {
    steps.push(
      conditioned(
        {
          type: 'dynamic_pause',
          nextPageIndex: branch.pause.page,
          pauseMultiplier: branch.pause.multiplier,
        },
        condition,
      ),
    );
  }
  if (branch.revealAll) {
    steps.push(conditioned({ type: 'show_all_pages' }, condition));
  }
  if (branch.markFailed) {
    steps.push(conditioned({ type: 'mark_failed' }, condition));
  }
  if (branch.rate === 'fromAnswer') {
    steps.push(conditioned({ type: 'auto_rate_from_answer' }, condition));
  } else if (branch.rate === 'fixed') {
    steps.push(conditioned({ type: 'auto_rate_fixed', rating: branch.fixedRating }, condition));
  }
  if (branch.advance) {
    steps.push(conditioned({ type: 'next_card' }, condition));
  }

  return steps;
}

function expandGrade(params: Extract<CompoundParams, { kind: 'listen_grade' | 'grade_after_listen' }>): AtomicStep[] {
  const steps: AtomicStep[] = [];

  if (params.kind === 'listen_grade') {
    steps.push({
      type: 'listen_and_check',
      pageIndex: params.answerPage,
      successThreshold: params.threshold,
    });
  }

  steps.push(...expandBranch(params.onCorrect, 'correct'));
  steps.push(...expandBranch(params.onWrong, 'wrong'));

  if (params.manualFallback) {
    steps.push({ type: 'show_all_pages' }, { type: 'show_ratings' });
  }

  return steps;
}

export function expandCompound(step: CompoundStep): AtomicStep[] {
  const params = normalizeCompoundParams(step.params);
  if (!params) return [];

  switch (params.kind) {
    case 'present_front':
      return params.speak
        ? [
            { type: 'show_page', pageIndex: params.page },
            { type: 'speak_page', pageIndex: params.page },
          ]
        : [{ type: 'show_page', pageIndex: params.page }];
    case 'flip_reveal':
      return [
        { type: params.revealStyle === 'next' ? 'wait_for_tap_to_reveal_next' : 'wait_for_tap_to_reveal' },
        { type: 'show_all_pages' },
        { type: 'show_ratings' },
      ];
    case 'show_all_grade':
      return [{ type: 'show_all_pages' }, { type: 'show_ratings' }];
    case 'speak_pause_next':
      return [
        { type: 'speak_page', pageIndex: params.page },
        {
          type: 'dynamic_pause',
          nextPageIndex: params.nextPage,
          pauseMultiplier: params.multiplier,
        },
        { type: 'show_page', pageIndex: params.nextPage },
      ];
    case 'auto_flip': {
      const steps: AtomicStep[] = [{ type: 'show_page', pageIndex: params.questionPage }];
      if (params.speakQuestion) {
        steps.push({ type: 'speak_page', pageIndex: params.questionPage });
      }
      steps.push({
        type: 'dynamic_pause',
        nextPageIndex: params.questionPage,
        pauseMultiplier: params.multiplier,
      });
      steps.push({ type: 'show_all_pages' });
      if (params.speakAnswer) {
        steps.push({ type: 'speak_page', pageIndex: params.answerPage });
      }
      return steps;
    }
    case 'listen_grade':
    case 'grade_after_listen':
      return expandGrade(params);
    case 'auto_pass_next':
      return [{ type: 'auto_rate_fixed', rating: params.rating }, { type: 'next_card' }];
    case 'fail_next':
      return [{ type: 'mark_failed' }, { type: 'auto_rate_fixed', rating: 1 }, { type: 'next_card' }];
  }
}

export function isAtomicStep(step: ModeStep): step is AtomicStep {
  return step.type !== 'compound';
}

export function expandModeSteps(steps: ModeStep[]): AtomicStep[] {
  return steps.flatMap((step) => (isAtomicStep(step) ? [step] : expandCompound(step)));
}

export function expandWithSource(
  steps: ModeStep[],
): { step: AtomicStep; sourceIndex: number }[] {
  return steps.flatMap((step, sourceIndex) =>
    (isAtomicStep(step) ? [step] : expandCompound(step)).map((expanded) => ({
      step: expanded,
      sourceIndex,
    })),
  );
}
