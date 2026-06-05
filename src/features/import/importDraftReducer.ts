import type { Flashcard } from '@/types/models';
import { MAX_STORED_PAGE_COUNT, MIN_PAGE_COUNT } from '@/constants/pages';

export interface ImportDraftState {
  name: string;
  rawText: string;
  sepKey: string;
  customSep: string;
  pageCount: number;
  pageNames: string[];
  pageLangs: string[];
  cards: Flashcard[];
  importError: string;
  firstRowIsHeader: boolean;
  rawColumnCount: number;
}

export type DraftAction =
  | { type: 'SET_NAME'; value: string }
  | { type: 'SET_RAW_TEXT'; value: string }
  | { type: 'SET_SEP_KEY'; value: string }
  | { type: 'SET_CUSTOM_SEP'; value: string }
  | { type: 'SET_PAGE_COUNT'; value: number }
  | { type: 'SET_PAGE_NAMES'; value: string[] }
  | { type: 'SET_PAGE_LANGS'; value: string[] }
  | { type: 'SET_CARDS'; value: Flashcard[] }
  | { type: 'SET_IMPORT_ERROR'; value: string }
  | { type: 'SET_FIRST_ROW_IS_HEADER'; value: boolean }
  | { type: 'SET_RAW_COLUMN_COUNT'; value: number };

export function createInitialDraftState(): ImportDraftState {
  return {
    name: '',
    rawText: '',
    sepKey: 'semicolon',
    customSep: '',
    pageCount: 2,
    pageNames: Array.from({ length: MAX_STORED_PAGE_COUNT }, () => ''),
    pageLangs: Array.from({ length: MAX_STORED_PAGE_COUNT }, () => ''),
    cards: [],
    importError: '',
    firstRowIsHeader: false,
    rawColumnCount: MIN_PAGE_COUNT,
  };
}

export function importDraftReducer(state: ImportDraftState, action: DraftAction): ImportDraftState {
  switch (action.type) {
    case 'SET_NAME':
      return { ...state, name: action.value };
    case 'SET_RAW_TEXT':
      return { ...state, rawText: action.value };
    case 'SET_SEP_KEY':
      return { ...state, sepKey: action.value };
    case 'SET_CUSTOM_SEP':
      return { ...state, customSep: action.value };
    case 'SET_PAGE_COUNT':
      return { ...state, pageCount: action.value };
    case 'SET_PAGE_NAMES':
      return { ...state, pageNames: action.value };
    case 'SET_PAGE_LANGS':
      return { ...state, pageLangs: action.value };
    case 'SET_CARDS':
      return { ...state, cards: action.value };
    case 'SET_IMPORT_ERROR':
      return { ...state, importError: action.value };
    case 'SET_FIRST_ROW_IS_HEADER':
      return { ...state, firstRowIsHeader: action.value };
    case 'SET_RAW_COLUMN_COUNT':
      return { ...state, rawColumnCount: action.value };
  }
}
