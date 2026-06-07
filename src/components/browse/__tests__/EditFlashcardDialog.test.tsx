import React from 'react';
import { fireEvent, renderAsync, screen } from '@testing-library/react-native';

import { TestProviders } from '../../../test/renderWithAppProviders';
import { DEFAULT_STUDY_FILTER } from '../../../store/storeDataNormalization';
import { EditFlashcardDialog } from '../EditFlashcardDialog';

const group = {
  activeModeId: 'classic',
  activePageCount: 2,
  cards: [],
  id: 'deck-1',
  name: 'Spanish',
  pageLanguages: ['en-US', 'pl-PL'],
  pageNames: ['Front', 'Back'],
  studyFilter: DEFAULT_STUDY_FILTER,
};

describe('EditFlashcardDialog validation', () => {
  it('keeps save clickable and only renders validation when the parent asks for it', async () => {
    const onSave = jest.fn();

    await renderAsync(
      <TestProviders>
        <EditFlashcardDialog
          visible
          group={group}
          editPages={['', '']}
          onPagesChange={jest.fn()}
          onSave={onSave}
          onCancel={jest.fn()}
        />
      </TestProviders>,
    );

    expect(screen.queryByText('Fill at least 2 pages to save this flashcard.')).toBeNull();

    const saveButton = screen.getByText('Save');
    expect(saveButton).toBeEnabled();

    await fireEvent.press(saveButton);

    expect(onSave).toHaveBeenCalledTimes(1);
  });

  it('shows the validation message once the parent marks the edit as invalid', async () => {
    await renderAsync(
      <TestProviders>
        <EditFlashcardDialog
          visible
          group={group}
          editPages={['', '']}
          onPagesChange={jest.fn()}
          onSave={jest.fn()}
          onCancel={jest.fn()}
          validationMessage="Fill at least 2 pages to save this flashcard."
        />
      </TestProviders>,
    );

    expect(screen.getByText('Fill at least 2 pages to save this flashcard.')).toBeOnTheScreen();
  });
});
