import { act, renderHook } from '@testing-library/react-native';
import { createNewSrsState } from '@/srs/srsEngine';
import type { Flashcard } from '@/types/models';

const mockSession = {
  dueCards: [] as Flashcard[],
  sessionState: { currentCardIndex: 0, isSessionFinished: false } as {
    currentCardIndex: number;
    isSessionFinished: boolean;
  },
  handleRating: jest.fn(),
  handleCardPress: jest.fn(),
  startSession: jest.fn(),
  stopSession: jest.fn(),
  endSession: jest.fn(),
  pauseSession: jest.fn(),
  resumeSession: jest.fn(),
  setHolding: jest.fn(),
  restartSession: jest.fn(),
  restartFailed: jest.fn(),
  failedCount: 0,
  clearError: jest.fn(),
};

const mockFlushPersistence = jest.fn().mockResolvedValue(undefined);
const mockGetDueCards = jest.fn().mockReturnValue([]);

jest.mock('@/hooks/useStudySession', () => ({
  useStudySession: () => mockSession,
}));

jest.mock('@/store/FlashcardStoreContext', () => ({
  useFlashcardStore: () => ({
    groups: [{ id: 'g1', name: 'G1', cards: [], activeModeId: 'm1' }],
    studyModes: [{ id: 'm1', steps: [] }],
    isLoading: false,
    reviewFlashcard: jest.fn(),
    getDueCards: mockGetDueCards,
    flushPersistence: mockFlushPersistence,
  }),
}));

jest.mock('expo-router', () => ({
  useLocalSearchParams: () => ({ groupId: 'g1' }),
}));

// eslint-disable-next-line import/first
import { useStudyPageController } from '@/features/study/hooks/useStudyPageController';

function makeDueCard(): Flashcard {
  return {
    id: 'c1',
    pages: ['front'],
    srsState: createNewSrsState(),
    contentUpdatedAt: 0,
    srsUpdatedAt: 0,
  };
}

describe('useStudyPageController — exit flow', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockSession.dueCards = [];
    mockSession.sessionState = { currentCardIndex: 0, isSessionFinished: false };
    mockSession.failedCount = 0;
  });

  it('opens the confirm dialog (no navigation) when a session is active', () => {
    mockSession.dueCards = [makeDueCard()];
    const navigateBack = jest.fn();
    const { result } = renderHook(() => useStudyPageController({ navigateBack }));

    expect(result.current.isExitBlocked).toBe(true);
    act(() => result.current.handleBack());

    expect(result.current.showExitConfirm).toBe(true);
    expect(navigateBack).not.toHaveBeenCalled();
    expect(mockSession.endSession).not.toHaveBeenCalled();
    // The run must freeze while the dialog is up.
    expect(mockSession.pauseSession).toHaveBeenCalledTimes(1);
  });

  it('confirming exit ends the session for the summary, without navigating', () => {
    mockSession.dueCards = [makeDueCard()];
    const navigateBack = jest.fn();
    const { result } = renderHook(() => useStudyPageController({ navigateBack }));

    act(() => result.current.handleBack());
    act(() => result.current.confirmExit());

    expect(mockSession.endSession).toHaveBeenCalledTimes(1);
    expect(mockSession.resumeSession).not.toHaveBeenCalled();
    expect(result.current.showExitConfirm).toBe(false);
    expect(result.current.endedEarly).toBe(true);
    expect(navigateBack).not.toHaveBeenCalled();
  });

  it('cancelling keeps the session and hides the dialog', () => {
    mockSession.dueCards = [makeDueCard()];
    const { result } = renderHook(() => useStudyPageController({ navigateBack: jest.fn() }));

    act(() => result.current.handleBack());
    act(() => result.current.cancelExit());

    expect(result.current.showExitConfirm).toBe(false);
    expect(result.current.endedEarly).toBe(false);
    expect(mockSession.endSession).not.toHaveBeenCalled();
    // Cancelling resumes the paused run.
    expect(mockSession.resumeSession).toHaveBeenCalledTimes(1);
  });

  it('leaves immediately (no dialog) when the session is already finished', () => {
    mockSession.dueCards = [makeDueCard()];
    mockSession.sessionState = { currentCardIndex: 0, isSessionFinished: true };
    const navigateBack = jest.fn();
    const { result } = renderHook(() => useStudyPageController({ navigateBack }));

    expect(result.current.isExitBlocked).toBe(false);
    act(() => result.current.handleBack());

    expect(result.current.showExitConfirm).toBe(false);
    expect(navigateBack).toHaveBeenCalledTimes(1);
    expect(mockSession.endSession).not.toHaveBeenCalled();
  });

  it('leaves immediately when there are no due cards', () => {
    const navigateBack = jest.fn();
    const { result } = renderHook(() => useStudyPageController({ navigateBack }));

    expect(result.current.isExitBlocked).toBe(false);
    act(() => result.current.handleBack());

    expect(navigateBack).toHaveBeenCalledTimes(1);
    expect(mockSession.endSession).not.toHaveBeenCalled();
  });

  it('restarting from the summary clears endedEarly and restarts the session', () => {
    mockSession.dueCards = [makeDueCard()];
    const { result } = renderHook(() => useStudyPageController({ navigateBack: jest.fn() }));

    act(() => result.current.handleBack());
    act(() => result.current.confirmExit());
    expect(result.current.endedEarly).toBe(true);

    act(() => result.current.restartSession());

    expect(mockSession.restartSession).toHaveBeenCalledTimes(1);
    expect(result.current.endedEarly).toBe(false);
  });
});
