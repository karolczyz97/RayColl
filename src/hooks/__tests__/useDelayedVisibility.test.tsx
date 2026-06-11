import { renderHook, act } from '@testing-library/react-native';
import { useDelayedVisibility } from '../useDelayedVisibility';

const SHOW_DELAY = 1200;
const MIN_VISIBLE = 1500;

function renderVisibility(initialActive: boolean) {
  return renderHook(({ active }: { active: boolean }) => useDelayedVisibility(active), {
    initialProps: { active: initialActive },
  });
}

describe('useDelayedVisibility', () => {
  beforeEach(() => {
    jest.useFakeTimers();
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  it('never shows for a short pulse', () => {
    const { result, rerender } = renderVisibility(false);

    rerender({ active: true });
    act(() => {
      jest.advanceTimersByTime(100);
    });
    rerender({ active: false });
    act(() => {
      jest.advanceTimersByTime(10_000);
    });

    expect(result.current).toBe(false);
  });

  it('never shows for rapid repeated pulses', () => {
    const { result, rerender } = renderVisibility(false);

    for (let i = 0; i < 5; i++) {
      rerender({ active: true });
      act(() => {
        jest.advanceTimersByTime(80);
      });
      rerender({ active: false });
      act(() => {
        jest.advanceTimersByTime(80);
      });
    }
    act(() => {
      jest.advanceTimersByTime(10_000);
    });

    expect(result.current).toBe(false);
  });

  it('shows after the delay when continuously active', () => {
    const { result, rerender } = renderVisibility(false);

    rerender({ active: true });
    act(() => {
      jest.advanceTimersByTime(SHOW_DELAY - 1);
    });
    expect(result.current).toBe(false);

    act(() => {
      jest.advanceTimersByTime(1);
    });
    expect(result.current).toBe(true);
  });

  it('holds for the minimum visible time when deactivated right after showing', () => {
    const { result, rerender } = renderVisibility(false);

    rerender({ active: true });
    act(() => {
      jest.advanceTimersByTime(SHOW_DELAY);
    });
    expect(result.current).toBe(true);

    rerender({ active: false });
    act(() => {
      jest.advanceTimersByTime(MIN_VISIBLE - 1);
    });
    expect(result.current).toBe(true);

    act(() => {
      jest.advanceTimersByTime(1);
    });
    expect(result.current).toBe(false);
  });

  it('hides immediately when already visible longer than the minimum', () => {
    const { result, rerender } = renderVisibility(false);

    rerender({ active: true });
    act(() => {
      jest.advanceTimersByTime(SHOW_DELAY + MIN_VISIBLE + 500);
    });
    expect(result.current).toBe(true);

    rerender({ active: false });
    expect(result.current).toBe(false);
  });

  it('stays visible when reactivated during the hide hold', () => {
    const { result, rerender } = renderVisibility(false);

    rerender({ active: true });
    act(() => {
      jest.advanceTimersByTime(SHOW_DELAY);
    });
    rerender({ active: false });
    act(() => {
      jest.advanceTimersByTime(500);
    });
    rerender({ active: true });
    act(() => {
      jest.advanceTimersByTime(10_000);
    });

    expect(result.current).toBe(true);
  });

  it('cleans up pending timers on unmount', () => {
    const { rerender, unmount } = renderVisibility(false);

    rerender({ active: true });
    act(() => {
      jest.advanceTimersByTime(100);
    });
    unmount();

    expect(jest.getTimerCount()).toBe(0);
  });
});
