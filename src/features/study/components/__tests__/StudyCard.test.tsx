import React from 'react';
import { render } from '@testing-library/react-native';
import type { Flashcard, FlashcardGroup } from '@/types/models';
import { createNewSrsState } from '@/srs/srsEngine';
import { StudyCard } from '@/features/study/components/StudyCard';
import { CardPageSection } from '@/features/study/components/CardPageSection';

jest.mock('@/i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}));

jest.mock('@/hooks/usePulseAnimation', () => ({
  usePulseAnimation: () => ({}),
}));

function makeCard(): Flashcard {
  return {
    id: 'card-1',
    pages: ['front', 'back'],
    srsState: createNewSrsState(),
    contentUpdatedAt: 0,
    srsUpdatedAt: 0,
  };
}

function makeGroup(): FlashcardGroup {
  return {
    id: 'group-1',
    name: 'Deck',
    cards: [],
    activeModeId: 'classic',
    studyFilter: 'all',
    cardOrder: 'sequential',
    pageLanguages: ['en-US', 'en-US'],
    pageNames: ['Front', 'Back'],
    activePageCount: 2,
    updatedAt: 0,
  };
}

describe('StudyCard', () => {
  const originalRequestAnimationFrame = global.requestAnimationFrame;

  beforeAll(() => {
    global.requestAnimationFrame = (callback: FrameRequestCallback): number => {
      callback(0);
      return 0;
    };
  });

  afterAll(() => {
    global.requestAnimationFrame = originalRequestAnimationFrame;
  });

  it('does not reveal hidden pages just because rating buttons are visible', () => {
    const { UNSAFE_getAllByType } = render(
      <StudyCard
        currentCard={makeCard()}
        activeGroup={makeGroup()}
        revealedPages={[0]}
        peekedPageIndex={null}
        showRatingButtons
        waitingForTap={false}
        audioPageIndex={null}
        audioMode={null}
        onCardPress={jest.fn()}
        onHoldingChange={jest.fn()}
      />,
    );

    const pageSections = UNSAFE_getAllByType(CardPageSection);

    expect(pageSections.map((section) => section.props.isRevealed)).toEqual([true, false]);
  });

  it('reveals hidden pages only when the step state says they are revealed', () => {
    const { UNSAFE_getAllByType } = render(
      <StudyCard
        currentCard={makeCard()}
        activeGroup={makeGroup()}
        revealedPages={[0, 1]}
        peekedPageIndex={null}
        showRatingButtons
        waitingForTap={false}
        audioPageIndex={null}
        audioMode={null}
        onCardPress={jest.fn()}
        onHoldingChange={jest.fn()}
      />,
    );

    const pageSections = UNSAFE_getAllByType(CardPageSection);

    expect(pageSections.map((section) => section.props.isRevealed)).toEqual([true, true]);
  });
});
