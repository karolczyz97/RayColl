import { createContext, useContext } from 'react';
import type { FlashcardStoreState } from './FlashcardStoreTypes';

export const StoreStateContext = createContext<FlashcardStoreState | undefined>(undefined);

export function useStoreStateContext(): FlashcardStoreState {
  const state = useContext(StoreStateContext);

  if (!state) {
    throw new Error('useStoreState must be used within a FlashcardStoreProvider');
  }

  return state;
}
