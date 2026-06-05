import React from 'react';
import { act, render, screen } from '@testing-library/react-native';
import { Text } from 'react-native';
import { useDebounce } from '../useDebounce';

function Probe({ value, delayMs = 100 }: { value: string; delayMs?: number }) {
  const debounced = useDebounce(value, delayMs);
  return <Text testID="debounced">{debounced}</Text>;
}

describe('useDebounce', () => {
  beforeEach(() => {
    jest.useFakeTimers();
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  it('updates only after the configured delay', () => {
    const { rerender } = render(<Probe value="first" />);
    expect(screen.getByTestId('debounced')).toHaveTextContent('first');

    rerender(<Probe value="second" />);
    expect(screen.getByTestId('debounced')).toHaveTextContent('first');

    act(() => {
      jest.advanceTimersByTime(99);
    });
    expect(screen.getByTestId('debounced')).toHaveTextContent('first');

    act(() => {
      jest.advanceTimersByTime(1);
    });
    expect(screen.getByTestId('debounced')).toHaveTextContent('second');
  });

  it('cleans up the previous timer when value changes quickly', () => {
    const { rerender } = render(<Probe value="first" />);

    rerender(<Probe value="second" />);
    act(() => {
      jest.advanceTimersByTime(50);
    });
    rerender(<Probe value="third" />);
    act(() => {
      jest.advanceTimersByTime(50);
    });

    expect(screen.getByTestId('debounced')).toHaveTextContent('first');

    act(() => {
      jest.advanceTimersByTime(50);
    });
    expect(screen.getByTestId('debounced')).toHaveTextContent('third');
  });
});
