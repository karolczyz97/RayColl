import { describe, expect, it } from '@jest/globals';

import { darkTheme, lightTheme } from '../createAppTheme';
import { getReviewStatusColor } from '../semanticColors';
import { TOKENS } from '../tokens';

describe('semanticColors', () => {
  it('uses an explicit mastery-oriented palette for SRS categories', () => {
    expect(getReviewStatusColor(lightTheme, 'new')).toEqual({
      color: '#5f6368',
      bg: '#eceff1',
    });
    expect(getReviewStatusColor(lightTheme, 'learning')).toEqual({
      color: TOKENS.colors.warning,
      bg: TOKENS.colors.warningBg,
    });
    expect(getReviewStatusColor(lightTheme, 'review')).toEqual({
      color: '#2d6cdf',
      bg: '#dbeafe',
    });
    expect(getReviewStatusColor(lightTheme, 'mastered')).toEqual({
      color: '#0f7b55',
      bg: '#d7f7e7',
    });

    const categoryColors = ['new', 'learning', 'review', 'mastered'].map((category) =>
      getReviewStatusColor(lightTheme, category as Parameters<typeof getReviewStatusColor>[1]),
    );

    expect(categoryColors).not.toContainEqual({
      color: lightTheme.colors.error,
      bg: lightTheme.colors.errorContainer,
    });
    expect(categoryColors).not.toContainEqual({ color: lightTheme.colors.primary, bg: lightTheme.colors.primaryContainer });
    expect(categoryColors).not.toContainEqual({ color: lightTheme.colors.secondary, bg: lightTheme.colors.secondaryContainer });
  });

  it('keeps the explicit SRS palette readable in dark mode', () => {
    expect(getReviewStatusColor(darkTheme, 'new')).toEqual({
      color: '#c4c7c5',
      bg: '#323539',
    });
    expect(getReviewStatusColor(darkTheme, 'learning')).toEqual({
      color: '#ffd180',
      bg: '#4a3000',
    });
    expect(getReviewStatusColor(darkTheme, 'review')).toEqual({
      color: '#9ec5ff',
      bg: '#173a75',
    });
    expect(getReviewStatusColor(darkTheme, 'mastered')).toEqual({
      color: '#7ee2b8',
      bg: '#063f2c',
    });
  });
});
