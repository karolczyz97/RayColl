import React, { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';

export type NavigationBlockerRequestLeave = (navigate: () => void) => void;

interface NavigationBlockerContextValue {
  isNavigationBlocked: boolean;
  registerNavigationBlocker: (
    id: symbol,
    requestLeave: NavigationBlockerRequestLeave | null,
  ) => void;
  requestBlockedNavigation: (navigate: () => void) => boolean;
}

const NavigationBlockerContext = createContext<NavigationBlockerContextValue | null>(null);

function useNavigationBlockerContext(): NavigationBlockerContextValue {
  const context = useContext(NavigationBlockerContext);
  if (!context) {
    throw new Error('Navigation blocker hooks must be used inside NavigationBlockerProvider.');
  }

  return context;
}

export function NavigationBlockerProvider({ children }: { children: React.ReactNode }) {
  const blockersRef = useRef<Map<symbol, NavigationBlockerRequestLeave>>(new Map());
  const [blockerCount, setBlockerCount] = useState(0);

  const registerNavigationBlocker = useCallback(
    (id: symbol, requestLeave: NavigationBlockerRequestLeave | null) => {
      const blockers = blockersRef.current;

      if (requestLeave) {
        blockers.set(id, requestLeave);
      } else {
        blockers.delete(id);
      }

      setBlockerCount(blockers.size);
    },
    [],
  );

  const requestBlockedNavigation = useCallback((navigate: () => void) => {
    const handlers = Array.from(blockersRef.current.values());
    const requestLeave = handlers[handlers.length - 1];
    if (!requestLeave) {
      return false;
    }

    requestLeave(navigate);
    return true;
  }, []);

  const value = useMemo(
    () => ({
      isNavigationBlocked: blockerCount > 0,
      registerNavigationBlocker,
      requestBlockedNavigation,
    }),
    [blockerCount, registerNavigationBlocker, requestBlockedNavigation],
  );

  return (
    <NavigationBlockerContext.Provider value={value}>
      {children}
    </NavigationBlockerContext.Provider>
  );
}

export function useNavigationBlocker({
  active,
  requestLeave,
}: {
  active: boolean;
  requestLeave: NavigationBlockerRequestLeave;
}): void {
  const [blockerId] = useState(() => Symbol('navigation-blocker'));
  const { registerNavigationBlocker } = useNavigationBlockerContext();

  useEffect(() => {
    registerNavigationBlocker(blockerId, active ? requestLeave : null);
    return () => registerNavigationBlocker(blockerId, null);
  }, [active, blockerId, registerNavigationBlocker, requestLeave]);
}

export function useNavigationBlockerState() {
  const { isNavigationBlocked, requestBlockedNavigation } = useNavigationBlockerContext();

  return {
    isNavigationBlocked,
    requestBlockedNavigation,
  };
}
