# Prototyp: jedna Activity na ekran — wyniki

Data: 2026-10-16 · gałąź `prototyp-activity` (worktree `RayColl-prototyp`, baza: HEAD `6f8529a6` + niezacommitowane zmiany z `przeglad-modernizacja`).
Zakres zawężony przez użytkownika do **pytania 1**. Pytania 2–4 nie były mierzone.

## Co zmieniono

- `ui/decksettings/DeckSettingsActivity.kt` — ustawienia talii jako osobna `@AndroidEntryPoint ComponentActivity` z Compose.
  `deckId` w extras Intentu (przekazywany do istniejącej fabryki ViewModelu, zamiast przez `SavedStateHandle` — Intent i tak przeżywa śmierć procesu).
  Wynik „Archived” wraca przez `ActivityResultContract` (`DeckSettingsActivity.Open`). Strzałka = `finish()`.
  Pierwsza klatka czeka na wczytanie motywu (`OnPreDrawListener`), żeby animacja otwarcia nie pokazała pustego okna.
- `ui/main/AppWindow.kt` — wspólny dla obu Activity motyw okna (paski systemu, tło okna) wydzielony z `MainActivity`,
  plus `UiModeManager.setApplicationNightMode` według motywu z aplikacji (tło okna z XML zgadza się z aplikacją).
- `NavGraph.kt` — „Opcje” na karcie talii uruchamia `DeckSettingsActivity`; reszta aplikacji nadal na Navigation 3.
- `AndroidManifest.xml` — rejestracja Activity.
- `BackHandler` nie był potrzebny: ustawienia talii zapisują się od razu, ekran nie ma stanu „niezapisane zmiany”.

## Pytanie 1 — czy system rysuje swoje przejście cofania?

Urządzenie: **Pixel 10 Pro, Android 17 (API 37), nawigacja gestami**, `activity-compose` 1.13.0, build debug.

**Zmierzone (logcat WM Shell, `ShellBackPreview` / `CoreBackPreview`):**

| Skąd cofanie | Typ animacji wybrany przez system |
|---|---|
| `DeckSettingsActivity` (prototyp) | `TYPE_CROSS_ACTIVITY (2)`, `mPrepareRemoteAnimation=true`, callback aplikacji `mPriority=-1` (domyślny systemowy) |
| Ustawienia Androida, `SubSettings` | `TYPE_CROSS_ACTIVITY (2)`, `mPrepareRemoteAnimation=true` — identycznie |
| Obecne ekrany Navigation 3 w `MainActivity` | `TYPE_CALLBACK (4)` — system oddaje gest aplikacji („Trigger back without dispatching to animator”) |

Wniosek: cofanie z osobnej Activity idzie tą samą ścieżką co w aplikacji Ustawienia — animację rysuje WM Shell
(cross-activity: zmniejszenie, zaokrąglone rogi, podążanie za palcem, anulowanie), a nie Compose.
W logach Ustawień widać też anulowanie gestu (`mTriggerBack == false`) obsłużone przez ten sam mechanizm.

**Ocena wizualna użytkownika na telefonie:** cofanie z ustawień talii wygląda jak w systemie („tak ma być”);
pozostałe ekrany (Navigation 3) — nie.

**Niezmierzone:**
- Nagranie ekranu / porównanie klatka po klatce z Ustawieniami.
- Długie przytrzymanie „Wstecz” w nawigacji 3-przyciskowej — telefon był w trybie gestów, nie przełączano.

## Pytania 2–4

Nie mierzone (poza zawężonym zakresem): opóźnienie otwarcia i błysk tła przy kombinacjach motywów,
cofanie po śmierci procesu i po obrocie, wygląd na Androidzie 14.

## Werdykt

**Wdrażać Activity na ekran** dla ekranów „liściowych” (ustawienia talii, ustawienia aplikacji, archiwum, statystyki itd.) —
to jedyny sposób, żeby cofanie było dokładnie systemowe; własne przejście w Navigation 3 byłoby tylko imitacją.
Użytkownik potwierdził na telefonie, że tak ma wyglądać.

Przed pełnym wdrożeniem do sprawdzenia:
- przejścia współdzielonych elementów (karta talii → ekran) w Navigation 3 znikną dla ekranów przeniesionych do Activity;
- ekrany zagnieżdżone (ustawienia talii → szczegóły trybu): w prototypie „edytuj tryb” w `DeckSettingsActivity` nic nie robi;
- pytania 2–4 (błysk tła przy motywie aplikacji ≠ systemowy, śmierć procesu, Android 14).

---

# Pełna migracja: jedna Activity na ekran — wyniki (2026-10-16)

Wszystkie 10 ekranów to osobne Activity (`AppActivity` + `*Activity` w pakiecie ekranu).
Navigation 3, przelot karty/tytułu talii (`SharedTransition`), `NavGraph`, `Routes`
i `ScreenCompletion` usunięte. Wyniki ekranów (import, archiwizacja) przez Activity Result API
(`OpenForResult`). Okna zamykają się po zalogowaniu na inne konto (`MainViewModel.accountChanged`).

## Zmierzone na Pixelu 10 Pro, Android 17, build debug

| Sprawdzenie | Wynik |
|---|---|
| Cofanie gestem z każdego ekranu bez niezapisanych zmian (statystyki, archiwum, ustawienia, przeglądanie, ustawienia talii, edytor trybu, import bez szkicu, podsumowanie nauki) | `TYPE_CROSS_ACTIVITY` — animacja systemu |
| Pulpit → ekran główny telefonu | `TYPE_RETURN_TO_HOME` |
| Gest: zmniejszenie, rogi, anulowanie i dokończenie | nagranie, `cofanie-gestem-android17.png` |
| Trwająca nauka, import ze szkicem, edytor ze zmianami | pytanie zamiast podglądu (zgodnie z planem) |
| Import z otwartą klawiaturą | 1. gest chowa klawiaturę, 2. gest pyta o porzucenie |
| Nawigacja 3-przyciskowa: przytrzymanie „Wstecz” | podgląd `TYPE_CROSS_ACTIVITY`, puszczenie kończy cofanie |
| Nawigacja 3-przyciskowa: dotknięcie „Wstecz” | systemowa animacja dokończenia |
| Obrót na przeglądaniu talii | ekran zostaje, cofanie dalej systemowe |
| Motyw aplikacji jasny przy ciemnym systemie | otwarcie bez błysku tła; przyciemnienie za cofanym ekranem idzie za motywem systemu (rysuje je system) |
| Śmierć procesu (`am kill`) na archiwum / imporcie / edytorze trybu | ekran wraca; szkic importu i edytora trybu wraca |
| Pierwsze cofanie po śmierci procesu | `TYPE_CALLBACK` — zwykłe zamknięcie bez podglądu (poprzednie okno nie istnieje jeszcze w systemie); kolejne cofania już z podglądem |
| Intent jak z powiadomienia sesji (ikona aplikacji) | wraca to samo okno nauki, bez nowej sesji |
| Otwieranie statystyk, przeglądania, archiwum, ustawień (nagranie klatka po klatce) | animacja otwarcia od razu z treścią, bez wskaźnika ładowania |
| Porzucenie szkicu importu | plik szkicu usunięty od razu |

Pierwsza klatka okna czeka (najwyżej 0,4 s) na konto i dane ekranu — stan sprawdzany w ViewModelach
(`AppActivity.isContentLoading`, `MainViewModel.accountGate`). Bez tego każde nowe okno zaczynało
animację od ekranu „wczytywanie danych” albo samego wskaźnika ładowania.

Testy: `testDebugUnitTest` — 549 testów, 0 błędów; `ktlintCheck`, `lintDebug` (0 zgłoszeń), `assembleRelease` — sukces.

## Nie sprawdzano

- Android 14 i telefony innych marek (decyzja: Android 14 bez testów).
- Wylogowanie na telefonie — żeby nie ruszać konta; zamykanie okien po zmianie konta pokrywa test jednostkowy.
