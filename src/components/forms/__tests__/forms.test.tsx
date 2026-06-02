import React from 'react';
import { fireEvent, renderAsync, screen } from '@testing-library/react-native';
import { Text } from 'react-native-paper';

import { TestProviders } from '../../../test/renderWithAppProviders';
import { AppFormRow } from '../AppFormRow';
import { AppNumberInput } from '../AppNumberInput';
import { AppTextInput } from '../AppTextInput';

async function renderWithProviders(children: React.ReactNode) {
  return renderAsync(<TestProviders>{children}</TestProviders>);
}

describe('App form components', () => {
  it('renders text inputs with their passed value', async () => {
    await renderWithProviders(
      <AppTextInput label="Deck name" value="Spanish" onChangeText={jest.fn()} />,
    );

    expect(screen.getByDisplayValue('Spanish')).toBeOnTheScreen();
  });

  it('clamps numeric input values before reporting changes', async () => {
    const onChange = jest.fn();

    await renderWithProviders(
      <AppNumberInput
        label="Pages"
        value={2}
        min={1}
        max={5}
        accessibilityLabel="Page count"
        onChange={onChange}
      />,
    );

    await fireEvent.changeText(screen.getByLabelText('Page count'), '10');
    await fireEvent.changeText(screen.getByLabelText('Page count'), '-4');

    expect(onChange).toHaveBeenNthCalledWith(1, 5);
    expect(onChange).toHaveBeenNthCalledWith(2, 1);
  });

  it('lays out form row children', async () => {
    await renderWithProviders(
      <AppFormRow>
        <Text>First</Text>
        <Text>Second</Text>
      </AppFormRow>,
    );

    expect(screen.getByText('First')).toBeOnTheScreen();
    expect(screen.getByText('Second')).toBeOnTheScreen();
  });
});
