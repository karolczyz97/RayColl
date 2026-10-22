# Przegląd pól tekstowych — 17.10.2026

Przegląd wszystkich wywołań `OutlinedTextField`, `TextField`, `BasicTextField`, dotychczasowego
`PersistedTextField`, `SearchBarDefaults.InputField` i ich opakowań w `app/src/main`.

| Miejsce | Decyzja | Uzasadnienie |
| --- | --- | --- |
| Ustawienia zestawu: nazwa | Klikalny wiersz → okno Anuluj / Zapisz | Pojedyncza zmiana istniejącej nazwy; pisanie nie powinno od razu zmieniać danych. |
| Ustawienia zestawu: nazwy stron | Klikalne wiersze → okno Anuluj / Zapisz | Ta sama zasada; język i kolejność pozostają osobnymi kontrolkami. |
| Import: nazwa zestawu | Pole w formularzu | Szkic; cały zestaw powstaje dopiero po zatwierdzeniu importu. |
| Import: tekst CSV/TSV | Wielowierszowe pole | Wprowadzanie lub wklejanie treści źródłowej. |
| Import: nazwy kolumn/stron | Pola w formularzu | Część tego samego szkicu importu. |
| Import: komórki podglądu | Pola w podglądzie | Bezpośrednia korekta danych przed importem. |
| Edycja/tworzenie fiszki: treść stron | Pola w istniejącym oknie | Okno ma już osobne Anuluj i Zapisz. |
| Edytor trybu: nazwa | Pole w formularzu | Edycja szkicu; istnieje wspólny zapis i ochrona przy wyjściu. |
| Edytor trybu: liczby i parametry kroków | Pola liczbowe | Część szkicu trybu, z walidacją przed zapisem. |
| Wyszukiwanie fiszek | Pole wyszukiwania | Wyniki mają reagować podczas pisania; nie ma danych do zatwierdzenia. |
| Potwierdzenie usunięcia: przepisanie nazwy | Pole w oknie potwierdzenia | Sprawdzenie wpisanego tekstu chroni usunięcie, nie zmienia nazwy. |

## Zmiany

- Zastąpiono dwa zastosowania automatycznego zapisu nazw standardowymi wierszami Material i `AlertDialog`.
- Tekst nazwy jest szkicem aż do Zapisz. Anuluj/cofnięcie nie uruchamia zapisu.
- Okno pozostaje otwarte do wyniku zapisu; błąd zachowuje szkic i pozwala ponowić próbę.
- Szkic i otwarte okno korzystają z `rememberSaveable`; błąd zapisu nie resetuje wpisanej nazwy.
- Stan trwającego zapisu należy do ViewModelu. Odtworzenie ekranu blokuje drugi zapis, a nowy proces pozwala ponownie zatwierdzić zachowany szkic.
- W nazwie zestawu zablokowano pustą wartość; pusta nazwa strony nadal oznacza domyślną „Strona N”.
- Nowe okno podpowiada wielką literę i pozwala zatwierdzić nazwę przyciskiem Gotowe na klawiaturze.
- Usunięto nieużywany komponent `PersistedTextField` i zastąpiono testy jego starego zachowania testami edycji z zatwierdzeniem.

## Podstawa decyzji

- [Android — ustawienia](https://developer.android.com/design/ui/mobile/guides/patterns/settings): wiersze pokazujące etykietę i bieżącą wartość.
- [AndroidX — edycja tekstowego ustawienia](https://github.com/androidx/androidx/blob/androidx-main/preference/preference/src/main/java/androidx/preference/EditTextPreferenceDialogFragmentCompat.java): zapis dopiero po zatwierdzeniu okna.

Dobór tego wzorca do nazw zestawu i stron jest decyzją UX dla RayColl, a nie wymogiem konkretnej wersji Androida.

## Weryfikacja

- 31 testów ustawień zestawu, edycji nazwy, odtwarzania okien i błędów zapisu: bez błędów.
- Sprawdzone m.in. Anuluj, Wstecz, zapis po zatwierdzeniu, ponowienie po błędzie, odtworzenie szkicu, stan zapisu po ponownym uruchomieniu oraz edycja po przestawieniu stron.
- Ostatni pełny przebieg UI: 265 testów, 262 poprawne, 3 błędy w powstającym równolegle `DeckListTest` (przeciąganie zestawów na pulpicie). Raport: `app/build/outputs/name-edit/verification.json`.
- Końcowy `:app:ktlintCheck` całego drzewa: poprawny.
- Android lint nie zakończył analizy: wewnętrzne błędy analizatora Kotlin FIR w `DashboardScreen.kt` i `AccountManager.kt`. Nie uznajemy tego sprawdzenia za zaliczone.
- APK release zbudowano w osobnym katalogu tymczasowym, z pominięciem wadliwych zadań lint tylko w tej komendzie. Konfiguracja kontroli projektu pozostaje włączona. Kopia APK: `app/build/outputs/name-edit/app-release.apk`.
- Nie instalowano na telefonie — lista urządzeń ADB była pusta.
