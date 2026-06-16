import { describe, it, expect } from '@jest/globals';

import { importDraftReducer, createInitialDraftState } from '@/features/import/importDraftReducer';

describe('importDraftReducer', () => {
  it('dismissing the snackbar keeps the blocking importError (cannot unblock a stale import)', () => {
    const withError = importDraftReducer(createInitialDraftState(), {
      type: 'SET_IMPORT_ERROR',
      value: 'import.err.too_many_columns',
    });
    const dismissed = importDraftReducer(withError, { type: 'DISMISS_IMPORT_ERROR' });

    expect(dismissed.importError).toBe('import.err.too_many_columns');
    expect(dismissed.errorDismissed).toBe(true);
  });

  it('a fresh error (or clearing it) re-arms snackbar visibility after a dismissal', () => {
    let state = importDraftReducer(createInitialDraftState(), {
      type: 'SET_IMPORT_ERROR',
      value: 'import.err.too_many_lines',
    });
    state = importDraftReducer(state, { type: 'DISMISS_IMPORT_ERROR' });
    expect(state.errorDismissed).toBe(true);

    state = importDraftReducer(state, { type: 'SET_IMPORT_ERROR', value: '' });
    expect(state.errorDismissed).toBe(false);
    expect(state.importError).toBe('');
  });
});
