import type { SessionAction, StudySessionState } from './sessionTypes';
import { INITIAL_STUDY_SESSION_STATE, sessionReducer } from './sessionReducer';

/**
 * Silnik sesji nauki poza Reactem — właściciel stanu sesji.
 *
 * `dispatch` wykonuje czysty `sessionReducer` SYNCHRONICZNIE, więc każdy odczyt
 * `getState()` w trakcie asynchronicznego łańcucha kroków jest świeży (koniec
 * z problemem "reducer state jest stale w obrębie łańcucha"). UI subskrybuje
 * przez `subscribe`/`getVersion` (useSyncExternalStore w useStudySession).
 */
export class SessionEngine {
  private state: StudySessionState = INITIAL_STUDY_SESSION_STATE;
  private version = 0;
  private listeners = new Set<() => void>();
  private unmounted = false;

  getState = (): StudySessionState => this.state;

  // Monotoniczny licznik zmian — snapshot dla useSyncExternalStore. Rośnie też
  // przy zmianach pól poza reducerem (dueCards/failedCards), stąd notifyExternal.
  getVersion = (): number => this.version;

  subscribe = (listener: () => void): (() => void) => {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  };

  /** Synchroniczne przejście stanu przez czysty sessionReducer + notyfikacja. */
  dispatch = (action: SessionAction): void => {
    if (this.unmounted) return;
    const next = sessionReducer(this.state, action);
    // Parytet z useReducer: identyczny stan nie powoduje notyfikacji.
    if (next === this.state) return;
    this.state = next;
    this.bumpAndNotify();
  };

  /** Po odmontowaniu hooka silnik ignoruje dispatch (dawne dispatchIfMounted). */
  markUnmounted = (): void => {
    this.unmounted = true;
  };

  protected isUnmounted(): boolean {
    return this.unmounted;
  }

  /** Notyfikacja dla zmian pól silnika spoza reducera (np. failedCards). */
  protected bumpAndNotify(): void {
    this.version += 1;
    for (const listener of [...this.listeners]) {
      listener();
    }
  }
}
