import { useEffect } from 'react';
import { AppState } from 'react-native';
import { useSyncedRef } from '@/hooks/useSyncedRef';
import { ARCHIVE_PURGE_INTERVAL_MS } from '@/constants/archive';

export function useArchivePurger(purgeArchives: () => void) {
  const purgeRef = useSyncedRef(purgeArchives);

  useEffect(() => {
    const interval = setInterval(() => {
      purgeRef.current();
    }, ARCHIVE_PURGE_INTERVAL_MS);
    return () => clearInterval(interval);
  }, [purgeRef]);

  useEffect(() => {
    const sub = AppState.addEventListener('change', (nextState) => {
      if (nextState === 'active') {
        purgeRef.current();
      }
    });
    return () => sub.remove();
  }, [purgeRef]);
}
