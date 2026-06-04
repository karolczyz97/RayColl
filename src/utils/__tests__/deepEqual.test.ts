import { describe, expect, it } from '@jest/globals';

import { deepEqual } from '../deepEqual';

describe('deepEqual', () => {
  it('primitives', () => {
    expect(deepEqual(1, 1)).toBe(true);
    expect(deepEqual('a', 'a')).toBe(true);
    expect(deepEqual(1, 2)).toBe(false);
    expect(deepEqual(1, '1')).toBe(false);
    expect(deepEqual(null, null)).toBe(true);
    expect(deepEqual(null, undefined)).toBe(false);
  });

  it('key order does not matter', () => {
    expect(deepEqual({ a: 1, b: 2 }, { b: 2, a: 1 })).toBe(true);
  });

  it('present undefined differs from missing key', () => {
    expect(deepEqual({ a: 'foo' }, { a: undefined })).toBe(false);
    expect(deepEqual({ a: undefined }, {})).toBe(false);
  });

  it('nested objects and arrays', () => {
    expect(deepEqual({ a: [1, { x: 1 }], b: 'z' }, { b: 'z', a: [1, { x: 1 }] })).toBe(true);
    expect(deepEqual({ a: [1, 2] }, { a: [1, 2, 3] })).toBe(false);
    expect(deepEqual([1, 2, 3], [1, 2, 3])).toBe(true);
    expect(deepEqual([1, 2], { 0: 1, 1: 2 })).toBe(false);
  });
});
