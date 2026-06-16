import { describe, it, expect } from '@jest/globals';

import {
  setVisiblePageCountAction,
  deleteGroupAction,
  updateGroupAction,
  archiveGroupAction,
  restoreGroupAction,
  purgeExpiredArchivesAction,
  setCardOrderAction,
} from '../groupActions';
import { CARD_ORDERS } from '@/constants/cardOrder';
import { DEFAULT_STUDY_FILTER } from '../../storeDataNormalization';
import { createNewSrsState } from '../../../srs/srsEngine';
import type { Flashcard } from '@/types/models';

describe('groupActions', () => {
  function makeCard(overrides: Partial<Flashcard> = {}): Flashcard {
    return {
      id: 'c1',
      pages: ['front', 'back', 'extra'],
      srsState: createNewSrsState(),
      contentUpdatedAt: 0,
      srsUpdatedAt: 0,
      ...overrides,
    } as Flashcard;
  }

  const group = {
    id: 'g1',
    name: 'Deck',
    cards: [makeCard()],
    activeModeId: 'classic',
    studyFilter: DEFAULT_STUDY_FILTER,
    cardOrder: CARD_ORDERS.sequential,
    pageLanguages: ['en-US', 'pl-PL', 'en-US'],
    pageNames: ['Front', 'Back', 'Extra'],
    activePageCount: 3,
    updatedAt: 0,
  };

  describe('setVisiblePageCountAction', () => {
    it('does not truncate arrays when decreasing visible pages', () => {
      const decreased = setVisiblePageCountAction([group], 'g1', 2)[0];
      expect(decreased.pageNames.length).toBe(3);
      expect(decreased.pageLanguages.length).toBe(3);
      expect(decreased.pageNames[2]).toBe('Extra');
      expect(decreased.activePageCount).toBe(2);
    });

    it('recovers hidden pages when increasing', () => {
      const decreased = setVisiblePageCountAction([group], 'g1', 2)[0];
      const increased = setVisiblePageCountAction([decreased], 'g1', 3)[0];
      expect(increased.activePageCount).toBe(3);
      expect(increased.pageNames[2]).toBe('Extra');
    });

    it('generates new page names and defaults when expanding beyond existing', () => {
      const expanded = setVisiblePageCountAction([group], 'g1', 4)[0];
      expect(expanded.pageNames[0]).toBe('Front');
      expect(expanded.pageNames[1]).toBe('Back');
      expect(expanded.pageLanguages[0]).toBe('en-US');
      expect(expanded.pageLanguages[1]).toBe('pl-PL');
      expect(expanded.pageNames[2]).toBe('Extra');
      expect(expanded.pageNames[3]).toBe('Page 4');
      expect(expanded.pageLanguages[3]).toBe('en-US');
    });
  });

  describe('deleteGroupAction', () => {
    it('soft-deletes group by setting deletedAt', () => {
      const result = deleteGroupAction([group], 'g1');
      expect(result.length).toBe(1);
      expect(result[0].deletedAt).toBeTruthy();
    });
  });

  describe('updateGroupAction', () => {
    it('preserves tombstoned cards from canon', () => {
      const now = Date.now();
      const canonGroup = {
        ...group,
        cards: [
          makeCard({ id: 'c1', pages: ['a', 'b', 'c'] }),
          makeCard({ id: 'c2', pages: ['x', 'y', 'z'], deletedAt: now }),
        ],
      };
      const uiGroup = {
        ...group,
        cards: [makeCard({ id: 'c1', pages: ['updated', 'b', 'c'] })],
      };
      const updated = updateGroupAction([canonGroup], uiGroup)[0];
      expect(updated.cards.length).toBe(2);
      expect(updated.cards.some((c) => c.id === 'c2')).toBe(true);
      expect(updated.cards.find((c) => c.id === 'c1')!.pages[0]).toBe('updated');
    });
  });

  describe('archiveGroupAction', () => {
    it('sets archivedAt and bumps updatedAt', () => {
      const archived = archiveGroupAction([group], 'g1');
      expect(archived.length).toBe(1);
      expect(archived[0].archivedAt).toBeTruthy();
      expect(archived[0].archivedAt).toBeGreaterThan(0);
      expect(archived[0].updatedAt).toBeTruthy();
      expect(archived[0].updatedAt).toBeGreaterThan(0);
      expect(archived[0].deletedAt ?? 0).toBe(0);
    });
  });

  describe('restoreGroupAction', () => {
    it('clears archivedAt and bumps updatedAt', () => {
      const archived = archiveGroupAction([group], 'g1');
      const restored = restoreGroupAction(archived, 'g1');
      expect(restored[0].archivedAt).toBeFalsy();
      expect(restored[0].updatedAt).toBeTruthy();
      expect(restored[0].updatedAt).toBeGreaterThan(0);
    });
  });

  describe('purgeExpiredArchivesAction', () => {
    it('does not purge before retention period', () => {
      const now = Date.now();
      const recentGroup = { ...group, archivedAt: now - 13 * 24 * 60 * 60 * 1000 };
      const result = purgeExpiredArchivesAction([recentGroup], now, 14 * 24 * 60 * 60 * 1000);
      expect(result.changed).toBe(false);
      expect(result.groups[0].deletedAt).toBeFalsy();
    });

    it('purges after retention period', () => {
      const now = Date.now();
      const oldGroup = { ...group, archivedAt: now - 14 * 24 * 60 * 60 * 1000 };
      const result = purgeExpiredArchivesAction([oldGroup], now, 14 * 24 * 60 * 60 * 1000);
      expect(result.changed).toBe(true);
      expect(result.groups[0].deletedAt).toBeTruthy();
      expect(result.groups[0].deletedAt).toBeGreaterThan(0);
    });

    it('skips already-deleted groups', () => {
      const now = Date.now();
      const alreadyDeleted = {
        ...group,
        archivedAt: now - 20 * 24 * 60 * 60 * 1000,
        deletedAt: now,
      };
      const result = purgeExpiredArchivesAction([alreadyDeleted], now, 14 * 24 * 60 * 60 * 1000);
      expect(result.changed).toBe(false);
      expect(result.groups[0].deletedAt).toBe(now);
    });

    it('skips groups without archivedAt', () => {
      const now = Date.now();
      const result = purgeExpiredArchivesAction([group], now, 14 * 24 * 60 * 60 * 1000);
      expect(result.changed).toBe(false);
    });
  });

  describe('setCardOrderAction', () => {
    it('updates card order and bumps updatedAt', () => {
      const updated = setCardOrderAction([group], 'g1', CARD_ORDERS.hardest)[0];
      expect(updated.cardOrder).toBe(CARD_ORDERS.hardest);
      expect(updated.updatedAt).toBeGreaterThan(0);
    });
  });
});
