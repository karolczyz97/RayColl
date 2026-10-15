# Rozliczenie 166 uwag — stan po kontynuacji

Każdy identyfikator został rozliczony. DOMKNIĘTE oznacza wdrożenie zaakceptowanego zakresu kodu/testów/dokumentacji, nie każdej pierwotnej propozycji. Przy PARTIAL obowiązuje skorygowany zakres werdyktu. ZACHOWANE oznacza świadome pozostawienie zgodne z oceną lub wynikiem weryfikacji. Duplikaty oznaczają wspólną zmianę, nie kolejne niezależne błędy.

Weryfikacja fizycznego urządzenia jest jawnie oddzielona w [WDROZENIE.md](WDROZENIE.md); nie została zastąpiona testem JVM. Historyczne audyty Claude’a (`audit-*.md`) opisują wcześniejszy stan.

| ID | Werdykt | Stan | Zakres / źródło | Uzupełnienie |
| --- | --- | --- | --- | --- |
| DOM-N1 | CONFIRMED | DOMKNIĘTE | [usunięty własny tryb wywołuje pytanie o migrację](verdict-domain-codex.md) |  |
| DOM-N2 | PARTIAL | DOMKNIĘTE | [błąd separatora realny, proponowana heurystyka powoduje regresję](verdict-domain-codex.md) |  |
| DOM-N3 | PARTIAL | DOMKNIĘTE | [puste końcowe kolumny są rzeczywiste, lecz nie zawsze zbędne](verdict-domain-codex.md) |  |
| DOM-N4 | DECISION | DOMKNIĘTE | [ocena za drugą próbę jest elementem zasad nauki](verdict-domain-codex.md) | Druga poprawna próba = HARD w zapisie i panelu; upgrade tylko nieedytowanego Audio, własne tryby zachowane. |
| DOM-N5 | CONFIRMED | DOMKNIĘTE | [seria naprawdę zatrzymuje się na 365](verdict-domain-codex.md) | Seria >365 dni i start od wczoraj: testy 400 dni/gap. |
| DOM-N6 | CONFIRMED | DOMKNIĘTE | [fallback automatycznej oceny jest obecnie nieosiągalny](verdict-domain-codex.md) |  |
| DOM-N7 | CONFIRMED | DOMKNIĘTE | [zbędny fallback i dwa odczyty mapy](verdict-domain-codex.md) |  |
| DOM-N8 | PARTIAL | DOMKNIĘTE | [powtórzone komunikaty warto usunąć, nowy miniwalidator zbędny](verdict-domain-codex.md) |  |
| DOM-N9 | CONFIRMED | DOMKNIĘTE | [cztery kopie zwalniania przytrzymania](verdict-domain-codex.md) |  |
| DOM-N10 | CONFIRMED | DOMKNIĘTE | [domyślna nazwa strony ma cztery kopie](verdict-domain-codex.md) |  |
| DOM-N11 | PARTIAL | DOMKNIĘTE | [wspólne obliczenie ukrytych stron, bez nowego API dla każdej listy](verdict-domain-codex.md) |  |
| DOM-N12 | CONFIRMED | DOMKNIĘTE | [niejawny receiver w `takeIf` utrudnia odczyt](verdict-domain-codex.md) |  |
| DOM-N13 | PARTIAL | DOMKNIĘTE | [drobne `takeIf` dopuszczalne, logika priorytetów zostaje](verdict-domain-codex.md) |  |
| DOM-N14 | CONFIRMED | DOMKNIĘTE | [można zawęzić widoczność pomocników](verdict-domain-codex.md) |  |
| DOM-N15 | PARTIAL | DOMKNIĘTE | [wspólny klucz dnia tak, przeniesienie `live()` opcjonalne](verdict-domain-codex.md) |  |
| DOM-N16 | PARTIAL | DOMKNIĘTE | [uprościć czas, nie przepisywać poprawnej arytmetyki](verdict-domain-codex.md) |  |
| DOM-N17 | CONFIRMED | DOMKNIĘTE | [nazwać progi heurystyki nagłówka](verdict-domain-codex.md) |  |
| DOM-N18 | CONFIRMED | DOMKNIĘTE | [jawna karta i kontekst anulowania upraszczają `runSteps`](verdict-domain-codex.md) |  |
| DOM-N19 | PARTIAL | DOMKNIĘTE | [zbędny clamp tak, nowy obiekt czasu niepotrzebny](verdict-domain-codex.md) |  |
| DOM-N20 | CONFIRMED | DOMKNIĘTE | [`WallClock` lepiej wyraża kontrakt czasu](verdict-domain-codex.md) |  |
| DAT-N1 | CONFIRMED | DOMKNIĘTE | [lokalna kolejność nie zbiega się między urządzeniami](verdict-data-codex.md) | Autorytatywna kolejność z pełnymi pozycjami; stara chmura zachowuje lokalną kolejność podczas migracji. |
| DAT-N2 | DECISION | DOMKNIĘTE | [reset/przywrócenie mogą zostać cofnięte przez dane drugiego urządzenia](verdict-data-codex.md) | Room v1→v2, generacje konta i dokumentów, transakcyjny warunek wersji; testy wielu porcji i legacy. |
| DAT-N3 | CONFIRMED | DOMKNIĘTE | [timeout bootstrapu wygląda jak poprawna synchronizacja](verdict-data-codex.md) |  |
| DAT-N4 | CONFIRMED | DOMKNIĘTE | [powrót do aplikacji nie opróżnia oczekującej kolejki](verdict-data-codex.md) |  |
| DAT-N5 | PARTIAL | DOMKNIĘTE | [nieobsłużony wyjątek `await()` jest realny, diagnoza przestrzeni zbyt ogólna](verdict-data-codex.md) |  |
| DAT-N6 | PARTIAL | DOMKNIĘTE | [brak limitu na oczekiwanie za reconcile; wyjątki providera wymagają ostrożności](verdict-data-codex.md) |  |
| DAT-N7 | CONFIRMED | DOMKNIĘTE | [pusty save robi zbędny odczyt i może przesunąć znacznik bez odczytania danych](verdict-data-codex.md) |  |
| DAT-N8 | NOT_WORTH | ZACHOWANE | [sam fakt podwójnego cache nie dowodzi potrzeby zmiany polityki SDK](verdict-data-codex.md) |  |
| DAT-N9 | PARTIAL | DOMKNIĘTE | [wydzielić niezależne pomocniki, nie budować nowej warstwy dla długości pliku](verdict-data-codex.md) |  |
| DAT-N10 | PARTIAL | DOMKNIĘTE | [nazwy i kolejność tak, czyszczenie archiwów nie musi trafić do Application](verdict-data-codex.md) |  |
| DAT-N11 | PARTIAL | DOMKNIĘTE | [guard archiwizacji/przywrócenia zasadny; pozostały scenariusz jest wąski](verdict-data-codex.md) |  |
| DAT-N12 | CONFIRMED | DOMKNIĘTE | [zapis bez zmiany może wygrać z faktyczną edycją](verdict-data-codex.md) |  |
| DAT-N13 | CONFIRMED | DOMKNIĘTE | [purge ma prostsze i atomowe rozwiązanie SQL](verdict-data-codex.md) |  |
| DAT-N14 | CONFIRMED | DOMKNIĘTE | [walidator dopuszcza dane odrzucane po zapisaniu do chmury](verdict-data-codex.md) | Własne puste nazwy odrzucone; znana dawna pusta nazwa wbudowanego trybu naprawiana podczas importu. |
| DAT-N15 | DECISION | DOMKNIĘTE | [jawny przycisk Google powinien używać przepływu przycisku](verdict-data-codex.md) |  |
| DAT-N16 | PARTIAL | DOMKNIĘTE | [komentarz konfiguracji wymaga korekty; migracja Firebase nie jest samym porządkiem](verdict-data-codex.md) |  |
| DAT-N17 | PARTIAL | DOMKNIĘTE | [komentarze poprawić, a nowa sesja wymaga sprawdzenia kolejki ocen](verdict-data-codex.md) |  |
| DAT-N18 | CONFIRMED | DOMKNIĘTE | [parametr kart w dekoderze talii nie jest potrzebny produkcji](verdict-data-codex.md) |  |
| DAT-N19 | PARTIAL | DOMKNIĘTE | [wspólna domyślna nazwa zamiast równoległego usuwania API](verdict-data-codex.md) |  |
| DAT-N20 | CONFIRMED | DOMKNIĘTE | [dwa jednakowe odczyty talii z kartami można nazwać](verdict-data-codex.md) |  |
| DAT-N21 | CONFIRMED | DOMKNIĘTE | [wspólne pobranie dokumentów z serwera usuwa powtórzenia](verdict-data-codex.md) |  |
| DAT-N22 | CONFIRMED | DOMKNIĘTE | [`ensureWorker` powinno jawnie zwracać Unit](verdict-data-codex.md) |  |
| DAT-N23 | CONFIRMED | DOMKNIĘTE | [logi tracą wyjątek](verdict-data-codex.md) |  |
| DAT-N24 | NOT_WORTH | ZACHOWANE | [wrapper i lokalizacja factoryReset mają sens w obecnej warstwie](verdict-data-codex.md) |  |
| DAT-N25 | INTENTIONAL | ZACHOWANE | [snapshot całego konta to świadomy prosty model sync](verdict-data-codex.md) |  |
| DAT-N26 | PARTIAL | DOMKNIĘTE | [fake ma pozycje i sortowanie, ale pomija stalePositions oraz kod produkcyjnego transportu](verdict-data-codex.md) |  |
| AUD-N1 | CONFIRMED | DOMKNIĘTE | [domyślne powiadomienie pomija postęp z subtitle](verdict-playback-codex.md) |  |
| AUD-N2 | CONFIRMED | DOMKNIĘTE | [dla mowy nie można polegać na automatycznym ściszaniu](verdict-playback-codex.md) |  |
| AUD-N3 | CONFIRMED | DOMKNIĘTE | [brak obsługi odłączenia słuchawek](verdict-playback-codex.md) |  |
| AUD-N4 | DECISION | DOMKNIĘTE | [poza serwisem polityka współdzielenia dźwięku jest nieokreślona](verdict-playback-codex.md) | Jeden właściciel fokusu; fizyczne STT z Google i Pixel Buds nadal do sprawdzenia na urządzeniu. |
| AUD-N5 | PARTIAL | DOMKNIĘTE | [początkowy push istnieje, lecz `collectLatest` nie jest bezpieczną gotową poprawką](verdict-playback-codex.md) |  |
| AUD-N6 | CONFIRMED | DOMKNIĘTE | [zasoby serwisu mogą ignorować język aplikacji na API 26–32](verdict-playback-codex.md) | ContextCompat.getString; lifecycle/kanał testowane na API 32/33. |
| AUD-N7 | WRONG | ZACHOWANE | [twierdzenie o braku obsługi IME przeczy kodowi](verdict-playback-codex.md) |  |
| AUD-N8 | CONFIRMED | DOMKNIĘTE | [efekty uprawnień startują ponownie po odtworzeniu ekranu](verdict-playback-codex.md) |  |
| AUD-N9 | INTENTIONAL | ZACHOWANE | [współdzielony silnik TTS to cache procesu, nie wykazany wyciek](verdict-playback-codex.md) |  |
| AUD-N10 | CONFIRMED | DOMKNIĘTE | [jedna mała reguła motywu może być wspólna](verdict-playback-codex.md) |  |
| AUD-N11 | CONFIRMED | DOMKNIĘTE | [`coroutineScope` w followSession nie ma zadania](verdict-playback-codex.md) |  |
| AUD-N12 | CONFIRMED | DOMKNIĘTE | [queries dla aktywności rozpoznawania nie jest używane](verdict-playback-codex.md) |  |
| AUD-N13 | PARTIAL | DOMKNIĘTE | [martwe SKIPPED można usunąć, ale nie usuwać testowanych zachowań](verdict-playback-codex.md) |  |
| AUD-N14 | PARTIAL | DOMKNIĘTE | [backup obejmuje wszystkie bazy, ale rozmiaru i plików urządzenia nie znamy](verdict-playback-codex.md) | Allowlist SQLite + WAL/SHM/journal; rzeczywisty restore wymaga urządzenia. |
| AUD-N15 | PARTIAL | DOMKNIĘTE | [proste porządki dopuszczalne; `.immediate` i offline nie są czystą kosmetyką](verdict-playback-codex.md) |  |
| AUD-N16 | CONFIRMED | DOMKNIĘTE | [nazwać powtarzane odtworzenie sygnału](verdict-playback-codex.md) |  |
| AUD-N17 | NOT_WORTH | ZACHOWANE | [działający observer nie wymaga migracji do Flow](verdict-playback-codex.md) |  |
| NAU-N1 | CONFIRMED | DOMKNIĘTE | [komunikat z akcją ma nieograniczony czas](verdict-ui-nauka-codex.md) |  |
| NAU-N2 | PARTIAL | DOMKNIĘTE | [powtarzane błędy są realne, deduplikacja wymaga rozróżnienia przyczyny](verdict-ui-nauka-codex.md) |  |
| NAU-N3 | CONFIRMED | DOMKNIĘTE | [miejsce paska podsumowania pojawia się dopiero po odczycie](verdict-ui-nauka-codex.md) |  |
| NAU-N4 | CONFIRMED | DOMKNIĘTE | [animacja odczytuje nową treść dla wychodzącego stanu](verdict-ui-nauka-codex.md) |  |
| NAU-N5 | CONFIRMED | DOMKNIĘTE | [potwierdzenie wyjścia może oznaczyć ukończoną sesję jako przerwaną](verdict-ui-nauka-codex.md) |  |
| NAU-N6 | DECISION | DOMKNIĘTE | [animowanie wszystkich stanów to propozycja wyglądu](verdict-ui-nauka-codex.md) |  |
| NAU-N7 | DECISION | DOMKNIĘTE | [wielkość licznika jest wyborem hierarchii informacji](verdict-ui-nauka-codex.md) |  |
| NAU-N8 | CONFIRMED | DOMKNIĘTE | [outline jest zbyt słabe dla zwykłego tekstu](verdict-ui-nauka-codex.md) |  |
| NAU-N9 | DECISION | DOMKNIĘTE | [stałe barwy ocen są rozsądną propozycją, nie błędem primaryContainer](verdict-ui-nauka-codex.md) |  |
| NAU-N10 | DECISION | DOMKNIĘTE | [wyróżnienie sugerowanej oceny to nowa wskazówka interfejsu](verdict-ui-nauka-codex.md) |  |
| NAU-N11 | CONFIRMED | DOMKNIĘTE | [próg odpowiedzi wymaga czytelniejszego opisu](verdict-ui-nauka-codex.md) |  |
| NAU-N12 | PARTIAL | DOMKNIĘTE | [warto nazwać postęp, ale nie dowiedziono odczytania go jako daty](verdict-ui-nauka-codex.md) |  |
| NAU-N13 | DECISION | DOMKNIĘTE | [animacja listy nie naprawia błędu danych](verdict-ui-nauka-codex.md) |  |
| NAU-N14 | DECISION | DOMKNIĘTE | [skrócenie tytułu przy pauzie jest kompromisem paska aplikacji](verdict-ui-nauka-codex.md) |  |
| NAU-N15 | DECISION | ZACHOWANE | [przejście całego motywu na Expressive zmienia zachowanie komponentów](verdict-ui-nauka-codex.md) | Zachowano obecną motion scheme zgodnie z rekomendacją; Expressive nie jest częścią naprawy. |
| NAU-N16 | PARTIAL | DOMKNIĘTE | [pojedynczy kształt odstaje, własne wrappery wszystkich przycisków zbędne](verdict-ui-nauka-codex.md) |  |
| NAU-N17 | CONFIRMED | DOMKNIĘTE | [autoscroll pozostaje aktywny przez cały czas karty](verdict-ui-nauka-codex.md) |  |
| NAU-N18 | PARTIAL | DOMKNIĘTE | [stan pełnego ekranu powinien dać się przewinąć, ale nie każdy caller ma skończoną wysokość](verdict-ui-nauka-codex.md) |  |
| NAU-N19 | PARTIAL | DOMKNIĘTE | [opisy już są wyśrodkowane](verdict-ui-nauka-codex.md) |  |
| NAU-N20 | CONFIRMED | DOMKNIĘTE | [klikany Surface zapewni poprawny obrys interakcji](verdict-ui-nauka-codex.md) |  |
| NAU-N21 | CONFIRMED | DOMKNIĘTE | [wynik i sugestia nie mają miejsca na zawijanie jako oddzielne elementy](verdict-ui-nauka-codex.md) |  |
| NAU-N22 | PARTIAL | DOMKNIĘTE | [kontekst talii poprawi listę akcji TalkBack, brak etykiety nie czyni całej karty nierozpoznawalną](verdict-ui-nauka-codex.md) |  |
| NAU-N23 | DECISION | DOMKNIĘTE | [wizualny priorytet migracji wymaga wyboru](verdict-ui-nauka-codex.md) |  |
| NAU-N24 | DECISION | ZACHOWANE | [układ zależny od wysokości byłby zmianą przyjętej reguły orientacji](verdict-ui-nauka-codex.md) | Zachowano zatwierdzony układ zależny od orientacji. |
| NAU-N25 | INTENTIONAL | ZACHOWANE | [Scaffold poprawnie odsuwa listę od systemowego paska](verdict-ui-nauka-codex.md) |  |
| NAU-N26 | PARTIAL | DOMKNIĘTE | [można ujednolicić animację, ale proponowany stabilny overload nie ma contentKey](verdict-ui-nauka-codex.md) |  |
| NAU-N27 | NOT_WORTH | ZACHOWANE | [arbitralne wydzielenie trzech helperów nie rozwiązuje wykazanego problemu](verdict-ui-nauka-codex.md) |  |
| NAU-N28 | PARTIAL | DOMKNIĘTE | [można rozdzielić odpowiedzialności, nie trzeba trzech plików dla limitu linii](verdict-ui-nauka-codex.md) |  |
| NAU-N29 | CONFIRMED | DOMKNIĘTE | [niewielka wspólna pastylka ma kilka rzeczywistych zastosowań](verdict-ui-nauka-codex.md) |  |
| NAU-N30 | PARTIAL | DOMKNIĘTE | [tylko interactionSource jest prostym, bezpiecznym porządkiem](verdict-ui-nauka-codex.md) |  |
| NAU-N31 | DECISION | DOMKNIĘTE | [karta/fiszka to niejednoznaczny wybór słownictwa](verdict-ui-nauka-codex.md) |  |
| EKR-N1 | DECISION | DOMKNIĘTE | [potwierdzenie porzucenia importu jest nowym zachowaniem](verdict-ui-ekrany-codex.md) |  |
| EKR-N2 | CONFIRMED | DOMKNIĘTE | [zakończenie operacji może zostać skonsumowane przed RESUMED](verdict-ui-ekrany-codex.md) |  |
| EKR-N3 | DECISION | DOMKNIĘTE | [nowa fiszka potrzebuje informacji zwrotnej, ale reset filtrów jest wyborem](verdict-ui-ekrany-codex.md) |  |
| EKR-N4 | PARTIAL | DOMKNIĘTE | [przycisk czyszczenia był zatwierdzony; pozostałe akcje są dodatkami](verdict-ui-ekrany-codex.md) |  |
| EKR-N5 | DECISION | DOMKNIĘTE | [podgląd wielu kolumn wymaga wybranego układu](verdict-ui-ekrany-codex.md) | Dwie kolumny obok siebie, >2 pionowe wiersze pól z nazwami; testy 2/3/5/20/>20 i dużej czcionki. |
| EKR-N6 | DECISION | DOMKNIĘTE | [stały przycisk importu jest celowym kompromisem](verdict-ui-ekrany-codex.md) | Import na dole; niskie okno z IME ma dostępną akcję w pasku, wymiar z WindowInfo. |
| EKR-N7 | CONFIRMED | DOMKNIĘTE | [komunikaty podają inne nazwy kroków niż lista](verdict-ui-ekrany-codex.md) |  |
| EKR-N8 | PARTIAL | DOMKNIĘTE | [żargon i zaokrąglanie są konkretne, nazwy grup nie są błędem funkcji](verdict-ui-ekrany-codex.md) |  |
| EKR-N9 | CONFIRMED | DOMKNIĘTE | [brakuje kontekstu akcji i etykiet podglądu](verdict-ui-ekrany-codex.md) |  |
| EKR-N10 | PARTIAL | DOMKNIĘTE | [powiększyć wiersze przełączników i uzupełnić rolę; nie wymaga to nowej wersji całej karty](verdict-ui-ekrany-codex.md) |  |
| EKR-N11 | DECISION | DOMKNIĘTE | [wstawianie kroku w środku to usprawnienie edytora](verdict-ui-ekrany-codex.md) |  |
| EKR-N12 | PARTIAL | DOMKNIĘTE | [współdzielona selekcja tak; zmiana przewijania Browse wymaga decyzji](verdict-ui-ekrany-codex.md) |  |
| EKR-N13 | CONFIRMED | DOMKNIĘTE | [usuwanie współdzielonego trybu wymaga informacji o skutku](verdict-ui-ekrany-codex.md) |  |
| EKR-N14 | CONFIRMED | DOMKNIĘTE | [migracja nie ma ochrony przed wyjątkiem i wielokrotnym zleceniem](verdict-ui-ekrany-codex.md) |  |
| EKR-N15 | CONFIRMED | DOMKNIĘTE | [komunikaty backupu mieszają się z importem fiszek](verdict-ui-ekrany-codex.md) |  |
| EKR-N16 | CONFIRMED | DOMKNIĘTE | [przywrócenie i reset mogą zostać uruchomione równolegle](verdict-ui-ekrany-codex.md) |  |
| EKR-N17 | DECISION | DOMKNIĘTE | [potwierdzenia na ekranie docelowym i Cofnij są nowym UX](verdict-ui-ekrany-codex.md) |  |
| EKR-N18 | PARTIAL | DOMKNIĘTE | [kolejki komunikatów istnieją, ale helper z samym dismiss nie zapewnia „najnowszy wygrywa”](verdict-ui-ekrany-codex.md) |  |
| EKR-N19 | CONFIRMED | DOMKNIĘTE | [stała wysokość przycisków może ucinać tekst](verdict-ui-ekrany-codex.md) |  |
| EKR-N20 | PARTIAL | DOMKNIĘTE | [duży tekst obciąża ekran, ale próg 2 MB nie wynika z pomiaru](verdict-ui-ekrany-codex.md) |  |
| EKR-N21 | CONFIRMED | DOMKNIĘTE | [ekran ustawień emituje wymyślone domyślne wartości przed odczytem](verdict-ui-ekrany-codex.md) |  |
| EKR-N22 | PARTIAL | DOMKNIĘTE | [akcje IME zasadne; automatyczne otwieranie klawiatury to wybór](verdict-ui-ekrany-codex.md) |  |
| EKR-N23 | DECISION | DOMKNIĘTE | [różne style tekstu mogą oznaczać różne poziomy hierarchii](verdict-ui-ekrany-codex.md) |  |
| EKR-N24 | DECISION | DOMKNIĘTE | [ikony i wielkości tytułów to propozycja stylistyczna](verdict-ui-ekrany-codex.md) |  |
| EKR-N25 | DECISION | DOMKNIĘTE | [animowanie list to wspólna decyzja z NAU-N13](verdict-ui-ekrany-codex.md) |  |
| EKR-N26 | PARTIAL | DOMKNIĘTE | [odstępy ikon warto ujednolicić bez nowej warstwy przycisków](verdict-ui-ekrany-codex.md) |  |
| EKR-N27 | CONFIRMED | DOMKNIĘTE | [statystyki dublują istniejący SectionCard](verdict-ui-ekrany-codex.md) |  |
| EKR-N28 | PARTIAL | DOMKNIĘTE | [wydzielić spójne pola parametrów, nie każdy krótki fragment](verdict-ui-ekrany-codex.md) |  |
| EKR-N29 | CONFIRMED | DOMKNIĘTE | [czyste parsowanie importu ma własną odpowiedzialność](verdict-ui-ekrany-codex.md) |  |
| EKR-N30 | PARTIAL | DOMKNIĘTE | [sekcja trybów może być osobno; sealed dialogi nie są konieczne](verdict-ui-ekrany-codex.md) |  |
| EKR-N31 | PARTIAL | DOMKNIĘTE | [dispatchery warto wstrzykiwać, ale ticker ma inną rolę niż obliczenia](verdict-ui-ekrany-codex.md) |  |
| EKR-N32 | PARTIAL | DOMKNIĘTE | [kilka lokalnych wartości może pomóc, formatowanie samo nie jest defektem](verdict-ui-ekrany-codex.md) |  |
| EKR-N33 | PARTIAL | DOMKNIĘTE | [komentarze są zbędne, fallback jest współdzielonym porządkiem, pusty katalog nie należy do git](verdict-ui-ekrany-codex.md) |  |
| EKR-N34 | CONFIRMED | DOMKNIĘTE | [banner na wyjściu dostaje już pustą treść](verdict-ui-ekrany-codex.md) |  |
| TB-N1 | PARTIAL | DOMKNIĘTE | [cudzysłowy trzeba naprawić, lecz ogólny zakaz znaków `"` w zasobach byłby błędny](verdict-testy-build-codex.md) | Skompilowane zasoby: właściwe cudzysłowy przy nazwie w pięciu językach. |
| TB-N2 | CONFIRMED | DOMKNIĘTE | [backup i reset wymagają testów rzeczywistego repozytorium](verdict-testy-build-codex.md) | Rzeczywiste repozytorium: błędny/niedostępny plik, rollback bazy, tombstony i ukryte strony. |
| TB-N3 | CONFIRMED | DOMKNIĘTE | [brak scenariuszy MIGRATE/FRESH](verdict-testy-build-codex.md) | MIGRATE/FRESH/CANCEL, stable seed IDs, retry, błąd i dwuklik MainViewModel. |
| TB-N4 | CONFIRMED | DOMKNIĘTE | [nazwy kroków w komunikatach są rozbieżne](verdict-testy-build-codex.md) |  |
| TB-N5 | CONFIRMED | DOMKNIĘTE | [włoskie Masterizzata nie opisuje opanowanej fiszki](verdict-testy-build-codex.md) |  |
| TB-N6 | PARTIAL | DOMKNIĘTE | [komunikat NO_TOKEN jest nieprzydatny, ale wyjątek nie oznacza wyłącznie braku konta](verdict-testy-build-codex.md) |  |
| TB-N7 | CONFIRMED | DOMKNIĘTE | [debug bez klucza może zostać zbudowany, lecz nie uruchomi się poprawnie](verdict-testy-build-codex.md) |  |
| TB-N8 | CONFIRMED | DOMKNIĘTE | [domyślny filtr i kolejność HARDEST nie mają bezpośrednich testów](verdict-testy-build-codex.md) |  |
| TB-N9 | CONFIRMED | DOMKNIĘTE | [scalanie metadanych i usunięć talii jest słabo przypięte testami](verdict-testy-build-codex.md) |  |
| TB-N10 | PARTIAL | DOMKNIĘTE | [izolować preferencje testów; awarii zależnej od kolejności nie odtworzono](verdict-testy-build-codex.md) |  |
| TB-N11 | CONFIRMED | DOMKNIĘTE | [usługa i platformowy fokus nie są testowane bezpośrednio](verdict-testy-build-codex.md) | Rzeczywista usługa Hilt i platformowy fokus na API 32/33; routing Bluetooth wymaga telefonu. |
| TB-N12 | PARTIAL | DOMKNIĘTE | [unsigned release jest opisany jako zamierzony; brakuje kroku podpisania do testu R8](verdict-testy-build-codex.md) | Podpis opcjonalny istniejącym kluczem, README; bez lokalnego klucza release pozostaje unsigned. |
| TB-N13 | PARTIAL | DOMKNIĘTE | [zakres backupu za szeroki; wzorzec firestore.* nie zadziała w XML](verdict-testy-build-codex.md) | Wspólna naprawa z AUD-N14; urządzenie nadal potrzebne do potwierdzenia Android Backup. |
| TB-N14 | PARTIAL | DOMKNIĘTE | [prostszy język tak, zawężenie feedbacku do dźwięku byłoby nieprawdziwe](verdict-testy-build-codex.md) |  |
| TB-N15 | PARTIAL | DOMKNIĘTE | [dotknięcie i wiersz można ujednolicić; karta/fiszka czeka na decyzję](verdict-testy-build-codex.md) |  |
| TB-N16 | PARTIAL | DOMKNIĘTE | [część ES/IT wymaga korekty, nie każdy synonim jest błędem](verdict-testy-build-codex.md) |  |
| TB-N17 | PARTIAL | DOMKNIĘTE | [poprawić zdania, nie zmieniać poprawnej typografii dla samej spójności](verdict-testy-build-codex.md) |  |
| TB-N18 | PARTIAL | DOMKNIĘTE | [część asercji warto wzmocnić, ale raport pomija istniejące pokrycie](verdict-testy-build-codex.md) |  |
| TB-N19 | PARTIAL | DOMKNIĘTE | [lokalne powtórzenia tak, trzy nowe frameworki testowe nie są wymagane](verdict-testy-build-codex.md) |  |
| TB-N20 | PARTIAL | DOMKNIĘTE | [teksty do znajdowania kontrolek można wiązać z zasobami, lecz nie wszystkie literały są błędem](verdict-testy-build-codex.md) |  |
| TB-N21 | PARTIAL | DOMKNIĘTE | [negatywne asercje po sleep są kruche; integracja z prawdziwym executorem nie zawsze ma być wirtualna](verdict-testy-build-codex.md) |  |
| TB-N22 | PARTIAL | DOMKNIĘTE | [część testów się pokrywa, lecz nie wolno usuwać par testujących inne ścieżki](verdict-testy-build-codex.md) |  |
| TB-N23 | PARTIAL | DOMKNIĘTE | [testy schematu należą bliżej danych; wspólne atrapy muszą zachować różnice](verdict-testy-build-codex.md) |  |
| TB-N24 | CONFIRMED | DOMKNIĘTE | [obliczenie pauzy w tle nie ma testów granic](verdict-testy-build-codex.md) |  |
| TB-N25 | CONFIRMED | DOMKNIĘTE | [fallback uszkodzonego JSON ma znaczenie i wymaga regresji](verdict-testy-build-codex.md) |  |
| TB-N26 | PARTIAL | DOMKNIĘTE | [testować mapowanie Credential Managera; nie wymusza to nowej funkcji dla każdej gałęzi](verdict-testy-build-codex.md) |  |
| TB-N27 | PARTIAL | ZACHOWANE | [bezpośredni wpis zależności może być zbędny, same importy nie są rozstrzygające](verdict-testy-build-codex.md) | Zależność bezpośrednia zachowana: zapewnia wymaganą wersję AndroidX test, brak pozornego odchudzania. |
| TB-N28 | PARTIAL | DOMKNIĘTE | [zabezpieczyć lokalne sekrety i worktree, `.firebaserc` nie musi być ignorowane](verdict-testy-build-codex.md) |  |
| TB-N29 | PARTIAL | DOMKNIĘTE | [README ma rzeczywiste nieścisłości, ograniczanie klucza wymaga poprawnej konfiguracji](verdict-testy-build-codex.md) |  |
| TB-N30 | PARTIAL | DOMKNIĘTE | [skrypt nie kontroluje kodów zakończenia, lecz część diagnozy configów jest błędna](verdict-testy-build-codex.md) |  |
| TB-N31 | PARTIAL | DOMKNIĘTE | [stare worktree to zasoby robocze, nie martwy kod aplikacji](verdict-testy-build-codex.md) | Blokada OS z testem dwóch procesów i awarii; nie usunięto worktree. |
| TB-N32 | CONFIRMED | DOMKNIĘTE | [zbędne queries aktywności rozpoznawania](verdict-testy-build-codex.md) |  |
| TB-N33 | DECISION | DOMKNIĘTE | [osobna nazwa debug ułatwi rozróżnianie dwóch instalacji](verdict-testy-build-codex.md) |  |
| TB-N34 | CONFIRMED | DOMKNIĘTE | [zadanie check nie obejmuje przyjętego lintu Kotlin](verdict-testy-build-codex.md) |  |
| TB-N35 | CONFIRMED | DOMKNIĘTE | [wrapper i końce linii można zabezpieczyć małym kosztem](verdict-testy-build-codex.md) | Oficjalny checksum 9.8.1-bin zweryfikowany; LF z wyjątkiem BAT/CMD. |
| TB-N36 | CONFIRMED | DOMKNIĘTE | [reguła root jest redundantna przy rules_version 2](verdict-testy-build-codex.md) | 10 lokalnych testów emulatora; bez wdrożenia reguł produkcyjnych. |
| TB-N37 | PARTIAL | DOMKNIĘTE | [komentarze ui 1/ui 2 można usunąć, identyczny format nie oznacza zakazu tłumaczenia](verdict-testy-build-codex.md) |  |
| TB-N38 | PARTIAL | DOMKNIĘTE | [kilka drobnych porządków; globalne default values w testach byłyby pogorszeniem](verdict-testy-build-codex.md) |  |
