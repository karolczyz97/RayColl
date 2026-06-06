import { describe, it, expect } from '@jest/globals';

import { hexToRgba } from '../colorUtils';

describe('hexToRgba', () => {
  it('expands 3-digit hex', () => {
    expect(hexToRgba('#abc', 1)).toBe('rgba(170, 187, 204, 1)');
  });

  it('parses 6-digit hex', () => {
    expect(hexToRgba('#ff8800', 0.5)).toBe('rgba(255, 136, 0, 0.5)');
  });

  it('clamps alpha to 0..1', () => {
    expect(hexToRgba('#000000', 5)).toBe('rgba(0, 0, 0, 1)');
    expect(hexToRgba('#000000', -1)).toBe('rgba(0, 0, 0, 0)');
  });

  it('passes through rgb and applies the requested alpha', () => {
    expect(hexToRgba('rgb(10, 20, 30)', 0.5)).toBe('rgba(10, 20, 30, 0.5)');
  });

  it('multiplies an existing source alpha by the requested alpha', () => {
    expect(hexToRgba('rgba(0, 0, 0, 0.5)', 0.5)).toBe('rgba(0, 0, 0, 0.25)');
  });

  it('clamps out-of-range rgb channels to 0..255', () => {
    expect(hexToRgba('rgb(300, 20, 30)', 1)).toBe('rgba(255, 20, 30, 1)');
  });

  it('falls back to black for invalid input', () => {
    expect(hexToRgba('not-a-color', 0.3)).toBe('rgba(0, 0, 0, 0.3)');
  });
});
