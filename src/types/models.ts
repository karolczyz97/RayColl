export interface SrsState {
  difficulty: number; // Trudność (1-10)
  stability: number; // Stabilność
  repetitions: number; // Liczba powtórzeń (repetitions >= 1 oznacza, że karta została "nauczona")
  state: number; // Stan FSRS (0: New, 1: Learning, 2: Review, 3: Relearning)
  lastReviewTimestamp: number; // Znacznik czasu ostatniej powtórki
  nextReviewTimestamp: number; // Znacznik czasu kolejnej powtórki
}

export interface Flashcard {
  id: string;
  pages: string[];
  srsState: SrsState;
}

export interface FlashcardGroup {
  id: string;
  name: string;
  cards: Flashcard[];
  activeModeId: string;
  studyFilter: 'all' | 'new' | 'review' | 'new+review';
  pageLanguages: string[]; // tagi BCP-47 dla stron
  pageNames: string[]; // np. ['Słowo', 'Tłumaczenie', 'Przykład']
  activePageCount: number; // liczba widocznych/aktywnych stron
}

export type ModeStep =
  | {
      type: 'show_page';
      pageIndex: number;
    }
  | {
      type: 'speak_page';
      pageIndex: number;
      extraPauseMs: number;
    }
  | {
      type: 'dynamic_pause';
      nextPageIndex: number;
      extraPauseMs: number;
    }
  | {
      type: 'wait';
      ms: number;
    }
  | {
      type: 'listen_and_branch';
      pageIndex: number;
      successThreshold: number; // np. 70 (%)
      incorrectTtsPageIndex?: number;
    }
  | {
      type: 'rate_knowledge';
    };

export interface StudyMode {
  id: string;
  name: string;
  steps: ModeStep[];
}
