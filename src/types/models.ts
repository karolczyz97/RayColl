import type { CardFilter } from '@/constants/cardFilters';

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
  pageLanguages: string[]; // tagi BCP-47 dla stron
  pageNames: string[]; // e.g. ['Word', 'Translation', 'Example']
  activePageCount: number; // liczba widocznych/aktywnych stron
  updatedAt?: number;
  deletedAt?: number | null;
  archivedAt?: number | null;
}

export type ModeStep =
  | {
      id?: string;
      type: 'show_page';
      pageIndex: number;
    }
  | {
      id?: string;
      type: 'speak_page';
      pageIndex: number;
      // Pauza po TTS = N × czas odsłuchania tej strony (0–5; 0 = bez pauzy).
      pauseMultiplier: number;
    }
  | {
      id?: string;
      type: 'dynamic_pause';
      nextPageIndex: number;
    }
  | {
      id?: string;
      type: 'wait';
      ms: number;
    }
  | {
      id?: string;
      type: 'listen_and_branch';
      pageIndex: number;
      successThreshold: number;
      incorrectTtsPageIndex?: number;
    }
  | {
      id?: string;
      type: 'reveal_on_tap';
    }
  | {
      id?: string;
      type: 'rate';
    }
  | {
      // Kończy kartę bez oceny SRS (tryb osłuchowy); zawsze ostatni krok trybu.
      id?: string;
      type: 'next_card';
    };

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
