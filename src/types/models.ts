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

/** Warunek wykonania kroku względem ostatniego sprawdzenia wymowy (listen_and_check). */
export type StepCondition = 'correct' | 'wrong';

interface ModeStepBase {
  id?: string;
  // Krok z warunkiem wykonuje się tylko, gdy ostatni krok "sprawdź wymowę"
  // na tej karcie zakończył się odpowiednio dobrze/źle; inaczej jest pomijany.
  condition?: StepCondition;
}

export type ModeStep = ModeStepBase &
  (
    | {
        type: 'show_page';
        pageIndex: number;
      }
    | {
        type: 'speak_page';
        pageIndex: number;
        // Pauza po TTS = N × czas odsłuchania tej strony (0–5; 0 = bez pauzy).
        pauseMultiplier: number;
      }
    | {
        type: 'dynamic_pause';
        nextPageIndex: number;
        // Pauza = N × szacowany czas odsłuchania następnej strony (0–5).
        pauseMultiplier: number;
      }
    | {
        type: 'wait';
        ms: number;
      }
    | {
        type: 'listen_and_branch';
        pageIndex: number;
        successThreshold: number;
        incorrectTtsPageIndex?: number;
      }
    | {
        // Sprawdza wymowę i zapisuje wynik (dobrze/źle) bez oceny SRS;
        // wynik sterują krokami z `condition`.
        type: 'listen_and_check';
        pageIndex: number;
        successThreshold: number;
      }
    | {
        type: 'rate';
      }
    | {
        // Kończy kartę bez oceny SRS (tryb osłuchowy).
        type: 'next_card';
      }
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
