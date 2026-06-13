import React from 'react';
import { fireEvent, renderAsync, screen } from '@testing-library/react-native';

import type { CardStats } from '../../store/selectors/stats';
import { TestProviders } from '../../test/renderWithAppProviders';
import { lightTheme } from '../../theme/createAppTheme';
import { getReviewStatusColor } from '../../theme/semanticColors';
import { SegmentedProgressBar } from '../SegmentedProgressBar';

const stats: CardStats = {
  total: 6,
  newCount: 2,
  learning: 1,
  review: 3,
  mastered: 0,
};

const hidden = { includeHiddenElements: true };

async function renderWithPaper(children: React.ReactNode) {
  return renderAsync(<TestProviders>{children}</TestProviders>);
}

describe('SegmentedProgressBar', () => {
  it('renders SRS stats as weighted progress segments', async () => {
    await renderWithPaper(<SegmentedProgressBar stats={stats} showLegend />);

    expect(screen.getByTestId('expressive-progress-segment-new', hidden).props.style).toEqual(
      expect.arrayContaining([expect.objectContaining({ flexGrow: 2 })]),
    );
    expect(screen.getByTestId('expressive-progress-segment-learning', hidden).props.style).toEqual(
      expect.arrayContaining([expect.objectContaining({ flexGrow: 1 })]),
    );
    expect(screen.getByTestId('expressive-progress-segment-review', hidden).props.style).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          flexGrow: 3,
          backgroundColor: getReviewStatusColor(lightTheme, 'review').bg,
        }),
      ]),
    );
    expect(screen.queryByTestId('expressive-progress-segment-mastered', hidden)).toBeNull();
    // Non-interactive bar without inline labels: category names come from the legend.
    expect(screen.getByText(/New \(2\)/)).toBeOnTheScreen();
  });

  it('exposes category filter toggles when a toggle handler is provided', async () => {
    const onCategoryToggle = jest.fn();

    await renderWithPaper(
      <SegmentedProgressBar
        stats={stats}
        showLegend
        selectedCategories={['new']}
        onCategoryToggle={onCategoryToggle}
      />,
    );

    const reviewSegment = screen.getByTestId('expressive-progress-segment-review');
    expect(reviewSegment.props.accessibilityRole).toBe('checkbox');
    expect(reviewSegment.props.accessibilityState).toEqual({ checked: false });
    expect(reviewSegment.props.style).toEqual(
      expect.arrayContaining([expect.objectContaining({ minWidth: '12.5%' })]),
    );

    await fireEvent.press(reviewSegment);

    expect(onCategoryToggle).toHaveBeenCalledWith('review');
  });

  it('renders the count (not a text label) inside interactive segments', async () => {
    await renderWithPaper(
      <SegmentedProgressBar
        stats={stats}
        selectedCategories={['review']}
        onCategoryToggle={jest.fn()}
      />,
    );

    // Inline content is "<count> <icon>", not the old "Review (3)" text label.
    expect(screen.getByText('3')).toBeOnTheScreen();
    expect(screen.queryByText('Review (3)')).toBeNull();
    // Tiny categories still get their count + icon (no minimum-share threshold).
    expect(screen.getByText('2')).toBeOnTheScreen();
  });

  it('renders inline content without interactivity when showInlineLabels is set', async () => {
    await renderWithPaper(<SegmentedProgressBar stats={stats} showInlineLabels />);

    // Counts appear inside the bar (the deck-preview look)...
    expect(screen.getByText('3')).toBeOnTheScreen();
    // ...but the segment is not a toggle (no onCategoryToggle provided).
    expect(
      screen.getByTestId('expressive-progress-segment-review').props.accessibilityRole,
    ).toBeUndefined();
  });
});
