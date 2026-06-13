import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react-native';
import { PaperProvider } from 'react-native-paper';

import { createAppTheme } from '../../../theme/createAppTheme';
import { ExpressiveProgress } from '../ExpressiveProgress';
import { ExpressiveSegmentedProgress } from '../ExpressiveSegmentedProgress';

const theme = createAppTheme({
  isDark: false,
  useSystemColors: false,
});
const segmentColors = {
  new: theme.colors.primary,
  empty: theme.colors.secondary,
  review: theme.colors.tertiary,
};

function renderWithPaper(children: React.ReactNode) {
  return render(<PaperProvider theme={theme}>{children}</PaperProvider>);
}

describe('Expressive progress components', () => {
  it('renders accessible linear progress with a clamped fill width', async () => {
    await renderWithPaper(
      <ExpressiveProgress value={15} max={10} accessibilityLabel="Deck progress" />,
    );

    const track = screen.getByLabelText('Deck progress');
    const fill = screen.getByTestId('expressive-progress-fill');

    expect(track.props.accessibilityRole).toBe('progressbar');
    expect(track.props.accessibilityValue).toEqual({ min: 0, max: 10, now: 10 });
    expect(fill.props.style).toEqual(
      expect.arrayContaining([expect.objectContaining({ width: '100%' })]),
    );
  });

  it('renders only positive segmented progress values', async () => {
    await renderWithPaper(
      <ExpressiveSegmentedProgress
        accessibilityLabel="SRS progress"
        segments={[
          { id: 'new', value: 2, color: segmentColors.new },
          { id: 'empty', value: 0, color: segmentColors.empty },
          { id: 'review', value: 3, color: segmentColors.review },
        ]}
      />,
    );

    expect(screen.getByLabelText('SRS progress').props.accessibilityRole).toBe('progressbar');
    expect(screen.getByTestId('expressive-progress-segment-new').props.style).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ flexGrow: 2, backgroundColor: segmentColors.new }),
      ]),
    );
    expect(screen.queryByTestId('expressive-progress-segment-empty')).toBeNull();
    expect(screen.getByTestId('expressive-progress-segment-review').props.style).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ flexGrow: 3, backgroundColor: segmentColors.review }),
      ]),
    );
  });

  it('grows a labeled interactive segment to fit its measured label width', async () => {
    await renderWithPaper(
      <ExpressiveSegmentedProgress
        segments={[
          {
            id: 'review',
            value: 3,
            color: segmentColors.review,
            label: 'Review (3)',
            onPress: () => {},
          },
        ]}
      />,
    );

    const segment = screen.getByTestId('expressive-progress-segment-review');
    // Before measurement, the segment falls back to the percentage floor.
    expect(segment.props.style).toEqual(
      expect.arrayContaining([expect.objectContaining({ minWidth: '12.5%' })]),
    );

    // Simulate the off-screen label reporting its natural single-line width.
    fireEvent(
      screen.getByTestId('expressive-progress-measure-review', { includeHiddenElements: true }),
      'layout',
      { nativeEvent: { layout: { width: 60 } } },
    );

    // minWidth = measured width + horizontal padding + fit buffer + width slack.
    expect(segment.props.style).toEqual(
      expect.arrayContaining([expect.objectContaining({ minWidth: 60 + 4 * 2 + 2 + 12 })]),
    );
  });

  it('sizes a count + icon segment to fit the measured count plus the icon', async () => {
    await renderWithPaper(
      <ExpressiveSegmentedProgress
        segments={[
          {
            id: 'review',
            value: 3,
            color: segmentColors.review,
            label: '3',
            icon: 'refresh',
            onPress: () => {},
          },
        ]}
      />,
    );

    const segment = screen.getByTestId('expressive-progress-segment-review');
    // Before measurement: percentage fallback.
    expect(segment.props.style).toEqual(
      expect.arrayContaining([expect.objectContaining({ minWidth: '12.5%' })]),
    );

    // Measure the count text width.
    fireEvent(
      screen.getByTestId('expressive-progress-measure-review', { includeHiddenElements: true }),
      'layout',
      { nativeEvent: { layout: { width: 10 } } },
    );

    // minWidth = countWidth + icon + gap + horizontal padding + fit buffer + slack.
    const expectedMinWidth = 10 + 16 + 4 + 4 * 2 + 2 + 12;
    expect(segment.props.style).toEqual(
      expect.arrayContaining([expect.objectContaining({ minWidth: expectedMinWidth })]),
    );
  });
});
