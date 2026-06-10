import { describe, it, expect } from '@jest/globals';
import { coerceStringArray, padArray, filterLive, swapElements } from '../array';

describe('coerceStringArray', () => {
  it('returns string items from a valid array', () => {
    expect(coerceStringArray(['a', 'b'])).toEqual(['a', 'b']);
  });

  it('filters out non-string items', () => {
    expect(coerceStringArray(['a', 1, 'b', null, undefined])).toEqual(['a', 'b']);
  });

  it('returns empty array for non-array input', () => {
    expect(coerceStringArray(null)).toEqual([]);
    expect(coerceStringArray(undefined)).toEqual([]);
    expect(coerceStringArray(42)).toEqual([]);
    expect(coerceStringArray({})).toEqual([]);
  });

  it('returns empty array for empty array', () => {
    expect(coerceStringArray([])).toEqual([]);
  });
});

describe('padArray', () => {
  it('returns original array when length is sufficient', () => {
    expect(padArray(['a', 'b'], 2, 'x')).toEqual(['a', 'b']);
  });

  it('pads array to target length', () => {
    expect(padArray(['a'], 3, '')).toEqual(['a', '', '']);
  });

  it('returns original when longer than target', () => {
    expect(padArray(['a', 'b', 'c'], 1, 'x')).toEqual(['a', 'b', 'c']);
  });
});

describe('filterLive', () => {
  it('filters out items with deletedAt set', () => {
    const items = [
      { id: '1', deletedAt: null },
      { id: '2', deletedAt: 123 },
      { id: '3', deletedAt: undefined },
      { id: '4' },
    ];
    expect(filterLive(items)).toEqual([
      { id: '1', deletedAt: null },
      { id: '3', deletedAt: undefined },
      { id: '4' },
    ]);
  });

  it('returns empty array when all items are deleted', () => {
    expect(filterLive([{ deletedAt: 1 }, { deletedAt: 2 }])).toEqual([]);
  });

  it('returns all items when none are deleted', () => {
    const items = [{ id: '1' }, { deletedAt: null }];
    expect(filterLive(items)).toEqual(items);
  });

  it('returns the same array reference when nothing is tombstoned', () => {
    const items = [{ id: '1' }, { id: '2', deletedAt: null }];
    expect(filterLive(items)).toBe(items);
  });

  it('returns a new array when a tombstone is filtered out', () => {
    const items = [{ id: '1' }, { id: '2', deletedAt: 1 }];
    expect(filterLive(items)).not.toBe(items);
  });
});

describe('swapElements', () => {
  it('swaps two elements', () => {
    expect(swapElements(['a', 'b', 'c'], 0, 2)).toEqual(['c', 'b', 'a']);
  });

  it('returns unchanged when indices are out of bounds', () => {
    expect(swapElements(['a', 'b'], -1, 0)).toEqual(['a', 'b']);
    expect(swapElements(['a', 'b'], 0, 5)).toEqual(['a', 'b']);
  });

  it('returns unchanged when indices are equal', () => {
    expect(swapElements(['a', 'b'], 1, 1)).toEqual(['a', 'b']);
  });
});
