import { useCallback, useEffect, useRef } from 'react';
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

export interface PersistenceQueueCallbacks {
  persistNow: (snapshot: PersistenceSnapshot) => Promise<void>;
  onSaving?: () => void;
  onSynced?: () => void;
  onError?: (error: unknown) => void;
}

export function createPersistenceQueueController({
  delayMs = 1200,
  callbacksRef,
}: {
  delayMs?: number;
  callbacksRef: { current: PersistenceQueueCallbacks };
}): PersistenceQueueController {
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
        callbacksRef.current.onSaving?.();

        try {
          await callbacksRef.current.persistNow(snapshot);

          if (!isDisposed) {
            callbacksRef.current.onSynced?.();
          }
        } catch (error) {
          if (!isDisposed) {
            callbacksRef.current.onError?.(error);
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
  const callbacksRef = useRef({ persistNow, onSaving, onSynced, onError });

  useEffect(() => {
    callbacksRef.current = { persistNow, onSaving, onSynced, onError };
  });

  const controllerRef = useRef<PersistenceQueueController | null>(null);

  useEffect(() => {
    if (!controllerRef.current) {
      controllerRef.current = createPersistenceQueueController({ delayMs, callbacksRef });
    }

    return () => {
      controllerRef.current?.dispose();
      controllerRef.current = null;
    };
  }, [delayMs]);

  const enqueue = useCallback(
    (snapshot: PersistenceSnapshot, options?: { immediate?: boolean }) => {
      controllerRef.current?.enqueue(snapshot, options);
    },
    [],
  );

  const flush = useCallback(() => {
    return controllerRef.current?.flush() ?? Promise.resolve();
  }, []);

  const cancel = useCallback(() => {
    controllerRef.current?.cancel();
  }, []);

  return { enqueue, flush, cancel };
}
