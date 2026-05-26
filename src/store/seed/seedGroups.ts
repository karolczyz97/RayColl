import { FlashcardGroup, Flashcard } from '../../types/models';
import { createNewSrsState } from '../../srs/srsEngine';
import { uid } from '../../utils/id';

export function createSeedGroups(): FlashcardGroup[] {
  const mk = (pages: string[]): Flashcard => ({
    id: uid(),
    pages,
    srsState: createNewSrsState(),
  });

  return [
    {
      id: uid(),
      name: 'Angielski - Podstawy',
      activeModeId: 'classic',
      pageLanguages: ['en-US', 'pl-PL'],
      pageNames: ['Phrase', 'Tłumaczenie'],
      activePageCount: 2,
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
      pageLanguages: ['es-ES', 'pl-PL'],
      pageNames: ['Palabra', 'Tłumaczenie'],
      activePageCount: 2,
      cards: [
        mk(['Hola', 'Cześć']),
        mk(['Gracias', 'Dziękuję']),
        mk(['Por favor', 'Proszę']),
        mk(['Adiós', 'Do widzenia']),
      ],
    },
  ];
}
export const SEED_VERSION = 3;
