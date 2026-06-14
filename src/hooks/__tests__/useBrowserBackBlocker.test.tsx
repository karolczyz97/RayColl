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
  navigateUp: jest.fn(),
}));

// eslint-disable-next-line import/first
import { navigateUp } from '@/utils/navigation';
// eslint-disable-next-line import/first
import {
  createBrowserBackBlockerState,
  hasBrowserBackBlocker,
  useBrowserBackBlocker,
} from '../useBrowserBackBlocker';

const mockedNavigateUp = navigateUp as jest.MockedFunction<typeof navigateUp>;

interface FakeHistory {
  back: jest.Mock;
  pushState: jest.Mock;
  replaceState: jest.Mock;
  state: unknown;
}

let fakeHistory: FakeHistory;
let fakeLocation: { href: string };
let listeners: Record<string, ((event: Event) => void)[]>;
let captureListeners: Record<string, ((event: Event) => void)[]>;

function setHistoryState(state: unknown, path = '/study/group-1') {
  fakeHistory.state = state;
  fakeLocation.href = `http://localhost${path}`;
}

describe('useBrowserBackBlocker', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    listeners = {};
    captureListeners = {};
    fakeHistory = {
      back: jest.fn(),
      pushState: jest.fn((state: unknown, _title: string, href?: string | URL | null) => {
        fakeHistory.state = state;
        if (href) {
          fakeLocation.href = href.toString();
        }
      }),
      replaceState: jest.fn((state: unknown, _title: string, href?: string | URL | null) => {
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
      value: jest.fn((
        type: string,
        listener: (event: Event) => void,
        options?: boolean | AddEventListenerOptions,
      ) => {
        const targetListeners = options === true || (typeof options === 'object' && options.capture)
          ? captureListeners
          : listeners;
        targetListeners[type] = [...(targetListeners[type] ?? []), listener];
      }),
    });
    Object.defineProperty(window, 'removeEventListener', {
      configurable: true,
      value: jest.fn((
        type: string,
        listener: (event: Event) => void,
        options?: boolean | EventListenerOptions,
      ) => {
        const targetListeners = options === true || (typeof options === 'object' && options.capture)
          ? captureListeners
          : listeners;
        targetListeners[type] = (targetListeners[type] ?? []).filter(
          (registered) => registered !== listener,
        );
      }),
    });
    Object.defineProperty(window, 'dispatchEvent', {
      configurable: true,
      value: jest.fn((event: Event) => {
        for (const listener of captureListeners[event.type] ?? []) {
          listener(event);
        }
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

  it('refreshes an existing guarded entry so browser back stays on the study route', () => {
    setHistoryState(
      {
        expo: true,
        __raycollBrowserBackBlocker: true,
      },
      '/study/group-1',
    );

    renderHook(() =>
      useBrowserBackBlocker({
        active: true,
        onBackBlocked: jest.fn(),
      }),
    );

    expect(fakeHistory.replaceState).toHaveBeenCalledWith(
      { expo: true },
      '',
      'http://localhost/study/group-1',
    );
    expect(fakeHistory.pushState).toHaveBeenCalledTimes(1);
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

  it('restores the guarded study URL before later popstate listeners run', () => {
    const onBackBlocked = jest.fn();
    const routerListener = jest.fn(() => fakeLocation.href);

    renderHook(() =>
      useBrowserBackBlocker({
        active: true,
        onBackBlocked,
      }),
    );

    window.addEventListener('popstate', routerListener);

    act(() => {
      setHistoryState({ expo: true }, '/');
      window.dispatchEvent(new Event('popstate'));
    });

    expect(onBackBlocked).toHaveBeenCalledTimes(1);
    expect(routerListener).toHaveReturnedWith('http://localhost/study/group-1');
    expect(fakeLocation.href).toBe('http://localhost/study/group-1');
    expect(hasBrowserBackBlocker(window.history.state)).toBe(true);
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
    expect(mockedNavigateUp).toHaveBeenCalledTimes(1);

    historyBack.mockRestore();
  });

  it('blocks browser back again after the guard is temporarily disabled and re-enabled', () => {
    const onBackBlocked = jest.fn();
    const { rerender } = renderHook<() => void, { active: boolean }>(
      ({ active }) =>
        useBrowserBackBlocker({
          active,
          onBackBlocked,
        }),
      { initialProps: { active: true } },
    );

    act(() => {
      rerender({ active: false });
    });
    act(() => {
      rerender({ active: true });
    });
    act(() => {
      setHistoryState({ expo: true });
      window.dispatchEvent(new Event('popstate'));
    });

    expect(onBackBlocked).toHaveBeenCalledTimes(1);
  });
});
