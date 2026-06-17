import { act, renderHook } from '@testing-library/react-native';
import { STUDY_MODE_NEW_ID } from '@/constants/routes';
import type { StudyMode } from '@/types/models';

const mockStore = {
  studyModes: [] as StudyMode[],
  isLoading: false,
  addStudyMode: jest.fn(),
  updateStudyMode: jest.fn(),
  setActiveStudyMode: jest.fn(),
};
const mockUid = jest.fn();

jest.mock('@/store/FlashcardStoreContext', () => ({
  useFlashcardStore: () => mockStore,
}));

jest.mock('@/utils/id', () => ({
  uid: () => mockUid(),
}));

jest.mock('@/i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}));

// eslint-disable-next-line import/first
import { useStudyModeDraftController } from '@/features/settings/useStudyModeDraftController';
// eslint-disable-next-line import/first
import { defaultCompoundParams } from '@/features/settings/compoundSteps';

function makeMode(overrides: Partial<StudyMode> = {}): StudyMode {
  return {
    id: 'custom',
    name: 'Original mode',
    isBuiltIn: false,
    steps: [{ type: 'show_page', pageIndex: 0 }],
    updatedAt: 10,
    ...overrides,
  };
}

describe('useStudyModeDraftController', () => {
  beforeEach(() => {
    mockStore.studyModes = [makeMode()];
    mockStore.isLoading = false;
    mockStore.addStudyMode.mockClear();
    mockStore.updateStudyMode.mockClear();
    mockStore.setActiveStudyMode.mockClear();
    mockUid.mockReset();
  });

  it('creates one draft mode and saves it with a stable id', () => {
    mockUid.mockReturnValueOnce('new-mode').mockReturnValueOnce('new-step');
    const { result } = renderHook(() =>
      useStudyModeDraftController({ modeId: STUDY_MODE_NEW_ID }),
    );

    expect(result.current.draft?.id).toBe('new-mode');

    act(() => {
      result.current.setName('  New mode  ');
      result.current.addAtomicStep({ type: 'show_page', pageIndex: 0 });
    });
    expect(result.current.isValid).toBe(true);

    act(() => {
      expect(result.current.save()).toBe(true);
    });

    expect(mockStore.addStudyMode).toHaveBeenCalledTimes(1);
    expect(mockStore.addStudyMode).toHaveBeenCalledWith({
      id: 'new-mode',
      name: 'New mode',
      isBuiltIn: false,
      updatedAt: 0,
      steps: [{ id: 'new-step', type: 'show_page', pageIndex: 0 }],
    });
    expect(mockStore.updateStudyMode).not.toHaveBeenCalled();
  });

  it('saves an edited name by updating the existing id instead of adding a copy', () => {
    const { result } = renderHook(() =>
      useStudyModeDraftController({ modeId: 'custom' }),
    );

    act(() => {
      result.current.setName('Renamed mode');
    });

    act(() => {
      expect(result.current.save()).toBe(true);
    });

    expect(mockStore.updateStudyMode).toHaveBeenCalledTimes(1);
    expect(mockStore.updateStudyMode).toHaveBeenCalledWith({
      id: 'custom',
      name: 'Renamed mode',
      isBuiltIn: false,
      steps: [{ type: 'show_page', pageIndex: 0 }],
      updatedAt: 10,
    });
    expect(mockStore.addStudyMode).not.toHaveBeenCalled();
  });


  it('adds a compound as one saved step', () => {
    mockUid.mockReturnValueOnce('new-mode').mockReturnValueOnce('compound-1');
    const { result } = renderHook(() =>
      useStudyModeDraftController({ modeId: STUDY_MODE_NEW_ID }),
    );

    act(() => {
      result.current.setName('Compound mode');
      result.current.confirmCompoundStep(defaultCompoundParams('listen_grade'));
    });

    expect(result.current.draft?.steps).toHaveLength(1);
    expect(result.current.draft?.steps[0]).toMatchObject({
      id: 'compound-1',
      type: 'compound',
      version: 1,
      params: { kind: 'listen_grade' },
    });
  });

  it('edits compound params without expanding the step list', () => {
    const mode = makeMode({
      steps: [{ type: 'compound', version: 1, params: defaultCompoundParams('present_front') }],
    });
    mockStore.studyModes = [mode];
    const { result } = renderHook(() => useStudyModeDraftController({ modeId: 'custom' }));

    act(() => {
      result.current.editStep(mode, 0);
    });
    expect(result.current.compoundDialogMode).toBe('edit');

    act(() => {
      result.current.confirmCompoundStep({ kind: 'present_front', page: 2, speak: false });
    });

    expect(result.current.draft?.steps).toEqual([
      { type: 'compound', version: 1, params: { kind: 'present_front', page: 2, speak: false } },
    ]);
  });

  it('switches add dialog by expert mode without expanding existing compounds', () => {
    const { result } = renderHook(() =>
      useStudyModeDraftController({ modeId: STUDY_MODE_NEW_ID }),
    );

    act(() => {
      result.current.addStepToMode();
    });
    expect(result.current.compoundDialogOpen).toBe(true);
    expect(result.current.stepDialogOpen).toBe(false);

    act(() => {
      result.current.setCompoundDialogOpen(false);
      result.current.setExpertMode(true);
    });
    act(() => {
      result.current.addStepToMode();
    });
    expect(result.current.stepDialogOpen).toBe(true);
  });

  it('applies the audio template as two compound steps', () => {
    mockUid.mockReturnValueOnce('new-mode');
    const { result } = renderHook(() =>
      useStudyModeDraftController({ modeId: STUDY_MODE_NEW_ID }),
    );

    act(() => {
      result.current.applyTemplate('audio');
    });

    expect(result.current.selectedTemplate).toBe('audio');
    expect(result.current.draft?.steps).toEqual([
      { type: 'compound', version: 1, params: defaultCompoundParams('present_front') },
      { type: 'compound', version: 1, params: defaultCompoundParams('listen_grade') },
    ]);
  });
});
