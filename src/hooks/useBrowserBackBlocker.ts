import { useCallback, useEffect, useRef } from 'react';
import { Platform } from 'react-native';

import { navigateUp } from '@/utils/navigation';

export const BROWSER_BACK_BLOCKER_KEY = '__raycollBrowserBackBlocker';

type BrowserHistoryLike = Pick<History, 'back' | 'pushState' | 'replaceState' | 'state'>;

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

export function removeBrowserBackBlockerState(state: unknown): Record<string, unknown> {
  const nextState = { ...(isRecord(state) ? state : {}) };
  delete nextState[BROWSER_BACK_BLOCKER_KEY];
  return nextState;
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
  const blockerInstalledRef = useRef(false);
  const guardedHrefRef = useRef<string | null>(null);
  const onBackBlockedRef = useRef(onBackBlocked);

  useEffect(() => {
    onBackBlockedRef.current = onBackBlocked;
  }, [onBackBlocked]);

  const installBlocker = useCallback((history: BrowserHistoryLike, href: string) => {
    guardedHrefRef.current = href;

    if (blockerInstalledRef.current && hasBrowserBackBlocker(history.state)) {
      return;
    }

    if (hasBrowserBackBlocker(history.state)) {
      history.replaceState(removeBrowserBackBlockerState(history.state), '', href);
    }

    history.pushState(createBrowserBackBlockerState(history.state), '', href);
    blockerInstalledRef.current = true;
  }, []);

  const navigateBack = useCallback(() => {
    if (!canUseBrowserHistory() || !hasBrowserBackBlocker(window.history.state)) {
      navigateUp();
      return;
    }

    // If the blocker wasn't active/installed, just go back normally.
    if (!blockerInstalledRef.current) {
      window.history.back();
      return;
    }

    // 1. Tell the popstate listener to ignore the next popstate event.
    skippingControlledPopRef.current = true;
    // 2. We're about to pop, so the blocker will be uninstalled.
    blockerInstalledRef.current = false;
    // 3. Perform the actual back navigation.
    window.history.back();
    navigateUp();
  }, []);

  useEffect(() => {
    if (!active || !canUseBrowserHistory()) {
      return undefined;
    }

    installBlocker(window.history, window.location.href);

    const handlePopState = () => {
      if (skippingControlledPopRef.current) {
        blockerInstalledRef.current = false;
        skippingControlledPopRef.current = false;
        return;
      }

      blockerInstalledRef.current = false;
      installBlocker(window.history, guardedHrefRef.current ?? window.location.href);
      onBackBlockedRef.current();
    };

    window.addEventListener('popstate', handlePopState, { capture: true });
    return () => {
      window.removeEventListener('popstate', handlePopState, { capture: true });
    };
  }, [active, installBlocker]);

  useEffect(() => {
    if (!active && blockerInstalledRef.current && Platform.OS === 'web') {
      skippingControlledPopRef.current = true;
      blockerInstalledRef.current = false;
      window.history.back();
    }
  }, [active]);

  return navigateBack;
}
