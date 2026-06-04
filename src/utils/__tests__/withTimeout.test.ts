import { describe, it, expect } from '@jest/globals';
import { withTimeout } from '../withTimeout';

describe('withTimeout', () => {
  it('resolves when the promise resolves before the timeout', async () => {
    const result = await withTimeout(Promise.resolve('ok'), 1000, 'test');
    expect(result).toBe('ok');
  });

  it('rejects when the promise takes longer than the timeout', async () => {
    const slow = new Promise((resolve) => setTimeout(resolve, 100));
    await expect(withTimeout(slow, 10, 'test')).rejects.toThrow('test timed out after 10ms');
  });

  it('clears the timeout when the promise resolves in time', async () => {
    jest.useFakeTimers();
    const promise = Promise.resolve('fast');
    const timed = withTimeout(promise, 1000, 'test');

    await expect(timed).resolves.toBe('fast');
    expect(jest.getTimerCount()).toBe(0);
    jest.useRealTimers();
  });

  it('rejects with the original error when the promise rejects', async () => {
    const failing = Promise.reject(new Error('original error'));
    await expect(withTimeout(failing, 1000, 'test')).rejects.toThrow('original error');
  });
});
