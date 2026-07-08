import type { AtomicStep, Flashcard, FlashcardGroup, SrsState } from '@/types/models';
import type { SpeechRecognitionOutcome, StudySkipState } from '@/features/study/hooks/useStudyAudio';
import { playRatingHaptic } from '@/services/hapticFeedback';
import { ttsService } from '@/services/ttsService';
import { getErrorMessage } from '@/utils/errors';
import type {
  CardReviewState,
  LastAnswerResult,
  SessionAction,
  StudySessionState,
} from './sessionTypes';
import { INITIAL_STUDY_SESSION_STATE, sessionReducer } from './sessionReducer';
import { executeStudyStep } from './stepExecutor';
import { hasCardBeenReviewed, startReviewAttempt, tryMarkCardReviewed } from './sessionReview';
import { sleep } from './sessionUtils';

const WAIT_RELEASE_POLL_MS = 100;
const DEFAULT_TTS_RATE = 1.0;
// Semantyka "poprzednia" jak w odtwarzaczach muzyki: wciśnięcie tuż po starcie
// karty cofa do poprzedniej, później restartuje bieżącą od początku.
const PREVIOUS_GOES_BACK_WINDOW_MS = 3000;

/** Zależności audio/callbacki wstrzykiwane przez hooka (silnik nie zna hooków). */
export interface SessionEngineDeps {
  runSpeechRecognition: (lang: string, timeoutMs: number) => Promise<SpeechRecognitionOutcome>;
  guardedAwait: <T>(promise: Promise<T>) => Promise<T | undefined>;
  stopAudio: () => void;
  skip: StudySkipState;
  /**
   * Zapis oceny. `replaceFromBase` obecne = ponowna ocena tej samej karty w tej
   * próbie sesji: liczy się najnowsza ocena, SRS przeliczany od podanego stanu
   * bazowego (sprzed pierwszej oceny), bez ponownego zliczenia aktywności.
   */
  onCardReviewed: (
    groupId: string,
    cardId: string,
    rating: number,
    replaceFromBase?: SrsState,
  ) => void;
}

/**
 * Silnik sesji nauki poza Reactem — jedyne źródło prawdy stanu sesji.
 *
 * `dispatch` wykonuje czysty `sessionReducer` SYNCHRONICZNIE, więc każdy odczyt
 * stanu w trakcie asynchronicznego łańcucha kroków jest świeży — dawne
 * ref-mirrory (lastAnswerResult, revealedPages, epoch, pause, failed...) są
 * zwykłymi polami silnika. UI subskrybuje przez `subscribe`/`getVersion`
 * (useSyncExternalStore w useStudySession).
 *
 * Tryb tła (backgroundMode): TTS używa statycznego singletona ttsService zamiast
 * wstrzykniętych zależności z hooka, co pozwala działać po odmontowaniu widoku.
 */
export class SessionEngine {
  private state: StudySessionState = INITIAL_STUDY_SESSION_STATE;
  private version = 0;
  private listeners = new Set<() => void>();
  private unmounted = false;

  private deps: SessionEngineDeps | null = null;

  // Dane wejściowe pchane przez hooka przy każdym renderze.
  private group: FlashcardGroup | null = null;
  private activeSteps: AtomicStep[] = [];

  // Stan przebiegu sesji (dawne refy useStudySession).
  private dueCards: Flashcard[] = [];
  private allCards: Flashcard[] = [];
  private failedCards: Flashcard[] = [];
  private reviewedAttemptKeys = new Set<string>();
  private sessionAttempt = 0;
  // Generation counter for step chains; łańcuch łapie epokę na starcie, mismatch
  // znaczy że został wyprzedzony i musi umrzeć bez efektów ubocznych.
  private runEpoch = 0;
  private aborted = false;
  private paused = false;
  private holding = false;
  private lastExecutedCardIndex: number | null = null;
  // Moment startu bieżącej karty (krok 0) — próg decyzji "poprzednia vs restart".
  private currentCardStartedAt = 0;

  // cardReviewState to runner-only pole — next_card/show_ratings/auto_rate blokują
  // podwójną ocenę tej samej karty. NIE jest w reducerze, bo dispatch nie jest
  // potrzebny dla logiki która działa w tej samej synchronicznej ścieżce.
  private cardReviewState: CardReviewState = 'none';

  // TTS — tryb tła potrzebuje statycznego dostępu przez singleton ttsService.
  private ttsRate = DEFAULT_TTS_RATE;
  private lastTtsDuration = 0;
  private backgroundMode = false;
  // Callback opcjonalny — wywoływany przy auto-pauzie w tle (zmiana notyfikacji).
  private onBackgroundPause: (() => void) | null = null;

  // ---------------------------------------------------------------- UI store

  getState = (): StudySessionState => this.state;

  getVersion = (): number => this.version;

  getDueCards = (): Flashcard[] => this.dueCards;

  getFailedCount = (): number => this.failedCards.length;

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
    // Dawne efekty hooka: wznowienie po gate i auto-start karty od kroku 0.
    this.handlePendingStep();
    this.maybeAutoRunCard();
  };

  /** Po odmontowaniu hooka silnik ignoruje dispatch (dawne dispatchIfMounted). */
  markUnmounted = (): void => {
    this.unmounted = true;
  };

  private bumpAndNotify(): void {
    this.version += 1;
    for (const listener of [...this.listeners]) {
      listener();
    }
  }

  // ------------------------------------------------------------ konfiguracja

  /** Wywoływane przy każdym renderze hooka — idempotentne przypisania pól. */
  configure(deps: SessionEngineDeps, ttsRate: number = DEFAULT_TTS_RATE): void {
    this.deps = deps;
    this.ttsRate = ttsRate;
  }

  setGroup(group: FlashcardGroup | null): void {
    this.group = group;
  }

  setActiveSteps(steps: AtomicStep[]): void {
    this.activeSteps = steps;
  }

  getGroup = (): FlashcardGroup | null => this.group;

  setHolding = (holding: boolean): void => {
    this.holding = holding;
  };

  getSkip = (): StudySkipState | null => this.deps?.skip ?? null;

  // ------------------------------------------------ tryb tła (backgroundMode)

  setBackgroundMode = (enabled: boolean): void => {
    this.backgroundMode = enabled;
  };

  isBackgroundMode = (): boolean => this.backgroundMode;

  setOnBackgroundPause = (cb: (() => void) | null): void => {
    this.onBackgroundPause = cb;
  };

  /**
   * Pominięcie reszty bieżącej karty i przejście do następnej.
   * Przerywa aktywny TTS/STT łańcuch i wymusza ADVANCE_CARD.
   */
  skipToNextCard = (): void => {
    const skip = this.deps?.skip;
    if (skip && skip.armed) {
      skip.requested = true;
      this.deps?.stopAudio();
      skip.signalResolve();
    }
    this.aborted = true;
    this.runEpoch += 1;
    this.aborted = false;
    this.paused = false;
    void this.advanceToNextCard();
  };

  /**
   * Przycisk "poprzednia" — semantyka jak w odtwarzaczach muzyki: tuż po starcie
   * karty (okno PREVIOUS_GOES_BACK_WINDOW_MS) cofa do poprzedniej karty, później
   * restartuje bieżącą od kroku 0. Samo cofnięcie/restart nie zapisuje SRS.
   */
  goToPreviousCard = (): void => {
    if (this.state.status === 'finished') return;
    const cardIndex = this.state.currentCardIndex;
    const withinWindow =
      Date.now() - this.currentCardStartedAt <= PREVIOUS_GOES_BACK_WINDOW_MS;
    if (withinWindow && cardIndex > 0) {
      this.deps?.stopAudio();
      this.aborted = true;
      this.runEpoch += 1;
      this.aborted = false;
      this.paused = false;
      this.holding = false;
      // Inny index niż lastExecutedCardIndex — maybeAutoRunCard startuje kartę sam.
      this.dispatch({ type: 'ADVANCE_CARD', nextCardIndex: cardIndex - 1 });
      return;
    }
    this.replayCurrentCard();
  };

  /** Restart bieżącej karty od kroku 0 BEZ ponownego zapisu SRS. */
  replayCurrentCard = (): void => {
    const cardIndex = this.state.currentCardIndex;
    const card = this.dueCards[cardIndex];
    if (!card) return;
    this.deps?.stopAudio();
    this.runEpoch += 1;
    this.aborted = false;
    this.paused = false;
    this.holding = false;
    // Blokada auto-runa PRZED dispatchem — replay odpalamy jawnie niżej.
    this.lastExecutedCardIndex = cardIndex;
    // ADVANCE_CARD resetuje stan karty; na ten sam index = restart bez oceny SRS.
    this.dispatch({ type: 'ADVANCE_CARD', nextCardIndex: cardIndex });
    setTimeout(() => {
      if (!this.aborted && !this.unmounted) {
        void this.executeStep(card, 0);
      }
    }, 0);
  };

  // ------------------------------------------------------------ cykl sesji

  start = (cards: Flashcard[]): void => {
    this.aborted = false;
    this.paused = false;
    this.runEpoch += 1;
    this.lastExecutedCardIndex = null;
    this.allCards = cards;
    this.failedCards = [];
    this.sessionAttempt = startReviewAttempt(this.reviewedAttemptKeys, this.sessionAttempt);
    this.dueCards = cards;
    this.bumpAndNotify();
    this.dispatch({ type: 'START_SESSION' });
    if (cards.length === 0) {
      this.dispatch({ type: 'FINISH_SESSION' });
    }
  };

  restart = (): void => {
    this.start(this.getFreshCards(this.allCards));
  };

  restartFailed = (): void => {
    if (this.failedCards.length > 0) {
      this.start(this.getFreshCards(this.failedCards));
    }
  };

  /** Zatrzymaj przebieg (wyjście z ekranu): zabij łańcuch i audio. */
  stop = (): void => {
    this.aborted = true;
    this.holding = false;
    this.deps?.stopAudio();
  };

  isPaused = (): boolean => this.paused;

  /** Zakończ sesję wcześniej: podsumowanie renderuje się w miejscu. */
  end = (): void => {
    this.paused = false;
    this.stop();
    this.dispatch({ type: 'FINISH_SESSION' });
  };

  /**
   * Zamrożenie przebiegu na czas modala (dialog wyjścia): zabij łańcuch w locie
   * i audio. No-op gdy nic nie działa w tle (czekamy na tap / ocenę / koniec).
   *
   * W trybie backgroundMode: jeśli stan wymaga interakcji (revealed / gate / error),
   * wywołuje onBackgroundPause do aktualizacji notyfikacji na lockscreenie.
   */
  pause = (): void => {
    const current = this.state;
    // Zawsze zwalniaj hold — nawet gdy pauza jest no-opem. Zablokowanie ekranu
    // w trakcie trzymania karty może nigdy nie dostarczyć "puszczono", a wiszący
    // hold zawiesiłby advanceToNextCard na zawsze.
    this.holding = false;
    if (current.status === 'finished') return;

    // W tle: revealed i gate to stany wymagające ręcznej interakcji — auto-pauza
    if (this.backgroundMode) {
      if (current.status === 'revealed' || current.interactionGate.kind !== 'none') {
        this.paused = true;
        this.runEpoch += 1;
        this.aborted = true;
        this.deps?.stopAudio();
        this.onBackgroundPause?.();
        return;
      }
    }

    if (current.status === 'revealed' || current.interactionGate.kind !== 'none') {
      return;
    }
    this.paused = true;
    this.runEpoch += 1;
    this.aborted = true;
    this.deps?.stopAudio();
  };

  /**
   * Wznowienie po pauzie = powtórka bieżącej karty od pierwszego kroku —
   * TTS/STT nie da się wznowić w połowie wypowiedzi, uczciwy jest czysty replay.
   */
  resume = (): void => {
    if (!this.paused) return;
    this.paused = false;
    this.aborted = false;
    this.runEpoch += 1;
    const cardIndex = this.state.currentCardIndex;
    const card = this.dueCards[cardIndex];
    // Blokada auto-runa PRZED dispatchem — replay odpalamy jawnie niżej.
    this.lastExecutedCardIndex = cardIndex;
    this.dispatch({ type: 'ADVANCE_CARD', nextCardIndex: cardIndex });
    if (!card) return;
    setTimeout(() => {
      if (!this.aborted) {
        void this.executeStep(card, 0);
      }
    }, 0);
  };

  // ------------------------------------------------------------ ocena kart

  rate = async (rating: number): Promise<void> => {
    if (this.cardReviewState !== 'none') return;
    const index = this.state.currentCardIndex;
    if (index >= this.dueCards.length || !this.group) return;
    const card = this.dueCards[index];
    if (!card) return;
    playRatingHaptic(rating);
    // Ponowna ocena po cofnięciu: liczy się najnowsza, więc członkostwo na liście
    // failed też ustawiamy od nowa (rating 1 zaraz niżej doda kartę z powrotem).
    if (hasCardBeenReviewed(this.reviewedAttemptKeys, this.sessionAttempt, card.id)) {
      this.unmarkCardFailed(card);
    }
    if (rating === 1) {
      this.markCardFailed(card);
    }
    this.processCardReview(card, rating);
    this.cardReviewState = 'manuallyRated';
    // Ocena po auto-pauzie (tło zatrzymało się na ratingu): odblokuj przebieg,
    // inaczej aborted=true wiecznie blokowałby auto-run następnej karty.
    if (this.paused) {
      this.paused = false;
      this.aborted = false;
      this.runEpoch += 1;
    }
    await this.advanceToNextCard();
  };

  markCardFailed = (card: Flashcard): void => {
    if (this.failedCards.find((item) => item.id === card.id)) return;
    this.failedCards.push(card);
    this.bumpAndNotify();
  };

  unmarkCardFailed = (card: Flashcard): void => {
    const next = this.failedCards.filter((item) => item.id !== card.id);
    if (next.length === this.failedCards.length) return;
    this.failedCards = next;
    this.bumpAndNotify();
  };

  processCardReview = (card: Flashcard, rating: number): void => {
    if (!this.group || !this.deps) return;
    if (tryMarkCardReviewed(this.reviewedAttemptKeys, this.sessionAttempt, card.id)) {
      this.deps.onCardReviewed(this.group.id, card.id, rating);
      return;
    }
    // Karta oceniona ponownie w tej samej próbie (cofnięcie): najnowsza ocena
    // nadpisuje poprzednią. `card.srsState` to snapshot sesji sprzed pierwszej
    // oceny — dueCards nie są odświeżane po zapisie do store'u.
    this.deps.onCardReviewed(this.group.id, card.id, rating, card.srsState);
  };

  private getFreshCards(cards: Flashcard[]): Flashcard[] {
    if (!this.group) return cards;
    const cardsById = new Map(this.group.cards.map((card) => [card.id, card]));
    return cards.map((card) => cardsById.get(card.id) ?? card);
  }

  // ------------------------------------------------------------ runner

  // Bez limitu czasu: trzymanie karty wstrzymuje przejście tak długo, jak user
  // trzyma. Wiszący hold po zablokowaniu ekranu rozwiązują pause()/stop()/
  // goToPreviousCard() oraz AppState w useBackgroundStudy (holding = false).
  private async waitUntilReleased(): Promise<void> {
    while (this.holding && !this.aborted) {
      await sleep(WAIT_RELEASE_POLL_MS);
    }
  }

  advanceToNextCard = async (): Promise<void> => {
    await this.waitUntilReleased();
    const nextIndex = this.state.currentCardIndex + 1;
    if (nextIndex >= this.dueCards.length) {
      this.dispatch({ type: 'FINISH_SESSION' });
    } else {
      this.dispatch({ type: 'ADVANCE_CARD', nextCardIndex: nextIndex });
    }
  };

  /**
   * Po domknięciu tap-gate gesty ustawiają pendingStepIndexToRun. Wznawiamy
   * runner od wskazanego kroku TEJ SAMEJ karty — auto-run startuje kartę tylko
   * od kroku 0 i blokuje ponowne odpalenie przez lastExecutedCardIndex.
   */
  private handlePendingStep(): void {
    const pending = this.state.pendingStepIndexToRun;
    if (pending === null) return;
    const card = this.dueCards[this.state.currentCardIndex];
    // Konsumuj najpierw — zagnieżdżony dispatch wejdzie tu i wyjdzie od razu.
    this.dispatch({ type: 'CONSUME_PENDING_STEP_INDEX' });
    if (!card || this.aborted) return;
    void this.executeStep(card, pending);
  }

  /** Auto-start bieżącej karty od kroku 0 (dawny efekt auto-run hooka). */
  private maybeAutoRunCard(): void {
    const state = this.state;
    if (this.dueCards.length === 0 || state.status === 'finished') {
      this.lastExecutedCardIndex = null;
      return;
    }
    // Nie startuj karty od 0, gdy: pokazujemy ratingi, czekamy w tap-gate, albo
    // mamy zaplanowane wznowienie kroku (pendingStepIndexToRun) tej karty.
    if (state.status === 'revealed') return;
    if (state.interactionGate.kind !== 'none') return;
    if (state.pendingStepIndexToRun !== null) return;
    const card = this.dueCards[state.currentCardIndex];
    if (!card) return;
    if (this.lastExecutedCardIndex === state.currentCardIndex) return;

    this.lastExecutedCardIndex = state.currentCardIndex;
    setTimeout(() => {
      if (!this.aborted && !this.unmounted && this.state.status !== 'finished') {
        void this.executeStep(card, 0);
      }
    }, 0);
  }

  executeStep = async (card: Flashcard, stepIndex: number): Promise<void> => {
    // Krok 0 = start (lub replay) karty — punkt odniesienia dla goToPreviousCard.
    if (stepIndex === 0) {
      this.currentCardStartedAt = Date.now();
    }
    await executeStudyStep(card, stepIndex, this);
  };

  // ---------------------------------------------------- API runnera (kroki)
  // stepExecutor czyta/pisze pola silnika wyłącznie przez te akcesory.

  isAborted = (): boolean => this.aborted;

  getRunEpoch = (): number => this.runEpoch;

  getActiveSteps = (): AtomicStep[] => this.activeSteps;

  getLastAnswerResult = (): LastAnswerResult => this.state.lastAnswerResult;

  getCardReviewState = (): CardReviewState => this.cardReviewState;

  setCardReviewState = (reviewState: CardReviewState): void => {
    this.cardReviewState = reviewState;
  };

  getRevealedPages = (): number[] => this.state.revealedPages;

  getLastTtsDuration = (): number => this.lastTtsDuration;

  /**
   * TTS przez statyczny singleton ttsService — działa niezależnie od Reacta,
   * więc przeżywa odmontowanie widoku w trybie tła.
   */
  playTts = async (text: string, lang: string): Promise<void> => {
    const startTime = Date.now();
    try {
      await ttsService.speak({ text, lang, rate: this.ttsRate });
    } catch (err) {
      console.error('TTS Speak Error:', getErrorMessage(err));
      // W tle: błąd TTS = auto-pauza + notyfikacja
      if (this.backgroundMode) {
        this.paused = true;
        this.aborted = true;
        this.onBackgroundPause?.();
        return;
      }
      this.dispatch({ type: 'SET_ERROR', errorMsg: 'study.error.tts' });
    }
    this.lastTtsDuration = Date.now() - startTime;
  };

  /**
   * guardedAwait gdy deps != null (tryb z UI): races z sygnałem skip.
   * W tle (deps == null): wykonuje promis bez skipu.
   */
  guardedAwait = async <T>(promise: Promise<T>): Promise<T | undefined> => {
    if (!this.deps) {
      return (await promise.catch(() => undefined)) as T | undefined;
    }
    return this.deps.guardedAwait(promise);
  };

  runSpeechRecognition = async (
    lang: string,
    timeoutMs: number,
  ): Promise<SpeechRecognitionOutcome> => {
    if (!this.deps) return { status: 'error' };
    return this.deps.runSpeechRecognition(lang, timeoutMs);
  };
}
