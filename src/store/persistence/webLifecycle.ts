export function shouldFlushOnVisibilityChange(visibilityState: string | undefined): boolean {
  return visibilityState === 'hidden';
}

export function createWebPersistenceHandlers(
  flushPersistence: () => Promise<void>,
  getVisibilityState: () => string | undefined,
) {
  const flush = () => {
    void flushPersistence();
  };

  return {
    handleBeforeUnload: flush,
    handlePageHide: flush,
    handleVisibilityChange: () => {
      if (shouldFlushOnVisibilityChange(getVisibilityState())) {
        flush();
      }
    },
  };
}
