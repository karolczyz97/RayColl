import { describe, it, expect, jest } from '@jest/globals';
import {
  createWebPersistenceHandlers,
  shouldFlushOnVisibilityChange,
} from '../../useStorePersistence';

jest.mock('../firebasePersistence', () => ({
  saveCloudData: jest.fn(),
}));

jest.mock('../localPersistence', () => ({
  saveLocalData: jest.fn(),
}));

describe('webLifecycle', () => {
  it('flushes only when the page becomes hidden', () => {
    expect(shouldFlushOnVisibilityChange('hidden')).toBe(true);
    expect(shouldFlushOnVisibilityChange('visible')).toBe(false);
  });

  it('flushes on hidden, pagehide and beforeunload', () => {
    let flushCount = 0;
    let visibilityState = 'visible';
    const handlers = createWebPersistenceHandlers(
      async () => {
        flushCount += 1;
      },
      () => visibilityState,
    );

    handlers.handleVisibilityChange();
    expect(flushCount).toBe(0);

    visibilityState = 'hidden';
    handlers.handleVisibilityChange();
    handlers.handlePageHide();
    handlers.handleBeforeUnload();
    expect(flushCount).toBe(3);
  });
});
