import { describe, expect, it } from '@jest/globals';

import { darkTheme, lightTheme } from '../createAppTheme';
import { getReviewStatusColor, getSuccessBgColor, getSuccessColor } from '../semanticColors';

describe('semanticColors', () => {
  it('keeps success green even when the theme tertiary color is not green', () => {
    const redTertiaryTheme = {
      ...lightTheme,
      colors: {
        ...lightTheme.colors,
        tertiary: lightTheme.colors.error,
        tertiaryContainer: lightTheme.colors.errorContainer,
      },
    };

    expect(getSuccessColor(redTertiaryTheme)).toBe('#0f7b55');
    expect(getSuccessBgColor(redTertiaryTheme)).toBe('#d7f7e7');
  });

  it('uses an explicit pastel palette for SRS categories', () => {
    // Hues: new=blue, learning=yellow, review=orange, mastered=green.
    // Pastel fill (bg) with darker same-hue content (fg) drawn on top.
    expect(getReviewStatusColor(lightTheme, 'new')).toEqual({
      fg: '#1b4fa8',
      bg: '#c2d9ff',
    });
    expect(getReviewStatusColor(lightTheme, 'learning')).toEqual({
      fg: '#8a6d00',
      bg: '#f4dd87',
    });
    expect(getReviewStatusColor(lightTheme, 'review')).toEqual({
      fg: '#b4480f',
      bg: '#ffcfa8',
    });
    expect(getReviewStatusColor(lightTheme, 'mastered')).toEqual({
      fg: '#0c6e4c',
      bg: '#c2f0d9',
    });

    const categoryColors = ['new', 'learning', 'review', 'mastered'].map((category) =>
      getReviewStatusColor(lightTheme, category as Parameters<typeof getReviewStatusColor>[1]),
    );

    expect(categoryColors).not.toContainEqual({
      color: lightTheme.colors.error,
      fg: lightTheme.colors.error,
      bg: lightTheme.colors.errorContainer,
    });
    expect(categoryColors).not.toContainEqual({ fg: lightTheme.colors.primary, bg: lightTheme.colors.primaryContainer });
    expect(categoryColors).not.toContainEqual({ fg: lightTheme.colors.secondary, bg: lightTheme.colors.secondaryContainer });
  });

  it('uses the same pastel SRS palette in dark mode', () => {
    // The palette is intentionally identical across themes so progress colors
    // read equally bright in light and dark.
    (['new', 'learning', 'review', 'mastered'] as const).forEach((category) => {
      expect(getReviewStatusColor(darkTheme, category)).toEqual(
        getReviewStatusColor(lightTheme, category),
      );
    });
  });
});
