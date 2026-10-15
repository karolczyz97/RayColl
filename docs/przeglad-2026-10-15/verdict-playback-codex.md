# Red team Codex: AUD (17 uwag)

Stan `39f0690b`, 15.10.2026. Ścieżki względem `app/src/main/java/com/raycoll/app/`. API sprawdzono w dokumentacji Androida i bytecode bibliotek użytych przez projekt. Bez testu fizycznego audio; próba ADB nie uruchomiła serwera. Nie zmieniono kodu.

### AUD-N1 — CONFIRMED — domyślne powiadomienie pomija postęp z subtitle

`SessionPlayer.kt:44–48` zapisuje postęp do subtitle. `javap` na Media3 1.11.1 potwierdził: `getNotificationContentText` zwraca wyłącznie artist, a title pochodzi z title. To mocniejszy dowód niż przypuszczenie o System UI; wszystkich ekranów blokady nie sprawdzono.
Naprawa S/M: w widocznej drugiej linii umieścić nazwę talii i postęp albo neutralne „Wstrzymano”; nie pokazywać „otwórz aplikację” przy każdej ręcznej pauzie. Najmniejszy wariant nie wymaga nowego stanu silnika: neutralny tekst dla każdej pauzy. Dokładne rozróżnienie błędu TTS można dodać osobno, jeśli ma być widoczne. Test metadanych i `sessionPlayback`, sprawdzenie na telefonie. CROSS-LAYER DOM tylko dla szczegółowego powodu pauzy. Średnie ryzyko UX.

### AUD-N2 — CONFIRMED — dla mowy nie można polegać na automatycznym ściszaniu

`AudioFocusManager.kt:54` ignoruje CAN_DUCK i ma błędny komentarz. Android dokumentuje wyjątek od automatycznego duckingu dla CONTENT_TYPE_SPEECH. Wczorajsze P35 było w tej części błędne. Nie potrzeba dowodzić szczegółów UID silnika TTS, żeby wykazać ten błąd.
Naprawa S: `setWillPauseWhenDucked(true)` i CAN_DUCK → LOST_TRANSIENT. Zachować odróżnienie pauzy użytkownika od utraty fokusu. Test mapowania i `SessionAudioFocusTest`, telefon z komunikatem nawigacji. Niskie ryzyko; realizuje opisany już kontrakt pauzy przy nawigacji.

### AUD-N3 — CONFIRMED — brak obsługi odłączenia słuchawek

W kodzie main nie ma ACTION_AUDIO_BECOMING_NOISY. Własny SimpleBasePlayer nie konfiguruje obsługi znanej z ExoPlayera. Dokumentacja potwierdza przełączenie strumienia na głośnik.
Naprawa S: receiver w serwisie, wywołanie świadomej pauzy `controls.pause`, rejestracja/wyrejestrowanie powiązane z życiem serwisu. Dobrać flagę receivera do chronionego broadcastu systemowego i sprawdzić na urządzeniu. Test receiver → pauza i brak samowznowienia; niskie ryzyko. Tryby bez serwisu rozpatrzyć razem z AUD-N4.

### AUD-N4 — DECISION — poza serwisem polityka współdzielenia dźwięku jest nieokreślona

Fokus istnieje tylko w serwisie, a tryby z mikrofonem i wyłączoną pracą w tle go nie uruchamiają. To fakt. Brak reakcji UI na `controls.play()==false` to osobny drobny brak informacji, nie dowód konieczności przebudowy holdera.
Rekomendacja: każda sesja wydająca/rozpoznająca mowę respektuje fokus, a odmowa pokazuje krótki komunikat i zostawia pauzę. Alternatywa: zachować współdzielenie muzyki poza serwisem. Po decyzji M: jeden właściciel fokusu, bez dwóch listenerów walczących o singleton. CROSS-LAYER NAU/DOM; test odmowy i ręcznej pauzy, telefon z muzyką i rozmową. Średnie ryzyko.

### AUD-N5 — PARTIAL — początkowy push istnieje, lecz `collectLatest` nie jest bezpieczną gotową poprawką

`SessionEndSync` emituje true dla początkowego braku sesji, a `pushNow` ustawia pending. Dla gościa kończy się no-op, dla konta może zrobić dodatkową pracę po bootstrapie. `awaitIdle` tutaj zwykle już ma zakończone oczekiwanie: combine wpuszcza tylko pending==0 lub failed. Raport wyolbrzymia właśnie to blokowanie; wolna bywa dalsza wysyłka sieciowa.
Naprawa S/M: pominąć wyłącznie początkowy stan „żadnej sesji” i reagować na rzeczywiste zakończenia oraz późniejsze udane ponowienie ocen. Nie używać bezwarunkowo `drop(1)`, jeśli start obserwatora może nastąpić po rozpoczęciu/zakończeniu sesji. Nie zastępować collect przez collectLatest bez testu anulowania wysyłki: nowa sesja mogłaby anulować końcowy push. Scheduler już służy do kolejkowania; sygnał zakończenia może zlecać flush bez blokowania kolektora. CROSS-LAYER DAT + TB. Test zimnego startu, dwóch szybkich sesji i retry ocen; średnie ryzyko.

### AUD-N6 — CONFIRMED — zasoby serwisu mogą ignorować język aplikacji na API 26–32

Serwis wywołuje własne getString; per-app locale AppCompat na starszych Androidach nie obejmuje dowolnego Context.
Naprawa S: użyć istniejącego `ContextCompat.getString` / `getContextForLanguage`, zamiast pisać ręcznie duplikat kontekstu konfiguracji. Dotyczy kanału, powiadomienia startowego i pauzy. Test locale aplikacji inne niż system na API 32 i przypadek API 33+. Niskie ryzyko. Nazwa kanału aktualizowana ponownym createNotificationChannel pozostaje.

### AUD-N7 — WRONG — twierdzenie o braku obsługi IME przeczy kodowi

`imePadding()` jest w `ImportScreen.kt:93`, `BrowseScreen.kt:134`, `CardEditorDialog.kt:75`, `DeckSettingsScreen.kt:130` i `ModeDetailScreen.kt`. Nie ma podstaw do globalnego dodawania kolejnego paddingu do ScreenScaffold; mogłoby to go podwoić. Ewentualne konkretne problemy poziomu z klawiaturą mają osobne ID EKR-N6. Pominięta w źródłowym raporcie implementacja istnieje.

### AUD-N8 — CONFIRMED — efekty uprawnień startują ponownie po odtworzeniu ekranu

`StudyScreen.SessionPermissions` ma LaunchedEffect bez zapamiętania próby. Po odmowie i obrocie, jeżeli system nadal pozwala pytać, ponowi launch; po trwałej odmowie system może już nie pokazać okna. Nie twierdzić, że okno pojawi się po dowolnej liczbie odmów.
Naprawa S: zapisane flagi próby per sesja/ekran, oddzielnie mikrofon i powiadomienia; jawna akcja ponowienia prowadząca do ustawień. Test restoration po odmowie i nowe otwarcie sesji. CROSS-LAYER NAU; niskie ryzyko.

### AUD-N9 — INTENTIONAL — współdzielony silnik TTS to cache procesu, nie wykazany wyciek

`@Singleton TtsPlayer` jawnie ponownie używa engine, a po błędzie robi shutdown. Zwalnianie po każdej sesji zwiększa koszt następnego startu i wymaga obsługi wyścigów; brak pomiaru pamięci czy zacięć. Pozostawić ten model. Ewentualny cache ostatniej konfiguracji rozpatrzyć dopiero po pomiarze, z obsługą zmiany głosów. Nie otwierać zadania optymalizacyjnego wyłącznie na podstawie trzech IPC.

### AUD-N10 — CONFIRMED — jedna mała reguła motywu może być wspólna

`MainActivity` oraz `Theme` mają te same trzy gałęzie ThemePref. Naprawa S: wspólna funkcja rozwiązująca dark mode z podanym systemowym bool albo niewielka composable; bez nowej warstwy. CROSS-LAYER NAU; test tabeli LIGHT/DARK/SYSTEM lub istniejące testy motywu; niskie ryzyko.

### AUD-N11 — CONFIRMED — `coroutineScope` w followSession nie ma zadania

Po usunięciu dodatkowego collectora ciało tylko zbiera combine. Naprawa S: funkcja suspend z blokiem, usunąć import; nie zmieniać `collectLatest` obserwującego zmianę aktywnej sesji. Kompilacja i testy playback; niskie ryzyko.

### AUD-N12 — CONFIRMED — queries dla aktywności rozpoznawania nie jest używane

Kod nie wyszukuje/uruchamia tej aktywności; startListening nie jest queryIntentActivities. Zostawić RecognitionService i TTS_SERVICE. S, manifest + build/lint, test dostępności rozpoznawania na telefonie przy okazji. Duplikat TB-N32, wykonać raz.

### AUD-N13 — PARTIAL — martwe SKIPPED można usunąć, ale nie usuwać testowanych zachowań

`SttRecognizer` zwraca COMPLETED/ERROR, tap daje null z runSkippable. Enum nie jest formatem danych zapisanych użytkownika. Naprawa S: usunąć wariant i gałąź, a testy przestawić na rzeczywiste anulowanie/tap. Nie kasować testu „pominięty nasłuch nie daje oceny” tylko dlatego, że używał sztucznego wariantu.
Gałęzie REVEALED/ShowRatings są sensowną obroną przed nieobsługiwanym stanem lub bezpośrednim użyciem silnika; nie usuwać ich jako części sprzątania enum. CROSS-LAYER DOM + TB; testy sesji pominiętej/błędnej; niskie ryzyko.

### AUD-N14 — PARTIAL — backup obejmuje wszystkie bazy, ale rozmiaru i plików urządzenia nie znamy

XML rzeczywiście dołącza cały katalog database; dokumentacja potwierdza limit 25 MB dla kopii chmurowej. Nie dowiedziono, że bieżący backup przekracza limit ani że taka sama kwota ogranicza transfer urządzenie–urządzenie.
Naprawa S/M: jawnie ograniczyć kopię do bazy aplikacji i poprawnie uwzględnić SQLite/WAL, pozostawić ustawienia i locale. Sprawdzić odtworzenie bazy, nie tylko obecność nazwy `raycoll.db`. Nie używać wildcard `firestore.*` w XML, bo path nie obsługuje globów. CROSS-LAYER TB; test reguł + restore na emulatorze/telefonie. Duplikat TB-N13, średnie ryzyko utraty części kopii przy błędnej konfiguracji.

### AUD-N15 — PARTIAL — proste porządki dopuszczalne; `.immediate` i offline nie są czystą kosmetyką

`Looper.getMainLooper().isCurrentThread` jest dostępne przy minSdk 26. Logowanie błędu cleanup może ułatwić diagnozę, ale catch chroni ścieżkę sprzątania i nie należy go usuwać.
Naprawa S: czytelniejsza kontrola wątku i ewentualny pojedynczy log. Nie zmieniać dispatchera bez testu synchronicznych callbacków/anulowania; `.immediate` zmienia reentrancję. EXTRA_PREFER_OFFLINE to osobna DECISION i tylko wskazówka dla providera, nie gwarancja pracy bez sieci. Testy STT i telefonu po każdej takiej zmianie; niskie/średnie ryzyko.

### AUD-N16 — CONFIRMED — nazwać powtarzane odtworzenie sygnału

Dwa identyczne `pool.play` można schować w małym prywatnym helperze `playOnce`, z wartościami opisującymi kanały/priorytet bez rozbudowanego obiektu konfiguracji. S, `HapticAndSoundPlayerTest`, kompilacja; niskie ryzyko. Nie zmieniać głośności, tempa ani czasu haptyki.

### AUD-N17 — NOT_WORTH — działający observer nie wymaga migracji do Flow

`DefaultLifecycleObserver` ma symetryczne add/remove, odpowiada na start/stop procesu i jest testowany przez zachowanie ViewModelu. Zamiana na currentStateFlow zmienia obsługę pierwszej wartości i nie daje potwierdzonej korzyści. Zostawić; aktualność API nie oznacza, że prosty observer jest błędem.
