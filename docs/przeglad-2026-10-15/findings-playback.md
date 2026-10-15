# Skan 15.10.2026 — Dźwięk, serwis nauki, MainActivity, manifest

Pełna lista agenta (tylko odczyt kodu, gałąź `przeglad-modernizacja`). Zbiorczy raport: [RAPORT.md](RAPORT.md).

# Przegląd obszaru playback + MainActivity + Application + Manifest

## Ocena ogólna
Po wczorajszych poprawkach obszar jest czysty: małe pliki z jedną rolą, sensowne polskie komentarze, dużo czystych funkcji z testami. W kodzie nie ma śladów "TS" ani "parytet". Poprawki P1, P3, P6, P8, P9, P12, P14–P16, P19, P21, P25, P38, P40, P43 i P47–P49 są wdrożone poprawnie i nie znalazłem w nich błędów. Najsłabsze są zachowania systemowe wokół audio, a nie styl kodu:
- fokus audio ignoruje ściszanie (nawigacja),
- brakuje reakcji na odłączenie słuchawek,
- tryby bez serwisu w ogóle nie biorą fokusu,
- druga linia powiadomienia (postęp, powód pauzy) prawdopodobnie nigdy się nie wyświetla.

Serwis, `SttRecognizer` i `AudioFocusManager` nie mają testów. Testowane są tylko czyste funkcje wokół nich.

Odwołuję się do dwóch wczorajszych pozycji: P13 (wdrożona tylko częściowo, N1) i P35 (nowy argument, N2).

## Znaleziska

**N1. [bug] [M] `SessionPlayer.kt:44-48`, `StudySessionService.kt:135,210-221` — postęp i podpowiedź pauzy nie są widoczne** (dotyczy P13).
- Postęp ("3/20") i tekst "Wstrzymano — otwórz aplikację" trafiają do pola `subtitle`.
- Domyślne powiadomienie Media3 1.11.1 czyta tylko `title` (tytuł) i `artist` (druga linia). Sprawdziłem to w kodzie bajtowym `DefaultMediaNotificationProvider` z cache Gradle (metody `getNotificationContentTitle` i `getNotificationContentText`).
- Według mojej wiedzy System UI (ekran blokady) też pokazuje tylko te dwa pola, więc P13 dopracowało tekst, którego nikt nie widzi.
- Uwaga dla naprawiającego: po ujawnieniu tekstu "otwórz aplikację" pojawi się przy każdej pauzie, także ręcznej i po utracie fokusu, a wtedy przycisk play działa. Pojawi się też, gdy aplikacja jest otwarta i czeka bramka tapnięcia lub status ERROR.
- Poprawka: dać `artist = "$deckName · $progress"` i zostawić `subtitle`. Tekst "otwórz aplikację" pokazywać tylko po pauzie z powodu błędu TTS w tle. To wymaga flagi z silnika (`SessionEngine.onTtsFailure`, zmiana między warstwami). W pozostałych pauzach dać neutralne "Wstrzymano".
- Wpływ na użytkownika: tak (brak postępu i powodu pauzy w powiadomieniu).

**N2. [bug] [S] `AudioFocusManager.kt:43-56` (szczególnie `:54`) — ściszenie (CAN_DUCK) jest ignorowane** (nowy argument przeciw P35).
- Komentarz zakłada, że "system sam ścisza mowę". Lektor gra jednak w procesie silnika TTS, czyli pod innym UID niż aplikacja.
- Z pamięci kodu AOSP (`PlaybackActivityMonitor.duckPlayers`): system nie ścisza odtwarzaczy typu SPEECH, tylko zostawia to aplikacji. Nasza `SilentPlayback` jest właśnie typu SPEECH.
- Skutek: komunikat nawigacji (Mapy proszą o MAY_DUCK) nakłada się na lektora. KDoc w `SessionAudioFocus.kt:14` obiecuje w tym miejscu pauzę, a jej nie ma.
- Poprawka: dodać `.setWillPauseWhenDucked(true)` i mapować `AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK` na `FocusChange.LOST_TRANSIENT`. Mapowanie warto wyciągnąć do czystej funkcji z testem.
- Potwierdzić na telefonie z włączoną nawigacją.
- Wpływ na użytkownika: tak.

**N3. [bug] [S] `StudySessionService.kt` (brak kodu) — brak reakcji na odłączenie słuchawek.**
- W całym `main` nie ma `ACTION_AUDIO_BECOMING_NOISY` ani `registerReceiver`.
- Gdy słuchawki lub Bluetooth się rozłączą w trakcie sesji w tle, lektor czyta dalej przez głośnik. ExoPlayer robi to sam, ale tu jest własny `SimpleBasePlayer`.
- Poprawka: w `onCreate` zarejestrować `ContextCompat.registerReceiver(..., RECEIVER_NOT_EXPORTED)` na tę akcję i wołać `controls.pause()` (oddaje fokus, bez auto-wznowienia). Wyrejestrować w `onDestroy`.
- Wpływ na użytkownika: tak (niespodziewany głos z głośnika w miejscu publicznym).

**N4. [bug] [M] `StudySessionService.kt:101`, `StudyViewModel.kt:143`, `StudySessionHolder.kt:54-56,78` — poza serwisem nie ma fokusu audio.**
- `SessionAudioFocus` istnieje tylko w serwisie. Serwis rusza tylko dla trybów bez mikrofonu i przy włączonym "granie w tle".
- W trybach z nasłuchem i przy wyłączonym ustawieniu aplikacja nigdy nie prosi o fokus: nie wycisza ani nie pauzuje muzyki innych aplikacji. Czy silnik TTS robi to sam, zależy od silnika.
- Komentarz `TtsPlayer.kt:99` ("pauzę przy utracie fokusu robi AudioFocusManager") jest prawdziwy tylko w serwisie.
- `StudySessionHolder.resume()` ignoruje wynik `controls.play()`. Gdy fokus jest odmówiony (np. trwa rozmowa), "Wznów" w aplikacji nic nie robi i nie ma komunikatu.
- Poprawka: przenieść `SessionAudioFocus` z serwisu na poziom holdera lub ViewModelu, żeby działał też bez serwisu, i pokazać snackbar przy odmowie. Albo świadomie zostawić i poprawić komentarz.
- Wpływ na użytkownika: tak. To decyzja produktowa (patrz pytania).

**N5. [wydajność] [S] `SessionEndSync.kt:28-43` — dodatkowa wysyłka przy każdym starcie aplikacji.**
- Pierwsza wartość strumienia to "brak sesji i pusta kolejka" = `true`. Przy każdym zimnym starcie leci więc `awaitIdle()` i `syncLifecycle.pushNow()`. `flushImmediately` zawsze ustawia `pending=true`.
- Dzieje się to równolegle z bootstrapem. Dochodzi drugi `purgeExpiredArchives` (`SyncLifecycle.kt:99-100` oraz `:161-164` wołane z `RayCollApplication.kt:27`).
- Poprawka: dodać `.drop(1)` po `distinctUntilChanged()`, bo kolejka ocen jest tylko w pamięci i przy starcie nie ma czego domykać. Zamienić `collect` na `collectLatest`, żeby czekanie na zapis nie blokowało następnej sesji.
- Uwaga poboczna: serwis kończy się natychmiast na FINISHED (`StudySessionService.kt:122-124`), a końcowa wysyłka dopiero wtedy startuje. Przy ograniczeniach tła może poczekać do następnego otwarcia aplikacji. Dane są bezpieczne w Room.
- Wpływ na użytkownika: raczej nie (mniej odczytów Firestore, szybszy start).

**N6. [bug] [S] `StudySessionService.kt:135,177-178,188,193` — teksty serwisu ignorują język wybrany w aplikacji na Androidzie 8–12.**
- Według dokumentacji AppCompat język aplikacji stosuje się tylko do Activity, a od Androida 13 działa wszędzie.
- Dotyczy powiadomienia startowego, nazwy kanału i tekstu pauzy.
- Poprawka: do `getString` w serwisie użyć kontekstu z `createConfigurationContext` ustawionym na `AppCompatDelegate.getApplicationLocales()`.
- Wpływ na użytkownika: tak, ale tylko na Androidzie 12 i starszym.

**N7. [bug?] [S] `AndroidManifest.xml:41` oraz cały `ui/` — klawiatura może zasłaniać pola tekstowe.**
- `adjustResize` razem z `enableEdgeToEdge()` na API 30+ nie zmniejsza już okna. W projekcie nie ma żadnego `imePadding()` ani `WindowInsets.ime` (grep "Insets" daje 0 trafień).
- Pola przy dole (import, edytor karty) mogą być zasłonięte. Nie sprawdzałem tego na urządzeniu.
- Poprawka: jeśli się potwierdzi, dodać `Modifier.imePadding()` na treści ekranów z polami albo odpowiednie `contentWindowInsets` w `ScreenScaffold`.
- Wpływ na użytkownika: możliwy.

**N8. [bug] [S] `StudyScreen.kt:272-282` (poza obszarem, ale dotyczy obrotu) — prośba o uprawnienia wraca po obrocie.**
- `LaunchedEffect(needsMicrophone)` i `LaunchedEffect(runsInBackground)` startują od nowa w nowej kompozycji po obrocie. Jeśli uprawnienia nie ma, okno systemowe wyskakuje ponownie.
- Poprawka: zapamiętać "już pytano" w `rememberSaveable` albo w ViewModelu.
- Wpływ na użytkownika: tak (po jednej odmowie każdy obrót pyta znowu).

**N9. [wydajność] [M] `TtsPlayer.kt:29-32,40-45` — silnik TTS żyje tyle co proces.**
- Singleton trzyma `TextToSpeech` (połączenie z procesem silnika TTS z załadowanym głosem) do końca życia procesu, także po wyjściu z nauki.
- Przy każdej wypowiedzi na wątku głównym idą trzy synchroniczne wywołania IPC: `isLanguageAvailable`, `language=` i `setSpeechRate`. Dzieje się to także wtedy, gdy język się nie zmienił.
- Poprawka: zwalniać silnik (`shutdown`) po zwolnieniu sesji albo po kilku minutach bezczynności i pamiętać ostatni język. Przed przenoszeniem czegokolwiek z wątku głównego zmierzyć StrictMode lub Perfetto.
- Wpływ na użytkownika: raczej nie (pamięć, ewentualne drobne zacięcia przy zmianie języka strony).

**N10. [czytelność] [S] `MainActivity.kt:45-50` i `Theme.kt:99-103` — ta sama reguła "czy ciemno" w dwóch miejscach.**
- P2 dopuszczał duplikat, ale łatwo o rozjazd.
- Poprawka: dodać `@Composable fun ThemePref.isDark()` w `Theme.kt` i użyć w obu miejscach.
- Wpływ na użytkownika: nie.

**N11. [martwy-kod] [S] `StudySessionService.kt:118` — zbędne `coroutineScope`.**
- Po usunięciu drugiego kolektora (P12) `followSession` nie uruchamia żadnych podkorutyn, więc owinięcie `coroutineScope { }` i import są zbędne.
- Wpływ na użytkownika: nie.

**N12. [martwy-kod] [S] `AndroidManifest.xml:20-22` — zbędny wpis w `<queries>`.**
- `<queries>` dla `RECOGNIZE_SPEECH` (aktywność) nic nie daje: aplikacja nie uruchamia aktywności rozpoznawania, a ten `Intent` służy tylko jako argument `startListening` (`SttRecognizer.kt:139`).
- Zostają `TTS_SERVICE` i `RecognitionService`.
- Wpływ na użytkownika: nie.

**N13. [martwy-kod] [S] `SessionDeps.kt:21`, `StepExecutor.kt:162` — nieużywana wartość w produkcji.**
- `ListenStatus.SKIPPED` nigdy nie jest zwracany. `SttRecognizer` daje tylko COMPLETED i ERROR, a tap przerywający nasłuch to `null` z `runSkippable`. Wartość zostaje w atrapach testowych.
- Podobnie nieosiągalne w trybie z serwisem są gałąź `REVEALED` w `sessionAudioIdle` (`StudySessionService.kt:231`) oraz `ShowRatingsStep` w `SessionEngine.kt:238-244`. Powód: `BackgroundSubstitution.kt:15` wyklucza `ShowRatingsStep` z trybu bez rąk.
- Poprawka: usunąć `SKIPPED` razem z 2 testami. Pozostałe dwa miejsca usunąć albo świadomie zostawić jako zabezpieczenie.
- Wpływ na użytkownika: nie.

**N14. [modernizacja] [S] `backup_rules.xml:3`, `data_extraction_rules.xml:4,9` — kopia zapasowa obejmuje cały katalog baz.**
- `<include domain="database" path="."/>` kopiuje cały katalog baz, a więc prawdopodobnie także lokalną pamięć podręczną Firestore (w kodzie nie ma ustawień persistence, więc jest domyślnie włączona) i bazy bibliotek Google.
- Limit Auto Backup to 25 MB. Po przekroczeniu cała kopia jest pomijana.
- Poprawka: ograniczyć do `raycoll.db` (sprawdzić pliki `-wal` i `-shm`) i zweryfikować na telefonie, jakie pliki są w `databases/`.
- Wpływ na użytkownika: możliwy (niezawodność backupu).

**N15. [modernizacja] [S] `SttRecognizer.kt:56,76-82,83,144` — drobne nieidiomatyczności.**
- Puste `catch (_: Exception) {}` (w `SilentPlayback` jest `runCatching`; ujednolicić lub logować).
- `Looper.myLooper() == Looper.getMainLooper()` zamienić na `Looper.getMainLooper().isCurrentThread`.
- `withContext(Dispatchers.Main)` zamienić na `.immediate`, bo przy wołaniu z `viewModelScope` robi zbędny przeskok.
- Opcjonalnie dodać `EXTRA_PREFER_OFFLINE=true`, żeby rozpoznawanie działało bez internetu. To zmienia zachowanie, więc wymaga testu na telefonie, tak jak P9.
- Wpływ na użytkownika: nie (offline: tak).

**N16. [czytelność] [S] `HapticAndSoundPlayer.kt:63,85` — powtórzone wywołanie z 6 argumentami.**
- To samo `pool.play(soundId, 1f, 1f, 1, 0, 1f)` występuje w dwóch miejscach.
- Poprawka: wyciągnąć `private fun SoundPool.playOnce(id: Int)` z nazwanymi stałymi.
- Wpływ na użytkownika: nie.

**N17. [modernizacja] [S] `StudyViewModel.kt:110-114,145,311` — ręczny observer procesu (opcjonalne).**
- Ręczne add/remove observera `ProcessLifecycleOwner` można zastąpić `lifecycle.currentStateFlow` zbieranym w `viewModelScope` (lifecycle 2.11).
- `appVisible()` w `registerSession` zostaje, bo jest potrzebne przy restarcie.
- Wpływ na użytkownika: nie.

## TOP 5
1. **N2** — nawigacja nakłada się na lektora. To mała zmiana (S), a ma realny skutek.
2. **N3** — odłączenie słuchawek powinno pauzować sesję. Mała zmiana (S).
3. **N1** — postęp i powód pauzy w powiadomieniu nie są widoczne, a po ujawnieniu pokażą mylący tekst.
4. **N4** — tryby z mikrofonem i wyłączone granie w tle nie biorą fokusu audio. Wymaga decyzji produktowej.
5. **N5** — dodatkowa wysyłka przy każdym starcie aplikacji, plus zablokowane `collect`.

## Pytania do właściciela
1. **N4:** czy w trybach z mikrofonem (i przy wyłączonym graniu w tle) muzyka innej aplikacji ma być wyciszana lub pauzowana na czas sesji?
2. **N2:** czy komunikat nawigacji ma zawsze wstrzymywać lektora? Kod i komentarze już to zakładają.
3. **N1:** co ma być w drugiej linii powiadomienia: nazwa talii, postęp (3/20), czy oba?

Model do napraw: Sonnet wystarczy dla N2, N3, N6, N10–N16. Opus dla N1, N4 i N5, bo zmieniają zachowanie i dotykają kilku warstw.
