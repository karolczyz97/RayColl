import { useCallback, useEffect, useRef } from 'react';
import { useNavigation } from 'expo-router';
// usePreventRemove is not re-exported from expo-router; imported from the bundled
// React Navigation core, which the stack relies on for native gesture handling.
import { usePreventRemove } from 'expo-router/build/react-navigation/core';
import { useNavigationBlocker } from '@/contexts/NavigationBlockerContext';
import { useBrowserBackBlocker } from '@/hooks/useBrowserBackBlocker';
import { navigateUp } from '@/utils/navigation';

interface UseNavigationLeaveGuardOptions {
  active: boolean;
  onAttemptLeave: (navigate: () => void) => void;
}

export function useNavigationLeaveGuard({
  active,
  onAttemptLeave,
}: UseNavigationLeaveGuardOptions): () => void {
  const navigation = useNavigation();
  const navigateBackRef = useRef<() => void>(navigateUp);

  const attemptLeave = useCallback(() => {
    onAttemptLeave(navigateBackRef.current);
  }, [onAttemptLeave]);

  const navigateBackWithBrowserBlocker = useBrowserBackBlocker({
    active,
    onBackBlocked: attemptLeave,
  });

  useEffect(() => {
    navigateBackRef.current = navigateBackWithBrowserBlocker;
  }, [navigateBackWithBrowserBlocker]);

  useNavigationBlocker({
    active,
    requestLeave: onAttemptLeave,
  });

  usePreventRemove(active, () => {
    attemptLeave();
  });

  useEffect(() => {
    if (!active) return;
    const unsubscribe = navigation.addListener('beforeRemove', (e: {
      preventDefault: () => void;
    }) => {
      e.preventDefault();
      attemptLeave();
    });
    return unsubscribe;
  }, [active, attemptLeave, navigation]);

  useEffect(() => {
    if (!active) return;
    const addGestureListener = navigation.addListener as unknown as (
      type: 'gestureCancel',
      callback: () => void,
    ) => () => void;
    return addGestureListener('gestureCancel', () => {
      attemptLeave();
    });
  }, [active, attemptLeave, navigation]);

  return useCallback(() => {
    if (active) {
      attemptLeave();
      return;
    }
    navigateBackRef.current();
  }, [active, attemptLeave]);
}
