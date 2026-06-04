import { useEffect, useMemo } from 'react';
import type { StoreData } from '@/types/models';

export interface PersistenceSnapshot extends StoreData {
  uid: string | null;
}

interface PersistenceQueueOptions {
  delayMs?: number;
  persistNow: (snapshot: PersistenceSnapshot) => Promise<void>;
  onSaving?: () => void;
  onSynced?: () => void;
  onError?: (error: unknown) => void;
}

interface PersistenceQueueController {
  enqueue: (snapshot: PersistenceSnapshot, options?: { immediate?: boolean }) => void;
  flush: () => Promise<void>;
  cancel: () => void;
  dispose: () => void;
}

export function createPersistenceQueueController({
  delayMs = 1200,
  persistNow,
  onSaving,
  onSynced,
  onError,
}: PersistenceQueueOptions): PersistenceQueueController {
  let pendingSnapshot: PersistenceSnapshot | null = null;
  let timer: ReturnType<typeof setTimeout> | null = null;
  let isDisposed = false;
  let sequence = Promise.resolve();

  const clearTimer = () => {
    if (timer) {
      clearTimeout(timer);
      timer = null;
    }
  };

  const flush = async () => {
    clearTimer();

    const run = async () => {
      while (pendingSnapshot && !isDisposed) {
        const snapshot = pendingSnapshot;
        pendingSnapshot = null;
        onSaving?.();

        try {
          await persistNow(snapshot);

          if (!isDisposed) {
            onSynced?.();
          }
        } catch (error) {
          if (!isDisposed) {
            onError?.(error);
          }
        }
      }
    };

    sequence = sequence.then(run, run);
    await sequence;
  };

  const enqueue = (snapshot: PersistenceSnapshot, options?: { immediate?: boolean }) => {
    pendingSnapshot = snapshot;

    if (options?.immediate) {
      void flush();
      return;
    }

    clearTimer();
    timer = setTimeout(() => {
      void flush();
    }, delayMs);
  };

  const cancel = () => {
    pendingSnapshot = null;
    clearTimer();
  };

  const dispose = () => {
    isDisposed = true;
    clearTimer();
  };

  return { enqueue, flush, cancel, dispose };
}

export function usePersistenceQueue({
  delayMs = 1200,
  persistNow,
  onSaving,
  onSynced,
  onError,
}: PersistenceQueueOptions) {
  const controller = useMemo(
    () =>
      createPersistenceQueueController({
        delayMs,
        persistNow,
        onSaving,
        onSynced,
        onError,
      }),
    [delayMs, onError, onSaving, onSynced, persistNow],
  );

  useEffect(() => {
    return () => {
      controller.dispose();
    };
  }, [controller]);

  return {
    enqueue: controller.enqueue,
    flush: controller.flush,
    cancel: controller.cancel,
  };
}
