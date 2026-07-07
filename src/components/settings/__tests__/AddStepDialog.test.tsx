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
            mode="add"
            initialStep={null}
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

  it('prefills fields from initialStep and confirms the edited value', () => {
    const mockDismiss = jest.fn();
    const mockConfirm = jest.fn();

    const { getByLabelText } = render(
      <PaperProvider>
        <Portal.Host>
          <AddStepDialog
            visible={true}
            mode="edit"
            initialStep={{ id: 'step-1', type: 'wait', ms: 500 }}
            pageCount={3}
            onDismiss={mockDismiss}
            onConfirm={mockConfirm}
          />
        </Portal.Host>
      </PaperProvider>
    );

    const saveButton = getByLabelText('Save step button');
    fireEvent.press(saveButton);

    expect(mockConfirm).toHaveBeenCalledWith(
      expect.objectContaining({ type: 'wait', ms: 500 })
    );
  });

  it('prefills all dynamic_pause fields when editing', () => {
    const mockConfirm = jest.fn();

    const { getByLabelText } = render(
      <PaperProvider>
        <Portal.Host>
          <AddStepDialog
            visible={true}
            mode="edit"
            initialStep={{ id: 's', type: 'dynamic_pause', nextPageIndex: 2, pauseMultiplier: 3 }}
            pageCount={3}
            onDismiss={jest.fn()}
            onConfirm={mockConfirm}
          />
        </Portal.Host>
      </PaperProvider>
    );

    fireEvent.press(getByLabelText('Save step button'));

    expect(mockConfirm).toHaveBeenCalledWith(
      expect.objectContaining({ type: 'dynamic_pause', nextPageIndex: 2, pauseMultiplier: 3 })
    );
  });

  it('prefills all listen_and_check fields when editing', () => {
    const mockConfirm = jest.fn();

    const { getByLabelText } = render(
      <PaperProvider>
        <Portal.Host>
          <AddStepDialog
            visible={true}
            mode="edit"
            initialStep={{ id: 's', type: 'listen_and_check', pageIndex: 1, successThreshold: 85 }}
            pageCount={3}
            onDismiss={jest.fn()}
            onConfirm={mockConfirm}
          />
        </Portal.Host>
      </PaperProvider>
    );

    fireEvent.press(getByLabelText('Save step button'));

    expect(mockConfirm).toHaveBeenCalledWith(
      expect.objectContaining({ type: 'listen_and_check', pageIndex: 1, successThreshold: 85 })
    );
  });

  it('shows only the fields of the selected step type', () => {
    const { getByLabelText, queryByLabelText } = render(
      <PaperProvider>
        <Portal.Host>
          <AddStepDialog
            visible={true}
            mode="add"
            initialStep={null}
            pageCount={3}
            onDismiss={jest.fn()}
            onConfirm={jest.fn()}
          />
        </Portal.Host>
      </PaperProvider>
    );

    expect(getByLabelText('Page number input')).toBeTruthy();
    expect(queryByLabelText('Duration in milliseconds input')).toBeNull();
  });
});
