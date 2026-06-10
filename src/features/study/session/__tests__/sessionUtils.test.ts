import {
  getActiveRevealedPage,
  getAdaptiveCardFontSize,
  getBottomAlignedScrollY,
} from '@/features/study/session/sessionUtils';
import { TOKENS } from '@/theme/tokens';

const sizes = TOKENS.typography.studyCardSize;

describe('getAdaptiveCardFontSize', () => {
  it('keeps short content at the full size', () => {
    expect(getAdaptiveCardFontSize('')).toBe(sizes.full);
    expect(getAdaptiveCardFontSize('Bonjour')).toBe(sizes.full);
  });

  it('steps down through tiers as content grows', () => {
    expect(getAdaptiveCardFontSize('x'.repeat(100))).toBe(sizes.long);
    expect(getAdaptiveCardFontSize('x'.repeat(200))).toBe(sizes.longer);
  });

  it('clamps very long content to the minimum size', () => {
    expect(getAdaptiveCardFontSize('x'.repeat(500))).toBe(sizes.min);
  });

  it('treats nullish text as empty', () => {
    expect(getAdaptiveCardFontSize(undefined as unknown as string)).toBe(sizes.full);
  });
});

describe('getActiveRevealedPage', () => {
  it('returns null when nothing new is revealed', () => {
    expect(getActiveRevealedPage([0], [0])).toBeNull();
    expect(getActiveRevealedPage([0, 1], [0, 1])).toBeNull();
  });

  it('returns the page index for a fresh card', () => {
    expect(getActiveRevealedPage([], [0])).toBe(0);
  });

  it('returns the highest newly revealed page', () => {
    expect(getActiveRevealedPage([0], [0, 1])).toBe(1);
    expect(getActiveRevealedPage([0, 1], [0, 1, 2])).toBe(2);
    expect(getActiveRevealedPage([0], [0, 1, 2])).toBe(2);
    expect(getActiveRevealedPage([0], [0, 2])).toBe(2);
  });
});

describe('getBottomAlignedScrollY', () => {
  it('keeps a page that already fits at the top (no scroll)', () => {
    // Page fits within the viewport from the top -> stays at 0.
    expect(getBottomAlignedScrollY(0, 100, 500)).toBe(0);
  });

  it('docks a lower short page to the bottom of the viewport', () => {
    // Page bottom (600+100) should sit at viewport bottom -> y = 700 - 500.
    expect(getBottomAlignedScrollY(600, 100, 500)).toBe(200);
  });

  it('top-aligns a page taller than the viewport so it reads from the start', () => {
    expect(getBottomAlignedScrollY(600, 900, 500)).toBe(600);
  });

  it('never returns a negative offset', () => {
    expect(getBottomAlignedScrollY(0, 50, 500)).toBe(0);
  });
});
