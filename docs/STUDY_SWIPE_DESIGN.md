# Przewijanie kart z podglądem sąsiadów

Status: wdrożono `HorizontalPager`. 2026-10-17.

Weryfikacja: 202 testy sesji, pagera, odtwarzania i tłumaczeń — bez błędów;
`ktlintCheck`, `lintDebug` i `assembleDebug` zakończone powodzeniem.
Podgląd w trakcie gestu: `app/build/outputs/ui-previews/study-pager-half.png`.

## Cel

Przy przeciąganiu karty następna lub poprzednia karta jest już widoczna obok.
Po puszczeniu palca przejście zostaje zatwierdzone albo widok wraca na bieżącą kartę.
Samo oglądanie sąsiada nie uruchamia jego kroków nauki, audio, mikrofonu ani oceny.

## Ustalenia przed wdrożeniem

- `ui/study/CardSwipe.kt` przesuwa tylko bieżącą kartę; po przekroczeniu progu wywołuje przejście.
- `ui/study/FlashcardView.kt` dopiero po zmianie karty uruchamia osobną animację `AnimatedContent`.
  Tych dwóch mechanizmów nie należy nakładać na nowy pager.
- `domain/session/SessionEngine.kt` jest właścicielem bieżącej karty i wykonania kroków trybu.
  Widok nie powinien tworzyć dodatkowych silników dla sąsiadów.
- `playback/SessionDepsAdapter.kt` uruchamia zapis ocen w zakresie aplikacji, poza cyklem życia widoku karty.
  Przewinięcie widoku nie powinno anulować tej pracy. Nie jest to jednak potwierdzenie trwałego
  zapisu każdej rozpoczętej operacji: zanim Firestore otrzyma zmianę, jest jeszcze odczyt cache.
- `data/firestore/FirestoreDocumentStore.kt` wysyła zapisy do Firestore asynchronicznie.
  `di/FirebaseModule.kt` włącza trwały cache. Operacje już przekazane do trwałej kolejki mogą być
  synchronizowane po powrocie sieci; sama animacja nie może być traktowana jako potwierdzenie zapisu.
- `domain/session/ReviewLedger.kt` przechowuje bazowy stan SRS pierwszej oceny danej karty w próbie.
  Ponowna ocena zastępuje poprzednią, zamiast naliczać dodatkową powtórkę.
- `data/repository/DeckRepository.kt` zapisuje zmianę SRS i licznik aktywności osobnymi operacjami.
  Możliwe utwardzenie: jeden batch tych dwóch operacji, aby nie zostały rozdzielone przez awarię.
- `SessionState` i `ReviewLedger` są w pamięci. Brak kompletnego odtwarzania sesji po zabiciu procesu.
  Zapisane oceny to osobne dane od pozycji, odkrytych stron i przebiegu sesji.
- Obecne `pause()` / `resume()` mogą uruchomić kartę od początku. Nie wolno używać ich bezpośrednio
  jako mechanizmu anulowania przeciągnięcia, jeśli wymagamy zachowania bieżącego stanu.

## Rozważone rozwiązania

| Rozwiązanie | Zalety | Koszt i ryzyko |
| --- | --- | --- |
| `HorizontalPager` z pasywnym renderowaniem kart | Standardowy mechanizm Compose: przeciąganie, prędkość gestu, przyciąganie, ograniczenie przeskoku do jednej karty. Dobra baza na dłużej. | Trzeba jawnie zsynchronizować widok z silnikiem, obsłużyć pionowy scroll i różne wysokości kart. Samo wstawienie pagera nie zapewnia poprawności zapisów. |
| Własny widok trzech kart: poprzednia, bieżąca, następna | Mały zakres przebudowy istniejącego układu; pełna kontrola nad tym, co pokazuje podgląd. | Samodzielna obsługa fizyki gestu, przerwań, szybkości, dostępności i współpracy z pionowym scrollem. Większy koszt utrzymania. |
| Interaktywne przejście dwóch widoków (`SeekableTransitionState`) | Pozwala powiązać postęp animacji starej i nowej karty z pozycją palca; można zachować część istniejącego renderowania. | Nadal trzeba samemu obsłużyć gest, prędkość, odwrócenie kierunku i anulowanie. Obecne przejście wymaga zmiany na przesunięcie całej szerokości bez zanikania. |

Odrzucona droga na skróty: zmiana rzeczywistej karty silnika już podczas ruchu palca.
Cofnięcie niedokończonego gestu wymagałoby cofania skutków kroków: audio, mikrofonu i automatycznej oceny.

Rekomendacja: `HorizontalPager` jako warstwa prezentacji; jeden istniejący silnik jako właściciel sesji.

## Kontrakt widoku i sesji

1. Każda strona ma stabilny klucz `cardId`. Lista i kolejność pochodzą z bieżącej sesji,
   a nie z ponownie przeliczonej listy kart należnych do nauki po każdej ocenie.
2. Sąsiednie strony są wyłącznie pasywnymi obrazami danych. Nie wywołują `start()`,
   `runCurrentCard()`, oceny, TTS ani STT. Nowa karta pokazuje zakryte odpowiedzi.
3. Pozycja palca i `currentPage` służą tylko do rysowania. Komendę nawigacji można wydać dopiero
   po rozstrzygnięciu gestu i ustabilizowaniu pagera (`settledPage`, bez trwającego scrolla).
4. Koordynator rozróżnia gest użytkownika od animacji wywołanej oceną, przyciskiem lub automatyką.
   Programowe ustawienie widoku na aktywną kartę nie może wywołać drugiego przejścia.
5. Komenda nawigacji wskazuje konkretną kartę docelową oraz oczekiwaną kartę źródłową
   i identyfikator/generację sesji. Nieaktualna komenda po restarcie sesji lub wyścigu z timerem
   jest ignorowana. Nie używamy ślepego `currentIndex++` z opóźnionego zdarzenia.
   Nakładające się gesty kontynuują ten sam token; silnik dopuszcza przesunięcie najwyżej
   o liczbę faktycznie rozpoczętych gestów. Karty mijane przed ustabilizowaniem pagera pozostają bierne.
6. Przez czas gestu i domykania animacji nie można jednocześnie oceniać innej karty
   ani uruchomić automatycznego drugiego przejścia. Istniejący zapis oceny może się dokończyć.
7. Anulowanie gestu nie zmienia aktywnej karty, odkrytych odpowiedzi ani ocen.
   Nie restartuje trybu. Zatwierdzenie uruchamia nową kartę dokładnie raz.
8. Dla audio potrzebny jest osobny mechanizm blokady nawigacji, niezależny od przytrzymania
   służącego podglądowi odpowiedzi i od obecnego `pause()` / `resume()`.
   Propozycja: bieżąca wypowiedź lub nasłuch mogą się dokończyć, ale dalsze kroki i przejście
   czekają na rozstrzygnięcie gestu. Przy zatwierdzeniu zmiany aktywne audio jest przerywane.
   Wyniki starej karty muszą być przypisane do jej identyfikatora/generacji, a nie nowej pozycji.
9. Na pierwszej karcie brak poprzednika. Na ostatniej potrzebna jest jawna obsługa końca,
   zgodna z dotychczasowym przejściem do podsumowania; nie tworzymy fikcyjnej karty w danych.
10. Stan przewinięcia pionowego i ewentualny obraz poprzednio odwiedzonej karty są indeksowane
    przez `cardId`. Nie przechowujemy osobnej aktywnej sesji dla każdej strony pagera.

## Ochrona zapisów

- Zmiana animacji nie wymaga zmiany identyfikatorów kart, resetowania talii ani migracji SRS.
- Przeciągnięcie i podgląd nie są oceną. Pominięcie bez oceny zachowuje dotychczasową zasadę aplikacji.
- Powrót do wcześniej ocenionej karty nie usuwa jej oceny. Świadoma ponowna ocena korzysta
  z zapisanej bazy pierwszej oceny w tej próbie.
- Zapisu nie uruchamia się z kompozycji strony pagera ani z obserwacji jej widoczności.
- Osobny zakres: odtwarzanie sesji po zabiciu procesu. Samo zapisanie numeru strony nie wystarczy:
  potrzebne są przynajmniej tożsamość próby, stała kolejność `cardId`, zatwierdzona karta,
  reguły wznowienia trybu oraz dane bazowe pozwalające bezpiecznie zastępować oceny.
  Dla większego stanu należy użyć trwałego magazynu; mały identyfikator checkpointu może być
  odtwarzany przez `SavedStateHandle`. Nie zapisujemy surowych obiektów audio, zadań ani całej talii w Bundle.
- Checkpoint i zapis oceny muszą mieć określony porządek oraz sposób uzgodnienia po awarii.
  Przy rozbudowie trwałości warto użyć stabilnego identyfikatora zdarzenia oceny, aby ponowienie
  nie zwiększyło liczników ani powtórek drugi raz.

## Kolejność wdrożenia

1. Testy kontraktu: anulowanie niczego nie ocenia, nawigacja nie anuluje zapisu,
   powrót i ponowna ocena nie dublują powtórek.
2. Koordynator nawigacji i blokada automatyki z identyfikatorami kart/sesji.
3. Pasywne renderowanie sąsiadów i `HorizontalPager`; usunięcie starego `cardSwipe`
   oraz przejścia kart z `AnimatedContent`.
4. Spięcie ocen, przycisków i automatycznych przejść z tą samą nawigacją.
5. Testy i weryfikacja na danych testowych. Instalacja dopiero po przejściu kontroli.
   Pełne odtwarzanie sesji po zamknięciu wymaga uzgodnienia jako dodatkowe zachowanie.

## Scenariusze akceptacyjne

- W połowie ruchu widać właściwego sąsiada. Odwrócenie i anulowanie gestu niczego nie zatwierdza.
- Jeden gest daje najwyżej jedno przejście; szybka seria gestów nie gubi ani nie powiela zdarzeń.
- Ocena, trwający zapis i automatyczne przejście nie wyprzedzają się w sposób zmieniający inną kartę.
- Podgląd nie uruchamia autooceny, TTS ani mikrofonu; nie odsłania ukrytych odpowiedzi.
- Powrót po ocenie i ponowna ocena zachowują właściwy stan SRS i licznik aktywności.
- Długie karty nadal przewijają się pionowo; krótkie stuknięcie i przytrzymanie nadal działają.
- Pierwsza/ostatnia karta, obrót telefonu, zejście w tło i restart sesji w trakcie gestu są obsłużone.
- Offline i opóźniony/błędny zapis nie są przedstawiane jako potwierdzony zapis do chmury.
- Jeśli dodamy trwałe wznowienie: wymuszone zakończenie procesu i ponowne otwarcie odtwarzają
  zatwierdzoną kartę, a odtworzenie/ponowienie nie powiela ocen.

## Przyjęte zachowanie

- Użytkownik wybrał `HorizontalPager`; powrót zachowuje odkryte strony, wynik odpowiedzi i zapisany SRS.
- Zapamiętany wygląd obowiązuje także w podglądzie sąsiada. Pionowa pozycja wraca po odtworzeniu
  widoku usuniętego z cache pagera.
- Świadoma zmiana oceny wracającej karty zastępuje poprzednią ocenę w tej próbie.
  Samo cofnięcie nie uruchamia ponownie automatycznej oceny.
- Przycisk „poprzednia” nadal po 3 sekundach restartuje bieżącą kartę; gest cofa bez tej reguły.
- Pamięć dotyczy aktywnej sesji. Pełne odtwarzanie sesji po zabiciu procesu pozostaje osobnym zakresem.

## Źródła

- https://developer.android.com/develop/ui/compose/layouts/pager
- https://developer.android.com/reference/kotlin/androidx/compose/animation/core/SeekableTransitionState
- https://developer.android.com/develop/ui/compose/state-saving
- https://firebase.google.com/docs/firestore/manage-data/enable-offline
