import type { Flashcard } from '../types/models';

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
const MIN_PAGE_COUNT = 2;
const MAX_PAGE_COUNT = 5;

export function validateImportDeckPayload(payload: ImportDeckPayload): NormalizedImportDeckPayload {
  const name = payload.name.trim();
  if (!name) {
    throw new Error('Deck name is required.');
  }

  const pageNames = payload.pageNames.slice(0, MAX_PAGE_COUNT);
  const pageCount = pageNames.length;
  if (pageCount < MIN_PAGE_COUNT || pageCount > MAX_PAGE_COUNT) {
    throw new Error(`Decks must contain between ${MIN_PAGE_COUNT} and ${MAX_PAGE_COUNT} pages.`);
  }

  const languages = payload.languages.slice(0, pageCount);
  if (languages.length !== pageCount) {
    throw new Error('Every page must have a language entry.');
  }

  for (const language of languages) {
    if (language && !LANGUAGE_CODE_PATTERN.test(language)) {
      throw new Error(`Invalid language code: ${language}`);
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
