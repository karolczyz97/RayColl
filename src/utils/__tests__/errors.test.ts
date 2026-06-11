import { describe, it, expect } from '@jest/globals';
import { getErrorMessage, isOfflineError } from '../errors';

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

describe('isOfflineError', () => {
  it('detects Firestore client-offline message', () => {
    expect(isOfflineError(new Error('Failed to get document because the client is offline.'))).toBe(true);
  });

  it('detects code: unavailable object', () => {
    expect(isOfflineError({ code: 'unavailable' })).toBe(true);
  });

  it('detects code with namespace prefix (firestore/unavailable)', () => {
    expect(isOfflineError({ code: 'firestore/unavailable' })).toBe(true);
  });

  it('detects auth/network-request-failed code', () => {
    expect(isOfflineError({ code: 'auth/network-request-failed' })).toBe(true);
  });

  it('detects web TypeError "Failed to fetch"', () => {
    expect(isOfflineError(new TypeError('Failed to fetch'))).toBe(true);
  });

  it('detects cloud-load timeout string', () => {
    expect(isOfflineError('Cloud load timed out after 8000ms')).toBe(true);
  });

  it('detects message containing "network error"', () => {
    expect(isOfflineError(new Error('network error: connection refused'))).toBe(true);
  });

  it('does NOT match local persistence timeout', () => {
    expect(isOfflineError('Local persistence timed out after 8000ms')).toBe(false);
  });

  it('does NOT match permission-denied error', () => {
    expect(isOfflineError(new Error('permission-denied: missing rights'))).toBe(false);
  });

  it('returns false for null', () => {
    expect(isOfflineError(null)).toBe(false);
  });

  it('returns false for undefined', () => {
    expect(isOfflineError(undefined)).toBe(false);
  });

  it('returns false for empty string', () => {
    expect(isOfflineError('')).toBe(false);
  });

  it('returns false for plain object without code or message', () => {
    expect(isOfflineError({ foo: 'bar' })).toBe(false);
  });
});
