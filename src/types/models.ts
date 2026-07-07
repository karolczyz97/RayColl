import type { CardFilter } from '@/constants/cardFilters';
import type { CardOrder } from '@/constants/cardOrder';

export interface SrsState {
  difficulty: number;
  stability: number;
  repetitions: number;
  state: 0 | 1 | 2 | 3;
  lastReviewTimestamp: number;
  nextReviewTimestamp: number;
}

export interface Flashcard {
  id: string;
  pages: string[];
  srsState: SrsState;
  contentUpdatedAt: number;
  srsUpdatedAt: number;
  deletedAt?: number | null;
}

export interface FlashcardGroup {
  id: string;
  name: string;
  cards: Flashcard[];
  activeModeId: string;
  studyFilter: CardFilter;
  cardOrder: CardOrder;
  pageLanguages: string[];
  pageNames: string[];
  activePageCount: number;
  updatedAt: number;
  deletedAt?: number | null;
  archivedAt?: number | null;
}

export type StepCondition = 'correct' | 'wrong';

interface StepBase {
  id?: string;
}

interface AtomicStepBase extends StepBase {
  // A conditional atomic step runs only after a matching listen_and_check result.
  condition?: StepCondition;
}

export type AtomicStep = AtomicStepBase &
  (
    | { type: 'show_page'; pageIndex: number }
    | { type: 'show_all_pages' }
    | { type: 'wait_for_tap_to_reveal_next' }
    | { type: 'wait_for_tap_to_reveal' }
    | { type: 'show_ratings' }
    | { type: 'speak_page'; pageIndex: number }
    | { type: 'speak_all_pages' }
    | { type: 'dynamic_pause'; nextPageIndex: number; pauseMultiplier: number }
    | { type: 'wait'; ms: number }
    | { type: 'listen_and_check'; pageIndex: number; successThreshold: number }
    | { type: 'feedback_success' }
    | { type: 'feedback_error' }
    | { type: 'auto_rate_from_answer' }
    | { type: 'auto_rate_fixed'; rating: number }
    | { type: 'mark_failed' }
    | { type: 'wait_for_tap' }
    | { type: 'next_card' }
  );

export type CompoundStepKind =
  | 'present_front'
  | 'flip_reveal'
  | 'show_all_grade'
  | 'speak_pause_next'
  | 'auto_flip'
  | 'listen_grade'
  | 'grade_after_listen'
  | 'auto_pass_next'
  | 'fail_next';

export type CompoundPause =
  | { kind: 'fixed'; ms: number }
  | { kind: 'dynamic'; page: number; multiplier: number };

export interface CompoundBranch {
  feedback: boolean;
  speakPage: number | null;
  pause: CompoundPause | null;
  revealAll: boolean;
  markFailed: boolean;
  rate: 'fromAnswer' | 'fixed' | 'none';
  fixedRating: number;
  advance: boolean;
}

export type CompoundParams =
  | { kind: 'present_front'; page: number; speak: boolean }
  | { kind: 'flip_reveal'; revealStyle: 'all' | 'next' }
  | { kind: 'show_all_grade' }
  | { kind: 'speak_pause_next'; page: number; nextPage: number; multiplier: number }
  | {
      kind: 'auto_flip';
      questionPage: number;
      answerPage: number;
      multiplier: number;
      speakQuestion: boolean;
      speakAnswer: boolean;
    }
  | {
      kind: 'listen_grade';
      answerPage: number;
      threshold: number;
      onCorrect: CompoundBranch;
      onWrong: CompoundBranch;
      manualFallback: boolean;
    }
  | {
      kind: 'grade_after_listen';
      onCorrect: CompoundBranch;
      onWrong: CompoundBranch;
      manualFallback: boolean;
    }
  | { kind: 'auto_pass_next'; rating: number }
  | { kind: 'fail_next' };

export interface CompoundStep extends StepBase {
  type: 'compound';
  version: 1;
  params: CompoundParams;
}

export type ModeStep = AtomicStep | CompoundStep;

export interface StudyMode {
  id: string;
  name: string;
  steps: ModeStep[];
  isBuiltIn: boolean;
  builtInSourceId?: string;
  updatedAt: number;
  deletedAt?: number | null;
}

export interface StoreData {
  groups: FlashcardGroup[];
  studyModes: StudyMode[];
  activityHeatmap: Record<string, number>;
  lastSyncedAt?: number;
}
