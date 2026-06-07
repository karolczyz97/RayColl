import { useCallback, useEffect, useRef } from 'react';
import { Platform } from 'react-native';

import { safeBack } from '@/utils/navigation';

export const STUDY_BROWSER_BACK_GUARD_KEY = '__raycollStudyBackGuard';

type BrowserHistoryLike = Pick<History, 'back' | 'pushState' | 'state'>;

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

export function hasStudyBrowserBackGuard(state: unknown): boolean {
  return isRecord(state) && state[STUDY_BROWSER_BACK_GUARD_KEY] === true;
}

export function createStudyBrowserBackGuardState(state: unknown): Record<string, unknown> {
  return {
    ...(isRecord(state) ? state : {}),
    [STUDY_BROWSER_BACK_GUARD_KEY]: true,
  };
}

function canUseBrowserHistory(): boolean {
  return Platform.OS === 'web' && typeof window !== 'undefined';
}

interface UseStudyBrowserBackGuardOptions {
  active: boolean;
  onBackBlocked: () => void;
}

export function useStudyBrowserBackGuard({
  active,
  onBackBlocked,
}: UseStudyBrowserBackGuardOptions): () => void {
  const skippingControlledPopRef = useRef(false);

  const installGuard = useCallback((history: BrowserHistoryLike, href: string) => {
    if (hasStudyBrowserBackGuard(history.state)) {
      return;
    }

    history.pushState(createStudyBrowserBackGuardState(history.state), '', href);
  }, []);

  const navigateBack = useCallback(() => {
    if (!canUseBrowserHistory() || !hasStudyBrowserBackGuard(window.history.state)) {
      safeBack();
      return;
    }

    skippingControlledPopRef.current = true;
    window.history.back();
    window.setTimeout(() => {
      skippingControlledPopRef.current = false;
      safeBack();
    }, 0);
  }, []);

  useEffect(() => {
    if (!active || !canUseBrowserHistory()) {
      return undefined;
    }

    installGuard(window.history, window.location.href);

    const handlePopState = () => {
      if (skippingControlledPopRef.current) {
        return;
      }

      installGuard(window.history, window.location.href);
      onBackBlocked();
    };

    window.addEventListener('popstate', handlePopState);
    return () => {
      window.removeEventListener('popstate', handlePopState);
    };
  }, [active, installGuard, onBackBlocked]);

  useEffect(() => {
    if (active || !canUseBrowserHistory() || !hasStudyBrowserBackGuard(window.history.state)) {
      return;
    }

    skippingControlledPopRef.current = true;
    window.history.back();
    window.setTimeout(() => {
      skippingControlledPopRef.current = false;
    }, 0);
  }, [active]);

  return navigateBack;
}
