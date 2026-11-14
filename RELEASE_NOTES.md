# ORBADA — informacje o wydaniach

## ORBADA 1.3.8 „Fuerteventura” (2026-11-14)

### Nowości

- **Pełna obsługa SQLite** — nowy sterownik SQLite (sqlite-jdbc 3.54, pozycja „SQLite (sqlite-jdbc 3)” na liście sterowników; dotychczasowy był z 2008 roku i nie znał nowszych funkcji SQLite). Nowe widoki: obiekty z wyszukiwaniem po nazwie, typie, tabeli i treści SQL, indeksy (kolumny, indeksy częściowe i na wyrażeniach, źródło), liczniki AUTOINCREMENT, parametry PRAGMA z opisami i zmianą wartości, funkcje SQL oraz rozmiar tabel i indeksów. Tabela ma nowe zakładki: szczegóły (STRICT, WITHOUT ROWID, licznik AUTOINCREMENT, liczba wierszy z ANALYZE, rozmiar), klucze obce z ON UPDATE/ON DELETE, tabele wskazujące na nią, obiekty jej używające i statystyki; kolumny pokazują wartość domyślną i kolumny generowane. Kreatory z zakładką SQL: nowa tabela, kopia tabeli (z definicją, danymi i indeksami), zmiana nazwy tabeli, dodanie, zmiana nazwy i usunięcie kolumny, nowy indeks (także częściowy i na wyrażeniu), widok, wyzwalacz (także INSTEAD OF na widoku), zmiana parametru PRAGMA, ustawienie i zerowanie licznika AUTOINCREMENT, usuwanie obiektów i wszystkich wierszy tabeli, ANALYZE, REINDEX. Dla bazy: kopia do pliku w trakcie pracy (VACUUM INTO), VACUUM, ANALYZE, PRAGMA optimize, punkt kontrolny WAL, sprawdzenie integralności i kluczy obcych (także pojedynczej tabeli). Wszystkie widoki SQLite pozwalają wybrać bazę dołączoną przez ATTACH albo temp.
- **Oracle: Wyrównaj dane (MERGE)** — w edytorze SQL (menu Zapytanie i menu pod prawym przyciskiem) z zapytania pod kursorem, np. `select * from tabela@dblink where ...`, powstaje blok PL/SQL, który kopiuje wiersze do tej samej tabeli w bieżącej bazie (albo do wskazanej innej tabeli). Klucz główny z IDENTITY lub z sekwencji (`XXX_ID` → `XXX_SEQ.nextval`) jest pomijany i wiersze są dopasowywane po kluczu unikalnym. Identyfikatory w kluczach obcych są przeliczane na te z bieżącej bazy według klucza unikalnego tabeli nadrzędnej, a brakujące wiersze nadrzędne są najpierw kopiowane ze źródła (także ich własne klucze obce). Przed wstawieniem skryptu do edytora można zmienić klucz dopasowania i wyłączyć aktualizację istniejących wierszy albo kopiowanie wierszy nadrzędnych. Skrypt nie zatwierdza zmian.

### Poprawki

- **SQLite: skrypt tabeli zawiera wszystkie indeksy** — wcześniej tylko pierwszy; nazwy z apostrofem lub znakami specjalnymi nie psują już zapytań list i źródeł.

## ORBADA 1.3.7 „Fuerteventura” (2026-11-12)

### Poprawki

- **Widok „Użytkownicy”: zakładki pod listą idą za zaznaczonym użytkownikiem** — po przejściu na inny wiersz listy szczegóły, role, uprawnienia, limity, sesje, proxy i skrypt zostawały na pierwszym użytkowniku. Poprawka dotyczy też innych widoków bez schematu, które używają tych samych zakładek.

## ORBADA 1.3.6 „Fuerteventura” (2026-11-12)

### Nowości

- **Zarządzanie użytkownikami Oracle** — nowy widok „Użytkownicy” (menu Oracle DBA, jak w Toad): lista użytkowników bazy ze stanem konta (otwarte, zablokowane, wygasłe), datami blokady i wygaśnięcia hasła, przestrzeniami, profilem i ostatnim logowaniem, a pod nią zakładki: szczegóły, role, uprawnienia systemowe i do obiektów, limity na przestrzeniach, obiekty, sesje, proxy i skrypt odtworzenia użytkownika. Akcje: utworzenie użytkownika (także na wzór istniejącego — z rolami, uprawnieniami i limitami), zmiana hasła, zablokowanie i odblokowanie konta, wygaszenie hasła, zmiana przestrzeni, profilu i ról domyślnych, limity, nadawanie i odbieranie ról oraz uprawnień, połączenia proxy, zakończenie sesji użytkownika i usunięcie użytkownika. Akcje, do których zalogowany użytkownik nie ma uprawnień, są wyłączone; bez uprawnień administratora można zmienić własne hasło (z podaniem obecnego). Każda operacja — także zablokowanie, odblokowanie, wygaszenie hasła czy zakończenie sesji — otwiera okno z zakładką SQL z poleceniem, które Orbada wykona (do skopiowania np. do skryptu Liquibase); hasła nie pojawiają się w podglądzie SQL.

- **Debugger PL/SQL z podłączeniem do sesji innej aplikacji** — nowy widok „Debugger PL/SQL” (menu Oracle → zaawansowane). Aplikacja (np. program w Delphi łączący się przez OCI) włącza debugowanie swojej sesji (`DBMS_DEBUG.INITIALIZE` + `DEBUG_ON`) i pokazuje identyfikator; po wklejeniu go w Orbadzie i podłączeniu każde wywołanie PL/SQL z aplikacji zatrzymuje się na breakpointach ustawionych kliknięciem na marginesie źródła pakietu, procedury, funkcji lub wyzwalacza. Na postoju: kontynuacja, krok, wejście do wywołania, wyjście (Shift+F8), przerwanie wykonania, stos wywołań z podglądem każdej ramki, zmienne (parametry, zmienne lokalne, zmienne pętli i globalne pakietu) z możliwością zmiany wartości, obserwowane wyrażenia i opcjonalne zatrzymanie na wyjątku. Obiekt bez informacji do debugowania jest oznaczony i można go skompilować z DEBUG jednym przyciskiem; przycisk „Kod dla aplikacji” pokazuje gotowy blok do wywołania w aplikacji i potrzebne uprawnienia. Gdy Orbada się odłączy albo zniknie, aplikacja nie zawiesza się — czeka najwyżej ustawiony timeout i działa dalej bez debugowania.

## ORBADA 1.3.5 „Fuerteventura” (2026-11-11)

### Zmiany

- **Bez przycisku „Wesprzyj projekt” na pasku** — po aktualizacji nie pojawia się już przycisk w prawym górnym rogu okna; link do wsparcia zostaje w menu Pomoc.

## ORBADA 1.3.4 „Fuerteventura” (2026-11-11)

### Nowości

- **IF NOT EXISTS / IF EXISTS w kreatorach** — kreatory tworzenia obiektów Oracle (tabele, indeksy, sekwencje, synonimy, typy, procedury, funkcje, pakiety, wyzwalacze, DB linki, źródła Java) mają pole „IF NOT EXISTS” (bez błędu, gdy obiekt już istnieje), a kreatory usuwania — „IF EXISTS”; pole pojawia się dla Oracle 23ai i nowszych (starsze wersje tej składni nie znają). W kreatorach PostgreSQL IF NOT EXISTS przy tworzeniu tabeli, indeksu, sekwencji, schematu, widoku zmaterializowanego i dodawaniu kolumn.

### Zmiany

- **Podpowiedzi po kropce bez czekania** — po wpisaniu np. `dbms_output.` w edytorze SQL Oracle lista otwiera się od razu z pozycją „Wczytywanie z bazy...”, a metody pakietu (kolumny tabeli itp.) są pobierane ze słownika w tle; można pisać dalej, a po pobraniu lista sama się uzupełnia i filtruje po tym, co dopisano. Wcześniej edytor czekał na zapytanie do słownika (wolne widoki DBA_/ALL_).

## ORBADA 1.3.3 „Fuerteventura” (2026-11-09)

### Nowości

- **Porównanie zawartości: powrót do bazy** — w widoku „Różnice zbiorów” otwartym z menu edytora (prawy klawisz → Porównanie zawartości) są przyciski przeniesienia lewej albo prawej strony — także po zmianach zrobionych w porównaniu — do nowego edytora SQL bazy, z której pochodzi (Ctrl+Enter kompiluje np. pakiet), albo z powrotem do edytora, z którego ją wzięto. Nowe ikony w menu porównania.

### Zmiany

- **Nowy sterownik MySQL** — „MySQL (Connector/J 26)” łączy się z MySQL 8 i nowszym (logowanie `caching_sha2_password`) i pokazuje prawdziwy błąd serwera zamiast „Communications link failure” (np. „Can't get hostname for your address”); stary sterownik 5.1.7 zostaje na liście.

## ORBADA 1.3.2 „Fuerteventura” (2026-11-06)

### Nowości

- **Edycja obiektów Oracle Scheduler** — przycisk „Edytuj...” w zakładkach Joby, Programy i Harmonogramy otwiera kreator wypełniony istniejącym obiektem; można zmienić m.in. akcję (blok PL/SQL, procedura, program, chain), harmonogram (kalendarz z podglądem, nazwany harmonogram, okno), argumenty i opcje. Przed zapisaniem widać kod PL/SQL, który zmienia tylko zmienione atrybuty.
- **Kolumny IDENTITY i wirtualne w kreatorze tabel** — nowa kolumna „Generowana” w definicji kolumn (tabela zwykła i tymczasowa): `GENERATED ALWAYS / BY DEFAULT [ON NULL] AS IDENTITY` z opcjami `START WITH`/`INCREMENT BY` oraz `GENERATED ALWAYS AS (wyrażenie) VIRTUAL`; wśród wartości domyślnych `DEFAULT ON NULL`, `SYSTIMESTAMP`, `SYS_GUID()`. Opcje z Oracle 12c pokazywane tylko dla baz 12c i nowszych.
- **Automatyczna aktualizacja** — w oknie „Dostępne są aktualizacje” przycisk „Pobierz” proponuje „Zainstaluj automatycznie”: Orbada pobiera nową wersję z GitHuba, sprawdza sumę kontrolną SHA-256 i po zamknięciu podmienia pliki (poprzednia wersja zostaje w katalogu `update\backup`, profil użytkownika bez zmian), a potem uruchamia się ponownie. Działa od tej wersji na kolejne; gdy katalog programu nie jest zapisywalny, jak dotąd otwiera stronę pobierania.
- **Procedury PostgreSQL** — nowy widok „Procedury” (PostgreSQL 11+) z zakładkami źródła, argumentów, uprawnień, szczegółów i zależności; lista funkcji nie pokazuje już procedur. Widok dodaje się z menu widoków, jeśli zapisana perspektywa go nie ma.
- **Kreatory PostgreSQL** — w menu „Akcje” list tabel, kolumn, ograniczeń, indeksów, widoków, funkcji, procedur, wyzwalaczy, sekwencji, typów i schematów oraz w nowym menu „PostgreSQL → Kreatory”: tworzenie tabel (z kolumnami `GENERATED … AS IDENTITY` i `GENERATED ALWAYS AS (…) STORED/VIRTUAL`), widoków (też zmaterializowanych, z `REFRESH`), indeksów, ograniczeń, funkcji, procedur, wyzwalaczy, sekwencji, typów, domen i schematów, a także zmiana kolumn, nazw i schematu, kopia tabeli, `TRUNCATE`, `LOCK`, wywołanie funkcji/procedury z wynikiem, `GRANT`/`REVOKE` i usuwanie obiektów. Przed wykonaniem widać kod SQL. Bez autocommit kreator działa w `SAVEPOINT`, a zmiany zatwierdza się jak polecenia z edytora. W menu „PostgreSQL” także parametry sesji (`search_path`, poziom izolacji, `statement_timeout`, `lock_timeout`, `work_mem`, ostrzeżenia PL/pgSQL i inne).

### Zmiany

- **Nowy sterownik PostgreSQL** — „PostgreSQL (pgjdbc 42)” (pgjdbc 42.7.14) łączy się z PostgreSQL 14–18 i logowaniem SCRAM-SHA-256; stary sterownik 9.3 kończył się błędem „Uwierzytelnienie typu 10 nie jest obsługiwane”.
- **Wtyczka PostgreSQL działa z PostgreSQL 12–18** — szczegóły tabel i widoków, zależności obiektów, ograniczenia i rozmiar tabel obcych nie kończą się już błędem kolumny lub funkcji katalogu usuniętej w nowszych wersjach.
- **Zaktualizowane biblioteki** — log4j 1.2.15 zastąpiony przez reload4j 1.2.27, BeanShell 2.0b4 przez 2.0b6, iText 2.1.3 przez OpenPDF 1.3.43 (eksport do PDF); usuwa znane podatności starych wersji. Przy rozpakowaniu nowej wersji na starą stare pliki są usuwane przy starcie.
- **Bez wewnętrznego API JDK** — `orbada.bat` nie potrzebuje już opcji `--add-exports` i uruchamia się także na Javie 8.

### Poprawki błędów

- Na JDK 26 i nowszych rozwijanie menu w wyglądzie JGoodies (a także Substance) kończyło się błędem `NoClassDefFoundError: javax/swing/JApplet` — cień pod menu jest wtedy wyłączany.
- **Zawieszanie się okna przy pracy z Oracle** — program przestawał odpowiadać na czas operacji w bazie, przy kilku połączeniach i wolnym łączu nawet na minuty: podczas wczytywania listy obiektów (np. po podłączeniu kolejnej bazy), wczytywania źródła pakietu, procedury czy widoku, wypełniania list w kreatorach i zapisu (kompilacji) obiektu. Teraz te operacje działają w tle: lista i źródło wczytują się bez blokowania okna, a gdy kompilacja albo wypełnianie listy trwa dłużej niż chwilę, pojawia się okno „Wykonywanie…” z przyciskiem „Przerwij” (np. kompilacja pakietu używanego przez inną sesję).

## ORBADA 1.3.1 „Fuerteventura” (2026-11-01)

Pierwsze wydanie po 1.2.6 beta (2019) — numer 1.3.1 oddziela je od ostatniego wydania autora. Autor Orbady, Andrzej Kałuża, nie planuje wznawiać projektu — rozwój
przejmujemy jako kontynuację na tej samej licencji **GNU GPL v3** (informacje o autorze oryginału zostają w programie
i w dokumentacji).

### Nowości

- **Logowanie przez TNS_NAMES** — nowy sterownik „Oracle TNS (ojdbc11)” i przycisk „TNS…” w oknie połączenia: lista aliasów z `tnsnames.ora` (z `TNS_ADMIN`, `ORACLE_HOME\network\admin` albo wskazanego katalogu, z obsługą `IFILE`), wyszukiwanie, podgląd hosta/portu/usługi.
- **Logowanie walletem Oracle** — przycisk „Wallet…” w oknie połączenia: wskazanie katalogu Oracle Wallet (`cwallet.sso`), m.in. dla Autonomous Database (aliasy z `tnsnames.ora` walleta) i logowania bez hasła (Secure External Password Store).
- **Sterownik Oracle JDBC 23.26 (ojdbc11)** — nowy sterownik „Oracle (ojdbc11)” do Oracle 12c–26ai; stare `ojdbc6`/`ojdbc14` nowe bazy odrzucają (ORA-28040).

- **Oracle Scheduler** — nowy widok „Scheduler (DBMS_SCHEDULER)”: joby, programy, harmonogramy, chainy (kroki, reguły, działające), okna, grupy, klasy jobów i historia uruchomień; szczegóły w zakładkach (argumenty, historia, log, „używane przez”); uruchamianie, zatrzymywanie, włączanie, wyłączanie i usuwanie.
- **Kreatory schedulera** — przycisk „Nowy...” tworzy job, program, harmonogram albo chain krok po kroku; kalendarz składa się z pól (np. „co tydzień, pn i pt, 8:00 i 16:00”) z podglądem najbliższych uruchomień, a przed utworzeniem widać i można poprawić wygenerowany kod PL/SQL. Kreatory są też w menu **Kreatory → Scheduler**.
- Kolumny typu **`INTERVAL`** (np. czasy trwania) wyświetlane czytelnie zamiast śmieci.

### Zmiany

- **Lista obiektów ładowana w tle** — tabele, pakiety i inne obiekty są pobierane na osobnym wątku, paczkami i z widocznym postępem; aplikacja nie zawiesza się przy dużych bazach i nie kończy się brakiem pamięci przy tysiącach obiektów. Podczas pobierania na liście widać postęp; Esc albo kliknięcie przerywa (pobrana część zostaje).
- **Działa na współczesnej Javie** — zbudowana ze źródeł i sprawdzona na JDK 27 (kod zgodny z Javą 8); usunięte
  odwołania biblioteki bazodanowej do wewnętrznych klas JDK (`sun.misc.BASE64*`, `sun.security.util.BitArray`).
- Nowy obraz powitalny wersji **Fuerteventura**; okno „O programie” pokazuje autorów (Andrzej Kałuża 2007–2019,
  Norbert Przybyła 2025–2026) i adres projektu https://github.com/woolfik/orbada.
- **Projekt na GitHubie** zamiast SourceForge autora: strona domowa i „Oceń projekt (gwiazdka na GitHubie)” w menu Pomoc; informacje o aktualizacjach i „Ostatnie zmiany” z pliku `changes.json` w repozytorium, „Pobierz” prowadzi do wydań na GitHubie; zgłoszenia błędów, problemów i propozycji otwierają nowe zgłoszenie w GitHub Issues z wypełnioną treścią (bez identyfikatora użytkownika). „Dokumentacja na dysku” otwiera lokalną dokumentację. Pozycja „Wesprzyj projekt” wróci z GitHub Sponsors.
- **Szybsza lista pakietów Oracle** — status DEBUG pobierany jednym zapytaniem dla całego schematu; przy 3000 pakietów ok. 6× szybciej.

### Poprawki błędów

- Kolumny **`LONG RAW`** i **`BLOB`** wyświetlane szesnastkowo jak `RAW` (dotąd bajty jako tekst); duże wartości skrócone z podanym rozmiarem.
- **`NCLOB`**: tabela z kolumną `NCLOB` w ogóle się nie otwierała (ORA-17023), a edycja gubiła wartość — poprawione.
- Sprawdzony odczyt i zapis `BLOB`, `CLOB`, `NCLOB`, `LONG`, `LONG RAW` również dla dużych wartości.
