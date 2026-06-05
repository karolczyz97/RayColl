import { createContext, useContext } from 'react';
import type { FlashcardStoreActions, FlashcardStoreState } from './FlashcardStoreTypes';

export const StoreActionsContext = createContext<FlashcardStoreActions | undefined>(undefined);
export const StoreStateContext = createContext<FlashcardStoreState | undefined>(undefined);

export function useStoreActionsContext(): FlashcardStoreActions {
  const actions = useContext(StoreActionsContext);

  if (!actions) {
    throw new Error('useStoreActions must be used within a FlashcardStoreProvider');
  }

  return actions;
}

export function useStoreStateContext(): FlashcardStoreState {
  const state = useContext(StoreStateContext);

  if (!state) {
    throw new Error('useStoreState must be used within a FlashcardStoreProvider');
  }

  return state;
}
