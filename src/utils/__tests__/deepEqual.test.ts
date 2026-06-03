import assert from 'node:assert/strict';

import { deepEqual } from '../deepEqual';

export async function runTests() {
  // Primitives
  assert.equal(deepEqual(1, 1), true);
  assert.equal(deepEqual('a', 'a'), true);
  assert.equal(deepEqual(1, 2), false);
  assert.equal(deepEqual(1, '1'), false);
  assert.equal(deepEqual(null, null), true);
  assert.equal(deepEqual(null, undefined), false);

  // Key order must not matter (the old JSON.stringify approach was order-sensitive)
  assert.equal(deepEqual({ a: 1, b: 2 }, { b: 2, a: 1 }), true);

  // A present `undefined` value differs from a missing key (JSON.stringify dropped both)
  assert.equal(deepEqual({ a: 'foo' }, { a: undefined }), false);
  assert.equal(deepEqual({ a: undefined }, {}), false);

  // Nested objects and arrays
  assert.equal(
    deepEqual({ a: [1, { x: 1 }], b: 'z' }, { b: 'z', a: [1, { x: 1 }] }),
    true,
  );
  assert.equal(deepEqual({ a: [1, 2] }, { a: [1, 2, 3] }), false);
  assert.equal(deepEqual([1, 2, 3], [1, 2, 3]), true);
  assert.equal(deepEqual([1, 2], { 0: 1, 1: 2 }), false); // array vs object

  console.log('deepEqual tests passed');
}
