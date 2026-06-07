import React from 'react';
import { fireEvent, renderAsync, screen } from '@testing-library/react-native';

import { TestProviders } from '../../../test/renderWithAppProviders';
import { CreateStudyModeSection } from '../CreateStudyModeSection';

async function renderSection(overrides: Partial<React.ComponentProps<typeof CreateStudyModeSection>> = {}) {
  const props: React.ComponentProps<typeof CreateStudyModeSection> = {
    customSteps: [],
    formatStepSummary: jest.fn(() => 'Show page 1'),
    newModeName: '',
    onDismiss: jest.fn(),
    saveCustomMode: jest.fn(),
    setCustomSteps: jest.fn(),
    setEditingModeId: jest.fn(),
    setNewModeName: jest.fn(),
    setStepDialogOpen: jest.fn(),
    visible: true,
    ...overrides,
  };

  await renderAsync(
    <TestProviders>
      <CreateStudyModeSection {...props} />
    </TestProviders>,
  );

  return props;
}

describe('CreateStudyModeSection validation', () => {
  it('keeps save clickable and shows required errors only after submit', async () => {
    const props = await renderSection();

    expect(screen.queryByText('This field is required.')).toBeNull();
    expect(screen.queryByText('Add at least one step.')).toBeNull();

    await fireEvent.press(screen.getByLabelText('Save study mode'));

    expect(props.saveCustomMode).not.toHaveBeenCalled();
    expect(screen.getByText('This field is required.')).toBeOnTheScreen();
    expect(screen.getByText('Add at least one step.')).toBeOnTheScreen();
  });
});
