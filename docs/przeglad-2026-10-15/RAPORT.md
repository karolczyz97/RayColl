# RayColl — drugi pełny skan (15.10.2026)

Skanowana gałąź: `przeglad-modernizacja`. Zawiera wczorajsze ~188 poprawek, 533 testy są zielone. `main` i telefon mają jeszcze starą wersję. Sześciu recenzentów przeczytało każdy plik: ok. 14 tys. linii aplikacji, 9,5 tys. linii testów, zasoby i build. Pozycji ocenionych wczoraj nie zgłaszano ponownie.

**Wynik: 166 nowych pozycji.** Większość to drobiazgi. Kod jest w dobrym stanie: nie ma resztek po Expo/TS, pliki są małe, a logika ma testy. Pozostały cztery grupy problemów:
- zachowania systemowe przy dźwięku,
- synchronizacja po błędach sieci,
- kilka ekranów bez informacji zwrotnej,
- długie pliki UI, tłumaczenia i luki w testach.

Pełne listy z numerami linii są w plikach poniżej. Prefiks ID w raporcie wskazuje plik:

| Prefiks | Plik | Obszar |
|---|---|---|
| DOM | [findings-domain.md](findings-domain.md) | logika: sesja, FSRS, import, scalanie |
| DAT | [findings-data.md](findings-data.md) | baza, synchronizacja, logowanie |
| AUD | [findings-playback.md](findings-playback.md) | dźwięk, serwis nauki w tle, manifest |
| NAU | [findings-ui-nauka.md](findings-ui-nauka.md) | ekran nauki, pulpit, motyw, nawigacja |
| EKR | [findings-ui-ekrany.md](findings-ui-ekrany.md) | przeglądanie, import, ustawienia, edytor trybów |
| TB | [findings-testy-build.md](findings-testy-build.md) | testy, build, zasoby, tłumaczenia |

---

## 1. Ikonki głośnika i mikrofonu

**Stan:** na gałęzi `przeglad-modernizacja` to już jest naprawione (commit `6c11a77b`). Ta wersja nie trafiła jeszcze na telefon ani do `main`, dlatego na telefonie tekst nadal skacze. W starej wersji ikonka była osobnym „dymkiem” wstawianym nad tekstem i spychała wszystko w dół.

**Jak jest teraz.** Zrzut z prawdziwego kodu gałęzi: [obecna-galaz-ekran.png](obecna-galaz-ekran.png). Czytana strona dostaje jasne tło i ramkę. Ikonka siedzi w marginesie po lewej, który jest zarezerwowany zawsze, więc tekst się nie rusza.

**Warianty do wyboru.** We wszystkich tekst stoi w miejscu. Zrzuty: [warianty-jasny.png](warianty-jasny.png) i [warianty-ciemny.png](warianty-ciemny.png).

| Wariant | Jak wygląda | Plusy | Minusy |
|---|---|---|---|
| **A** (obecny) | mała ikonka (16 dp) w bocznym marginesie i podświetlona strona | gotowe, przetestowane | ikonkę łatwo przeoczyć; margines 20 dp po obu stronach zawsze zabiera szerokość tekstu |
| **B** | etykieta „🔊 Czytam” / „🎤 Słucham” na linii nad czytaną stroną | słowo zamiast samej ikonki, stoi przy stronie, której dotyczy; tekst ma pełną szerokość | karta jest o ok. 12 dp wyższa na każdą stronę (stałe miejsce na linię) |
| **C** | etykieta w prawym górnym rogu karty i podświetlona strona | etykieta zawsze w tym samym miejscu | karta jest zawsze o ok. 32 dp wyższa; która strona jest czytana, widać tylko po kolorze |

**Rekomendacja: B.** Mówi słowami, co się dzieje, i nie zabiera tekstowi szerokości.

**Do poprawienia przy okazji:** gdy apka słucha odpowiedzi, która jest jeszcze zakryta, pod kartą widać szary pasek z samą ikonką fali, bez słowa „Słucham” (drugi kadr na zrzucie). Ten pasek powinien dostać taką samą etykietę jak wybrany wariant.

---

## 2. Błędy widoczne w apce

Naprawa tych błędów nie zmienia zamierzonego działania aplikacji. ✔ oznacza pozycję sprawdzoną ręcznie w kodzie.

**Nauka i dźwięk**
1. ✔ Komunikat „Sprawdź dostęp do mikrofonu” nigdy sam nie znika i zasłania przyciski. Nie znika też po przyznaniu zgody (NAU-N1).
2. Ten sam błąd mowy i okno „brak głosu TTS” wracają przy każdej karcie (NAU-N2).
3. Po odłączeniu słuchawek sesja nie pauzuje, tylko lektor czyta dalej przez głośnik (AUD-N3).
4. Komunikat nawigacji, np. z Map, nakłada się na lektora, bo prośba o ściszenie jest ignorowana (AUD-N2).
5. Gdy sesja skończy się sama przy otwartym oknie „Opuścić sesję?”, kliknięcie „Wyjdź” oznacza ją jako przerwaną (NAU-N5).
6. Pasek w podsumowaniu sesji pojawia się z opóźnieniem i przesuwa całą treść. To ten sam typ problemu co ikonki (NAU-N3).
7. „Dotknij, aby odkryć” znika skokowo zamiast płynnie (NAU-N4).
8. Po obrocie ekranu prośba o mikrofon lub powiadomienia pojawia się ponownie (AUD-N8).
9. Powiadomienie nauki w tle nie pokazuje postępu „3/20” ani powodu pauzy, bo trafiają do pola, którego system nie wyświetla (AUD-N1).

**Import i przeglądanie**

10. ✔ Przy wklejaniu z arkusza przecinki w zdaniach przegrywają z tabulatorem w wykrywaniu separatora, więc podgląd jest rozjechany (DOM-N2).
11. Pusta ostatnia kolumna w pliku tworzy pustą trzecią stronę talii (DOM-N3).
12. ✔ Jeśli apka zejdzie w tło w trakcie importu, po powrocie ekran może zostać z wiecznie kręcącym się kółkiem (EKR-N2).
13. ✔ Wyszukiwarka kart nie ma przycisku „X” do czyszczenia, choć było to wcześniej uzgodnione (EKR-N4).
14. Dodana karta ląduje na końcu listy bez żadnego potwierdzenia, a przy aktywnym filtrze jej nie widać (EKR-N3).
15. Filtry „Wszystkie / Nowe / … / Opanowane” nie mieszczą się na szerokości telefonu, ostatnie są ukryte (EKR-N12).

**Konto i synchronizacja**

16. Możliwy crash przy pierwszym logowaniu, gdy przeniesienie danych gościa się nie uda (DAT-N5, EKR-N14).
17. Przy słabej sieci na starcie nie ma ponowienia, a status pokazuje „zsynchronizowano” (DAT-N3). Niewysłane zmiany czekają do wyjścia z apki (DAT-N4).
18. Kolejność kart na dwóch urządzeniach może się rozjeżdżać po równoległym dodaniu kart (DAT-N1).
19. Wylogowanie przy złej sieci potrafi wisieć (DAT-N6).
20. Gość, który utworzył i usunął własny tryb, dostaje niepotrzebne pytanie o przeniesienie danych (DOM-N1).

**Teksty**

21. ✔ W oknie trwałego usunięcia nazwa talii nie ma cudzysłowów, bo Android je wycina (TB-N1).
22. Komunikaty błędów w edytorze trybów cytują nazwy kroków, których nie ma na liście (EKR-N7, TB-N4).
23. Brak konta Google na telefonie daje komunikat „Brak tokena, spróbuj ponownie”, a ponowienie nie pomoże (TB-N6).
24. Włoskie „Masterizzata” znaczy „nagrana na płytę” (TB-N5). W ES/IT/DE są też inne błędy (TB-N16, TB-N17).

---

## 3. Do Twojej decyzji (zmieniają działanie lub wygląd)

1. **Ikonki:** wariant A, B czy C (sekcja 1).
2. **Tryb „Audio”:** karta, która nie wyszła za pierwszym razem, a za drugim była poprawna, może dziś dostać „Łatwe” i bardzo długą przerwę. Zostawić ocenę z dopasowania czy dawać najwyżej „Dobrze” (DOM-N4)?
3. **Muzyka innych aplikacji:** w trybach z mikrofonem apka nie wycisza ani nie pauzuje muzyki. Wyciszać (AUD-N4)?
4. **Import, „Wstecz”:** dziś kasuje wpisane dane bez pytania. Pytać „Odrzucić wpisane dane?” (EKR-N1)?
5. **Komunikat na pulpicie** po imporcie, archiwizacji i usunięciu, np. „Zaimportowano „X” (N fiszek)” albo „Zarchiwizowano — Cofnij” (EKR-N17)?
6. **Podgląd importu przy 3+ kolumnach:** pola w pionie czy wiersz tylko do odczytu z edycją po dotknięciu (EKR-N5)?
7. **Liczba kart do powtórki** na pulpicie (dziś 11 sp): większa pastylka czy „Ucz się · 12” na przycisku (NAU-N7)?
8. **Podświetlać sugerowaną ocenę** na przyciskach po odpowiedzi głosowej (NAU-N10)?
9. **„Karta” czy „fiszka”** w polskim interfejsie? Dziś jest mieszane, podobnie „tapnięcie” i „dotknięcie” (NAU-N31, TB-N15).
10. **Reset lub przywrócenie kopii przy dwóch urządzeniach:** dziś stare dane wracają z drugiego urządzenia. Czy reset ma „usuwać wszędzie” (DAT-N2)?
11. **Edytor trybów:** dodać przycisk „Dodaj krok poniżej”, bo dziś nowy krok zawsze ląduje na końcu (EKR-N11)?
12. **Kolor oceny „Dobrze”:** dziś zależy od tapety (Material You), więc może wyglądać jak „Łatwe”. Dać stały kolor (NAU-N9)?

---

## 4. Czytelność kodu i modernizacja (w apce tego nie widać)

**Za długie pliki do podziału.** To czyste przeniesienie kodu bez zmian w logice:
- `StudyCard.kt` (480 linii) → układ ekranu osobno, karta i strony osobno.
- `ModeDetailScreen.kt` (390) i `StepCard.kt` (340) → wybór szablonu i pola parametrów do osobnych plików.
- `ImportViewModel.kt` (364) i `ImportScreen.kt` (347) → parsowanie do `ImportParsing.kt`.
- `DeckSettingsScreen.kt` (360) → dialogi i sekcja trybów osobno.
- `SyncEngine.kt` → trzy pliki (pozycje, operacje na chmurze, odczyt). `SyncLifecycle.kt` → uporządkowanie kolejności i nazw.

**Powtórzenia, które powinny być wspólnym komponentem:**
- chipy wyboru, skopiowane w 5 miejscach,
- przyciski z ikoną (różne odstępy),
- pastylka z liczbą,
- prywatna kopia `SectionCard` w statystykach,
- „pokaż najnowszy komunikat” (dziś komunikaty ustawiają się w kolejkę),
- domyślna nazwa strony „Page N” w 4 kopiach.

**Modernizacja:**
- `MaterialExpressiveTheme` i systemowy `motionScheme` zamiast własnej kopii animacji (`RayCollMotion`),
- stabilny `Crossfade` zamiast eksperymentalnego,
- `animateItem()` w listach (dziś usunięcie lub przywrócenie powoduje skok listy),
- cache Firestore w pamięci zamiast na dysku,
- nowsza opcja logowania Google (`GetSignInWithGoogleOption`).

**Porządki:**
- powtórzenia w `BackupValidator`,
- wspólne „puść palec” w `SessionEngine`,
- martwy kod (`ListenStatus.SKIPPED`, nieużywana zależność testowa, zbędny wpis w manifeście),
- znaczniki `<!-- ui1 -->` w tekstach, pusty katalog `ui/component/`,
- `SystemClock` → `WallClock`: myli się z zegarem Androida.

---

## 5. Testy, build, repozytorium

**Brak testów tam, gdzie błąd kosztuje dane:**
- kopia zapasowa (eksport, przywrócenie, reset),
- przeniesienie danych gościa na konto,
- serwis nauki w tle,
- filtry nauki (`HARDEST`, domyślny `NEW_AND_REVIEW`),
- scalanie talii.

**Kruche testy:**
- wspólny plik ustawień przecieka między klasami testów,
- czekanie przez `Thread.sleep`,
- ok. 100 angielskich tekstów zaszytych w testach UI (każda zmiana tekstu psuje testy),
- duplikaty testów,
- boilerplate w 13 plikach.

**Build:**
- release jest niepodpisany, więc nie da się go zainstalować,
- debug bez klucza Firebase buduje się, ale crashuje przy starcie,
- README jest nieaktualny,
- wersja debug i release mają na telefonie identyczną nazwę,
- `.claude/worktrees/` zajmuje 495 MB starych kopii roboczych.

**Kopia zapasowa Androida** obejmuje cały katalog baz, razem z cache Firestore. Grozi to przekroczeniem limitu 25 MB i pominięciem całej kopii (AUD-N14, TB-N13).

---

## 6. Proponowana kolejność

1. Decyzje z sekcji 3, przede wszystkim wybór wariantu ikonek.
2. Pakiet „błędy” z sekcji 2. Nie zmienia zamierzonego działania.
3. Pakiet „teksty i tłumaczenia” (5 języków).
4. Pakiet UX według decyzji.
5. Podział plików, wspólne komponenty, testy.
6. Test na telefonie, potem scalenie `przeglad-modernizacja` do `main`.
