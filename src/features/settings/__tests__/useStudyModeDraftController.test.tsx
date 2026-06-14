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
import { useStudyModeDraftController } from '../useStudyModeDraftController';

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
      result.current.confirmAddStep();
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

  it('orders step type options by study-flow category', () => {
    const { result } = renderHook(() =>
      useStudyModeDraftController({ modeId: STUDY_MODE_NEW_ID }),
    );

    expect(Object.keys(result.current.stepLabels)).toEqual([
      'show_page',
      'show_all_pages',
      'wait_for_tap_to_reveal_next',
      'wait_for_tap_to_reveal',
      'speak_page',
      'listen_and_check',
      'dynamic_pause',
      'wait',
      'feedback_success',
      'feedback_error',
      'show_ratings',
      'auto_rate_from_answer',
      'auto_rate_fixed',
      'mark_failed',
      'next_card',
    ]);
  });
});
