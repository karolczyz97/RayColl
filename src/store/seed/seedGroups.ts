import { FlashcardGroup, Flashcard } from '@/types/models';
import { createNewSrsState } from '@/srs/srsEngine';
import { uid } from '@/utils/id';
import { DEFAULT_STUDY_FILTER } from '@/store/storeDataNormalization';
import { DEFAULT_CARD_ORDER } from '@/constants/cardOrder';

export function createSeedGroups(): FlashcardGroup[] {
  const mk = (pages: string[]): Flashcard => ({
    id: uid(),
    pages,
    srsState: createNewSrsState(),
    contentUpdatedAt: 0,
    srsUpdatedAt: 0,
  });

  return [
    {
      id: uid(),
      name: 'Angielski - Podstawy',
      activeModeId: 'classic',
      studyFilter: DEFAULT_STUDY_FILTER,
      cardOrder: DEFAULT_CARD_ORDER,
      pageLanguages: ['en-US', 'pl-PL'],
      pageNames: ['Phrase', 'Tłumaczenie'],
      activePageCount: 2,
      updatedAt: 0,
      cards: [
        mk(['Good morning', 'Dzień dobry']),
        mk(['Thank you', 'Dziękuję']),
        mk(['Please', 'Proszę']),
        mk(['Goodbye', 'Do widzenia']),
        mk(['Yes', 'Tak']),
      ],
    },
    {
      id: uid(),
      name: 'Hiszpański - Podstawy',
      activeModeId: 'classic',
      studyFilter: DEFAULT_STUDY_FILTER,
      cardOrder: DEFAULT_CARD_ORDER,
      pageLanguages: ['es-ES', 'pl-PL'],
      pageNames: ['Palabra', 'Tłumaczenie'],
      activePageCount: 2,
      updatedAt: 0,
      cards: [
        mk(['Hola', 'Cześć']),
        mk(['Gracias', 'Dziękuję']),
        mk(['Por favor', 'Proszę']),
        mk(['Adiós', 'Do widzenia']),
      ],
    },
  ];
}
