# RayColl — Refactor risks

Top ryzyka przy refaktoryzacji RayColl. Każde ryzyko ma plan minimalizacji i jasny test regresji. Lista jest **żywa** — aktualizujemy po każdej fazie.

## 1. Utrata danych przez źle zaprojektowaną persistence queue / flush

**Opis.** W Fazie 2 wprowadzimy debounce/queue dla zapisów. Każdy brakujący trigger flush (AppState background, beforeunload, unmount Providera, signOut, krytyczne akcje) = ryzyko, że użytkownik zobaczy "moje fiszki zniknęły" po restarcie aplikacji w środku sesji.

**Najbardziej narażone pliki:**

- `src/store/FlashcardStoreContext.tsx` (orchestracja persist + lifecycle)
- nowy `src/store/persistence/persistenceQueue.ts`
- `src/store/persistence/localPersistence.ts`
- `src/store/persistence/firebasePersistence.ts`
- `src/app/_layout.tsx` (tylko jeśli flush triggery muszą być na poziomie root — wg ustaleń trzymamy lifecycle głównie w store/persistence layer)

**Jak minimalizować:**

- Lista triggerów flush w jednym pliku, code-reviewed.
- Local AsyncStorage ma krótki delay (≤500 ms) i jest "safety netem" — zapisuje częściej i bezpieczniej niż cloud.
- `flushNow()` synchronous-await w `importDeck`, `importState`, `deleteGroup`, `signOut`.
- Unit testy queue: replace-pending (latest wins), single-in-flight, `flushNow` drain.

**Test regresji:**

- Wszystkie checkboxy z rozdziału [Persistence / Firebase smoke w REGRESSION.md](REGRESSION.md#persistence--firebase-smoke).
- Manualnie: dodaj 3 fiszki w Browse → kill aplikacji (native: force stop, web: zamknij tab) → relaunch → trzy fiszki muszą tam być.

## 2. Regresja review flow przez połączenie `updateFlashcard + recordActivity`

**Opis.** Faza 2 wprowadzi `reviewCard` jako jeden atomowy commit (fiszka + heatmap). Łatwo pomylić: brak `+1` w heatmap, podwójne zliczanie przy `restartFailed`, nie-zapisanie FSRS state, lub `reviewedCardIdsRef` nie chroni przed re-review tej samej karty w cyklu.

**Najbardziej narażone pliki:**

- `src/store/FlashcardStoreContext.tsx` (publiczne API store — nie zmieniać sygnatur, dodać nową `reviewCard`)
- nowy `src/store/actions/reviewActions.ts`
- `src/store/actions/activityActions.ts`, `src/store/actions/cardActions.ts` (nie usuwać starych — mogą być używane poza study)
- `src/hooks/useStudySession.ts` (`processCardReview` → wywołanie nowej akcji)

**Jak minimalizować:**

- `reviewCardAction(groups, heatmap, groupId, card) → { nextGroups, nextHeatmap }` jako **czysta funkcja**, pokryta unit testami (single review, restartFailed scenario, repeated review tego samego id).
- `useStudySession.processCardReview` zachowuje `reviewedCardIdsRef` żeby nie dublować w obrębie sesji.
- Stare API `updateFlashcard` i `recordActivity` zostają w store (używane spoza study — np. Browse edit), tylko **w study flow** ich osobne wywołanie zastępujemy jednym `reviewCard`.

**Test regresji:**

- [Study](REGRESSION.md#study) — rating 1/2/3/4, restart failed, restart session.
- Manualnie: wykonaj 5 review → otwórz Stats → heatmap dziś `+5`. Następnie restart failed (2 karty) → `+2`. Razem dziś `+7`.

## 3. Regresja import rollback

**Opis.** `importDeck` ma synchroniczny rollback: jeśli `persistAsync` rzuci, snapshot wraca do poprzedniego stanu (`FlashcardStoreContext.tsx`, ~360–418). Przejście na queue/debounce nie może rozspójnić tej semantyki — rollback musi zobaczyć **stan przed importem**, a po failu zapis poprzedniego stanu też musi się powieść (lub być widoczny w `lastPersistenceError`).

**Najbardziej narażone pliki:**

- `src/store/FlashcardStoreContext.tsx` (callback `importDeck`)
- `src/store/persistence/firebasePersistence.ts`
- `src/import/importDeck.ts`

**Jak minimalizować:**

- `importDeck` zawsze przez `flushNow()` (await), nigdy przez queue.
- `previousSnapshot` zachowywany lokalnie w callbacku — niezależny od `groupsRef.current`.
- Test integracyjny: mock `saveCloudData` → `throw` → sprawdź, że `groupsRef.current === previousSnapshot.groups` i że `lastPersistenceError` jest ustawione.

**Test regresji:**

- [Import → Rollback](REGRESSION.md#import) checkbox.
- Manualnie (zalogowany): wyłącz internet → Import → musi przyjść Snackbar błędu i dashboard nadal pokazuje listę talii **bez** niedoszłej nowej.

## 4. Regresja timingów animacji w `study/[groupId].tsx`

**Opis.** Plik ma kilka współpracujących Reanimated effectów: `scale` na card press, `ttsScale`/`sttScale` z `withRepeat(withSequence(...))`, `CardPageSection` opacity per page, `FadeIn`/`FadeInUp`/`ZoomIn` na completion screen. Przy splicie na podkomponenty (Faza 4) lub unifikacji motion (Faza 3) łatwo zepsuć kolejność `useEffect` zależności lub stałe spring.

**Najbardziej narażone pliki:**

- `src/app/study/[groupId].tsx`
- przyszły `src/hooks/useStudyAnimations.ts` (Faza 4)
- przyszłe `src/components/study/{StudyCard,StudyFeedbackPanel,StudyCompletionScreen}.tsx`

**Jak minimalizować:**

- Każdy split = jeden commit. Po splicie porównaj UX: tap na karcie, rating buttons fade, completion ZoomIn, TTS pulse, STT pulse.
- Wartości `damping` / `stiffness` / `mass` przenosić bez zmian.
- `delay(N)` w `FadeInUp.springify().delay(N)` zachować dokładnie (`150` / `250` / `350` / `450` ms).

**Test regresji:**

- Cały rozdział [Study](REGRESSION.md#study).
- Wizualnie: tap na karcie → `scale 0.96` → puść → `1`. Wejście do completion → checkmark zoom, "bravo" fade, opis fade, stats fade, buttony fade — w tej kolejności, ze stagger ~100 ms.

## 5. Layout shift po wprowadzeniu `AppScreen`, `SectionCard`, `AnimatedSection`

**Opis.** Faza 3 wprowadzi wrappery z domyślnym paddingiem / gap / borderRadius. Jeśli wrapper zaaplikuje inny padding niż obecny ekran, albo `SectionCard` doda dodatkowy `gap` względem aktualnego `AppCard.Content`, **wszystkie** ekrany skoczą o kilka pikseli. Drugie ryzyko: `AppCard` ma już web-hover scale — `SectionCard` na zewnętrznej warstwie podwoiłby efekt.

**Najbardziej narażone pliki:**

- nowe `src/components/{AppScreen,SectionCard,AnimatedSection}.tsx`
- migrowane ekrany: `src/app/index.tsx`, `import.tsx`, `settings/[groupId].tsx`, `study/[groupId].tsx`, `browse/[groupId].tsx`, `stats.tsx`, `app-settings.tsx`
- `src/components/AppCard.tsx` (web hover scale)

**Jak minimalizować:**

- Wrappery przyjmują pasywne styling propy (`style`, `contentStyle`) — sensowne defaulty z `TOKENS`, ale override możliwy.
- Migracja **1 ekran = 1 commit**.
- Przed/po screenshot per ekran (web + Android, light + dark + system colors) i porównanie.
- `AppCard` hover scale tylko na zewnętrznym wrapperze; `SectionCard` nie nakłada drugiego.

**Test regresji:**

- `npx expo export --platform web` przechodzi bez błędu.
- Wizualnie: nawigacja po każdym ekranie z [Manual smoke test](REGRESSION.md#manual-smoke-test); paddingi / gapy wyglądają identycznie do baseline screenshotów.

## 6. Przypadkowe usunięcie zależności Expo / Paper potrzebnej transitive

**Opis.** `@expo/vector-icons` jest w `dependencies`, ale ESLint (`no-restricted-imports`) blokuje bezpośredni import w `src/`. Pokusa: "skoro nikt nie importuje, można usunąć". Ale Paper / Expo mogą jej wymagać przez natywny autolink lub przez renderowanie ikon font-based. To samo dotyczy `expo-speech-recognition`, `expo-audio`, `expo-auth-session`, `expo-web-browser`, etc.

**Najbardziej narażone pliki:**

- `package.json`
- `android/` (autolink — odtwarzany przez `expo prebuild`)
- `eslint.config.js`

**Jak minimalizować:**

- **Faza 1–4: nie usuwać żadnej zależności.** Audit dopiero w Fazie 5.
- Decyzja "zostawić / usunąć" wymaga w tej kolejności:
  1. brak importu w `src/` (Grep),
  2. `npm ls <package>` — jeśli ktoś inny ją wymaga, **zostaw**,
  3. `expo prebuild --clean` + `npx expo export --platform web` muszą przejść,
  4. test build na Androidzie obowiązkowy.

**Test regresji:**

- `npm run lint && npm run typecheck && npm run test`.
- `npx expo export --platform web` bez błędu.
- Jeśli usunięcie tknęło coś natywnego: pełny rebuild Android + smoke test ekranów z ikonami (Dashboard FAB, Study rating buttons, IconButton w `AppTopBar`).

## 7. Zbyt duży refactor naraz

**Opis.** Pokusa "skoro siedzę w pliku, naprawię wszystko" prowadzi do PR-ów, których nie da się sensownie zreview'ować ani zrollbackować, i regresji, których nie da się przypisać do konkretnej zmiany. W RayColl szczególnie groźne, bo duże pliki (`FlashcardStoreContext.tsx`, `study/[groupId].tsx`, `useStudySession.ts`, `settings/[groupId].tsx`, `import.tsx`) mają wiele subtelnych zależności.

**Najbardziej narażone pliki:**

- `src/store/FlashcardStoreContext.tsx`
- `src/app/import.tsx`, `src/app/settings/[groupId].tsx`, `src/app/study/[groupId].tsx`
- `src/hooks/useStudySession.ts`

**Jak minimalizować:**

- 1 commit = 1 logiczna zmiana (kolejka persistence, jeden wrapper UI, jeden split komponentu).
- Po każdym commicie: lint + typecheck + test + manualny smoke odpowiedniego rozdziału z [REGRESSION.md](REGRESSION.md).
- Jeśli commit musi dotknąć > 5 plików produkcyjnych — zatrzymaj się i podziel.
- Nie miksuj fazy persistence (logic) z fazą UI (styling) w jednym commicie.

**Test regresji:**

- Code review: każdy commit jest zrozumiały bez kontekstu pozostałych.
- Po każdej fazie: pełny smoke z [REGRESSION.md](REGRESSION.md), nie tylko sekcja "relevant".
