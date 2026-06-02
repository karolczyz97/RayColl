import React from 'react';
import { fireEvent, renderAsync, screen } from '@testing-library/react-native';

import type { CardStats } from '../../store/selectors/stats';
import { TestProviders } from '../../test/renderWithAppProviders';
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
      expect.objectContaining({ flexGrow: 2 }),
    );
    expect(screen.getByTestId('expressive-progress-segment-learning', hidden).props.style).toEqual(
      expect.objectContaining({ flexGrow: 1 }),
    );
    expect(screen.getByTestId('expressive-progress-segment-review', hidden).props.style).toEqual(
      expect.objectContaining({ flexGrow: 3 }),
    );
    expect(screen.queryByTestId('expressive-progress-segment-mastered', hidden)).toBeNull();
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

    await fireEvent.press(screen.getByLabelText('Review (3)'));

    expect(onCategoryToggle).toHaveBeenCalledWith('review');
  });
});
