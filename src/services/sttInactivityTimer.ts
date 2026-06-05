export interface InactivityTimer {
  reset: () => void;
  clear: () => void;
}

const DEFAULT_INACTIVITY_MS = 1500;

export function createInactivityTimer(
  onInactivity: () => void,
  inactivityMs = DEFAULT_INACTIVITY_MS,
): InactivityTimer {
  let timer: ReturnType<typeof setTimeout> | null = null;

  const reset = () => {
    if (timer) clearTimeout(timer);
    timer = setTimeout(onInactivity, inactivityMs);
  };

  const clear = () => {
    if (timer) {
      clearTimeout(timer);
      timer = null;
    }
  };

  return { reset, clear };
}
