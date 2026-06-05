import { createInactivityTimer } from './sttInactivityTimer';

export function createSttSession(
  hardTimeoutMs: number,
  inactivityMs: number,
  onHardTimeout: () => void,
  onInactivityTimeout: () => void,
) {
  let resolved = false;

  const hardTimeout = setTimeout(() => {
    if (!resolved) onHardTimeout();
  }, hardTimeoutMs);

  const inactivityTimer = createInactivityTimer(() => {
    onInactivityTimeout();
  }, inactivityMs);

  return {
    resolved: () => resolved,
    setResolved: () => { resolved = true; },
    cancelTimers: () => {
      clearTimeout(hardTimeout);
      inactivityTimer.clear();
    },
    resetInactivity: () => inactivityTimer.reset(),
  };
}
