# ORBADA

Narzędzie do pracy z bazami danych napisane w Javie (Swing): edytor SQL, przeglądarka i edytor obiektów schematu,
wyniki zapytań w siatce z edycją danych, eksport i import, wtyczki dla Oracle, PostgreSQL, MySQL, Firebird, SQLite,
HSQLDB, Derby i innych baz JDBC. Najwięcej funkcji ma obsługa Oracle.

To kontynuacja programu [Orbada](https://sourceforge.net/projects/orbada/) Andrzeja Kałuży (2007–2019). Autor nie
wznawia projektu — rozwój od 2025 prowadzi Norbert Przybyła.

> **English:** ORBADA is a Java/Swing database tool (SQL editor, schema browser, data grid, Oracle-focused plugins).
> It continues Orbada by Andrzej Kałuża (2007–2019), maintained since 2025 by Norbert Przybyła under the same
> GNU GPL v3 license. The UI is mainly in Polish.

## Wersja 1.3.1 „Fuerteventura”

Pierwsze wydanie po 1.2.6 beta autora, m.in.:

- logowanie do Oracle przez alias z `tnsnames.ora` i przez Oracle Wallet, sterownik ojdbc11 (Oracle 12c–26ai),
- widok **Oracle Scheduler** i kreatory jobów, programów, harmonogramów i chainów,
- listy obiektów ładowane w tle — bez zawieszania przy dużych schematach,
- poprawki `LONG RAW`, `BLOB`, `NCLOB` i kolumn `INTERVAL`,
- działa na współczesnej Javie.

Wszystkie zmiany: [RELEASE_NOTES.md](RELEASE_NOTES.md).

## Pobieranie

Gotowa paczka jest w [wydaniach](https://github.com/woolfik/orbada/releases/latest): rozpakuj `orbada-1.3.1.zip`
i uruchom `orbada\orbada.bat`. Wymagana Java 9 lub nowsza.

Błędy i propozycje: [Issues](https://github.com/woolfik/orbada/issues) (także z menu programu).

## Licencja

ORBADA jest wolnym oprogramowaniem na licencji **GNU General Public License v3** — tekst w [LICENSE](LICENSE).
Copyright © 2007–2019 Andrzej Kałuża, © 2025–2026 Norbert Przybyła.

Dołączone biblioteki zewnętrzne mają własne licencje: `dist-1.2.6/orbada/doc/licences/` i
`dist-1.2.6/orbada/doc/3rdparty.txt`.
