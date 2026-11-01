# ORBADA — informacje o wydaniach

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
