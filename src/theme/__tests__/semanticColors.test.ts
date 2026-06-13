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

  it('uses an explicit mastery-oriented palette for SRS categories', () => {
    // Hues: new=blue, learning=yellow, review=orange, mastered=green.
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

  it('keeps the explicit SRS palette readable in dark mode', () => {
    expect(getReviewStatusColor(darkTheme, 'new')).toEqual({
      fg: '#9ec5ff',
      bg: '#173a75',
    });
    expect(getReviewStatusColor(darkTheme, 'learning')).toEqual({
      fg: '#f3d44e',
      bg: '#403300',
    });
    expect(getReviewStatusColor(darkTheme, 'review')).toEqual({
      fg: '#fdba74',
      bg: '#7c2d12',
    });
    expect(getReviewStatusColor(darkTheme, 'mastered')).toEqual({
      fg: '#7ee2b8',
      bg: '#063f2c',
    });
  });
});
