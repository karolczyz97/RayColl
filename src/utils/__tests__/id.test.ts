import { describe, it, expect } from '@jest/globals';
import { uid } from '../id';

describe('uid', () => {
  it('returns a string', () => {
    expect(typeof uid()).toBe('string');
  });

  it('returns a valid UUID format', () => {
    const uuidRegex = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
    expect(uid()).toMatch(uuidRegex);
  });

  it('returns unique values on subsequent calls', () => {
    const a = uid();
    const b = uid();
    expect(a).not.toBe(b);
  });
});
