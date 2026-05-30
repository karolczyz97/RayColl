import { TOKENS } from '../../theme/tokens';
import {
  getDeterministicContainerWidth,
  getGridColumns,
  getGridGap,
  getGridItemWidth,
} from '../gridLayout';

function assertEqual<T>(actual: T, expected: T, message: string) {
  if (actual !== expected) {
    throw new Error(`${message}: expected ${expected}, got ${actual}`);
  }
}

export async function runTests() {
  console.log('\n--- Running Grid Layout Tests ---');

  assertEqual(getGridGap(320), TOKENS.layout.minGap, 'Grid gap should clamp to minimum on small widths');
  assertEqual(getGridGap(768), 20, 'Grid gap should scale up on medium widths');
  assertEqual(getGridGap(1600), TOKENS.layout.maxGap, 'Grid gap should clamp to maximum on large widths');

  assertEqual(
    getDeterministicContainerWidth(1600, TOKENS.layout.maxWidth, true, true),
    1136,
    'Dashboard width calculation should account for all padding layers',
  );

  const gap = getGridGap(850);
  assertEqual(
    getGridColumns(883, 8, TOKENS.layout.minCardWidth, TOKENS.layout.maxCols, gap),
    2,
    'Columns should not jump early before the real three-column threshold',
  );
  assertEqual(
    getGridColumns(884, 8, TOKENS.layout.minCardWidth, TOKENS.layout.maxCols, gap),
    3,
    'Columns should expand exactly at the three-column threshold',
  );
  assertEqual(
    getGridItemWidth(884, 3, gap),
    280,
    'Threshold width should still keep cards at the minimum supported width',
  );

  console.log('Grid layout tests passed');
}
