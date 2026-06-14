import type { CardFilter } from '@/constants/cardFilters';
import type { CardOrder } from '@/constants/cardOrder';

export interface SrsState {
  difficulty: number; // Difficulty (1-10)
  stability: number; // Stability
  repetitions: number; // Repetition count (repetitions >= 1 means the card has been "learned")
  state: 0 | 1 | 2 | 3; // Stan FSRS (0: New, 1: Learning, 2: Review, 3: Relearning)
  lastReviewTimestamp: number; // Timestamp of the last review
  nextReviewTimestamp: number; // Timestamp of the next scheduled review
}

export interface Flashcard {
  id: string;
  pages: string[];
  srsState: SrsState;
  contentUpdatedAt?: number;
  srsUpdatedAt?: number;
  deletedAt?: number | null;
}

export interface FlashcardGroup {
  id: string;
  name: string;
  cards: Flashcard[];
  activeModeId: string;
  studyFilter: CardFilter;
  cardOrder?: CardOrder;
  pageLanguages: string[]; // tagi BCP-47 dla stron
  pageNames: string[]; // e.g. ['Word', 'Translation', 'Example']
  activePageCount: number; // liczba widocznych/aktywnych stron
  updatedAt?: number;
  deletedAt?: number | null;
  archivedAt?: number | null;
}

/**
 * Warunek wykonania kroku względem ostatniego wyniku odpowiedzi na tej karcie
 * (krok "Sprawdź wymowę"). `correct` → status `correct`; `wrong` → status
 * `incorrect`. Statusy `skipped`/`error`/`none` NIGDY nie pasują do warunku,
 * więc tap podczas STT (skipped) nie odpala kroków `correct`/`wrong`.
 */
export type StepCondition = 'correct' | 'wrong';

interface ModeStepBase {
  id?: string;
  // Krok z warunkiem wykonuje się tylko przy pasującym wyniku ostatniego
  // "Sprawdź wymowę"; brak warunku (undefined) = wykonuj zawsze.
  condition?: StepCondition;
}

/**
 * Pojedynczy primitive step. Runner jest głupim interpreterem tej listy —
 * każdy krok robi DOKŁADNIE jedną rzecz. Żadnych makro-kroków: STT nie ocenia,
 * TTS nie pauzuje, feedback nie branchuje, ocena nie przechodzi do następnej karty.
 */
export type ModeStep = ModeStepBase &
  (
    // --- Odsłanianie / oceny ---
    | { type: 'show_page'; pageIndex: number }
    | { type: 'show_all_pages' }
    | { type: 'wait_for_tap_to_reveal_next' }
    | { type: 'wait_for_tap_to_reveal' }
    | { type: 'show_ratings' }
    // --- Audio / czas ---
    | { type: 'speak_page'; pageIndex: number }
    | { type: 'dynamic_pause'; nextPageIndex: number; pauseMultiplier: number }
    | { type: 'wait'; ms: number }
    // --- STT ---
    | { type: 'listen_and_check'; pageIndex: number; successThreshold: number }
    // --- Feedback ---
    | { type: 'feedback_success' }
    | { type: 'feedback_error' }
    // --- Ocena SRS / failed list ---
    | { type: 'auto_rate_from_answer' }
    | { type: 'auto_rate_fixed'; rating: number }
    | { type: 'mark_failed' }
    // --- Przejście ---
    | { type: 'next_card' }
  );

export interface StudyMode {
  id: string;
  name: string;
  steps: ModeStep[];
  isBuiltIn: boolean;
  builtInSourceId?: string;
  updatedAt?: number;
  deletedAt?: number | null;
}

export interface StoreData {
  groups: FlashcardGroup[];
  studyModes: StudyMode[];
  activityHeatmap: Record<string, number>;
  lastSyncedAt?: number;
}
