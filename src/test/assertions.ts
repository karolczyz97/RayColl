export function assertEqual<T>(actual: T, expected: T, message?: string) {
  if (actual !== expected) {
    throw new Error(
      `Assertion failed: expected ${expected}, got ${actual}${message ? ` - ${message}` : ''}`,
    );
  }
}

export function assertOk(condition: boolean, message?: string) {
  if (!condition) {
    throw new Error(
      `Assertion failed: expected truthy value${message ? ` - ${message}` : ''}`,
    );
  }
}

export function assertThrows(fn: () => void, expectedMessagePart?: string) {
  try {
    fn();
  } catch (err: unknown) {
    const message = err instanceof Error ? err.message : String(err);
    if (expectedMessagePart && !message.includes(expectedMessagePart)) {
      throw new Error(
        `Assertion failed: expected error containing "${expectedMessagePart}", got "${message}"`,
      );
    }
    return;
  }
  throw new Error(
    `Assertion failed: expected function to throw${expectedMessagePart ? ` with "${expectedMessagePart}"` : ''}`,
  );
}

export function assertDeepEqual(actual: unknown, expected: unknown, message?: string) {
  const aStr = JSON.stringify(actual);
  const eStr = JSON.stringify(expected);
  if (aStr !== eStr) {
    throw new Error(`Assertion failed: expected ${eStr}, got ${aStr}${message ? ` - ${message}` : ''}`);
  }
}
