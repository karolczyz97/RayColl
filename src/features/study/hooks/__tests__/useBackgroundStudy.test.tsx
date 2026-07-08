import { act, renderHook } from '@testing-library/react-native';
import { AppState } from 'react-native';
import type { AppStateStatus, NativeEventSubscription } from 'react-native';
import type { AtomicStep } from '@/types/models';
import type { SessionEngine } from '@/features/study/session/SessionEngine';
import { useBackgroundStudy } from '@/features/study/hooks/useBackgroundStudy';
import { audioSessionManager } from '@/services/mediaSession';

jest.mock('expo-keep-awake', () => ({
  activateKeepAwakeAsync: jest.fn(() => Promise.resolve()),
  deactivateKeepAwake: jest.fn(),
}));

jest.mock('@/services/mediaSession', () => ({
  audioSessionManager: {
    activate: jest.fn(() => Promise.resolve()),
    deactivate: jest.fn(() => Promise.resolve()),
    updateNowPlaying: jest.fn(),
    getCurrentEngine: jest.fn(() => null),
  },
}));

jest.mock('@/i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}));

const manager = jest.mocked(audioSessionManager);

function makeEngine(): SessionEngine {
  return {
    setBackgroundMode: jest.fn(),
    setHolding: jest.fn(),
    pause: jest.fn(),
  } as unknown as SessionEngine;
}

const HANDS_FREE_STEPS: AtomicStep[] = [{ type: 'speak_page', pageIndex: 0 }];
const MANUAL_STEPS: AtomicStep[] = [{ type: 'show_all_pages' }, { type: 'show_ratings' }];

type HookProps = Parameters<typeof useBackgroundStudy>[0];

describe('useBackgroundStudy', () => {
  let appStateHandler: ((state: AppStateStatus) => void) | null = null;

  beforeEach(() => {
    jest.clearAllMocks();
    manager.getCurrentEngine.mockReturnValue(null);
    appStateHandler = null;
    jest.spyOn(AppState, 'addEventListener').mockImplementation((_type, handler) => {
      appStateHandler = handler as (state: AppStateStatus) => void;
      return { remove: jest.fn() } as unknown as NativeEventSubscription;
    });
  });

  afterEach(() => {
    jest.restoreAllMocks();
  });

  function renderBackgroundStudy(overrides: Partial<HookProps> = {}) {
    const props: HookProps = {
      engine: makeEngine(),
      groupName: 'G1',
      steps: HANDS_FREE_STEPS,
      dueCards: [],
      currentCardIndex: 0,
      isSessionFinished: false,
      backgroundPlaybackEnabled: true,
      ...overrides,
    };
    const utils = renderHook((p: HookProps) => useBackgroundStudy(p), {
      initialProps: props,
    });
    return { engine: props.engine, props, ...utils };
  }

  it('toggle OFF: going background pauses the engine and releases hold; no media session', () => {
    const { engine } = renderBackgroundStudy({ backgroundPlaybackEnabled: false });

    expect(engine.setBackgroundMode).toHaveBeenCalledWith(false);
    expect(manager.activate).not.toHaveBeenCalled();

    act(() => appStateHandler!('background'));
    expect(engine.setHolding).toHaveBeenCalledWith(false);
    expect(engine.pause).toHaveBeenCalledTimes(1);
  });

  it('toggle ON + hands-free mode: activates media session and backgroundMode; background does not pause', () => {
    const { engine } = renderBackgroundStudy();

    expect(engine.setBackgroundMode).toHaveBeenCalledWith(true);
    expect(manager.activate).toHaveBeenCalledWith(engine, 'G1', 'study.background_paused');

    act(() => appStateHandler!('background'));
    expect(engine.pause).not.toHaveBeenCalled();
    // Hold i tak zwolniony — "puszczono" może nie dotrzeć pod lockiem.
    expect(engine.setHolding).toHaveBeenCalledWith(false);
  });

  it('toggle ON + mode with manual rating: no media session, background pauses', () => {
    const { engine } = renderBackgroundStudy({ steps: MANUAL_STEPS });

    expect(engine.setBackgroundMode).toHaveBeenCalledWith(false);
    expect(manager.activate).not.toHaveBeenCalled();

    act(() => appStateHandler!('background'));
    expect(engine.pause).toHaveBeenCalledTimes(1);
  });

  it('returning to foreground after notification Stop reactivates the media session', () => {
    const { engine } = renderBackgroundStudy();
    manager.activate.mockClear();

    // Po "Stop" z notyfikacji manager nie trzyma już silnika.
    manager.getCurrentEngine.mockReturnValue(null);
    act(() => appStateHandler!('background'));
    act(() => appStateHandler!('active'));

    expect(manager.activate).toHaveBeenCalledTimes(1);
    expect(manager.activate).toHaveBeenCalledWith(engine, 'G1', 'study.background_paused');
  });

  it('does not reactivate on foreground when the media session is still bound to this engine', () => {
    const { engine } = renderBackgroundStudy();
    manager.activate.mockClear();

    manager.getCurrentEngine.mockReturnValue(engine);
    act(() => appStateHandler!('background'));
    act(() => appStateHandler!('active'));

    expect(manager.activate).not.toHaveBeenCalled();
  });

  it('session finish releases the media session', () => {
    const { rerender, props } = renderBackgroundStudy();
    expect(manager.deactivate).not.toHaveBeenCalled();

    rerender({ ...props, isSessionFinished: true });
    expect(manager.deactivate).toHaveBeenCalledTimes(1);
  });
});
