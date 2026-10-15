# Red team Codex: NAU (31 uwag)

Stan `39f0690b`, 15.10.2026. Ścieżki względem `app/src/main/java/com/raycoll/app/ui/`. Nie wybrano nowego wyglądu wskaźników audio; warianty A/B/C pozostają propozycjami. Nie zmieniono kodu.

### NAU-N1 — CONFIRMED — komunikat z akcją ma nieograniczony czas

`study/StudyScreen.kt:93–97` pomija duration przy niepustym actionLabel; domyślny Material3 wybiera wtedy Indefinite. Przyznanie zgody nie zamyka komunikatu.
Naprawa S: jawne Long i zamknięcie odpowiedniego komunikatu po zgodzie, bez usuwania w ciemno komunikatu o niezapisanych ocenach. Test braku zgody → zgoda i zaniku komunikatu; niskie ryzyko.

### NAU-N2 — PARTIAL — powtarzane błędy są realne, deduplikacja wymaga rozróżnienia przyczyny

ViewModel emituje błąd każdej karty, a CollectEvents uruchamia osobne showSnackbar. Brak głosu może ponownie otwierać dialog.
Naprawa M: okno brakującego głosu nie wraca co kartę w tej samej sesji; ponowienie jest możliwe po świadomej próbie naprawy. Nie deduplikować w ciemno wszystkich błędów typu STT przez całą sesję, bo nowa awaria po poprawnym nasłuchu też wymaga informacji. Gdy ma obowiązywać „najnowszy komunikat”, anulować poprzednią korutynę komunikatu, nie tylko dismiss bieżącego (pozostałe mogą już czekać w mutexie). Nie zgubić akcji retry zapisu ocen. Test serii identycznych błędów i błędu po odzyskaniu sprawności. CROSS-LAYER AUD; średnie ryzyko.

### NAU-N3 — CONFIRMED — miejsce paska podsumowania pojawia się dopiero po odczycie

`SessionSummary.kt:73` jest warunkowe, a `refreshResultCards` czeka na bazę. W wyśrodkowanej karcie dodanie paska i odstępów zmienia pozycję treści.
Naprawa S: zawsze rezerwować docelowe miejsce paska i odstępy, przed wynikiem pokazać pusty tor bez fałszywego opisu kategorii. Samo AnimatedVisibility łagodzi zmianę, ale nadal przesuwa tekst. Test geometrii przed/po dostarczeniu resultCards; niskie ryzyko.

### NAU-N4 — CONFIRMED — animacja odczytuje nową treść dla wychodzącego stanu

`StudyActions` ma target Boolean, lecz `canReveal` zależy od aktualnego session poza targetem. Naprawa S: jawny stan RATINGS/REVEAL/NONE jako target i renderowanie według parametru lambda; nie renderować wychodzącego REVEAL z nowej wartości canReveal. Test z zatrzymanym zegarem animacji i końcem odsłaniania; niskie ryzyko.

### NAU-N5 — CONFIRMED — potwierdzenie wyjścia może oznaczyć ukończoną sesję jako przerwaną

Dialog nie znika przy zakończeniu, a `endSessionEarly` zawsze ustawia endedEarly. Naprawa S: guard w ViewModelu dla FINISHED i zamknięcie dialogu po końcu. Test zakończenia audio przy otwartym dialogu i późnego potwierdzenia. Niskie ryzyko.
Osobna DECISION: czy samo otwarcie dialogu ma pauzować audio. Nie jest niezbędna do powyższej naprawy.

### NAU-N6 — DECISION — animowanie wszystkich stanów to propozycja wyglądu

Obecny when jest poprawny. Jeśli dodać animację, nie używać `it::class` do odróżnienia nauki od podsumowania: oba są `StudyUiState.Active`. Nie uruchamiać przejścia przy każdym częściowym wyniku mowy.
Rekomendacja: krótka animacja wyłącznie między Loading/Active/Summary/Empty, z osobnym kluczem ekranu. Alternatywa zachować obecne cięcia. Po decyzji S/M, test restartu i braku animowania każdej emisji; średnie ryzyko UX.

### NAU-N7 — DECISION — wielkość licznika jest wyborem hierarchii informacji

`DashboardScreen.DeckSubtitle` używa labelSmall i jest poprawnie skalowane z fontScale. Nie wykazano ucięcia lub niedostępności samej liczby.
Rekomendacja: labelMedium w obecnej pastylce; alternatywa liczba w przycisku „Ucz się”. S, wizualna kontrola 5 języków po decyzji. Nie przedstawiać 11 sp jako automatycznego błędu dostępności.

### NAU-N8 — CONFIRMED — outline jest zbyt słabe dla zwykłego tekstu

`AnswerPanel` używa outline na surfaceContainerHighest; kolory fallback rzeczywiście dają zbyt mały kontrast dla bodyLarge. W motywie dynamicznym dokładne wartości są inne, więc liczby z raportu nie są uniwersalne.
Naprawa S: onSurfaceVariant dla „Pominięto”; sprawdzić jasny/ciemny i dynamiczny motyw. Bez nowego testu dla pojedynczej podmiany tokenu.

### NAU-N9 — DECISION — stałe barwy ocen są rozsądną propozycją, nie błędem primaryContainer

Kolor GOOD jest dynamiczny, jednak oceny mają też teksty, różne ikony i stałe miejsca. „Pozostałe trzy mają stałe barwy” jest nieścisłe: AGAIN używa errorContainer z colorScheme.
Rekomendacja: spójny zestaw kolorów semantycznych ocen w obu motywach, jeśli chcesz znaczenie niezależne od tapety. Alternatywa zostawić Material You. S/M, kontrola kontrastu i 5 języków; bez zmiany przed wyborem.

### NAU-N10 — DECISION — wyróżnienie sugerowanej oceny to nowa wskazówka interfejsu

Sugestia jest już widoczna tekstowo w panelu odpowiedzi; przyciski jej nie powtarzają. Rekomendacja: cienka ramka sugerowanego przycisku bez automatycznego zaznaczenia/zapisu. Alternatywa obecny panel. S/M, test powiązania sugestii i resetu na nowej karcie po decyzji.

### NAU-N11 — CONFIRMED — próg odpowiedzi wymaga czytelniejszego opisu

„45% / 80%” nie nazywa progu. Naprawa S: jawny „wymagane 80%” z dwoma poprawnymi placeholderami we wszystkich językach. CROSS-LAYER TB; parytet zasobów i sprawdzenie zawijania z EKR/NAU panelami; niskie ryzyko.

### NAU-N12 — PARTIAL — warto nazwać postęp, ale nie dowiedziono odczytania go jako daty

`SessionProgressBar` nie ma słownego opisu „karta X z Y”. Zachowanie głosu TalkBack zależy od locale/silnika, więc diagnoza o dacie pozostaje hipotezą.
Naprawa S: zlokalizowany opis na liczniku, bez podwójnego odczytu tekstu i description. CROSS-LAYER TB, test semantyki i TalkBack przy okazji urządzenia; niskie ryzyko.

### NAU-N13 — DECISION — animacja listy nie naprawia błędu danych

Lista ma stabilne klucze, lecz brak animateItem. Rekomendacja: dodać subtelną animację dodania/usunięcia przy zgodzie na taki ruch, sprawdzić interakcję ze shared transition i ustawieniem systemowych animacji. Alternatywa zostawić natychmiastowe zmiany. S, kontrola wizualna; wspólna decyzja z EKR-N25.

### NAU-N14 — DECISION — skrócenie tytułu przy pauzie jest kompromisem paska aplikacji

Kod jawnie umieszcza pauzę w top bar, żeby nie przesuwać karty. Długi tytuł ma maxLines=1 i ellipsis. Rekomendacja: sam przycisk Wznów z ikoną i dostępnym opisem pauzy; alternatywa obecny tekst plus przycisk. S, kontrola tytułów DE/PL. Nie usuwać informacji o pauzie bez wybranego zastępstwa.

### NAU-N15 — DECISION — przejście całego motywu na Expressive zmienia zachowanie komponentów

`RayCollMotion` ma jawny komentarz o lokalnych wartościach i ograniczeniach API 1.4.0. MaterialExpressiveTheme może udostępnić schemat, lecz to zmiana ruchu komponentów i API eksperymentalne; sam fakt istnienia nowszego wejścia nie dowodzi usterki.
Rekomendacja na teraz: zachować schemat, modernizację zrobić jako osobną zmianę po obejrzeniu animacji. Jeśli wybrana: sprawdzić w konkretnej wersji domyślne wartości, przekazać istniejące colors/typography/shapes i przenieść odczyt motionScheme do composable (nie do stałych top-level NavGraph). M, testy UI i urządzenie.

### NAU-N16 — PARTIAL — pojedynczy kształt odstaje, własne wrappery wszystkich przycisków zbędne

Przycisk importu w EmptyState nie podaje shape; pozostałe takie przyciski zwykle podają small. Naprawa S: dopisać ten sam kształt. Nie budować RayCollButton/RayCollOutlinedButton wyłącznie dla jednego argumentu. Kontrola wizualna i ktlint; niskie ryzyko.

### NAU-N17 — CONFIRMED — autoscroll pozostaje aktywny przez cały czas karty

`StudyCard.kt:136–143` bez końca obserwuje maxValue po odsłonięciu; panel rozpoznania może zmienić wysokość długo później. Werdykt U7 wymagał ograniczonego oczekiwania na animację, którego nie wdrożono.
Naprawa S: ograniczyć śledzenie do odsłaniania (dotychczasowy spec: timeout 700 ms) i przerwać przy świadomym przewijaniu użytkownika. Nie wiązać go z każdą emisją częściowego STT. Test geometryczny/manual scroll po odsłonięciu; niskie ryzyko.

### NAU-N18 — PARTIAL — stan pełnego ekranu powinien dać się przewinąć, ale nie każdy caller ma skończoną wysokość

`MessageState` nie przewija treści, więc duży font w niskim oknie może uciąć akcję. Jednak EmptyState jest też elementem LazyColumn w Browse; bezwarunkowe dodanie wewnętrznego verticalScroll może dostać nieskończoną wysokość i spowodować wyjątek.
Naprawa S/M: przewijanie w kontenerze stanów pełnego ekranu o skończonej wysokości; w item listy korzystać z przewijania listy. Nie dokładać zagnieżdżonego scrolla wszystkim użyciom. Test landscape/fontScale 2 i pustego Browse. CROSS-LAYER EKR; średnie ryzyko regresji layoutu.

### NAU-N19 — PARTIAL — opisy już są wyśrodkowane

`SessionSummary` ma TextAlign.Center na głównym opisie. Brakuje go na tytule i części liczników; globalna diagnoza dla wszystkich opisów jest błędna.
Naprawa S: tylko zawijające się tytuł/liczniki, zgodnie z wyśrodkowaną kartą. Kontrola DE i fontScale; bez nowego testu dla samego wyrównania. Niskie ryzyko.

### NAU-N20 — CONFIRMED — klikany Surface zapewni poprawny obrys interakcji

Klikalność AnswerPanel jest na modifierze poza wewnętrznym clip Surface. Naprawa S: klikany overload Surface z tym samym shape i semantyką liveRegion. Nie usuwać komunikowania wyniku po zakończeniu nasłuchu. Kontrola ripple i istniejące testy karty; niskie ryzyko.

### NAU-N21 — CONFIRMED — wynik i sugestia nie mają miejsca na zawijanie jako oddzielne elementy

`ResultChip` to Row z ikoną, tytułem i badge bez wag. Przy małej szerokości/dużym foncie końcówka dostaje zbyt mało miejsca.
Naprawa S: zawijany układ ikona+wynik oraz sugestia, zachowujący kolejność odczytu; FlowRow jest wystarczający. Test 360 dp i fontScale 1.5/2 w PL/DE; niskie ryzyko.

### NAU-N22 — PARTIAL — kontekst talii poprawi listę akcji TalkBack, brak etykiety nie czyni całej karty nierozpoznawalną

Przyciski mają poprawne nazwy akcji; nazwa talii jest osobnym tekstem powyżej. W nawigowaniu samymi kontrolkami nazwy się jednak powtarzają.
Naprawa S: zlokalizowane etykiety „Ucz się: nazwa”/„Przeglądaj: nazwa”, bez podwójnego odczytu potomków. CROSS-LAYER TB; test semantyki. Nie zmieniać zwykłego widocznego tekstu przycisków.

### NAU-N23 — DECISION — wizualny priorytet migracji wymaga wyboru

Trzy TextButton są poprawnymi akcjami, a brak możliwości przypadkowego zamknięcia jest celowy. Rekomendacja: wyróżnić przeniesienie i oddzielić rezygnację, bez zmiany znaczenia FRESH/CANCEL. Alternatywa obecny równorzędny wybór. S, po decyzji razem z obsługą busy/błędu EKR-N14.

### NAU-N24 — DECISION — układ zależny od wysokości byłby zmianą przyjętej reguły orientacji

Zwarty landscape jest opisany komentarzem i zgodny z wcześniejszym zatwierdzeniem układu nauki. Tablet/fold/multiwindow uzasadniają przyszłe kryterium wysokości, lecz nie są dowodem regresji na obecnym Pixelu.
Rekomendacja: pozostawić teraz orientację; ewentualna adaptacja ma osobno rozwiązywać rozmiar tekstu i położenie strzałek, z testami okna, nie samym arbitralnym 480 dp. Alternatywa zaakceptować taki zakres responsywności. S/M.

### NAU-N25 — INTENTIONAL — Scaffold poprawnie odsuwa listę od systemowego paska

Padding Scaffolda na całej treści jest standardowym bezpiecznym układem; nie dowiedziono, że karta lub akcja jest niedostępna. Rysowanie pod przezroczystym paskiem jest wariantem wizualnym, nie naprawą obowiązkową. Zostawić. Jeśli chcesz edge-to-edge listy, osobna decyzja z kontrolą consumeWindowInsets/contentPadding.

### NAU-N26 — PARTIAL — można ujednolicić animację, ale proponowany stabilny overload nie ma contentKey

`javap` dla animation-android 1.12.1 potwierdził, że zwykły Crossfade(targetState,...) nie ma contentKey; ma go overload Transition.Crossfade. Podana w raporcie zamiana nie skompiluje się. Ten klucz celowo zapobiega przejściu przy każdej aktualizacji danych Ready.
Naprawa S: zachować obecną wersję z contentKey i jawnie podać animationSpec, jeśli celem jest spójność ruchu. Alternatywnie animować osobny klucz typu stanu w stabilnym API, a dane renderować aktualne; wymaga testu zachowania listy. Nie usuwać opt-in przed faktyczną zmianą API. Niskie ryzyko dla samej specyfikacji animacji.

### NAU-N27 — NOT_WORTH — arbitralne wydzielenie trzech helperów nie rozwiązuje wykazanego problemu

StudyScreen już oddziela gest i uprawnienia; wydarzenia, content i dwa dialogi naturalnie składają ekran. Po naprawieniu konkretnych problemów można ocenić czytelność, ale sama liczba ~150 linii nie uzasadnia przekazywania wielu stanów przez trzy nowe funkcje. Nie tworzyć osobnego obowiązkowego zadania.

### NAU-N28 — PARTIAL — można rozdzielić odpowiedzialności, nie trzeba trzech plików dla limitu linii

StudyCard zawiera zarówno układ całego ekranu, jak i renderowanie stron z wskaźnikiem audio. Naprawa porządkowa S/M: przenieść spójny blok stron/karty do osobnego pliku, pozostawić minimum widoczności. Nie dzielić dalej samych akcji bez korzyści. Po wyborze wariantu wskaźnika, żeby nie przesuwać kodu dwa razy. Istniejące testy karty/układu; średnie ryzyko konfliktów, bez zmiany UX.

### NAU-N29 — CONFIRMED — niewielka wspólna pastylka ma kilka rzeczywistych zastosowań

ModeBadge i badge licznika powtarzają shape/typography/padding. Naprawa S: mały komponent z tekstem i kolorami; zachować wagę i overflow tekstu trybu. Nie łączyć semantyki trybu z semantyką licznika. Testy istniejące i kontrola wizualna; niskie ryzyko.

### NAU-N30 — PARTIAL — tylko interactionSource jest prostym, bezpiecznym porządkiem

Przy indication=null można zrezygnować z pamiętanego MutableInteractionSource. Pozostałe propozycje nie są równoważne: @Immutable nadal wpływa na porównanie equals vs referencja przy strong skipping; `heightIn` i `minimumInteractiveComponentSize` mają różne kontrakty pomiaru; usunięcie Spacer wymaga sprawdzenia przejścia rozmiaru.
Naprawa S: tylko `interactionSource=null`, pozostałe zostawić bez pomiaru/testu geometrii. Nie usuwać adnotacji z uzasadnieniem „nic nie daje”. Kompilacja i istniejące testy UI; niskie ryzyko.

### NAU-N31 — DECISION — karta/fiszka to niejednoznaczny wybór słownictwa

Oba słowa opisują ten sam obiekt, ale „karta” jest też naturalną nazwą elementu UI. Brak odzyskanej decyzji ustalającej słowo dla fiszki; wcześniejsze „talia” dotyczyło deck.
Rekomendacja: fiszka dla jednostki nauki, dotknięcie zamiast tapnięcia; alternatywa konsekwentna karta. CROSS-LAYER TB/EKR; S, jedna wspólna zmiana słownika, nie dwa niezależne pakiety.
