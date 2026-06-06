import { describe, it, expect } from '@jest/globals';

import { validateBackupData, type BackupData } from '../backupValidation';
import type { FlashcardGroup, StudyMode } from '../../types/models';

function makeValidGroup(): FlashcardGroup {
  return {
    id: 'g1',
    name: 'Test Deck',
    cards: [
      {
        id: 'c1',
        pages: ['hello', 'world'],
        srsState: {
          difficulty: 3,
          stability: 10,
          repetitions: 5,
          state: 2,
          lastReviewTimestamp: Date.now(),
          nextReviewTimestamp: Date.now() + 86400000,
        },
      },
    ],
    activeModeId: 'classic',
    studyFilter: 'new+review',
    pageLanguages: ['en-US', 'pl-PL'],
    pageNames: ['Word', 'Translation'],
    activePageCount: 2,
  };
}

function makeValidMode(): StudyMode {
  return {
    id: 'classic',
    name: 'Classic',
    steps: [
      { type: 'show_page', pageIndex: 0 },
      { type: 'rate' },
    ],
    isBuiltIn: true,
  };
}

function makeValidBackup(): BackupData {
  return {
    groups: [makeValidGroup()],
    studyModes: [makeValidMode()],
    activityHeatmap: { '2025-01-01': 3 },
  };
}

describe('backupValidation', () => {
  it('accepts valid backup', () => {
    expect(() => validateBackupData(makeValidBackup())).not.toThrow();
  });

  it('accepts backup with optional fields', () => {
    const withOptional: BackupData = {
      ...makeValidBackup(),
      exportedAt: '2025-06-01T12:00:00.000Z',
    };
    expect(() => validateBackupData(withOptional)).not.toThrow();
  });

  it('throws on missing groups', () => {
    const { groups: _, ...noGroups } = makeValidBackup();
    expect(() => validateBackupData(noGroups)).toThrow();
  });

  it('throws on missing studyModes', () => {
    const { studyModes: _, ...noModes } = makeValidBackup();
    expect(() => validateBackupData(noModes)).toThrow();
  });

  it('throws on missing activityHeatmap', () => {
    const { activityHeatmap: _, ...noHeatmap } = makeValidBackup();
    expect(() => validateBackupData(noHeatmap)).toThrow();
  });

  it('throws on invalid card srsState.difficulty', () => {
    const bad = makeValidBackup();
    (bad.groups[0].cards[0].srsState as unknown as Record<string, unknown>).difficulty = 'easy';
    expect(() => validateBackupData(bad)).toThrow();
  });

  it('throws on invalid study mode step type', () => {
    const bad = makeValidBackup();
    bad.studyModes[0].steps.push({ type: 'unknown_type' } as never);
    expect(() => validateBackupData(bad)).toThrow();
  });

  it('throws on empty object', () => {
    expect(() => validateBackupData({} as BackupData)).toThrow();
  });

  describe('export structure contract', () => {
    it('contains all required keys', () => {
      const exportObj = { groups: [], studyModes: [], activityHeatmap: {} };
      expect('groups' in exportObj).toBe(true);
      expect('studyModes' in exportObj).toBe(true);
      expect('activityHeatmap' in exportObj).toBe(true);
    });
  });

  it('throws on null', () => {
    expect(() => validateBackupData(null)).toThrow('Backup data is not a valid JSON object.');
  });

  it('throws on null groups entry', () => {
    expect(() => validateBackupData({ groups: [null], studyModes: [], activityHeatmap: {} })).toThrow(
      'Each group must be a valid object.',
    );
  });

  it('throws on non-string group id', () => {
    expect(() => validateBackupData({ groups: [{ id: 123 }], studyModes: [], activityHeatmap: {} })).toThrow(
      'Each group must have a string id.',
    );
  });

  it('throws on non-string card pages', () => {
    const bad = makeValidBackup();
    (bad.groups[0].cards[0].pages as unknown[])[0] = 123;
    expect(() => validateBackupData(bad)).toThrow('must have string pages');
  });

  it('throws on a non-finite srsState number (Infinity)', () => {
    const bad = makeValidBackup();
    (bad.groups[0].cards[0].srsState as unknown as Record<string, unknown>).stability = Infinity;
    expect(() => validateBackupData(bad)).toThrow();
  });

  it('throws on successThreshold above 100', () => {
    const bad = makeValidBackup();
    bad.studyModes[0].steps.push({ type: 'listen_and_branch', pageIndex: 0, successThreshold: 150 });
    expect(() => validateBackupData(bad)).toThrow('successThreshold');
  });

  it('throws on a negative activity heatmap count', () => {
    const bad = makeValidBackup();
    bad.activityHeatmap['2025-01-02'] = -3;
    expect(() => validateBackupData(bad)).toThrow();
  });

});
