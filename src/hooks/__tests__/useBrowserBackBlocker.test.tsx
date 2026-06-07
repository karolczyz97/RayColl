import { act, renderHook } from '@testing-library/react-native';

jest.mock('react-native', () => {
  const actual = jest.requireActual('react-native');
  return new Proxy(actual, {
    get(target, prop) {
      if (prop === 'Platform') {
        return {
          ...target.Platform,
          OS: 'web',
        };
      }
      return target[prop];
    },
  });
});

jest.mock('@/utils/navigation', () => ({
  safeBack: jest.fn(),
}));

// eslint-disable-next-line import/first
import { safeBack } from '@/utils/navigation';
// eslint-disable-next-line import/first
import {
  createBrowserBackBlockerState,
  hasBrowserBackBlocker,
  useBrowserBackBlocker,
} from '../useBrowserBackBlocker';

const mockedSafeBack = safeBack as jest.MockedFunction<typeof safeBack>;

interface FakeHistory {
  back: jest.Mock;
  pushState: jest.Mock;
  state: unknown;
}

let fakeHistory: FakeHistory;
let fakeLocation: { href: string };
let listeners: Record<string, ((event: Event) => void)[]>;

function setHistoryState(state: unknown, path = '/study/group-1') {
  fakeHistory.state = state;
  fakeLocation.href = `http://localhost${path}`;
}

describe('useBrowserBackBlocker', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    listeners = {};
    fakeHistory = {
      back: jest.fn(),
      pushState: jest.fn((state: unknown, _title: string, href?: string | URL | null) => {
        fakeHistory.state = state;
        if (href) {
          fakeLocation.href = href.toString();
        }
      }),
      state: null,
    };
    fakeLocation = { href: 'http://localhost/study/group-1' };

    Object.defineProperty(window, 'history', {
      configurable: true,
      value: fakeHistory,
    });
    Object.defineProperty(window, 'location', {
      configurable: true,
      value: fakeLocation,
    });
    Object.defineProperty(window, 'addEventListener', {
      configurable: true,
      value: jest.fn((type: string, listener: (event: Event) => void) => {
        listeners[type] = [...(listeners[type] ?? []), listener];
      }),
    });
    Object.defineProperty(window, 'removeEventListener', {
      configurable: true,
      value: jest.fn((type: string, listener: (event: Event) => void) => {
        listeners[type] = (listeners[type] ?? []).filter((registered) => registered !== listener);
      }),
    });
    Object.defineProperty(window, 'dispatchEvent', {
      configurable: true,
      value: jest.fn((event: Event) => {
        for (const listener of listeners[event.type] ?? []) {
          listener(event);
        }
        return true;
      }),
    });
    Object.defineProperty(window, 'setTimeout', {
      configurable: true,
      value: setTimeout,
    });

    setHistoryState({ expo: true });
  });

  afterEach(() => {
    setHistoryState(null, '/');
    jest.useRealTimers();
  });

  it('preserves existing history state when adding the blocker marker', () => {
    expect(createBrowserBackBlockerState({ expo: true })).toEqual({
      expo: true,
      __raycollBrowserBackBlocker: true,
    });
  });

  it('pushes a guarded browser history entry while active', () => {
    renderHook(() =>
      useBrowserBackBlocker({
        active: true,
        onBackBlocked: jest.fn(),
      }),
    );

    expect(hasBrowserBackBlocker(window.history.state)).toBe(true);
  });

  it('opens the blocked path when browser back emits popstate', () => {
    const onBackBlocked = jest.fn();

    renderHook(() =>
      useBrowserBackBlocker({
        active: true,
        onBackBlocked,
      }),
    );

    act(() => {
      window.dispatchEvent(new Event('popstate'));
    });

    expect(onBackBlocked).toHaveBeenCalledTimes(1);
  });

  it('reinstalls the blocker so a cancelled browser back can be blocked again', () => {
    const onBackBlocked = jest.fn();

    renderHook(() =>
      useBrowserBackBlocker({
        active: true,
        onBackBlocked,
      }),
    );

    act(() => {
      setHistoryState({ expo: true });
      window.dispatchEvent(new Event('popstate'));
    });
    act(() => {
      setHistoryState({ expo: true });
      window.dispatchEvent(new Event('popstate'));
    });

    expect(onBackBlocked).toHaveBeenCalledTimes(2);
    expect(fakeHistory.pushState).toHaveBeenCalledTimes(3);
    expect(hasBrowserBackBlocker(window.history.state)).toBe(true);
  });

  it('skips the blocker entry before falling back to normal app back navigation', () => {
    jest.useFakeTimers();
    const historyBack = jest.spyOn(window.history, 'back').mockImplementation(() => undefined);
    const { result } = renderHook(() =>
      useBrowserBackBlocker({
        active: true,
        onBackBlocked: jest.fn(),
      }),
    );

    act(() => {
      result.current();
    });

    expect(historyBack).toHaveBeenCalledTimes(1);
    expect(mockedSafeBack).not.toHaveBeenCalled();

    act(() => {
      jest.runOnlyPendingTimers();
    });

    expect(mockedSafeBack).toHaveBeenCalledTimes(1);
    historyBack.mockRestore();
  });
});
