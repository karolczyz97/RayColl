import { describe, it, expect } from '@jest/globals';

import { toggleCategoryReducer, shouldShowCard } from '@/features/browse/browseFilter';
import type { SrsCardCategory } from '@/srs/srsEngine';

const ALL: SrsCardCategory[] = ['new', 'learning', 'review', 'mastered'];

describe('browseFilter', () => {
  describe('toggleCategoryReducer', () => {
    const nonEmpty4: SrsCardCategory[] = ['new', 'learning', 'review', 'mastered'];

    it('adds category when none selected', () => {
      expect(toggleCategoryReducer([], 'new', nonEmpty4)).toEqual(['new']);
    });

    it('adds second category', () => {
      expect(toggleCategoryReducer(['new'], 'review', nonEmpty4)).toEqual(['new', 'review']);
    });

    it('removes category when already selected', () => {
      expect(toggleCategoryReducer(['new', 'review'], 'new', nonEmpty4)).toEqual(['review']);
    });

    it('resets to [] when all 4 would be selected', () => {
      expect(toggleCategoryReducer(['new', 'review', 'mastered'], 'learning', nonEmpty4)).toEqual([]);
    });

    it('does NOT reset when only 3 selected', () => {
      expect(toggleCategoryReducer(['new', 'review'], 'mastered', nonEmpty4)).toEqual([
        'new',
        'review',
        'mastered',
      ]);
    });

    it('returns to original when toggling same category twice', () => {
      const step1 = toggleCategoryReducer([], 'new', nonEmpty4);
      const step2 = toggleCategoryReducer(step1, 'new', nonEmpty4);
      expect(step2).toEqual([]);
    });

    it('full cycle: select all one by one resets to empty', () => {
      let state: SrsCardCategory[] = [];
      for (const cat of ALL) {
        state = toggleCategoryReducer(state, cat, nonEmpty4);
      }
      expect(state).toEqual([]);
    });

    it('select 3 then deselect one stays filtered', () => {
      let state: SrsCardCategory[] = [];
      for (const cat of ALL.slice(0, 3)) {
        state = toggleCategoryReducer(state, cat, nonEmpty4);
      }
      expect(state).toEqual(['new', 'learning', 'review']);
      state = toggleCategoryReducer(state, 'new', nonEmpty4);
      expect(state).toEqual(['learning', 'review']);
    });

    it('toggling last remaining resets', () => {
      expect(toggleCategoryReducer(['new'], 'new', nonEmpty4)).toEqual([]);
    });

    it('resets when all 3 non-empty selected (mastered is empty)', () => {
      const nonEmpty3: SrsCardCategory[] = ['new', 'learning', 'review'];
      expect(toggleCategoryReducer(['new', 'learning'], 'review', nonEmpty3)).toEqual([]);
    });

    it('does NOT reset when selecting non-empty + empty category', () => {
      const nonEmpty2: SrsCardCategory[] = ['new', 'review'];
      expect(toggleCategoryReducer(['new'], 'mastered', nonEmpty2)).toEqual(['new', 'mastered']);
    });

    it('resets when last non-empty is selected (ignoring empty)', () => {
      const nonEmpty1: SrsCardCategory[] = ['review'];
      expect(toggleCategoryReducer(['review'], 'review', nonEmpty1)).toEqual([]);
    });
  });

  describe('shouldShowCard', () => {
    it('shows all cards when no categories selected', () => {
      for (const cat of ALL) {
        expect(shouldShowCard([], cat)).toBe(true);
      }
    });

    it('shows only matching category', () => {
      expect(shouldShowCard(['new'], 'new')).toBe(true);
      expect(shouldShowCard(['new'], 'learning')).toBe(false);
      expect(shouldShowCard(['new'], 'review')).toBe(false);
      expect(shouldShowCard(['new'], 'mastered')).toBe(false);
    });

    it('shows multiple matching categories', () => {
      const selected: SrsCardCategory[] = ['new', 'review'];
      expect(shouldShowCard(selected, 'new')).toBe(true);
      expect(shouldShowCard(selected, 'learning')).toBe(false);
      expect(shouldShowCard(selected, 'review')).toBe(true);
      expect(shouldShowCard(selected, 'mastered')).toBe(false);
    });
  });
});
