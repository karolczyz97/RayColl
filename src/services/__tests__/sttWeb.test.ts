import { describe, it, expect, jest, beforeEach, afterEach } from '@jest/globals';

import { WebSttService } from '../sttWeb';

interface FakeResultEvent {
  resultIndex: number;
  results: {
    length: number;
    [index: number]: { isFinal: boolean; [alt: number]: { transcript: string } };
  };
}
interface FakeErrorEvent {
  error: string;
}

let instances: FakeRecognition[] = [];

class FakeRecognition {
  lang = '';
  interimResults = false;
  maxAlternatives = 0;
  continuous = false;
  onstart: (() => void) | null = null;
  onresult: ((event: FakeResultEvent) => void) | null = null;
  onend: (() => void) | null = null;
  onerror: ((event: FakeErrorEvent) => void) | null = null;
  start = jest.fn();
  stop = jest.fn();

  constructor() {
    instances.push(this);
  }
}

function finalResultEvent(transcript: string): FakeResultEvent {
  return { resultIndex: 0, results: { length: 1, 0: { isFinal: true, 0: { transcript } } } };
}

describe('WebSttService teardown', () => {
  let originalWindow: unknown;

  beforeEach(() => {
    jest.useFakeTimers();
    instances = [];
    originalWindow = (globalThis as { window?: unknown }).window;
    (globalThis as { window?: unknown }).window = { SpeechRecognition: FakeRecognition };
  });

  afterEach(() => {
    jest.useRealTimers();
    (globalThis as { window?: unknown }).window = originalWindow;
  });

  it('detaches callbacks and stops recognition when the engine ends', async () => {
    const service = new WebSttService();
    const promise = service.startListening({ language: 'en-US' });
    const rec = instances[0];
    expect(rec.start).toHaveBeenCalledTimes(1);

    rec.onresult?.(finalResultEvent('hello'));
    rec.onend?.();

    await expect(promise).resolves.toBe('hello');
    expect(rec.stop).toHaveBeenCalled();
    expect(rec.onresult).toBeNull();
    expect(rec.onend).toBeNull();
    expect(rec.onerror).toBeNull();
  });

  it('defensively stops a lingering previous recognition when a new session starts', () => {
    const service = new WebSttService();
    // First session never ends (no onend) -> stays attached to the service.
    void service.startListening({ language: 'en-US' });
    const first = instances[0];
    expect(first.stop).not.toHaveBeenCalled();

    // Starting again must stop & detach the lingering instance before creating a new one.
    void service.startListening({ language: 'en-US' });
    expect(instances).toHaveLength(2);
    expect(first.stop).toHaveBeenCalled();
    expect(first.onresult).toBeNull();
    expect(first.onend).toBeNull();
    expect(instances[1].start).toHaveBeenCalledTimes(1);
  });

  it('stops recognition and detaches handlers on hard timeout', async () => {
    const service = new WebSttService();
    const promise = service.startListening({ language: 'en-US', timeoutMs: 5000 });
    const rec = instances[0];

    jest.advanceTimersByTime(5000);

    await expect(promise).resolves.toBe('');
    expect(rec.stop).toHaveBeenCalled();
    expect(rec.onend).toBeNull();
  });
});
