import { createContext, useContext } from 'react';
import type { FlashcardStoreActions } from './FlashcardStoreTypes';

export const StoreActionsContext = createContext<FlashcardStoreActions | undefined>(undefined);

export function useStoreActionsContext(): FlashcardStoreActions {
  const actions = useContext(StoreActionsContext);

  if (!actions) {
    throw new Error('useStoreActions must be used within a FlashcardStoreProvider');
  }

  return actions;
}
