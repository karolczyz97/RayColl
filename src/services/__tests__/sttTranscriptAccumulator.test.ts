import { describe, expect, it } from '@jest/globals';

import { createSttTranscriptAccumulator } from '../sttTranscriptAccumulator';

describe('createSttTranscriptAccumulator', () => {
  it('accumulates multiple final segments', () => {
    const accumulator = createSttTranscriptAccumulator();

    expect(accumulator.addFinal('hello')).toBe('hello');
    expect(accumulator.addFinal('world')).toBe('hello world');
    expect(accumulator.getText()).toBe('hello world');
  });

  it('dedupes a repeated final segment', () => {
    const accumulator = createSttTranscriptAccumulator();

    accumulator.addFinal('hello world');
    expect(accumulator.addFinal('world')).toBe('hello world');
  });

  it('lets a full final replacement extend the current final text', () => {
    const accumulator = createSttTranscriptAccumulator();

    accumulator.addFinal('hello');
    expect(accumulator.addFinal('hello world')).toBe('hello world');
  });

  it('combines final text with the current interim without duplicating overlap', () => {
    const accumulator = createSttTranscriptAccumulator();

    accumulator.addFinal('hello');
    expect(accumulator.setInterim('hello world')).toBe('hello world');
    expect(accumulator.getText()).toBe('hello world');
  });

  it('clears stale interim text when a final segment arrives', () => {
    const accumulator = createSttTranscriptAccumulator();

    accumulator.setInterim('hel');
    accumulator.addFinal('hello');
    expect(accumulator.getText()).toBe('hello');
  });

  it('normalizes whitespace and ignores empty final segments', () => {
    const accumulator = createSttTranscriptAccumulator();

    accumulator.addFinal('  hello   world  ');
    expect(accumulator.addFinal('   ')).toBe('hello world');
    expect(accumulator.getFinalText()).toBe('hello world');
  });
});
