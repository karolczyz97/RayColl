import React from 'react';
import { fireEvent, renderAsync, screen } from '@testing-library/react-native';

import { TestProviders } from '../../../test/renderWithAppProviders';
import { ImportScreen } from '../ImportScreen';
import { useImportDeckDraft } from '../useImportDeckDraft';

jest.mock('@/store/FlashcardStoreContext', () => ({
  useFlashcardStore: () => ({
    isLoading: false,
  }),
}));

jest.mock('../useImportDeckDraft', () => ({
  useImportDeckDraft: jest.fn(),
}));

const mockedUseImportDeckDraft = useImportDeckDraft as jest.MockedFunction<typeof useImportDeckDraft>;

function createDraftMock(overrides: Partial<ReturnType<typeof useImportDeckDraft>> = {}) {
  return {
    cancelEdit: jest.fn(),
    cards: [],
    confirmDeleteCard: jest.fn(),
    customSep: '',
    deleteCardId: null,
    dismissImportError: jest.fn(),
    editPages: [],
    editingId: null,
    errorDismissed: false,
    firstRowIsHeader: false,
    handleHeaderToggle: jest.fn(),
    handleMovePage: jest.fn(),
    handleNameBlur: jest.fn(),
    handlePageCountChange: jest.fn(),
    handlePageNameChange: jest.fn(),
    handlePaste: jest.fn(),
    handlePickFile: jest.fn(),
    handleSepKeyChange: jest.fn(),
    handleSourceBlur: jest.fn(),
    handleTextChange: jest.fn(),
    importError: '',
    isImportBlocked: false,
    isImporting: false,
    name: '',
    pageCount: 2,
    pageLangs: ['', ''],
    pageNames: ['', ''],
    previewGroup: {
      activeModeId: 'classic',
      activePageCount: 2,
      cards: [],
      id: 'import-preview',
      name: 'Import Preview',
      pageLanguages: ['', ''],
      pageNames: ['', ''],
      studyFilter: 'new+review',
    },
    rawColumnCount: 2,
    rawText: '',
    saveEdit: jest.fn(),
    sepKey: 'semicolon',
    setDeleteCardId: jest.fn(),
    setEditPages: jest.fn(),
    setImportError: jest.fn(),
    setName: jest.fn(),
    setPageLangs: jest.fn(),
    showNameRequiredError: false,
    showSourceRequiredError: false,
    startEdit: jest.fn(),
    submitImport: jest.fn(),
    ...overrides,
  } satisfies ReturnType<typeof useImportDeckDraft>;
}

describe('ImportScreen validation', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('keeps import submit clickable when required fields are still empty', async () => {
    const draft = createDraftMock();
    mockedUseImportDeckDraft.mockReturnValue(draft);

    await renderAsync(
      <TestProviders>
        <ImportScreen />
      </TestProviders>,
    );

    const submitButton = screen.getByLabelText('Perform flashcard import button');
    expect(submitButton).toBeEnabled();

    await fireEvent.press(submitButton);

    expect(draft.submitImport).toHaveBeenCalledTimes(1);
  });
});
