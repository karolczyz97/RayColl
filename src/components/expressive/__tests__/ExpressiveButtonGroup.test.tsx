import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react-native';
import { PaperProvider } from 'react-native-paper';

import { createAppTheme } from '../../../theme/createAppTheme';
import { getReviewStatusColor } from '../../../theme/semanticColors';
import { ExpressiveButtonGroup } from '../ExpressiveButtonGroup';

const theme = createAppTheme({ isDark: false, useSystemColors: false });

const BUTTONS = [
  { value: 'light', label: 'Light', icon: 'weather-sunny' },
  { value: 'system', label: 'System', icon: 'theme-light-dark' },
  { value: 'dark', label: 'Dark', icon: 'weather-night' },
];

function renderGroup(value: string, onValueChange: (value: string) => void) {
  return render(
    <PaperProvider theme={theme}>
      <ExpressiveButtonGroup value={value} onValueChange={onValueChange} buttons={BUTTONS} />
    </PaperProvider>,
  );
}

const srsNew = getReviewStatusColor(theme, 'new');
const srsLearning = getReviewStatusColor(theme, 'learning');
const srsReview = getReviewStatusColor(theme, 'review');
const srsMastered = getReviewStatusColor(theme, 'mastered');

const MULTI_BUTTONS = [
  { value: 'new', label: '5', icon: 'new-box', selectedBg: srsNew.bg, selectedContent: srsNew.fg },
  { value: 'learning', label: '3', icon: 'book-open-variant', selectedBg: srsLearning.bg, selectedContent: srsLearning.fg },
  { value: 'review', label: '2', icon: 'refresh', selectedBg: srsReview.bg, selectedContent: srsReview.fg },
  { value: 'mastered', label: '10', icon: 'trophy-outline', selectedBg: srsMastered.bg, selectedContent: srsMastered.fg },
];

function renderMultiGroup(value: string[], onValueChange: (v: string) => void) {
  return render(
    <PaperProvider theme={theme}>
      <ExpressiveButtonGroup
        multiSelect
        value={value}
        onValueChange={onValueChange}
        buttons={MULTI_BUTTONS}
      />
    </PaperProvider>,
  );
}

describe('ExpressiveButtonGroup', () => {
  describe('single-select', () => {
    it('renders every button and marks only the active value as selected', () => {
      renderGroup('system', () => {});

      expect(screen.getByText('Light')).toBeTruthy();
      expect(screen.getByText('System')).toBeTruthy();
      expect(screen.getByText('Dark')).toBeTruthy();

      expect(screen.getByTestId('expressive-button-system').props.accessibilityState).toEqual({
        selected: true,
      });
      expect(screen.getByTestId('expressive-button-light').props.accessibilityState).toEqual({
        selected: false,
      });
    });

    it('emits the pressed value through onValueChange', () => {
      const onValueChange = jest.fn();
      renderGroup('system', onValueChange);

      fireEvent.press(screen.getByTestId('expressive-button-dark'));

      expect(onValueChange).toHaveBeenCalledWith('dark');
    });
  });

  describe('multi-select', () => {
    it('marks multiple values as checked and renders per-button labels', () => {
      renderMultiGroup(['new', 'mastered'], () => {});

      // Checked segments
      expect(screen.getByTestId('expressive-button-new').props.accessibilityState).toEqual({
        checked: true,
      });
      expect(screen.getByTestId('expressive-button-mastered').props.accessibilityState).toEqual({
        checked: true,
      });
      // Unchecked segments
      expect(screen.getByTestId('expressive-button-learning').props.accessibilityState).toEqual({
        checked: false,
      });
      expect(screen.getByTestId('expressive-button-review').props.accessibilityState).toEqual({
        checked: false,
      });
    });

    it('emits the pressed value (parent controls toggle logic)', () => {
      const onValueChange = jest.fn();
      renderMultiGroup(['new'], onValueChange);

      fireEvent.press(screen.getByTestId('expressive-button-review'));
      expect(onValueChange).toHaveBeenCalledWith('review');

      fireEvent.press(screen.getByTestId('expressive-button-new'));
      expect(onValueChange).toHaveBeenCalledWith('new');
    });

    it('renders empty selection (all unchecked) when value is []', () => {
      renderMultiGroup([], () => {});

      MULTI_BUTTONS.forEach((btn) => {
        expect(screen.getByTestId(`expressive-button-${btn.value}`).props.accessibilityState).toEqual({
          checked: false,
        });
      });
    });
  });
});
