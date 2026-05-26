import type { SrsState } from '../types/models';

// --- FSRS Parameters (v4.5 defaults) ---
const W = [0.4, 0.6, 2.4, 5.8, 4.93, 0.94, 0.86, 0.01, 1.49, 0.14, 0.94, 2.18, 0.05, 0.34, 1.26, 0.29, 2.61];
const DECAY = -0.5;
const FACTOR = 0.9 ** (1 / DECAY) - 1;

export function createNewSrsState(): SrsState {
  return {
    difficulty: 5,
    stability: 0,
    repetitions: 0,
    state: 0, // New
    lastReviewTimestamp: 0,
    nextReviewTimestamp: 0,
  };
}

function clamp(val: number, min: number, max: number): number {
  return Math.max(min, Math.min(max, val));
}

function initDifficulty(rating: number): number {
  return clamp(W[4] - (rating - 3) * W[5], 1, 10);
}

function initStability(rating: number): number {
  return Math.max(W[rating - 1], 0.1);
}

function nextDifficulty(d: number, rating: number): number {
  const newD = d - W[6] * (rating - 3);
  return clamp(W[7] * initDifficulty(3) + (1 - W[7]) * newD, 1, 10);
}

function retrievability(elapsedDays: number, stability: number): number {
  if (stability <= 0) return 0;
  return (1 + FACTOR * elapsedDays / stability) ** DECAY;
}

function nextRecallStability(d: number, s: number, r: number, rating: number): number {
  const hardPenalty = rating === 2 ? W[15] : 1;
  const easyBonus = rating === 4 ? W[16] : 1;
  return s * (1 + Math.exp(W[8]) * (11 - d) * s ** (-W[9]) *
    (Math.exp((1 - r) * W[10]) - 1) * hardPenalty * easyBonus);
}

function nextForgetStability(d: number, s: number, r: number): number {
  return W[11] * d ** (-W[12]) * ((s + 1) ** W[13] - 1) * Math.exp((1 - r) * W[14]);
}

export function calculateFsrs(current: SrsState, rawRating: number): SrsState {
  const rating = Math.max(1, Math.min(4, Math.round(rawRating)));
  const now = Date.now();
  const elapsedMs = now - (current.lastReviewTimestamp || now);
  const elapsedDays = Math.max(elapsedMs / (1000 * 60 * 60 * 24), 0);

  let newD: number, newS: number, newState: number;

  if (current.state === 0 || current.stability <= 0) {
    // New or first review
    newD = initDifficulty(rating);
    newS = initStability(rating);
    newState = rating === 1 ? 1 : 2; // Learning or Review
  } else {
    newD = nextDifficulty(current.difficulty, rating);
    const r = retrievability(elapsedDays, current.stability);
    if (rating === 1) {
      newS = nextForgetStability(newD, current.stability, r);
      newState = 3; // Relearning
    } else {
      newS = nextRecallStability(newD, current.stability, r, rating);
      newState = 2; // Review
    }
  }

  newS = Math.max(newS, 0.1);
  const interval = newS; // stability = days until retrievability drops to 90%
  const nextReview = now + interval * 24 * 60 * 60 * 1000;

  return {
    difficulty: newD,
    stability: newS,
    repetitions: current.repetitions + 1,
    state: newState,
    lastReviewTimestamp: now,
    nextReviewTimestamp: nextReview,
  };
}

// --- Text normalization & speech matching ---

export function normalizeText(text: string): string {
  return text.toLowerCase().replace(/[^\p{L}\p{N}\s]/gu, '').replace(/\s+/g, ' ').trim();
}

export function levenshteinDistance(a: string, b: string): number {
  const m = a.length, n = b.length;
  const dp: number[][] = Array.from({ length: m + 1 }, () => Array(n + 1).fill(0));
  for (let i = 0; i <= m; i++) dp[i][0] = i;
  for (let j = 0; j <= n; j++) dp[0][j] = j;
  for (let i = 1; i <= m; i++) {
    for (let j = 1; j <= n; j++) {
      const cost = a[i - 1] === b[j - 1] ? 0 : 1;
      dp[i][j] = Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost);
    }
  }
  return dp[m][n];
}

function wordSimilarity(recognized: string, original: string): number {
  const recWords = recognized.split(' ').filter(Boolean);
  const origWords = original.split(' ').filter(Boolean);
  if (origWords.length === 0) return recWords.length === 0 ? 100 : 0;

  let matched = 0;
  const used = new Set<number>();
  for (const ow of origWords) {
    let bestScore = 0, bestIdx = -1;
    for (let i = 0; i < recWords.length; i++) {
      if (used.has(i)) continue;
      const maxLen = Math.max(ow.length, recWords[i].length);
      const dist = levenshteinDistance(ow, recWords[i]);
      const score = maxLen > 0 ? (1 - dist / maxLen) : 1;
      if (score > bestScore) { bestScore = score; bestIdx = i; }
    }
    if (bestIdx >= 0 && bestScore > 0.5) { matched += bestScore; used.add(bestIdx); }
  }
  return (matched / origWords.length) * 100;
}

export function matchSpeech(recognized: string, original: string): number {
  const normRec = normalizeText(recognized);
  const normOrig = normalizeText(original);
  if (normOrig.length === 0) return normRec.length === 0 ? 100 : 0;

  const maxLen = Math.max(normRec.length, normOrig.length);
  const globalSim = maxLen > 0 ? (1 - levenshteinDistance(normRec, normOrig) / maxLen) * 100 : 100;
  const wordSim = wordSimilarity(normRec, normOrig);

  return Math.round(Math.max(globalSim, wordSim));
}

export function mapMatchToRating(matchPercent: number): number {
  if (matchPercent >= 85) return 4; // Easy
  if (matchPercent >= 60) return 3; // Good
  if (matchPercent >= 40) return 2; // Hard
  return 1; // Again
}

// --- Shared SRS State Categorization Logic ---

export type SrsCardCategory = 'new' | 'learning' | 'review' | 'mastered';

export function getCardCategory(state: SrsState): SrsCardCategory {
  if (state.state === 0) return 'new';
  if (state.state === 1 || state.state === 3) return 'learning';
  if (state.state === 2) {
    return state.repetitions >= 3 ? 'mastered' : 'review';
  }
  return 'new'; // default fallback
}
