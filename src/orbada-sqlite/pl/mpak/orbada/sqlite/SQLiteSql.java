package pl.mpak.orbada.sqlite;

import java.util.ArrayList;
import java.util.List;
import pl.mpak.usedb.core.Database;
import pl.mpak.usedb.core.Query;
import pl.mpak.util.StringUtil;

/**
 * ORBADA 1.3.8: zapytania nowych widokow i zakladek SQLite (indeksy, obiekty, AUTOINCREMENT, pragmy, funkcje,
 * rozmiary, szczegoly tabeli, powiazania). Wymagaja nowego sterownika (sqlite-jdbc 3.x, SQLite 3.16+ - funkcje
 * pragma_*). Nazwy wstawiane jako literaly i identyfikatory w cudzyslowie (bez parametrow :nazwa - dwukropek w nazwie).
 * Kolumny wynikow: jak w zakladkach (QueryTableColumn) i filtrach (SqlFilter - zapytanie opakowane w podzapytanie).
 */
public class SQLiteSql {

  /** Identyfikator w cudzyslowie (zawsze - nazwy SQLite bywaja slowami kluczowymi albo maja spacje) */
  public static String q(String name) {
    return "\"" + (name == null ? "" : name.replace("\"", "\"\"")) + "\"";
  }

  /** Identyfikator: prosty bez cudzyslowu (czytelniejszy podglad SQL), inny w cudzyslowie */
  public static String ident(String name) {
    if (name != null && name.matches("[A-Za-z_][A-Za-z0-9_]*") && !isKeyword(name)) {
      return name;
    }
    return q(name);
  }

  /** Nazwa obiektu z baza: main pomijane (domyslna), inne bazy (ATTACH, temp) jako prefiks */
  public static String name(String database, String object) {
    if (StringUtil.isEmpty(database) || "main".equalsIgnoreCase(database)) {
      return ident(object);
    }
    return ident(database) + "." + ident(object);
  }

  /** Literal tekstowy */
  public static String lit(String s) {
    return s == null ? "NULL" : "'" + s.replace("'", "''") + "'";
  }

  private static String db(String database) {
    return StringUtil.isEmpty(database) ? "main" : database;
  }

  /** Tabela schematu bazy (sqlite_master dziala takze w starszych wersjach niz sqlite_schema) */
  private static String master(String database) {
    return q(db(database)) + ".sqlite_master";
  }

  private static String where(String sql, String filter) {
    if (StringUtil.isEmpty(filter)) {
      return sql;
    }
    return "select * from (\n" + sql + "\n) where " + filter;
  }

  private final static String[] KEYWORDS = {
    "ABORT", "ACTION", "ADD", "AFTER", "ALL", "ALTER", "ALWAYS", "ANALYZE", "AND", "AS", "ASC", "ATTACH", "AUTOINCREMENT",
    "BEFORE", "BEGIN", "BETWEEN", "BY", "CASCADE", "CASE", "CAST", "CHECK", "COLLATE", "COLUMN", "COMMIT", "CONFLICT",
    "CONSTRAINT", "CREATE", "CROSS", "CURRENT", "CURRENT_DATE", "CURRENT_TIME", "CURRENT_TIMESTAMP", "DATABASE",
    "DEFAULT", "DEFERRABLE", "DEFERRED", "DELETE", "DESC", "DETACH", "DISTINCT", "DO", "DROP", "EACH", "ELSE", "END",
    "ESCAPE", "EXCEPT", "EXCLUDE", "EXCLUSIVE", "EXISTS", "EXPLAIN", "FAIL", "FILTER", "FIRST", "FOLLOWING", "FOR",
    "FOREIGN", "FROM", "FULL", "GENERATED", "GLOB", "GROUP", "GROUPS", "HAVING", "IF", "IGNORE", "IMMEDIATE", "IN",
    "INDEX", "INDEXED", "INITIALLY", "INNER", "INSERT", "INSTEAD", "INTERSECT", "INTO", "IS", "ISNULL", "JOIN", "KEY",
    "LAST", "LEFT", "LIKE", "LIMIT", "MATCH", "MATERIALIZED", "NATURAL", "NO", "NOT", "NOTHING", "NOTNULL", "NULL",
    "NULLS", "OF", "OFFSET", "ON", "OR", "ORDER", "OTHERS", "OUTER", "OVER", "PARTITION", "PLAN", "PRAGMA", "PRECEDING",
    "PRIMARY", "QUERY", "RAISE", "RANGE", "RECURSIVE", "REFERENCES", "REGEXP", "REINDEX", "RELEASE", "RENAME",
    "REPLACE", "RESTRICT", "RETURNING", "RIGHT", "ROLLBACK", "ROW", "ROWS", "SAVEPOINT", "SELECT", "SET", "TABLE",
    "TEMP", "TEMPORARY", "THEN", "TIES", "TO", "TRANSACTION", "TRIGGER", "UNBOUNDED", "UNION", "UNIQUE", "UPDATE",
    "USING", "VACUUM", "VALUES", "VIEW", "VIRTUAL", "WHEN", "WHERE", "WINDOW", "WITH", "WITHOUT"};

  public static boolean isKeyword(String name) {
    for (String k : KEYWORDS) {
      if (k.equalsIgnoreCase(name)) {
        return true;
      }
    }
    return false;
  }

  // ---------------------------------------------------------------- pomocnicze

  /** Pierwsza kolumna pierwszego wiersza albo null (takze przy bledzie, np. brak tabeli) */
  public static String one(Database database, String sql) {
    Query query = database.createQuery();
    try {
      query.setParamCheck(false);
      query.setSqlText(sql);
      query.open();
      return query.eof() ? null : query.getField(0).getString();
    } catch (Exception ex) {
      log(sql, ex);
      return null;
    } finally {
      query.close();
    }
  }

  /** Pierwsza kolumna wszystkich wierszy (pusta lista przy bledzie) */
  public static List<String> list(Database database, String sql) {
    List<String> result = new ArrayList<String>();
    Query query = database.createQuery();
    try {
      query.setParamCheck(false);
      query.setSqlText(sql);
      query.open();
      while (!query.eof()) {
        result.add(query.getField(0).getString());
        query.next();
      }
    } catch (Exception ex) {
      // brak tabeli albo funkcji w danej wersji SQLite
      log(sql, ex);
    } finally {
      query.close();
    }
    return result;
  }

  /** Bledy zapytan pomocniczych (brak dbstat, starsza wersja SQLite) - tylko w logu diagnostycznym */
  private static void log(String sql, Exception ex) {
    java.util.logging.Logger log = java.util.logging.Logger.getLogger("orbada-sqlite");
    if (log.isLoggable(java.util.logging.Level.FINE)) {
      log.fine(ex.getMessage() + "\n" + sql);
    }
  }

  /** Czy w bazie jest obiekt danego typu (np. tabela sqlite_sequence, sqlite_stat1) */
  public static boolean exists(Database database, String dbName, String type, String name) {
    return one(database, "select 1 from " + master(dbName) + " where type = " + lit(type) + " and name = " + lit(name)) != null;
  }

  /** Wersja SQLite sterownika, np. 3054001 (0 - nieznana; bez polaczenia nie zapamietywana) */
  public static int version(Database database) {
    String v = database.getUserProperties().getProperty("sqlite-version-number");
    if (v == null) {
      if (!database.isConnected()) {
        return 0;
      }
      v = one(database, "select sqlite_version()");
      int n = 0;
      if (v != null) {
        String[] p = v.split("\\.");
        try {
          n = Integer.parseInt(p[0]) * 1000000 + (p.length > 1 ? Integer.parseInt(p[1]) * 1000 : 0) + (p.length > 2 ? Integer.parseInt(p[2]) : 0);
        } catch (NumberFormatException ex) {
          n = 0;
        }
      }
      v = Integer.toString(n);
      database.getUserProperties().setProperty("sqlite-version-number", v);
    }
    return Integer.parseInt(v);
  }

  /** Funkcje pragma_* (SQLite 3.16) - wymagane przez nowe widoki */
  public static boolean hasPragmaFunctions(Database database) {
    return version(database) >= 3016000;
  }

  /** Lista nazw baz (PRAGMA database_list) */
  public static List<String> databases(Database database) {
    return list(database, "select name from pragma_database_list order by seq");
  }

  // ---------------------------------------------------------------- obiekty (wyszukiwanie)

  /** Wszystkie obiekty schematu: type, name, tbl_name, sql, database - filtr po nazwie, typie, tabeli i tresci */
  public static String getObjects(String database, String filter) {
    return where(
      "select type, name, tbl_name, sql, " + lit(db(database)) + " database\n" +
      "  from " + master(database) + "\n" +
      " order by case type when 'table' then 1 when 'view' then 2 when 'index' then 3 when 'trigger' then 4 else 5 end, name",
      filter);
  }

  // ---------------------------------------------------------------- indeksy

  /**
   * Indeksy z sqlite_master (CREATE INDEX i automatyczne dla PRIMARY KEY/UNIQUE; bez klucza tabeli WITHOUT ROWID,
   * ktory jest sama tabela): name, tbl_name, unique, origin (c - CREATE INDEX, u - UNIQUE, pk), partial, columns, sql */
  public static String getIndexes(String database, String filter) {
    String d = lit(db(database));
    return where(
      "select m.name, m.tbl_name, il.\"unique\", il.origin, il.partial,\n" +
      "       (select group_concat(c, ', ') from (select case when ii.cid = -2 then '<expr>' when ii.cid = -1 then 'rowid' else ii.name end c\n" +
      "                                            from pragma_index_info(m.name, " + d + ") ii order by ii.seqno)) columns,\n" +
      "       m.sql, " + d + " database\n" +
      "  from " + master(database) + " m\n" +
      "  join pragma_index_list(m.tbl_name, " + d + ") il on il.name = m.name\n" +
      " where m.type = 'index'\n" +
      " order by m.tbl_name, m.name",
      filter);
  }

  /** Indeksy tabeli (zakladka tabeli) - jak getIndexes */
  public static String getTableIndexes(String database, String table) {
    return getIndexes(database, "tbl_name = " + lit(table));
  }

  /** Kolumny indeksu: seqno, cid, name, desc, coll, key (0 - kolumna dodatkowa: rowid albo PK tabeli WITHOUT ROWID) */
  public static String getIndexColumns(String database, String index) {
    return
      "select seqno, cid, case when cid = -2 then '<expr>' when cid = -1 then 'rowid' else name end name,\n" +
      "       case \"desc\" when 1 then 'DESC' else 'ASC' end \"desc\", coll, key\n" +
      "  from pragma_index_xinfo(" + lit(index) + ", " + lit(db(database)) + ")\n" +
      " order by seqno";
  }

  // ---------------------------------------------------------------- tabele

  /** Kolumny tabeli lub widoku z ukrytymi i generowanymi: cid, name, type, notnull, dflt_value, pk, hidden, generated */
  public static String getColumns(String database, String table) {
    return
      "select cid, name, type, \"notnull\", dflt_value, pk,\n" +
      "       case hidden when 1 then 'HIDDEN' when 2 then 'VIRTUAL' when 3 then 'STORED' else '' end generated\n" +
      "  from pragma_table_xinfo(" + lit(table) + ", " + lit(db(database)) + ")\n" +
      " order by cid";
  }

  /** Klucze obce tabeli: id, seq, table, from, to, on_update, on_delete, match */
  public static String getForeignKeys(String database, String table) {
    return
      "select id, seq, \"table\", \"from\", \"to\", on_update, on_delete, \"match\"\n" +
      "  from pragma_foreign_key_list(" + lit(table) + ", " + lit(db(database)) + ")\n" +
      " order by id, seq";
  }

  /** Tabele z kluczem obcym do tabeli: name (tabela), from, to, on_update, on_delete */
  public static String getReferencedFrom(String database, String table) {
    String d = lit(db(database));
    return
      "select m.name, fk.\"from\", fk.\"to\", fk.on_update, fk.on_delete\n" +
      "  from " + master(database) + " m\n" +
      "  join pragma_foreign_key_list(m.name, " + d + ") fk\n" +
      " where m.type = 'table' and fk.\"table\" = " + lit(table) + " collate nocase\n" +
      " order by m.name, fk.id, fk.seq";
  }

  /**
   * Obiekty uzywajace obiektu (widoki, wyzwalacze, indeksy i tabele z kluczem obcym, ktorych tresc zawiera nazwe).
   * Przyblizenie jak "Uzywany przez" w Oracle - SQLite nie ma slownika zaleznosci; type, name, tbl_name.
   */
  public static String getUsedBy(String database, String object) {
    String needle = object == null ? "" : object.toLowerCase();
    return
      "select type, name, tbl_name\n" +
      "  from " + master(database) + "\n" +
      " where name <> " + lit(object) + " and sql is not null\n" +
      "   and instr(lower(sql), " + lit(needle) + ") > 0\n" +
      "   and not (type = 'index' and tbl_name = " + lit(object) + ")\n" +
      "   and not (type = 'trigger' and tbl_name = " + lit(object) + ")\n" +
      " order by type, name";
  }

  /** Statystyki ANALYZE tabeli (sqlite_stat1): idx, stat; null gdy brak tabeli statystyk */
  public static String getTableStats(Database database, String dbName, String table) {
    if (!exists(database, dbName, "table", "sqlite_stat1")) {
      return "select null idx, null stat where 0";
    }
    return "select idx, stat from " + q(db(dbName)) + ".sqlite_stat1 where tbl = " + lit(table) + " order by idx is not null, idx";
  }

  /**
   * Szczegoly tabeli w jednym wierszu: typ, STRICT, WITHOUT ROWID, liczba kolumn, AUTOINCREMENT (ostatnia wartosc),
   * liczba wierszy wg ANALYZE, strony i bajty (dbstat: tabela + indeksy), indeksy, wyzwalacze, klucze obce.
   */
  public static String getTableDetails(Database database, String dbName, String table) {
    String d = lit(db(dbName));
    String t = lit(table);
    boolean tableList = version(database) >= 3037000;
    boolean seq = exists(database, dbName, "table", "sqlite_sequence");
    boolean stat = exists(database, dbName, "table", "sqlite_stat1");
    boolean dbstat = one(database, "select 1 from pragma_module_list where name = 'dbstat'") != null;
    StringBuilder sb = new StringBuilder("select ");
    if (tableList) {
      // pragma_table_list przyjmuje tylko nazwe tabeli - baza w warunku
      String tl = " from pragma_table_list(" + t + ") where schema = " + d + ")";
      sb.append("(select type").append(tl).append(" type,\n");
      sb.append("       (select case strict when 1 then 'YES' else 'NO' end").append(tl).append(" strict,\n");
      sb.append("       (select case wr when 1 then 'YES' else 'NO' end").append(tl).append(" without_rowid,\n");
    } else {
      sb.append("(select type from ").append(master(dbName)).append(" where name = ").append(t).append(") type,\n");
    }
    sb.append("       (select count(*) from pragma_table_xinfo(").append(t).append(", ").append(d).append(")) columns,\n");
    sb.append("       (select count(*) from pragma_table_xinfo(").append(t).append(", ").append(d).append(") where pk > 0) pk_columns,\n");
    if (seq) {
      sb.append("       (select seq from ").append(q(db(dbName))).append(".sqlite_sequence where name = ").append(t).append(") autoincrement_seq,\n");
    }
    if (stat) {
      sb.append("       (select cast(stat as integer) from ").append(q(db(dbName))).append(".sqlite_stat1 where tbl = ").append(t).append(" and (idx is null or idx = tbl) limit 1) analyzed_rows,\n");
    }
    if (dbstat) {
      sb.append("       (select count(*) from dbstat(").append(d).append(") where name = ").append(t).append(") table_pages,\n");
      sb.append("       (select sum(pgsize) from dbstat(").append(d).append(") where name = ").append(t).append(") table_bytes,\n");
      sb.append("       (select sum(pgsize) from dbstat(").append(d).append(") where name in (select name from ").append(master(dbName))
        .append(" where type = 'index' and tbl_name = ").append(t).append(")) index_bytes,\n");
    }
    sb.append("       (select count(*) from ").append(master(dbName)).append(" where type = 'index' and tbl_name = ").append(t).append(") indexes,\n");
    sb.append("       (select count(*) from ").append(master(dbName)).append(" where type = 'trigger' and tbl_name = ").append(t).append(") triggers,\n");
    sb.append("       (select count(distinct id) from pragma_foreign_key_list(").append(t).append(", ").append(d).append(")) foreign_keys");
    return sb.toString();
  }

  // ---------------------------------------------------------------- AUTOINCREMENT

  /** Liczniki AUTOINCREMENT (sqlite_sequence): name, seq, database; pusto gdy zadna tabela nie ma AUTOINCREMENT */
  public static String getSequences(Database database, String dbName) {
    if (!exists(database, dbName, "table", "sqlite_sequence")) {
      return "select null name, null seq, null database where 0";
    }
    return
      "select s.name, s.seq, " + lit(db(dbName)) + " database\n" +
      "  from " + q(db(dbName)) + ".sqlite_sequence s\n" +
      " order by s.name";
  }

  // ---------------------------------------------------------------- rozmiary (dbstat)

  /** Rozmiar obiektow (dbstat): name, type, tbl_name, pages, bytes, unused, payload, percent; pusto bez modulu dbstat */
  public static String getSizes(Database database, String dbName, String filter) {
    String d = lit(db(dbName));
    if (one(database, "select 1 from pragma_module_list where name = 'dbstat'") == null) {
      return "select null name, null type, null tbl_name, null pages, null bytes, null unused, null payload, null percent where 0";
    }
    return where(
      "select s.name, coalesce(m.type, 'table') type, coalesce(m.tbl_name, s.name) tbl_name, s.pages, s.bytes, s.unused, s.payload,\n" +
      "       round(100.0 * s.bytes / (select sum(pgsize) from dbstat(" + d + ")), 2) percent\n" +
      "  from (select name, count(*) pages, sum(pgsize) bytes, sum(unused) unused, sum(payload) payload\n" +
      "          from dbstat(" + d + ") group by name) s\n" +
      "  left join " + master(dbName) + " m on m.name = s.name\n" +
      " order by s.bytes desc",
      filter);
  }

  // ---------------------------------------------------------------- funkcje

  /** Funkcje SQL dostepne w polaczeniu: name, builtin, type (s - skalarna, a - agregujaca, w - okienkowa), narg, enc, flags */
  public static String getFunctions(String filter) {
    return where(
      "select name, case builtin when 1 then 'YES' else 'NO' end builtin,\n" +
      "       case type when 's' then 'SCALAR' when 'a' then 'AGGREGATE' when 'w' then 'WINDOW' else type end type,\n" +
      "       narg, enc, flags\n" +
      "  from pragma_function_list\n" +
      " order by name, narg",
      filter);
  }

  /** Moduly tabel wirtualnych (fts5, rtree, dbstat...): name */
  public static String getModules() {
    return "select name from pragma_module_list order by name";
  }

  /** Sortowania (COLLATE): seq, name */
  public static String getCollations() {
    return "select seq, name from pragma_collation_list order by name";
  }

  /** Opcje kompilacji biblioteki SQLite: compile_options */
  public static String getCompileOptions() {
    return "select compile_options from pragma_compile_options order by 1";
  }

  // ---------------------------------------------------------------- pragmy

  /** Pragma: nazwa, czy dotyczy pojedynczej bazy (prefiks schematu), czy da sie ustawic, mozliwe wartosci */
  public static class Pragma {
    public final String name;
    public final boolean schema;
    public final boolean settable;
    public final String[] values;

    Pragma(String name, boolean schema, boolean settable, String... values) {
      this.name = name;
      this.schema = schema;
      this.settable = settable;
      this.values = values;
    }
  }

  private final static String[] ON_OFF = {"ON", "OFF"};

  /** Pragmy z biezaca wartoscia (bez pragm tylko do zapisu, jak case_sensitive_like, i bez schema_version) */
  public final static Pragma[] PRAGMAS = {
    new Pragma("application_id", true, true),
    new Pragma("auto_vacuum", true, true, "NONE", "FULL", "INCREMENTAL"),
    new Pragma("automatic_index", false, true, ON_OFF),
    new Pragma("busy_timeout", false, true),
    new Pragma("cache_size", true, true),
    new Pragma("cache_spill", true, true, ON_OFF),
    new Pragma("cell_size_check", false, true, ON_OFF),
    new Pragma("checkpoint_fullfsync", false, true, ON_OFF),
    new Pragma("data_version", true, false),
    new Pragma("defer_foreign_keys", false, true, ON_OFF),
    new Pragma("encoding", false, false),
    new Pragma("foreign_keys", false, true, ON_OFF),
    new Pragma("freelist_count", true, false),
    new Pragma("fullfsync", false, true, ON_OFF),
    new Pragma("hard_heap_limit", false, true),
    new Pragma("journal_mode", true, true, "DELETE", "TRUNCATE", "PERSIST", "MEMORY", "WAL", "OFF"),
    new Pragma("journal_size_limit", true, true),
    new Pragma("legacy_alter_table", false, true, ON_OFF),
    new Pragma("locking_mode", true, true, "NORMAL", "EXCLUSIVE"),
    new Pragma("max_page_count", true, true),
    new Pragma("mmap_size", true, true),
    new Pragma("page_count", true, false),
    new Pragma("page_size", true, true, "512", "1024", "2048", "4096", "8192", "16384", "32768", "65536"),
    new Pragma("query_only", false, true, ON_OFF),
    new Pragma("read_uncommitted", false, true, ON_OFF),
    new Pragma("recursive_triggers", false, true, ON_OFF),
    new Pragma("reverse_unordered_selects", false, true, ON_OFF),
    new Pragma("secure_delete", true, true, "ON", "OFF", "FAST"),
    new Pragma("soft_heap_limit", false, true),
    new Pragma("synchronous", true, true, "OFF", "NORMAL", "FULL", "EXTRA"),
    new Pragma("temp_store", false, true, "DEFAULT", "FILE", "MEMORY"),
    new Pragma("threads", false, true),
    new Pragma("trusted_schema", false, true, ON_OFF),
    new Pragma("user_version", true, true),
    new Pragma("wal_autocheckpoint", false, true)
  };

  public static Pragma pragma(String name) {
    for (Pragma p : PRAGMAS) {
      if (p.name.equalsIgnoreCase(name)) {
        return p;
      }
    }
    return null;
  }

  /** Odczyt pragmy: "PRAGMA [baza.]nazwa" */
  public static String readPragma(String dbName, Pragma p) {
    return "PRAGMA " + (p.schema ? ident(db(dbName)) + "." : "") + p.name;
  }

  /** Ustawienie pragmy; wartosc liczbowa lub slowo kluczowe bez zmian, inna jako literal */
  public static String setPragma(String dbName, Pragma p, String value) {
    String v = value == null ? "" : value.trim();
    if (!v.matches("-?[0-9]+|[A-Za-z_]+")) {
      v = lit(v);
    }
    return "PRAGMA " + (p.schema ? ident(db(dbName)) + "." : "") + p.name + " = " + v;
  }

  /** Czytelna wartosc pragm wyliczeniowych (0/1/2 -> nazwy) */
  public static String describe(Pragma p, String value) {
    if (value == null) {
      return null;
    }
    String[][] map = null;
    if ("auto_vacuum".equals(p.name)) {
      map = new String[][] {{"0", "NONE"}, {"1", "FULL"}, {"2", "INCREMENTAL"}};
    } else if ("synchronous".equals(p.name)) {
      map = new String[][] {{"0", "OFF"}, {"1", "NORMAL"}, {"2", "FULL"}, {"3", "EXTRA"}};
    } else if ("temp_store".equals(p.name)) {
      map = new String[][] {{"0", "DEFAULT"}, {"1", "FILE"}, {"2", "MEMORY"}};
    } else if ("secure_delete".equals(p.name)) {
      map = new String[][] {{"0", "OFF"}, {"1", "ON"}, {"2", "FAST"}};
    } else if (p.values == ON_OFF) {
      map = new String[][] {{"0", "OFF"}, {"1", "ON"}};
    }
    if (map != null) {
      for (String[] m : map) {
        if (m[0].equals(value)) {
          return m[1];
        }
      }
    }
    return value.toUpperCase().equals(value.toLowerCase()) ? value : value.toUpperCase();
  }

  /**
   * Lista pragm z wartosciami jako zapytanie (wartosci odczytane teraz - pragmy nie da sie zlaczyc w jednym SELECT
   * dla wybranej bazy): name, value, scope (DATABASE/CONNECTION), settable, description, database.
   */
  public static String getPragmas(Database database, String dbName, java.util.Map<String, String> descriptions, String filter) {
    StringBuilder sb = new StringBuilder();
    for (Pragma p : PRAGMAS) {
      String value = one(database, readPragma(dbName, p));
      if (value == null && one(database, "select 1 from pragma_pragma_list where name = " + lit(p.name)) == null) {
        continue; // pragma nieznana tej wersji SQLite
      }
      if (sb.length() > 0) {
        sb.append("union all\n");
      }
      sb.append("select ").append(lit(p.name)).append(" name, ").append(lit(describe(p, value))).append(" value, ")
        .append(lit(p.schema ? "DATABASE" : "CONNECTION")).append(" scope, ").append(lit(p.settable ? "YES" : "NO")).append(" settable, ")
        .append(lit(descriptions == null ? null : descriptions.get(p.name))).append(" description, ")
        .append(lit(db(dbName))).append(" database\n");
    }
    if (sb.length() == 0) {
      return "select null name, null value, null scope, null settable, null description, null database where 0";
    }
    return where("select * from (\n" + sb + ") order by name", filter);
  }

  // ---------------------------------------------------------------- DDL

  /** Zrodlo obiektu z sqlite_master zakonczone srednikiem (null gdy brak, np. indeks automatyczny) */
  public static String source(Database database, String dbName, String name) {
    String sql = one(database, "select sql from " + master(dbName) + " where name = " + lit(name));
    return sql == null ? null : sql.trim() + ";";
  }

  /** Skrypt tabeli: CREATE TABLE, indeksy i wyzwalacze (kolejnosc jak w sqlite_master) */
  public static String tableScript(Database database, String dbName, String table) {
    StringBuilder sb = new StringBuilder();
    String t = source(database, dbName, table);
    if (t != null) {
      sb.append(t).append("\n");
    }
    for (String s : list(database, "select sql from " + master(dbName) + " where tbl_name = " + lit(table) +
        " and type in ('index', 'trigger') and sql is not null order by type, name")) {
      sb.append("\n").append(s.trim()).append(";\n");
    }
    return sb.toString();
  }

}
