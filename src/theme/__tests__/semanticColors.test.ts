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

  it('uses an explicit vivid palette for SRS categories', () => {
    // Hues: new=blue, learning=yellow, review=orange, mastered=green.
    // White content on saturated fills, dark content on the light yellow hue.
    expect(getReviewStatusColor(lightTheme, 'new')).toEqual({
      fg: '#ffffff',
      bg: '#2f6fed',
    });
    expect(getReviewStatusColor(lightTheme, 'learning')).toEqual({
      fg: '#3d2f00',
      bg: '#f3c01a',
    });
    expect(getReviewStatusColor(lightTheme, 'review')).toEqual({
      fg: '#ffffff',
      bg: '#e8600f',
    });
    expect(getReviewStatusColor(lightTheme, 'mastered')).toEqual({
      fg: '#ffffff',
      bg: '#0f9b4c',
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

  it('uses the same vivid SRS palette in dark mode', () => {
    // The palette is intentionally identical across themes so progress colors
    // read equally bright in light and dark.
    (['new', 'learning', 'review', 'mastered'] as const).forEach((category) => {
      expect(getReviewStatusColor(darkTheme, category)).toEqual(
        getReviewStatusColor(lightTheme, category),
      );
    });
  });
});
