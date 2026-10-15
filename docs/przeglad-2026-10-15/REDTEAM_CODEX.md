# RayColl: red team uwag Claude i skorygowany plan napraw

15.10.2026 · gałąź `przeglad-modernizacja` · commit `39f0690b993c7ed360be083ea3c690fd477077b2`

## Wniosek

Przegląd Claude wskazuje rzeczywiste problemy, ale nie jest gotową instrukcją do automatycznego wdrożenia wszystkich propozycji. Część napraw trzeba zmienić, część pomysłów dotyczy wyglądu, a kilka diagnoz przeczy kodowi lub bibliotece.

Zweryfikowano wszystkie **166 identyfikatorów** z sześciu list. Każdy ma osobny werdykt, powód i zakres działania w dokumentach poniżej. **68 uwag potwierdzono w całości, 66 częściowo, 24 są decyzjami produktowymi, 8 odrzucono jako błędne/celowe/nieopłacalne.** To nie jest 134 odrębnych błędów: są tu duplikaty między obszarami, brakujące testy i drobne porządki czytelności. Potwierdzone drobiazgi pozostają w planie.

Kod aplikacji, konfiguracja, oryginalne raporty i istniejący werdykt Claude nie zostały zmienione. Nie wykonano commitów, push, wdrożenia reguł ani sprzątania worktree. Dodano dokumenty tego review. Pomocniczy wykonany reproducer jest w ignorowanym `.gradle/redteam-codex/RedteamChecks.java`.

## Która rozmowa i jaki był plan

Odnaleziona rozmowa Claude: `2fdf7a40-4efc-464d-82b4-cae90cf3b0e2` w lokalnych logach projektu RayColl. Skan zaczął się **15.10 o 06:41 czasu polskiego**. Ostatnia prośba o red team została wysłana **o 07:28**, limit pojawił się **o 07:31**.

Ostatni prompt prosił o podważenie uwag, sprawdzenie celowości zachowania i uwzględnienie drobnych napraw, z pominięciem skrajnie niepraktycznych przypadków. Claude zapowiedział sześciu równoległych recenzentów i przygotował `FIX_INSTRUCTIONS.md`. Na dysku pozostał kompletny werdykt wyłącznie domeny; pozostałych pięciu werdyktów z tej rundy nie było.

Plan Claude z `RAPORT.md`: wybór wskaźnika audio → błędy → teksty → uzgodniony UX → podział kodu i testy → telefon → integracja z main. **Kod wcześniejszej modernizacji jest już zintegrowany na gałęzi roboczej; lista 166 uwag pochodzi z nowego skanu i nie została jeszcze wdrożona.** Warianty wskaźnika A/B/C są propozycjami. Obecna gałąź ma już wersję A, która rezerwuje miejsce i nie przesuwa tekstu; nie oznacza to, że ta wersja jest na telefonie.

Ten raport kończy etap review i przygotowania napraw. Wdrożenie zmian zachowania/wyglądu wymaga zaakceptowania konkretnego zakresu zgodnie z zasadami współpracy; sam review nie wybiera za właściciela wszystkich nowych opcji UX.

## Wyniki wszystkich obszarów

| Obszar | CONFIRMED | PARTIAL | DECISION | INTENTIONAL | NOT_WORTH | WRONG | Razem |
|---|---:|---:|---:|---:|---:|---:|---:|
| [DOM: domena i core](verdict-domain-codex.md) | 11 | 8 | 1 | 0 | 0 | 0 | 20 |
| [DAT: dane i sync](verdict-data-codex.md) | 12 | 9 | 2 | 1 | 2 | 0 | 26 |
| [AUD: dźwięk i serwis](verdict-playback-codex.md) | 9 | 4 | 1 | 1 | 1 | 1 | 17 |
| [NAU: nauka i pulpit](verdict-ui-nauka-codex.md) | 10 | 9 | 10 | 1 | 1 | 0 | 31 |
| [EKR: pozostałe ekrany](verdict-ui-ekrany-codex.md) | 12 | 13 | 9 | 0 | 0 | 0 | 34 |
| [TB: testy, build, teksty](verdict-testy-build-codex.md) | 14 | 23 | 1 | 0 | 0 | 0 | 38 |
| **Razem** | **68** | **66** | **24** | **3** | **4** | **1** | **166** |

Nie nadawano arbitralnego prawdopodobieństwa 0.01% bez danych. Rzadkie, ale tanie do zabezpieczenia problemy pozostawiono jako ograniczone naprawy. Nie ma całej pozycji EDGE; odrzucone podpunkty w PARTIAL opisano jawnie.

## Najważniejsze problemy do naprawy

1. **Brak zbieżności kolejności dwóch urządzeń (DAT-N1).** Produkcyjny merge daje telefonowi `[a,b,c,p,t]`, a tabletowi `[a,b,c,t,p]`. Diff ponownie zapisuje różnice. Potrzebny test dwóch jednocześnie piszących urządzeń i kanoniczna kolejność przy scalaniu. [Kod](../../app/src/main/java/com/raycoll/app/domain/merge/MergeEngine.kt#L42).
2. **Obsługa awarii logowania/migracji i wielokrotnego transferu (DAT-N5, EKR-N14).** Wyjątek wracający przez await do ViewModelu pozostaje nieobsłużony; przyciski pozwalają zlecić kolejną operację. Guard i jawny wynik błędu muszą objąć UI oraz dane. [Kod](../../app/src/main/java/com/raycoll/app/ui/main/MainViewModel.kt#L28).
3. **Utrata zdarzenia kończącego import (EKR-N2).** Odbiór zaczyna się przy STARTED, powrót wymaga RESUMED. Import może już istnieć, a ekran pozostanie w stanie importing. Naprawa musi być przypisana do właściwego wpisu stosu nawigacji. [Kod odbioru](../../app/src/main/java/com/raycoll/app/ui/components/UiEvents.kt#L54), [strażnik nawigacji](../../app/src/main/java/com/raycoll/app/ui/navigation/NavGraph.kt#L130).
4. **Niepełne ponawianie sync i mylący stan po timeout (DAT-N3/N4/N7).** Niewysłana kolejka może pozostać po powrocie do aplikacji. Pusty save nie powinien przesuwać znacznika znanej chmury. [Kod](../../app/src/main/java/com/raycoll/app/data/sync/SyncLifecycle.kt#L171).
5. **Mowa ignorująca prośbę o ducking i odłączenie słuchawek (AUD-N2/N3).** Ścieżkę w kodzie i kontrakt platformy potwierdzono; dokładny routing trzeba jeszcze sprawdzić na telefonie. Android wyłącza automatyczny ducking dla treści mówionych. [Dokumentacja audio focus](https://developer.android.com/media/optimize/audio-focus), [zmiany wyjścia audio](https://developer.android.com/media/platform/output).
6. **Komunikaty i geometria nauki (NAU-N1/N2/N3/N4/N5/N17).** Nieograniczony snackbar mikrofonu, kolejka powtarzanych błędów, przesunięcie podsumowania, wychodzące animacje z nową treścią, późne oznaczenie sesji jako przerwanej i ciągły autoscroll są odrębnymi problemami. Każdy ma ograniczony spec i odpowiedni test.

Reset/przywrócenie na dwóch urządzeniach (DAT-N2) jest istotne, ale wymaga najpierw doprecyzowania znaczenia operacji, dlatego nie wchodzi do automatycznego pakietu powyżej.

## Najważniejsze korekty do propozycji Claude

- **AUD-N7: brak IME jest nieprawdą.** `imePadding` jest m.in. w imporcie, przeglądaniu i dialogu edycji. Globalny dodatkowy padding może zepsuć poprawne ekrany.
- **DOM-N2: gotowa reguła separatorów ma regresję.** Obecny błąd z TAB jest prawdziwy, lecz bezwarunkowy priorytet średnika zmienia poprawny trzykolumnowy CSV na niewłaściwy separator. Odtworzono oba przypadki.
- **NAU-N26: proponowany Crossfade nie skompiluje się.** Stabilny overload w użytej wersji nie ma contentKey. Usunięcie klucza może dodatkowo animować każdą aktualizację danych, a nie tylko przejście typu ekranu. Sprawdzone w bytecode 1.12.1.
- **NAU-N30: @Immutable nadal ma znaczenie przy strong skipping.** Wpływa na porównywanie parametrów; nie usuwać go z uzasadnieniem „nic nie daje”. [Opis porównań stabilnych i niestabilnych parametrów](https://developer.android.com/develop/ui/compose/performance/stability/strongskipping).
- **TB-N6: NoCredentialException nie oznacza tylko braku konta Google.** Gotowy tekst naprawy był zbyt kategoryczny. [Oficjalne przyczyny wyjątku](https://developer.android.com/identity/sign-in/credential-manager-troubleshooting-guide).
- **DAT-N1: stalePositions puste nie oznacza po prostu obecności pozycji.** Duplikaty/luki również dają niepusty zbiór. Użycie tej flagi jako warunku naprawy mogłoby pozostawić dokładnie konflikt, który miało naprawić.
- **DAT-N2: same tombstony nie załatwiają resetu wszystkiego.** Aktywność scala się przez max, a tryby wbudowane mają regułę odżywania. Przywrócenie starej kopii z tymi samymi ID też potrzebuje określonej polityki.
- **NAU-N2/EKR-N18: samo dismiss nie anuluje kolejki oczekujących snackbarów.** Potrzebny kontrolowany job prezentacji; ważnych akcji retry i undo nie wolno zgubić.
- **NAU-N18: bezwarunkowy wewnętrzny scroll może spowodować crash.** MessageState jest też elementem LazyColumn; przewijany kontener musi mieć skończoną wysokość.
- **TB-N22: dwa testy audio nie są duplikatem.** Jeden przypina SEED_MODES, drugi osobny CompoundExpander. Nie usuwać ważnej regresji na podstawie podobnych asercji.
- **TB-N30: schematy Room już obejmuje główny config Repomix.** `app/**/*.json` jest wystarczającym wzorcem. Wzmianka o stylach nie jest błędem tylko dlatego, że ekran jest w Compose.
- **TB-N14: feedback to również wibracja.** Proponowana nazwa ograniczona do „dźwięku” nie opisywałaby pełnego działania.

Wcześniejsze P35 o automatycznym ściszaniu mowy także wymaga korekty. Celowość komentarza nie czyni go zgodnym z platformą.

## Plan wdrożenia po zatwierdzeniu zakresu

Zadania wykonywać kolejno. Nie potrzeba sześciu równoległych fixerów ani nowych warstw dla samych limitów długości pliku. Każdy pakiet obejmuje implementację i jego testy. Dla PARTIAL obowiązuje wyłącznie potwierdzony zakres, z wyraźnymi wyjątkami produktowymi w werdykcie.

| Kolejność | Pakiet | Zakres i kryterium zakończenia |
|---:|---|---|
| 1 | Synchronizacja i konto | DAT-N1/N3/N4/N5/N6/N7/N11/N12/N13/N14/N17/N26 + EKR-N14. Test dwóch piszących urządzeń, retry, awarii migracji i nowej sesji z oczekującym zapisem. Bez zmiany zasad resetu DAT-N2. |
| 2 | Nawigacja po zapisie | EKR-N2/N16: zdarzenia nie giną przy STARTED, właściwy ekran zamyka się raz, backup/reset nie uruchamiają się równocześnie. |
| 3 | Dźwięk i błędy nauki | AUD-N1/N2/N3/N5/N6/N8/N11/N12/N13/N15/N16 + NAU-N1/N2/N5/N17. Zachować ręczną pauzę, retry zapisów i działanie przy odmowie uprawnień. Zmianę fokusu poza serwisem AUD-N4 zostawić do decyzji. |
| 4 | Import i brakujące małe funkcje | DOM-N1/N2/N5 + EKR-N4; dalsza część DOM-N3/limity CSV dopiero po regule dla pustych kolumn i zatwierdzeniu ograniczeń. |
| 5 | Dostępność i błędy układu | NAU-N3/N4/N8/N12/N18/N19/N20/N21/N22 + EKR-N9/N10/N19/N21/N27/N34. Sprawdzić małą szerokość, landscape, duży font i semantykę. |
| 6 | Teksty | NAU-N11 + EKR-N7/N8/N13/N15 + TB-N1/N4/N5/N6/N14/N15/N16/N17. Jedna wspólna edycja 5 locale; karta/fiszka dopiero po decyzji. |
| 7 | Testy brakujących kontraktów | TB-N2/N3/N8/N9/N10/N11/N18/N21/N24/N25/N26 oraz ograniczone porządki TB-N19/N20/N22/N23. Testy regresyjne dopisywać przy pakietach 1–6, a nie odkładać całości na koniec. |
| 8 | Małe porządki kodu | Pozostałe CONFIRMED i potwierdzone fragmenty PARTIAL: m.in. DOM-N6–N20, DAT-N9/N10/N18–N23, AUD-N10, NAU-N16/N28/N29/N30, EKR-N12/N26/N28–N33. Szczegóły i wyłączenia są w werdyktach; to nie zgoda na wszystkie refaktoryzacje pierwotnych raportów. |
| 9 | Build i narzędzia | TB-N7/N12/N27–N32/N34–N38 w ograniczonym potwierdzonym zakresie. Bez zmiany podpisu istniejącej instalacji, bez wdrożenia reguł, bez usuwania worktree. |
| 10 | Weryfikacja końcowa | Pełne unit tests/ktlint, lintDebug, build debug/release, następnie scenariusze telefonu. Dopiero gotowy wynik do zatwierdzenia integracji/main i push. |

Nie wprowadzać nowej migracji Room ani zmiany serializowanych danych w ramach drobnych porządków. DAT-N2 może wymagać osobnej decyzji o formacie/metadanych; nie ukrywać tego pod hasłem „bez migracji”.

### Duplikaty i przekazania

Wykonać jeden raz: DOM-N10/DAT-N19/EKR-N33 (nazwa strony), DOM-N15/EKR(stats) (klucz dnia), EKR-N7/TB-N4 (nazwy kroków), AUD-N12/TB-N32 (queries), AUD-N14/TB-N13 (backup Androida), NAU-N31/TB-N15 (słownik), NAU-N2/EKR-N18 (prezentacja błędów), DAT-N5/EKR-N14/TB-N3 (migracja). NAU-N13/EKR-N25 to jeden wybór animacji list. Obsługa nawigacji EKR-N2 obejmuje wiele ekranów i NavGraph.

### Decyzje produktowe, z rekomendowanym wariantem

Te warianty są przygotowane do zatwierdzenia; nie zostały wdrożone. Obejmują 24 całe pozycje DECISION oraz wybory zawarte wewnątrz PARTIAL.

| Temat | Rekomendacja | Alternatywa / uwaga |
|---|---|---|
| Wskaźnik audio U0 | Zachować na razie A bez przesuwania tekstu; B wybrać, jeśli ważniejsza jest etykieta słowna. | Warianty A/B/C i zrzuty są w pierwotnym RAPORT. |
| Ocena drugiej próby DOM-N4 | Stałe HARD po udanej drugiej próbie. | GOOD albo dotychczasowa ocena z dopasowania; bez zmiany własnych trybów. |
| Reset/przywrócenie DAT-N2 | Zastąpienie danych konta powinno być spójne na wszystkich urządzeniach. | Sam reset lokalny musi być tak nazwany; trzeba rozstrzygnąć także aktywność i tryby. |
| Logowanie DAT-N15 | Wariant Credential Managera dla jawnego przycisku Google. | Obecny arkusz może zostać. |
| Fokus AUD-N4 | Respektować fokus także poza serwisem, z komunikatem odmowy. | Obecne współdzielenie dźwięku. |
| Import EKR-N1/N20, DOM-N3 | Pytać przy porzuceniu/nadpisaniu; puste kolumny usuwać tylko bez danych i nazwy. | Nie zmieniać limitu wielkości bez pomiaru. |
| Dodanie fiszki EKR-N3 | Potwierdzenie, bez automatycznego kasowania filtrów. | Akcja Pokaż albo pełne automatyczne przewinięcie po wybraniu tego zachowania. |
| Podgląd importu EKR-N5/N6 | >2 kolumny w pionie; dolny pasek zmieniać dopiero po odtworzeniu braku miejsca z IME. | Dialog edycji wiersza; osobna akcja w top bar. |
| Edytor EKR-N11 | Dodaj poniżej wybranego kroku. | Obecne dopisywanie na końcu. |
| Potwierdzenia EKR-N17 | Krótkie wyniki na pulpicie; Cofnij tylko archiwizacji. | Obecne same powroty. |
| Animacje NAU-N6/N13 i EKR-N25 | Krótkie przejścia między ekranowymi stanami i na listach. | Zachować brak dodatkowego ruchu; nie animować każdej emisji danych. |
| Licznik, oceny NAU-N7/N9/N10 | Większa obecna pastylka, spójne barwy semantyczne, ramka sugestii. | Obecny wygląd lub liczba przy Ucz się. |
| Pauza NAU-N5/N14 | Naprawić FINISHED niezależnie; w pasku sam przycisk Wznów. | Pauzowanie po otwarciu dialogu to dodatkowy wybór. |
| Migracja NAU-N23 | Wyróżnione przeniesienie; pozostałe opcje nadal jawne. | Obecne równorzędne przyciski. |
| Ruch i responsywność NAU-N15/N24 | Zachować obecny motion i regułę landscape w tej rundzie. | Oddzielna modernizacja Expressive / reguła zależna od wysokości okna. |
| Etykiety/ikony EKR-N23/N24 | Ujednolicać tylko te same role i działania, zachować hierarchię nagłówków. | Osobny redesign całości. |
| Terminologia NAU-N31/TB-N15 | Fiszka dla jednostki nauki, talia dla zbioru, dotknięcie dla gestu. | Konsekwentna karta; „talia” jest już zatwierdzona. |
| Nazwa TB-N33 | RayColl (debug) w debug, przez override zasobu. | Identyczna nazwa obu instalacji. |

## Wykonana weryfikacja i jej granice

1. Odczytano właściwą rozmowę Claude, raporty obu dni, wcześniejsze ustalenia i powiązaną historię git. Przejrzano kod/callerów oraz testy dla ocenianych mechanizmów; nie oparto werdyktów wyłącznie na deklaracji wcześniejszych agentów.
2. Uruchomiono istniejące testy ponownie, z pominięciem cache wyniku: `:app:testDebugUnitTest --rerun :app:ktlintCheck --offline --no-build-cache`. **BUILD SUCCESSFUL**,23 s; XML wyników: **533 tests,0 failures,0 errors,0 skipped**. To dowód aktualnej bazy regresyjnej, nie dowód nieistnienia opisanych luk.
3. Osobny Java reproducer wywołał rzeczywiste klasy ze skompilowanego debug jar. Asercje potwierdziły sześć poniższych obserwacji; nie podmieniano kodu aplikacji ich kopiami.

```text
DOM-N2: actual detector chooses COMMA for TAB data with commas.
DOM-N2 proposed rule regresses: current COMMA becomes SEMICOLON for valid 3-column CSV.
DOM-N3: trailing empty cells produce pageCount=3
DAT-N1: order does not converge: [a, b, c, p, t] vs [a, b, c, t, p]; SyncDiff writes it again.
DAT-N2: an empty cloud does not delete an offline device's old deck.
DOM-N5: 400 active days are displayed as 365.
```

4. `javap` sprawdził biblioteki z lokalnego Gradle cache:
   - Media3 1.11.1: tekst powiadomienia to artist, title to title; subtitle nie jest używane przez domyślnego providera.
   - animation-android 1.12.1: tylko overload Transition.Crossfade przyjmuje contentKey.
   - Material3 1.4.0: niepusty actionLabel i brak duration wybierają Indefinite.
   - firebase-common 23.0.0: setApiKey używa checkNotEmpty z komunikatem „ApiKey must be set.”
5. Sprawdzono kompletność dokumentów skryptem:20+26+17+31+34+38 identyfikatorów, brak brakujących i zdublowanych sekcji. `git diff --quiet` potwierdziło brak zmian śledzonych plików aplikacji.

To **przegląd jednego modelu Codex**, uzupełniony wykonaniem kodu i dokumentacją, nie niezależny konsensus kilku modeli. Przeczytano skill multi-review, ale jego probe Bash na tej sesji Windows kończył się kodem 1 bez wyniku; nie uruchamiano backendów recenzujących i nie wysyłano im kodu. Zastosowano własną weryfikację oraz zasady ograniczania zbędnych refaktoryzacji.

Nie wykonano w tej rundzie lintDebug/buildów release, testów Firestore Emulator ani wdrożenia reguł. Nie sprawdzono dźwięku, Bluetooth, rzeczywistej klawiatury, TalkBack i odtwarzania backupu na telefonie; ADB nie uruchomił serwera. Część uwag UX pozostaje wprost propozycją do oględzin. Nie wykonano niezależnej recenzji native speakera całych tłumaczeń.

Źródła platformowe użyte przy ocenie: lokalny bytecode wskazanych wersji oraz dokumentacja [audio focus](https://developer.android.com/media/optimize/audio-focus), [wyjścia audio](https://developer.android.com/media/platform/output), [ContextCompat dla języka aplikacji](https://developer.android.com/reference/androidx/core/content/ContextCompat), [błędów Credential Managera](https://developer.android.com/identity/sign-in/credential-manager-troubleshooting-guide), [jawnego przycisku Google](https://developer.android.com/identity/sign-in/credential-manager-siwg-implementation), [backupów i składni ścieżek](https://developer.android.com/identity/data/autobackup), [strong skipping](https://developer.android.com/develop/ui/compose/performance/stability/strongskipping) i [reguł Firestore v 2](https://firebase.google.com/docs/firestore/security/rules-structure). Źródła wspierają konkretne ustalenia w werdyktach; nie oznaczają wykonania opisanych w nich testów urządzenia.
