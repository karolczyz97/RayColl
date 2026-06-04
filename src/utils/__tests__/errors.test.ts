import { describe, it, expect } from '@jest/globals';
import { getErrorMessage } from '../errors';

describe('getErrorMessage', () => {
  it('returns the message of an Error instance', () => {
    expect(getErrorMessage(new Error('test error'))).toBe('test error');
  });

  it('returns string representation of a string', () => {
    expect(getErrorMessage('something went wrong')).toBe('something went wrong');
  });

  it('returns string representation of null/undefined', () => {
    expect(getErrorMessage(null)).toBe('null');
    expect(getErrorMessage(undefined)).toBe('undefined');
  });

  it('returns string representation of a number', () => {
    expect(getErrorMessage(42)).toBe('42');
    expect(getErrorMessage(0)).toBe('0');
  });
});
