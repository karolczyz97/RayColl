import {
  createWebPersistenceHandlers,
  shouldFlushOnVisibilityChange,
} from '../webLifecycle';

function assertEqual<T>(actual: T, expected: T, message: string) {
  if (actual !== expected) {
    throw new Error(`${message}: expected ${String(expected)}, got ${String(actual)}`);
  }
}

export async function runTests() {
  console.log('\n--- Running Web Lifecycle Tests ---');

  assertEqual(
    shouldFlushOnVisibilityChange('hidden'),
    true,
    'Visibility helper should flush when the page becomes hidden',
  );
  assertEqual(
    shouldFlushOnVisibilityChange('visible'),
    false,
    'Visibility helper should ignore visible pages',
  );

  let flushCount = 0;
  let visibilityState = 'visible';
  const handlers = createWebPersistenceHandlers(
    async () => {
      flushCount += 1;
    },
    () => visibilityState,
  );

  handlers.handleVisibilityChange();
  assertEqual(flushCount, 0, 'Visibility handler should ignore visible state');

  visibilityState = 'hidden';
  handlers.handleVisibilityChange();
  handlers.handlePageHide();
  handlers.handleBeforeUnload();
  assertEqual(flushCount, 3, 'Lifecycle handlers should flush on hidden, pagehide, and beforeunload');

  console.log('Web lifecycle tests passed');
}
