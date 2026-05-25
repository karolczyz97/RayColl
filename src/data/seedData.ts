import type { FlashcardGroup, StudyMode } from '../types/models';
import { getCardCategory } from '../srs/srsEngine';

/** Placeholder seed data for the UI demo */
export const SEED_GROUPS: FlashcardGroup[] = [
  {
    id: 'g1',
    name: 'Angielski — Podstawy',
    activeModeId: 'm1',
    pageLanguages: ['en-US', 'pl-PL'],
    pageNames: ['Słowo', 'Tłumaczenie'],
    cards: [
      { id: 'c1', pages: ['Good morning', 'Dzień dobry'], srsState: { difficulty: 5, stability: 4, repetitions: 3, state: 2, lastReviewTimestamp: Date.now() - 86400000, nextReviewTimestamp: Date.now() - 3600000 } },
      { id: 'c2', pages: ['Thank you', 'Dziękuję'], srsState: { difficulty: 3, stability: 8, repetitions: 6, state: 2, lastReviewTimestamp: Date.now() - 172800000, nextReviewTimestamp: Date.now() + 86400000 } },
      { id: 'c3', pages: ['How are you?', 'Jak się masz?'], srsState: { difficulty: 7, stability: 2, repetitions: 1, state: 1, lastReviewTimestamp: Date.now() - 43200000, nextReviewTimestamp: Date.now() - 7200000 } },
      { id: 'c4', pages: ['Goodbye', 'Do widzenia'], srsState: { difficulty: 4, stability: 6, repetitions: 4, state: 2, lastReviewTimestamp: Date.now() - 259200000, nextReviewTimestamp: Date.now() + 172800000 } },
      { id: 'c5', pages: ['Please', 'Proszę'], srsState: { difficulty: 2, stability: 10, repetitions: 8, state: 2, lastReviewTimestamp: Date.now() - 345600000, nextReviewTimestamp: Date.now() + 604800000 } },
      { id: 'c6', pages: ['Yes', 'Tak'], srsState: { difficulty: 1, stability: 15, repetitions: 12, state: 2, lastReviewTimestamp: Date.now() - 604800000, nextReviewTimestamp: Date.now() + 1209600000 } },
      { id: 'c7', pages: ['No', 'Nie'], srsState: { difficulty: 1, stability: 14, repetitions: 11, state: 2, lastReviewTimestamp: Date.now() - 518400000, nextReviewTimestamp: Date.now() + 1036800000 } },
      { id: 'c8', pages: ['Water', 'Woda'], srsState: { difficulty: 6, stability: 3, repetitions: 2, state: 1, lastReviewTimestamp: Date.now() - 21600000, nextReviewTimestamp: Date.now() - 1800000 } },
      { id: 'c9', pages: ['Food', 'Jedzenie'], srsState: { difficulty: 5, stability: 5, repetitions: 3, state: 2, lastReviewTimestamp: Date.now() - 86400000, nextReviewTimestamp: Date.now() + 43200000 } },
      { id: 'c10', pages: ['Help', 'Pomoc'], srsState: { difficulty: 8, stability: 1, repetitions: 0, state: 0, lastReviewTimestamp: 0, nextReviewTimestamp: 0 } },
    ],
  },
  {
    id: 'g2',
    name: 'Niemiecki — Podróże',
    activeModeId: 'm1',
    pageLanguages: ['de-DE', 'pl-PL'],
    pageNames: ['Deutsch', 'Polski'],
    cards: [
      { id: 'c11', pages: ['Guten Tag', 'Dzień dobry'], srsState: { difficulty: 4, stability: 5, repetitions: 4, state: 2, lastReviewTimestamp: Date.now() - 172800000, nextReviewTimestamp: Date.now() + 86400000 } },
      { id: 'c12', pages: ['Danke', 'Dziękuję'], srsState: { difficulty: 3, stability: 7, repetitions: 5, state: 2, lastReviewTimestamp: Date.now() - 259200000, nextReviewTimestamp: Date.now() + 259200000 } },
      { id: 'c13', pages: ['Entschuldigung', 'Przepraszam'], srsState: { difficulty: 7, stability: 2, repetitions: 1, state: 1, lastReviewTimestamp: Date.now() - 43200000, nextReviewTimestamp: Date.now() - 3600000 } },
      { id: 'c14', pages: ['Wie viel kostet das?', 'Ile to kosztuje?'], srsState: { difficulty: 6, stability: 3, repetitions: 2, state: 1, lastReviewTimestamp: Date.now() - 86400000, nextReviewTimestamp: Date.now() - 7200000 } },
      { id: 'c15', pages: ['Der Bahnhof', 'Dworzec'], srsState: { difficulty: 5, stability: 4, repetitions: 3, state: 2, lastReviewTimestamp: Date.now() - 172800000, nextReviewTimestamp: Date.now() + 43200000 } },
      { id: 'c16', pages: ['Das Hotel', 'Hotel'], srsState: { difficulty: 2, stability: 9, repetitions: 7, state: 2, lastReviewTimestamp: Date.now() - 345600000, nextReviewTimestamp: Date.now() + 518400000 } },
    ],
  },
  {
    id: 'g3',
    name: 'Hiszpański — Kolory',
    activeModeId: 'm2',
    pageLanguages: ['es-ES', 'pl-PL', 'en-US'],
    pageNames: ['Español', 'Polski', 'English'],
    cards: [
      { id: 'c17', pages: ['Rojo', 'Czerwony', 'Red'], srsState: { difficulty: 3, stability: 8, repetitions: 6, state: 2, lastReviewTimestamp: Date.now() - 259200000, nextReviewTimestamp: Date.now() + 432000000 } },
      { id: 'c18', pages: ['Azul', 'Niebieski', 'Blue'], srsState: { difficulty: 4, stability: 6, repetitions: 4, state: 2, lastReviewTimestamp: Date.now() - 172800000, nextReviewTimestamp: Date.now() + 172800000 } },
      { id: 'c19', pages: ['Verde', 'Zielony', 'Green'], srsState: { difficulty: 5, stability: 3, repetitions: 2, state: 1, lastReviewTimestamp: Date.now() - 43200000, nextReviewTimestamp: Date.now() - 1800000 } },
      { id: 'c20', pages: ['Amarillo', 'Żółty', 'Yellow'], srsState: { difficulty: 8, stability: 1, repetitions: 0, state: 0, lastReviewTimestamp: 0, nextReviewTimestamp: 0 } },
    ],
  },
];

export const SEED_MODES: StudyMode[] = [
  {
    id: 'm1',
    name: 'Klasyczny (pokaż + odpowiedz)',
    steps: [
      { type: 'show_page', pageIndex: 0 },
      { type: 'speak_page', pageIndex: 0, extraPauseMs: 500 },
      { type: 'dynamic_pause', nextPageIndex: 1, extraPauseMs: 1000 },
      { type: 'show_page', pageIndex: 1 },
    ],
  },
  {
    id: 'm2',
    name: 'Słuchanie + mówienie',
    steps: [
      { type: 'speak_page', pageIndex: 0, extraPauseMs: 300 },
      { type: 'wait', ms: 2000 },
      { type: 'listen_and_branch', pageIndex: 1, successThreshold: 70 },
      { type: 'show_page', pageIndex: 1 },
    ],
  },
  {
    id: 'm3',
    name: 'Tylko pokaz',
    steps: [
      { type: 'show_page', pageIndex: 0 },
      { type: 'wait', ms: 3000 },
      { type: 'show_page', pageIndex: 1 },
    ],
  },
];

/** Generate seed heatmap data for the last ~140 days */
export function generateSeedHeatmap(): Record<string, number> {
  const heatmap: Record<string, number> = {};
  const now = new Date();
  for (let i = 0; i < 140; i++) {
    const d = new Date(now);
    d.setDate(d.getDate() - i);
    const key = d.toISOString().slice(0, 10);
    if (Math.random() > 0.35) {
      heatmap[key] = Math.floor(Math.random() * 25) + 1;
    }
  }
  return heatmap;
}

/** Compute how many cards are due now */
export function getDueCount(group: FlashcardGroup): number {
  const now = Date.now();
  return group.cards.filter(
    c => getCardCategory(c.srsState) === 'new' || c.srsState.nextReviewTimestamp <= now,
  ).length;
}

/** Compute mastery % (cards in mastered state) */
export function getMastery(group: FlashcardGroup): number {
  if (group.cards.length === 0) return 0;
  const mastered = group.cards.filter(c => getCardCategory(c.srsState) === 'mastered').length;
  return Math.round((mastered / group.cards.length) * 100);
}
