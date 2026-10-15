# Red team Codex: EKR (34 uwagi)

Stan `39f0690b`, 15.10.2026. Ścieżki względem `app/src/main/java/com/raycoll/app/ui/`, poza wskazanymi wyjątkami. Propozycje nowego UX są DECISION, nawet gdy rozsądne. Nie zmieniono kodu.

### EKR-N1 — DECISION — potwierdzenie porzucenia importu jest nowym zachowaniem

Ekran importu rzeczywiście wychodzi bez pytania. Nie ma jednak ustalenia, że każdy szkic ma być chroniony identycznie jak edytowany zapisany tryb.
Rekomendacja: pytanie przy opuszczeniu niepustego/zmienionego importu, strzałka i systemowy back tą samą ścieżką. Uwzględnić poprawki komórek i ustawienia, nie tylko niepustą nazwę. W trakcie zapisu nie pozwalać przypadkowo skasować operacji. Po decyzji S/M; test obu ścieżek back i restoration; niskie ryzyko.

### EKR-N2 — CONFIRMED — zakończenie operacji może zostać skonsumowane przed RESUMED

`CollectEvents` odbiera od STARTED; `NavGraph.popIfResumed` po cichu odrzuca wcześniejszy powrót. Dotyczy także wejścia na ekran podczas przejścia, nie tylko powrotu z tła. Import po sukcesie celowo trzyma importing=true, więc utrata tego zdarzenia jest istotna.
Naprawa M: osobny callback ukończenia związany z konkretnym NavBackStackEntry. Przechować żądanie do RESUMED tego wpisu lub obsłużyć bezpiecznie jego zamknięcie; nie poluzować globalnego strażnika tapnięć. Sam warunek „current entry nie jest DESTROYED” jest za słaby: po opóźnieniu mógłby zamknąć inny ekran.
Test STARTED → Imported/Closed → RESUMED, brak podwójnego pop i brak zamknięcia innego wpisu. CROSS-LAYER NAU/navigation i wszystkie emitujące ekrany; średnie ryzyko.

### EKR-N3 — DECISION — nowa fiszka potrzebuje informacji zwrotnej, ale reset filtrów jest wyborem

Saved tylko zamyka dialog. Nowa karta jest na końcu, a filtr może ją ukryć. Nie dowiedziono utraty zapisu.
Rekomendacja: snackbar „Dodano fiszkę”, zachowujący wyszukiwanie i kategorię; opcjonalna akcja „Pokaż” może świadomie odsłonić kartę. Alternatywa automatyczne wyczyszczenie filtrów i przewinięcie. Po decyzji S/M; rozróżnić add/update w zdarzeniu i test filtra. Nie resetować użytkownikowi wyszukiwania bez przyjęcia tej zmiany.

### EKR-N4 — PARTIAL — przycisk czyszczenia był zatwierdzony; pozostałe akcje są dodatkami

`BrowseScreen.kt:215–225` nie ma trailingIcon, co potwierdza brak wcześniej uzgodnionej funkcji. `BrowseSearchFieldTest` testuje szybkie pisanie, nie istnienie X.
Naprawa S: X dla niepustego query z istniejącym lub nowym zlokalizowanym opisem; Search chowa klawiaturę, nie zmienia query. Nowe „Wyczyść wszystkie filtry” w pustym wyniku jest osobną propozycją UX, można dołączyć po zgodzie. Test czyszczenia i zachowania pola przy opóźnionym filtrowaniu. CROSS-LAYER TB; niskie ryzyko.

### EKR-N5 — DECISION — podgląd wielu kolumn wymaga wybranego układu

Przy 3–5 kolumnach równe pola robią się wąskie. Nie jest to błąd samego parsera, a limit zapisanych stron to 20, widocznych 5; nie należy ich mieszać.
Rekomendacja: dla >2 kolumn karta wiersza z nazwanymi polami w pionie; alternatywa kompaktowy podgląd i edycja w dialogu. Nie obcinać po cichu kolumn 6–20. Ostrzec o przekroczeniu dopuszczalnego importu przed tworzeniem ogromnego formularza. Po decyzji M, nie automatycznie L; test 2/3/5/20/>20 i fontScale; CROSS-LAYER DOM/TB, średnie ryzyko.

### EKR-N6 — DECISION — stały przycisk importu jest celowym kompromisem

Komentarz `SubmitBar` wyjaśnia, dlaczego przycisk nie przewija się z setkami wierszy. Raport nie sprawdził geometrii z rzeczywistą klawiaturą. Nie usuwać go automatycznie.
Rekomendacja: najpierw kontrola landscape z IME; jeśli brakuje miejsca, akcja importu w top bar w niskim oknie. Alternatywa ukrycie dolnego paska przy IME, ale musi pozostać dostępna ścieżka importu. S po decyzji i odtworzeniu problemu.

### EKR-N7 — CONFIRMED — komunikaty podają inne nazwy kroków niż lista

`StepTexts.issueText` ma osobne literalne nazwy w strings. Naprawa S: parametry komunikatów z tych samych zasobów step_* co tytuły. Ustalić poprawne przypadki gramatyczne zamiast ślepego składania zdania; placeholdery i cytowanie ×5 locale. CROSS-LAYER TB-N4, wykonać raz. Test widocznego tekstu i parytetu placeholderów; niskie ryzyko.

### EKR-N8 — PARTIAL — żargon i zaokrąglanie są konkretne, nazwy grup nie są błędem funkcji

Zasoby zawierają STT/TTS, „feedback”, „fallback” i tytuł mnożnika z jedną cyfrą po przecinku. Warto usunąć żargon oraz pokazywać wartość 1.25 zgodną z polem, z formatowaniem locale.
Naprawa S: poprawić teksty i format liczby; nie zmieniać serializowanych kind ani domyślnych nazw zapisanych trybów. Usunięcie słowa „Złożony”/przegrupowanie bloków jest opcjonalnym UX, nie techniczną koniecznością. CROSS-LAYER TB; test zasobów i tytułu mnożnika 1.25, niskie ryzyko.

### EKR-N9 — CONFIRMED — brakuje kontekstu akcji i etykiet podglądu

Usuń w Browse ma tę samą etykietę dla wszystkich kart; komórki podglądu nie nazywają kolumny/wiersza; wskazane tytuły nie są heading. Naprawa S: zlokalizowany opis z krótką treścią fiszki, etykiety komórek i heading dla nagłówków. Nie zastępować semantyki edycji samym contentDescription, które ukryje wartość pola. CROSS-LAYER TB; test semantyki i kolejności odczytu, niskie ryzyko.

### EKR-N10 — PARTIAL — powiększyć wiersze przełączników i uzupełnić rolę; nie wymaga to nowej wersji całej karty

Switch bez callbacku pozostawia interakcję rodzicowi; rodzic powinien zapewnić minimum 48 dp. Rzeczywisty hitbox może być większy przez rozszerzenie systemowe, więc liczby 32/40 dp nie dowodzą, że nic poza nimi nie reaguje. Jawna wysokość jest bezpieczniejsza.
Naprawa S: heightIn(min=48.dp) obu wierszy, Role.Button/onClickLabel na karcie trybu. Przycinanie ripple rozwiązać na właściwym klikalnym kontenerze; osobny overload RayCollCard tylko jeśli będzie rzeczywiście użyty. CROSS-LAYER NAU/components; test wymiarów i roli, niskie ryzyko.

### EKR-N11 — DECISION — wstawianie kroku w środku to usprawnienie edytora

`addStep` jawnie dopisuje na końcu, strzałki działają. Rekomendacja: „Dodaj poniżej” dla wybranego kroku, zachować też dopisanie na końcu. Alternatywa pozostawić obecny prosty model. Po decyzji M: index/key docelowego miejsca i test wstawienia, przesunięcia oraz undo; średnie ryzyko.

### EKR-N12 — PARTIAL — współdzielona selekcja tak; zmiana przewijania Browse wymaga decyzji

Cztery miejsca powtarzają FilterChip z rolą pojedynczego wyboru. Browse świadomie ma horizontalScroll, więc ostatnie opcje są dostępne po przewinięciu, a nie niedostępne.
Naprawa porządkowa S: wspólny mały wiersz wyboru z poprawną rolą, zachować etykiety i warianty potrzebne poszczególnym callerom; nie budować rozbudowanego modelu opcji. Rekomendacja UX do zatwierdzenia: Browse zawijane wiersze zamiast poziomego przewijania. Test pojedynczego wyboru i szerokości 360 dp; CROSS-LAYER NAU/components, niskie ryzyko.

### EKR-N13 — CONFIRMED — usuwanie współdzielonego trybu wymaga informacji o skutku

Tryby są wspólne, a `resolveStudyMode` wybiera zastępczy po usunięciu. Teksty nie wyjaśniają tego w dialogu i ustawieniach talii.
Naprawa S: dopisać zwięzłą informację o wspólnym użyciu oraz przejściu talii na domyślny tryb. Nie przepisywać zapisanych activeModeId jako skutku samej korekty tekstu. CROSS-LAYER TB; parytet tłumaczeń, niskie ryzyko.

### EKR-N14 — CONFIRMED — migracja nie ma ochrony przed wyjątkiem i wielokrotnym zleceniem

`MainViewModel.resolveMigration` nie obsługuje await, a dialog ma stale aktywne przyciski. Mutex serializuje operacje, ale ich nie deduplikuje: drugie żądanie wykona się potem.
Naprawa M: stan migrating i guard ustawiony przed launch, disabled dla wszystkich trzech opcji, obsłużony wynik/komunikat błędu z zachowaną możliwością retry. Sam launchSaving to za mało, jeśli dialog nie ma gdzie pokazać błędu. CROSS-LAYER DAT-N5 + NAU/main; test dwukliku i awarii. Średnie ryzyko danych.

### EKR-N15 — CONFIRMED — komunikaty backupu mieszają się z importem fiszek

Różne operacje mają te same ogólne słowa import/export. Naprawa S: sekcja „Kopia zapasowa” oraz wyniki „Przywrócono dane z kopii” / odpowiedni błąd w 5 językach. CROSS-LAYER TB; zasoby i kontrola dialogu zastępowania danych. Niskie ryzyko, bez zmiany zakresu przywracania.

### EKR-N16 — CONFIRMED — przywrócenie i reset mogą zostać uruchomione równolegle

Operacje w AppSettingsViewModel to niezależne launch; transakcja Room chroni pojedynczą podmianę, ale nie ustala, które z dwóch poleceń użytkownik chciał wykonać. Eksport może również pobrać stan w trakcie tego przepływu.
Naprawa M: wspólny stan operacji backup/reset i guard przed zleceniem; wyłączyć powiązane akcje oraz pokazać postęp. Nie blokować niepowiązanych ustawień motywu. Test konkurencyjnych poleceń i odblokowania po błędzie/anulowaniu. CROSS-LAYER DAT + TB; średnie ryzyko.

### EKR-N17 — DECISION — potwierdzenia na ekranie docelowym i Cofnij są nowym UX

Operacje kończą się przejściem na pulpit, ale brak snackbar nie dowodzi utraty danych. Rekomendacja: krótki wynik importu/archiwizacji na docelowym pulpicie; Cofnij tylko dla odwracalnej archiwizacji. Nie oferować cofnięcia trwałego usunięcia. Alternatywa obecne powroty.
Po decyzji M: wynik przypisany do konkretnego wpisu nawigacji, bez globalnego singletona wiadomości; test jednorazowego odbioru i restoration. CROSS-LAYER NAU/navigation/dashboard + TB; średnie ryzyko.

### EKR-N18 — PARTIAL — kolejki komunikatów istnieją, ale helper z samym dismiss nie zapewnia „najnowszy wygrywa”

CollectEvents odpala równoległe korutyny, a showSnackbar kolejkuje na mutexie. Kolejne wywołanie dismiss może usunąć bieżący komunikat, lecz nie anuluje wcześniej czekających wywołań.
Naprawa S/M: jeden kontrolowany job komunikatu na host, cancel poprzedniego zanim uruchomi się nowy; osobno traktować nawigację i komunikaty z ważną akcją. Nie zastępować całego CollectEvents collectLatest, co mogłoby anulować niezwiązany zapis/nawigację. CROSS-LAYER NAU-N2; test trzech szybkich komunikatów i zachowania retry/undo, średnie ryzyko.

### EKR-N19 — CONFIRMED — stała wysokość przycisków może ucinać tekst

`TemplatePicker` nie przewija się i ma height 48, podobnie SubmitBar. Naprawa S: heightIn(min=48.dp), przewijanie samego pełnoekranowego wyboru szablonu w skończonym kontenerze. Nie wkładać scrolla do istniejącej nieskończenie mierzonej listy. Test landscape/fontScale 2/DE i PL; niskie ryzyko.

### EKR-N20 — PARTIAL — duży tekst obciąża ekran, ale próg 2 MB nie wynika z pomiaru

DocumentFiles ma wspólny limit 20 MB, a edycja komórki kopiuje listę wierszy. Parsowanie tekstu jest jednak opóźniane o 150 ms i idzie poza UI, więc „całe przetwarzanie po każdym znaku” jest nieścisłe. Wybór pliku faktycznie nadpisuje obecne źródło.
Naprawa do zatwierdzenia: potwierdzenie nadpisania niepustego importu; osobny limit CSV ustalić po pomiarze na telefonie (propozycja 2–3 MB jest nowym ograniczeniem produktu). Nie obniżać limitu backupów. S/M, CROSS-LAYER DAT/files + TB; test granicy wielkości i odmowy nadpisania, średnie ryzyko.

### EKR-N21 — CONFIRMED — ekran ustawień emituje wymyślone domyślne wartości przed odczytem

`stateIn(...AppSettingsUiState())` zawiera UserPreferences defaults. Naprawa S: stan loading/null dla preferencji do pierwszej emisji; można nadal wyświetlać niezależny stan konta. Nie zapisywać przełączników z wartości początkowych. Test opóźnionego pierwszego odczytu; niskie ryzyko.

### EKR-N22 — PARTIAL — akcje IME zasadne; automatyczne otwieranie klawiatury to wybór

Next dla nazwy importu i Search dla wyszukiwania są małymi usprawnieniami. Wymuszony autofokus dialogu usunięcia powoduje dodatkowe pojawienie się klawiatury; nie jest wymagany do poprawności.
Naprawa S: spójne akcje Next/Search/Done; Done usuwa tylko po spełnieniu tego samego warunku i świadomej akcji co przycisk. Autofokus do zatwierdzenia z resztą UX. EKR-N4 pokrywa Search, nie robić drugi raz. Test warunku potwierdzenia usunięcia; niskie ryzyko.

### EKR-N23 — DECISION — różne style tekstu mogą oznaczać różne poziomy hierarchii

bodyLarge dla wiersza, labelLarge dla grupy i titleMedium dla sekcji nie są automatycznie niespójnością. Rekomendacja: zachować tytuły sekcji i ujednolicić wyłącznie równorzędne etykiety. Nie tworzyć FieldLabel, dopóki wspólny kontrakt nie jest ustalony. S po decyzji i obejrzeniu ekranów; kontrola wizualna.

### EKR-N24 — DECISION — ikony i wielkości tytułów to propozycja stylistyczna

Delete/DeleteOutline i strzałki pełne/klawiaturowe przedstawiają te same działania; nie dowiedziono pomylenia akcji. Nazwy talii na statystyce i w archiwum nie muszą mieć identycznej hierarchii.
Rekomendacja: jednolita ikona usuwania i przesuwania w podobnych edytorach, zachować różnice nagłówków wynikające z roli. Alternatywa obecny wygląd. S, wizualna kontrola po decyzji.

### EKR-N25 — DECISION — animowanie list to wspólna decyzja z NAU-N13

Stabilne klucze są dostępne, animateItem jest technicznie możliwe. Brak animacji nie jest usterką. Po zgodzie S: zastosować spójnie w Browse/Archive/StudyModes i Dashboard, bez zmiany porządku; sprawdzić shared transition i ograniczenie animacji. Jedno zadanie, kontrola wizualna.

### EKR-N26 — PARTIAL — odstępy ikon warto ujednolicić bez nowej warstwy przycisków

Różnice 4/8 dp są w kodzie. Naprawa S: użyć istniejących ButtonDefaults.IconSpacing/IconSize i właściwego contentPadding tam, gdzie to ten sam rodzaj przycisku. Nie rozciągać reguły dla pełnego Button na TextButton, jeśli mają inny kontrakt. Bez nowego komponentu dla pary Icon+Text. Ktlint i oględziny; niskie ryzyko.

### EKR-N27 — CONFIRMED — statystyki dublują istniejący SectionCard

Prywatna wersja nie nadaje heading. Naprawa S: użyć wspólnej z `spacing=Spacing.s`, jeśli dotychczasowe odstępy mają zostać. Zachować dostępny opis kategorii i nie zmieniać sensu tytułów. Test semantyki i statystyk; niskie ryzyko.

### EKR-N28 — PARTIAL — wydzielić spójne pola parametrów, nie każdy krótki fragment

StepCard łączy konstrukcję karty i rozbudowany zestaw pól. Naprawa porządkowa S/M: osobny StepParamFields i ewentualnie TemplatePicker, jeśli ułatwi prace UX. Nie trzeba równocześnie wydzielać małych NoticeBanner/ModeNameField dla liczby linii. Zachować key pól i ważność wpisanych liczb. `StepCardTest`, `NumberFieldTest`, test restoration; średnie ryzyko stanu UI.

### EKR-N29 — CONFIRMED — czyste parsowanie importu ma własną odpowiedzialność

ParseInput/ParsedSource/parseSource/buildSource nie potrzebują ViewModelu. Naprawa S/M: osobny wewnętrzny plik ImportParsing, bez nowego reducera i bez zmiany synchronizacji parse/rebuild jobs. Podgląd można przenieść jako spójny blok przy EKR-N5, nie obowiązkowo teraz. `ImportViewModelTest` i parser, szczególnie edycja przed debounce; średnie ryzyko przy przenoszeniu widoczności.

### EKR-N30 — PARTIAL — sekcja trybów może być osobno; sealed dialogi nie są konieczne

ModesSection jest spójną częścią dużego ekranu, możliwą do przeniesienia S. Flagi dialogów są niezależne i rememberSaveable. Nie zastępować ich równocześnie nową hierarchią stanów bez wykazanego problemu; wydzielenie DeckDialogs wymagałoby wielu parametrów.
Naprawa: ograniczony ruch sekcji, testy ustawień i restoration, ktlint; niskie ryzyko.

### EKR-N31 — PARTIAL — dispatchery warto wstrzykiwać, ale ticker ma inną rolę niż obliczenia

Różne sposoby wstrzyknięcia istnieją. `StatsViewModel` już przenosi ciężkie combine na Default; Main.immediate dotyczy tylko lekkiego tickera i sterowania jego czasem. Nie jest dowodem obliczeń UI na main.
Naprawa S/M: spójny @DefaultDispatcher dla obliczeń, zachować możliwość podania testowego dispatchera i test zatrzymanego filtrowania Browse. Jeśli usuwać flowOn tickera, wstrzyknąć/testować czas świadomie; nie zepsuć advanceTimeBy. Testy VM z zegarem i zmianą dnia, średnie ryzyko testów.

### EKR-N32 — PARTIAL — kilka lokalnych wartości może pomóc, formatowanie samo nie jest defektem

Wielowierszowe lambdy i if pochodzą z ktlint; ponowne ktlintFormat przy tym samym kodzie ich nie naprawi. Naprawa S tylko w miejscach, gdzie nazwana wartość rzeczywiście wyjaśnia warunek (np. nazwa wbudowanego trybu), bez dodatkowych helperów dla samej liczby linii. Kompilacja/ktlint i istniejące testy; niskie ryzyko.

### EKR-N33 — PARTIAL — komentarze są zbędne, fallback jest współdzielonym porządkiem, pusty katalog nie należy do git

Usunąć znaczniki ui 1/ui 2 przy porządkach zasobów. Fallback nazwy objęty DOM-N10/DAT-N19, nie zmieniać API niezależnie. Pusty `ui/component` można usunąć lokalnie, ale nie jest to zmiana wersjonowanego projektu. S, parytet zasobów i kompilacja; bez osobnego testu komentarza.

### EKR-N34 — CONFIRMED — banner na wyjściu dostaje już pustą treść

AnimatedVisibility widzi aktualną pustą listę blockingIssues/tips, więc znikają punkty przed końcem animacji kontenera. Naprawa S: zapamiętać ostatnią niepustą treść wyłącznie dla wychodzącego bannera; nowe błędy mają od razu zastępować stare. Nie wyłączać komunikatów jako sposobu naprawy. Test z wstrzymanym zegarem animacji i szybkim ponownym błędem; niskie ryzyko.
