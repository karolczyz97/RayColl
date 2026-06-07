import { useCallback, useEffect, useRef } from 'react';
import { Platform } from 'react-native';

import { safeBack } from '@/utils/navigation';

export const BROWSER_BACK_BLOCKER_KEY = '__raycollBrowserBackBlocker';

type BrowserHistoryLike = Pick<History, 'back' | 'pushState' | 'state'>;

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

export function hasBrowserBackBlocker(state: unknown): boolean {
  return isRecord(state) && state[BROWSER_BACK_BLOCKER_KEY] === true;
}

export function createBrowserBackBlockerState(state: unknown): Record<string, unknown> {
  return {
    ...(isRecord(state) ? state : {}),
    [BROWSER_BACK_BLOCKER_KEY]: true,
  };
}

function canUseBrowserHistory(): boolean {
  return Platform.OS === 'web' && typeof window !== 'undefined';
}

interface UseBrowserBackBlockerOptions {
  active: boolean;
  onBackBlocked: () => void;
}

export function useBrowserBackBlocker({
  active,
  onBackBlocked,
}: UseBrowserBackBlockerOptions): () => void {
  const skippingControlledPopRef = useRef(false);

  const installBlocker = useCallback((history: BrowserHistoryLike, href: string) => {
    if (hasBrowserBackBlocker(history.state)) {
      return;
    }

    history.pushState(createBrowserBackBlockerState(history.state), '', href);
  }, []);

  const navigateBack = useCallback(() => {
    if (!canUseBrowserHistory() || !hasBrowserBackBlocker(window.history.state)) {
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

    installBlocker(window.history, window.location.href);

    const handlePopState = () => {
      if (skippingControlledPopRef.current) {
        return;
      }

      installBlocker(window.history, window.location.href);
      onBackBlocked();
    };

    window.addEventListener('popstate', handlePopState);
    return () => {
      window.removeEventListener('popstate', handlePopState);
    };
  }, [active, installBlocker, onBackBlocked]);

  useEffect(() => {
    if (active || !canUseBrowserHistory() || !hasBrowserBackBlocker(window.history.state)) {
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
