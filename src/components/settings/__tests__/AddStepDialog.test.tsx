import React from 'react';
import { render, fireEvent } from '@testing-library/react-native';
import { PaperProvider, Portal } from 'react-native-paper';
import { AddStepDialog } from '@/components/settings/AddStepDialog';

jest.mock('@/i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}));

describe('AddStepDialog', () => {
  it('renders and allows confirming a default step', () => {
    const mockDismiss = jest.fn();
    const mockConfirm = jest.fn();

    const { getByLabelText } = render(
      <PaperProvider>
        <Portal.Host>
          <AddStepDialog
            visible={true}
            pageCount={3}
            onDismiss={mockDismiss}
            onConfirm={mockConfirm}
          />
        </Portal.Host>
      </PaperProvider>
    );

    const addButton = getByLabelText('Add step button');
    fireEvent.press(addButton);

    expect(mockConfirm).toHaveBeenCalledTimes(1);
    expect(mockConfirm).toHaveBeenCalledWith(
      expect.objectContaining({ type: 'show_page', pageIndex: 0 })
    );
  });
});
