import type { Flashcard } from '@/types/models';
import { MIN_PAGE_COUNT, MAX_STORED_PAGE_COUNT } from '@/constants/pages';

export interface ImportDeckCardInput {
  pages: string[];
}

export interface ImportDeckPayload {
  name: string;
  languages: string[];
  pageNames: string[];
  cards: ImportDeckCardInput[];
}

export interface NormalizedImportDeckPayload {
  name: string;
  languages: string[];
  pageNames: string[];
  cards: Omit<Flashcard, 'id' | 'srsState'>[];
}

export type ImportDeckResult =
  | {
      ok: true;
      groupId: string;
      importedCards: number;
    }
  | {
      ok: false;
      error: string;
    };

const LANGUAGE_CODE_PATTERN = /^[a-z]{2,3}(?:-[A-Za-z0-9]{2,8})?$/;

export function validateImportDeckPayload(payload: ImportDeckPayload): NormalizedImportDeckPayload {
  const name = payload.name.trim();
  if (!name) {
    throw new Error('Deck name is required.');
  }

  const pageNames = payload.pageNames;
  const pageCount = pageNames.length;
  if (pageCount < MIN_PAGE_COUNT) {
    throw new Error(`Decks must contain at least ${MIN_PAGE_COUNT} pages.`);
  }
  if (pageCount > MAX_STORED_PAGE_COUNT) {
    throw new Error(`Decks can contain at most ${MAX_STORED_PAGE_COUNT} pages.`);
  }

  const languages = payload.languages.slice(0, pageCount);
  if (languages.length !== pageCount) {
    throw new Error('Every page must have a language entry.');
  }

  for (let i = 0; i < languages.length; i++) {
    if (!LANGUAGE_CODE_PATTERN.test(languages[i])) {
      throw new Error(`Missing language for page ${i + 1}`);
    }
  }

  if (payload.cards.length === 0) {
    throw new Error('Import does not contain any cards.');
  }

  const cards = payload.cards.map((card, index) => {
    if (!Array.isArray(card.pages)) {
      throw new Error(`Row ${index + 1} is malformed.`);
    }
    if (card.pages.length < pageCount) {
      throw new Error(`Row ${index + 1} is missing one or more pages.`);
    }

    const pages = card.pages.slice(0, pageCount);
    if (!pages.some((page) => page.trim().length > 0)) {
      throw new Error(`Row ${index + 1} is empty.`);
    }

    return { pages };
  });

  return {
    name,
    languages,
    pageNames,
    cards,
  };
}
