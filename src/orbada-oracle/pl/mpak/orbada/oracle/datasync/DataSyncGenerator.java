package pl.mpak.orbada.oracle.datasync;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import pl.mpak.orbada.oracle.OrbadaOraclePlugin;
import pl.mpak.util.StringManager;
import pl.mpak.util.StringManagerFactory;

/**
 * ORBADA 1.3.8: "Wyrownaj dane" - z zapytania (zwykle select * from tabela@dblink where ...) blok PL/SQL, ktory
 * w petli po wierszach zapytania robi MERGE do tej samej tabeli w biezacej bazie.
 * <ul>
 * <li>PK z IDENTITY albo z wartoscia domyslna z sekwencji - pomijany w DML; PK nazwy XXX_ID z sekwencja XXX_SEQ
 * (takze TABELA_SEQ, SEQ_TABELA) - przy INSERT XXX_SEQ.nextval. Wtedy dopasowanie (ON) po kluczu naturalnym
 * (UNIQUE: ograniczenie albo indeks unikalny), bo ID na produkcji sa inne.</li>
 * <li>Klucz obcy do tabeli z takim PK: funkcja map_* bierze ze zrodla klucz naturalny wiersza nadrzednego i szuka
 * ID w tej bazie; gdy brak - procedura sync_* kopiuje najpierw wiersz nadrzedny (rekurencyjnie, z jego kluczami
 * obcymi). Do FK po kluczu naturalnym map_* tylko sprawdza istnienie i w razie braku kopiuje wiersz.</li>
 * <li>Cykle kluczy obcych (np. dzial.kierownik -> pracownik.dzial) przerywa zbior g_busy: wiersz w trakcie
 * kopiowania nie jest kopiowany drugi raz, FK do niego zostaje pusty (kolejne uruchomienie go uzupelni).</li>
 * </ul>
 * Struktura czytana ze slownika biezacej bazy (zalozenie: zrodlo ma te same tabele). Bez dblinka i z tego samego
 * schematu (tabela A do B) klucze obce przepisywane bez zmian.
 */
public class DataSyncGenerator {

  private final static StringManager stringManager = StringManagerFactory.getStringManager(OrbadaOraclePlugin.class);

  /** Tekst i18n; z argumentami przez MessageFormat */
  static String msg(String key, Object... args) {
    return args.length == 0 ? stringManager.getString(key) : stringManager.getString(key, args);
  }

  /** Maksymalna liczba tabel nadrzednych wczytywanych przez klucze obce */
  public final static int MAX_TABLES = 200;
  /** Dlugosc nazw zmiennych i podprogramow (Oracle przed 12.2: 30) */
  private final static int MAX_NAME = 30;

  private final static Set<String> RESERVED = new HashSet<String>(Arrays.asList(
    "ACCESS", "ADD", "ALL", "ALTER", "AND", "ANY", "AS", "ASC", "AUDIT", "BETWEEN", "BY", "CHAR", "CHECK", "CLUSTER",
    "COLUMN", "COMMENT", "COMPRESS", "CONNECT", "CREATE", "CURRENT", "DATE", "DECIMAL", "DEFAULT", "DELETE", "DESC",
    "DISTINCT", "DROP", "ELSE", "EXCLUSIVE", "EXISTS", "FILE", "FLOAT", "FOR", "FROM", "GRANT", "GROUP", "HAVING",
    "IDENTIFIED", "IMMEDIATE", "IN", "INCREMENT", "INDEX", "INITIAL", "INSERT", "INTEGER", "INTERSECT", "INTO", "IS",
    "LEVEL", "LIKE", "LOCK", "LONG", "MAXEXTENTS", "MINUS", "MLSLABEL", "MODE", "MODIFY", "NOAUDIT", "NOCOMPRESS",
    "NOT", "NOWAIT", "NULL", "NUMBER", "OF", "OFFLINE", "ON", "ONLINE", "OPTION", "OR", "ORDER", "PCTFREE", "PRIOR",
    "PUBLIC", "RAW", "RENAME", "RESOURCE", "REVOKE", "ROW", "ROWID", "ROWNUM", "ROWS", "SELECT", "SESSION", "SET",
    "SHARE", "SIZE", "SMALLINT", "START", "SUCCESSFUL", "SYNONYM", "SYSDATE", "TABLE", "THEN", "TO", "TRIGGER", "UID",
    "UNION", "UNIQUE", "UPDATE", "USER", "VALIDATE", "VALUES", "VARCHAR", "VARCHAR2", "VIEW", "WHENEVER", "WHERE",
    "WITH", "TYPE", "RESULT", "BEGIN", "END", "LOOP", "EXCEPTION", "DECLARE", "RECORD", "TIMESTAMP", "INTERVAL"));

  // ---------------------------------------------------------------- model

  /** Tabela zrodlowa rozpoznana w zapytaniu (pierwsze FROM poza nawiasami) */
  public static class Source {
    public String owner;
    public String table;
    public String link;
  }

  public static class Col {
    public String name;
    public String type;
    public boolean nullable;
    /** IDENTITY (12c+) */
    public boolean identity;
    /** DEFAULT sekwencja.nextval */
    public boolean seqDefault;
    public boolean virtual;

    boolean isLong() {
      return "LONG".equals(type) || "LONG RAW".equals(type);
    }

    boolean isLob() {
      return type != null && (type.endsWith("LOB") || "BFILE".equals(type));
    }
  }

  public static class Key {
    public String name;
    public List<String> cols = new ArrayList<String>();
  }

  public static class Fk {
    public String name;
    public List<String> cols = new ArrayList<String>();
    public String rOwner;
    public String rTable;
    public List<String> rCols = new ArrayList<String>();
  }

  public static class Table {
    public String owner;
    public String name;
    public final List<Col> cols = new ArrayList<Col>();
    public final Map<String, Col> byName = new HashMap<String, Col>();
    public Key pk;
    public final List<Key> uniques = new ArrayList<Key>();
    public final List<Fk> fks = new ArrayList<Fk>();
    /** Sekwencja klucza glownego (owner.nazwa w postaci do SQL) wg nazwy XXX_ID -> XXX_SEQ */
    public String seq;

    public String id() {
      return owner + "." + name;
    }

    /** PK jednokolumnowy nadawany przez baze (IDENTITY, DEFAULT nextval) albo z sekwencji wg nazwy */
    public boolean isSurrogate() {
      if (pk == null || pk.cols.size() != 1) {
        return false;
      }
      Col c = byName.get(pk.cols.get(0));
      return c != null && (c.identity || c.seqDefault || seq != null);
    }

    public List<String> colNames() {
      List<String> result = new ArrayList<String>();
      for (Col c : cols) {
        result.add(c.name);
      }
      return result;
    }
  }

  public static class Model {
    public String query;
    public Source source;
    public String currentSchema;
    public Table target;
    /** Kolumny zwracane przez zapytanie */
    public final List<String> queryCols = new ArrayList<String>();
    /** Tabele wg owner.nazwa (docelowa i nadrzedne) */
    public final Map<String, Table> tables = new LinkedHashMap<String, Table>();
    /** Zrodlo w innej bazie (dblink) albo innym schemacie - ID w kluczach obcych trzeba przeliczac */
    public boolean remote;

    /** Kolumny tabeli docelowej obecne w zapytaniu */
    public List<String> availableCols() {
      List<String> result = new ArrayList<String>();
      for (Col c : target.cols) {
        if (queryCols.contains(c.name)) {
          result.add(c.name);
        }
      }
      return result;
    }

    /** Kolumny mozliwe w kluczu dopasowania (ON): dostepne bez LOB, LONG i wirtualnych */
    public List<String> keyCols() {
      List<String> result = new ArrayList<String>();
      for (String name : availableCols()) {
        Col c = target.byName.get(name);
        if (!c.virtual && !c.isLong() && !c.isLob()) {
          result.add(name);
        }
      }
      return result;
    }

    public List<String> defaultKey() {
      return DataSyncGenerator.defaultKey(target, new HashSet<String>(keyCols()));
    }
  }

  public static class Options {
    /** Klucz dopasowania tabeli docelowej (null = domyslny) */
    public List<String> key;
    public boolean update = true;
    public boolean syncParents = true;
  }

  // ---------------------------------------------------------------- parsowanie zapytania

  /** Zapytanie bez koncowego ";" i "/" */
  public static String cleanQuery(String sql) {
    String s = sql == null ? "" : sql.trim();
    boolean changed = true;
    while (changed && s.length() > 0) {
      changed = false;
      if (s.endsWith(";") || s.endsWith("/")) {
        s = s.substring(0, s.length() - 1).trim();
        changed = true;
      }
    }
    return s;
  }

  /** Pierwsza tabela po FROM poza nawiasami: [wlasciciel.]tabela[@dblink]; null, gdy nie rozpoznano */
  public static Source parseSource(String sql) {
    int depth = 0;
    int i = 0;
    int n = sql.length();
    while (i < n) {
      char ch = sql.charAt(i);
      if (ch == '-' && i + 1 < n && sql.charAt(i + 1) == '-') {
        while (i < n && sql.charAt(i) != '\n') i++;
      } else if (ch == '/' && i + 1 < n && sql.charAt(i + 1) == '*') {
        int e = sql.indexOf("*/", i + 2);
        i = e < 0 ? n : e + 2;
      } else if (ch == '\'') {
        i = skipQuoted(sql, i, '\'');
      } else if (ch == '"') {
        i = skipQuoted(sql, i, '"');
      } else if (ch == '(') {
        depth++;
        i++;
      } else if (ch == ')') {
        depth--;
        i++;
      } else if (isIdentStart(ch)) {
        int s = i;
        while (i < n && isIdentPart(sql.charAt(i))) i++;
        if (depth == 0 && "FROM".equalsIgnoreCase(sql.substring(s, i))) {
          return parseTableRef(sql, i);
        }
      } else {
        i++;
      }
    }
    return null;
  }

  private static Source parseTableRef(String sql, int i) {
    int[] pos = {skipWs(sql, i)};
    String first = readIdent(sql, pos);
    if (first == null) {
      return null;
    }
    Source src = new Source();
    src.table = first;
    int p = skipWs(sql, pos[0]);
    if (p < sql.length() && sql.charAt(p) == '.') {
      pos[0] = skipWs(sql, p + 1);
      String second = readIdent(sql, pos);
      if (second == null) {
        return null;
      }
      src.owner = first;
      src.table = second;
      p = skipWs(sql, pos[0]);
    }
    if (p < sql.length() && sql.charAt(p) == '@') {
      p = skipWs(sql, p + 1);
      int s = p;
      if (p < sql.length() && sql.charAt(p) == '"') {
        p = skipQuoted(sql, p, '"');
        src.link = sql.substring(s, p);
      } else {
        while (p < sql.length() && (isIdentPart(sql.charAt(p)) || sql.charAt(p) == '.' || sql.charAt(p) == '@')) p++;
        src.link = sql.substring(s, p);
      }
      if (src.link.length() == 0) {
        src.link = null;
      }
    }
    return src;
  }

  /** Identyfikator: w cudzyslowie dokladnie, inny wielkimi literami */
  private static String readIdent(String sql, int[] pos) {
    int p = pos[0];
    if (p >= sql.length()) {
      return null;
    }
    if (sql.charAt(p) == '"') {
      int e = skipQuoted(sql, p, '"');
      pos[0] = e;
      return sql.substring(p + 1, e - 1).replace("\"\"", "\"");
    }
    if (!isIdentStart(sql.charAt(p))) {
      return null;
    }
    int s = p;
    while (p < sql.length() && isIdentPart(sql.charAt(p))) p++;
    pos[0] = p;
    return sql.substring(s, p).toUpperCase(Locale.ENGLISH);
  }

  /** Nazwa tabeli wpisana przez uzytkownika: [wlasciciel.]tabela */
  public static String[] parseName(String text) {
    int[] pos = {skipWs(text, 0)};
    String first = readIdent(text, pos);
    if (first == null) {
      return null;
    }
    int p = skipWs(text, pos[0]);
    if (p < text.length() && text.charAt(p) == '.') {
      pos[0] = skipWs(text, p + 1);
      String second = readIdent(text, pos);
      return second == null ? null : new String[] {first, second};
    }
    return new String[] {null, first};
  }

  private static int skipQuoted(String sql, int i, char q) {
    i++;
    while (i < sql.length()) {
      if (sql.charAt(i) == q) {
        if (i + 1 < sql.length() && sql.charAt(i + 1) == q) {
          i += 2;
          continue;
        }
        return i + 1;
      }
      i++;
    }
    return i;
  }

  private static int skipWs(String sql, int i) {
    while (i < sql.length() && Character.isWhitespace(sql.charAt(i))) i++;
    return i;
  }

  private static boolean isIdentStart(char ch) {
    return Character.isLetter(ch);
  }

  private static boolean isIdentPart(char ch) {
    return Character.isLetterOrDigit(ch) || ch == '_' || ch == '$' || ch == '#';
  }

  // ---------------------------------------------------------------- slownik

  /**
   * Wczytanie struktury: tabela docelowa (podana albo ta z zapytania), kolumny zapytania (zapytanie wykonane z
   * warunkiem 1 = 0 - sprawdza tez dblink) i tabele nadrzedne kluczy obcych.
   */
  public static Model load(Connection conn, String sql, String targetName) throws SQLException {
    Model m = new Model();
    m.query = cleanQuery(sql);
    m.source = parseSource(m.query);
    m.currentSchema = one(conn, "select sys_context('USERENV', 'CURRENT_SCHEMA') from dual");
    String owner;
    String name;
    if (targetName != null && targetName.trim().length() > 0) {
      String[] parts = parseName(targetName);
      if (parts == null) {
        throw new IllegalArgumentException(msg("datasync-no-table", targetName));
      }
      owner = parts[0];
      name = parts[1];
    } else if (m.source != null) {
      owner = m.source.owner;
      name = m.source.table;
    } else {
      throw new IllegalArgumentException(msg("datasync-no-source"));
    }
    String[] resolved = resolveTable(conn, owner, name, m.currentSchema);
    if (resolved == null) {
      throw new IllegalArgumentException(msg("datasync-no-table", (owner == null ? "" : owner + ".") + name));
    }
    int major = conn.getMetaData().getDatabaseMajorVersion();
    m.target = loadTable(conn, resolved[0], resolved[1], major);
    m.tables.put(m.target.id(), m.target);
    describe(conn, m);
    if (m.source != null) {
      String srcOwner = m.source.owner == null ? m.currentSchema : m.source.owner;
      m.remote = m.source.link != null || !srcOwner.equals(m.target.owner);
    }
    if (m.remote) {
      loadParents(conn, m, major);
    }
    return m;
  }

  private static void describe(Connection conn, Model m) throws SQLException {
    Statement st = conn.createStatement();
    try {
      ResultSet rs = st.executeQuery("select * from (\n" + m.query + "\n) where 1 = 0");
      ResultSetMetaData md = rs.getMetaData();
      for (int i = 1; i <= md.getColumnCount(); i++) {
        m.queryCols.add(md.getColumnName(i));
      }
      rs.close();
    } finally {
      st.close();
    }
  }

  /** Tabele nadrzedne kluczy obcych (wszerz): docelowej - tylko FK z kolumn zapytania, nadrzednych - wszystkie */
  private static void loadParents(Connection conn, Model m, int major) throws SQLException {
    List<Table> queue = new ArrayList<Table>();
    queue.add(m.target);
    Set<String> avail = new HashSet<String>(m.availableCols());
    for (int q = 0; q < queue.size(); q++) {
      Table t = queue.get(q);
      for (Fk fk : t.fks) {
        if (fk.cols.size() != 1 || (q == 0 && !avail.contains(fk.cols.get(0)))) {
          continue;
        }
        String id = fk.rOwner + "." + fk.rTable;
        if (!m.tables.containsKey(id) && m.tables.size() < MAX_TABLES) {
          Table p = loadTable(conn, fk.rOwner, fk.rTable, major);
          m.tables.put(id, p);
          queue.add(p);
        }
      }
    }
  }

  private static String[] resolveTable(Connection conn, String owner, String name, String currentSchema) throws SQLException {
    String sql = "select owner, table_name from all_tables where owner = ? and table_name = ?";
    if (owner != null) {
      String[] r = row(conn, sql, owner, name);
      if (r != null) {
        return r;
      }
    }
    String[] r = row(conn, sql, currentSchema, name);
    if (r != null || owner != null) {
      return r;
    }
    return row(conn,
      "select table_owner, table_name from all_synonyms where synonym_name = ? and owner in (?, 'PUBLIC') and db_link is null " +
      "order by decode(owner, 'PUBLIC', 1, 0)", name, currentSchema);
  }

  public static Table loadTable(Connection conn, String owner, String name, int major) throws SQLException {
    Table t = new Table();
    t.owner = owner;
    t.name = name;
    // DATA_DEFAULT (LONG) na koncu - sterownik czyta LONG strumieniem
    PreparedStatement ps = conn.prepareStatement(
      "select column_name, data_type, nullable, virtual_column, " + (major >= 12 ? "identity_column" : "'NO'") + ", data_default " +
      "from all_tab_cols where owner = ? and table_name = ? and hidden_column = 'NO' order by column_id");
    try {
      ps.setString(1, owner);
      ps.setString(2, name);
      ResultSet rs = ps.executeQuery();
      while (rs.next()) {
        Col c = new Col();
        c.name = rs.getString(1);
        c.type = rs.getString(2);
        c.nullable = "Y".equals(rs.getString(3));
        c.virtual = "YES".equals(rs.getString(4));
        c.identity = "YES".equals(rs.getString(5));
        String def = rs.getString(6);
        c.seqDefault = def != null && def.toUpperCase(Locale.ENGLISH).contains(".NEXTVAL");
        t.cols.add(c);
        t.byName.put(c.name, c);
      }
      rs.close();
    } finally {
      ps.close();
    }
    // PK, UNIQUE, FK
    ps = conn.prepareStatement(
      "select c.constraint_name, c.constraint_type, c.r_owner, c.r_constraint_name, cc.column_name " +
      "from all_constraints c join all_cons_columns cc on cc.owner = c.owner and cc.constraint_name = c.constraint_name and cc.table_name = c.table_name " +
      "where c.owner = ? and c.table_name = ? and c.constraint_type in ('P', 'U', 'R') " +
      // wylaczony PK/UNIQUE nie gwarantuje jednoznacznosci (MERGE: ORA-30926); FK wylaczony i tak wskazuje tabele
      "and (c.constraint_type = 'R' or c.status = 'ENABLED') " +
      "order by c.constraint_type, c.constraint_name, cc.position");
    Map<String, Fk> fks = new LinkedHashMap<String, Fk>();
    Map<String, Key> uks = new LinkedHashMap<String, Key>();
    Map<String, String[]> fkRefs = new HashMap<String, String[]>();
    try {
      ps.setString(1, owner);
      ps.setString(2, name);
      ResultSet rs = ps.executeQuery();
      while (rs.next()) {
        String cname = rs.getString(1);
        String type = rs.getString(2);
        String col = rs.getString(5);
        if ("P".equals(type)) {
          if (t.pk == null) {
            t.pk = new Key();
            t.pk.name = cname;
          }
          t.pk.cols.add(col);
        } else if ("U".equals(type)) {
          Key k = uks.get(cname);
          if (k == null) {
            k = new Key();
            k.name = cname;
            uks.put(cname, k);
          }
          k.cols.add(col);
        } else {
          Fk fk = fks.get(cname);
          if (fk == null) {
            fk = new Fk();
            fk.name = cname;
            fks.put(cname, fk);
            fkRefs.put(cname, new String[] {rs.getString(3), rs.getString(4)});
          }
          fk.cols.add(col);
        }
      }
      rs.close();
    } finally {
      ps.close();
    }
    for (Fk fk : fks.values()) {
      String[] ref = fkRefs.get(fk.name);
      ps = conn.prepareStatement(
        "select c.table_name, cc.column_name from all_constraints c " +
        "join all_cons_columns cc on cc.owner = c.owner and cc.constraint_name = c.constraint_name and cc.table_name = c.table_name " +
        "where c.owner = ? and c.constraint_name = ? order by cc.position");
      try {
        ps.setString(1, ref[0]);
        ps.setString(2, ref[1]);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
          fk.rOwner = ref[0];
          fk.rTable = rs.getString(1);
          fk.rCols.add(rs.getString(2));
        }
        rs.close();
      } finally {
        ps.close();
      }
      if (fk.rTable != null && fk.rCols.size() == fk.cols.size()) {
        t.fks.add(fk);
      }
    }
    for (Key k : uks.values()) {
      addUnique(t, k);
    }
    // indeksy unikalne bez ograniczenia (czesto w starszych schematach); funkcyjne pomijane
    ps = conn.prepareStatement(
      "select i.index_name, ic.column_name from all_indexes i " +
      "join all_ind_columns ic on ic.index_owner = i.owner and ic.index_name = i.index_name " +
      "where i.table_owner = ? and i.table_name = ? and i.uniqueness = 'UNIQUE' order by i.index_name, ic.column_position");
    Map<String, Key> idx = new LinkedHashMap<String, Key>();
    try {
      ps.setString(1, owner);
      ps.setString(2, name);
      ResultSet rs = ps.executeQuery();
      while (rs.next()) {
        Key k = idx.get(rs.getString(1));
        if (k == null) {
          k = new Key();
          k.name = rs.getString(1);
          idx.put(k.name, k);
        }
        k.cols.add(rs.getString(2));
      }
      rs.close();
    } finally {
      ps.close();
    }
    for (Key k : idx.values()) {
      if (t.byName.keySet().containsAll(k.cols)) {
        addUnique(t, k);
      }
    }
    // sekwencja klucza glownego wg nazwy (XXX_ID -> XXX_SEQ)
    if (t.pk != null && t.pk.cols.size() == 1) {
      Col c = t.byName.get(t.pk.cols.get(0));
      if (c != null && !c.identity && !c.seqDefault) {
        for (String s : sequenceCandidates(t)) {
          if (row(conn, "select sequence_owner, sequence_name from all_sequences where sequence_owner = ? and sequence_name = ?", owner, s) != null) {
            t.seq = owner + "." + s;
            break;
          }
        }
      }
    }
    return t;
  }

  private static void addUnique(Table t, Key k) {
    if (t.pk != null && new HashSet<String>(t.pk.cols).equals(new HashSet<String>(k.cols))) {
      return;
    }
    for (Key u : t.uniques) {
      if (new HashSet<String>(u.cols).equals(new HashSet<String>(k.cols))) {
        return;
      }
    }
    t.uniques.add(k);
  }

  /** XXX_ID -> XXX_SEQ (przyjeta konwencja), dalej TABELA_SEQ i SEQ_TABELA */
  static List<String> sequenceCandidates(Table t) {
    List<String> result = new ArrayList<String>();
    String pk = t.pk.cols.get(0);
    if (pk.endsWith("_ID") && pk.length() > 3) {
      result.add(pk.substring(0, pk.length() - 3) + "_SEQ");
    }
    result.add(t.name + "_SEQ");
    result.add("SEQ_" + t.name);
    return new ArrayList<String>(new LinkedHashSet<String>(result));
  }

  private static String one(Connection conn, String sql) throws SQLException {
    String[] r = row(conn, sql);
    return r == null ? null : r[0];
  }

  private static String[] row(Connection conn, String sql, String... params) throws SQLException {
    PreparedStatement ps = conn.prepareStatement(sql);
    try {
      for (int i = 0; i < params.length; i++) {
        ps.setString(i + 1, params[i]);
      }
      ResultSet rs = ps.executeQuery();
      try {
        if (!rs.next()) {
          return null;
        }
        String[] r = new String[rs.getMetaData().getColumnCount()];
        for (int i = 0; i < r.length; i++) {
          r[i] = rs.getString(i + 1);
        }
        return r;
      } finally {
        rs.close();
      }
    } finally {
      ps.close();
    }
  }

  // ---------------------------------------------------------------- klucze

  /** Pierwszy klucz unikalny (bez PK zastepczego) z kolumn dostepnych; najpierw bez kolumn NULL */
  static Key naturalKey(Table t, Set<String> avail) {
    Key any = null;
    for (Key k : t.uniques) {
      if (!avail.containsAll(k.cols) || (t.isSurrogate() && k.cols.contains(t.pk.cols.get(0)))) {
        continue;
      }
      boolean notNull = true;
      for (String c : k.cols) {
        Col col = t.byName.get(c);
        notNull &= col != null && !col.nullable;
      }
      if (notNull) {
        return k;
      }
      if (any == null) {
        any = k;
      }
    }
    return any;
  }

  /** Klucz dopasowania: przy PK zastepczym klucz naturalny, inaczej PK, a bez PK pierwszy unikalny */
  static List<String> defaultKey(Table t, Set<String> avail) {
    if (t.pk != null && t.isSurrogate()) {
      Key nk = naturalKey(t, avail);
      if (nk != null) {
        return new ArrayList<String>(nk.cols);
      }
    }
    if (t.pk != null && avail.containsAll(t.pk.cols)) {
      return new ArrayList<String>(t.pk.cols);
    }
    Key nk = naturalKey(t, avail);
    return nk == null ? new ArrayList<String>() : new ArrayList<String>(nk.cols);
  }

  // ---------------------------------------------------------------- generowanie

  /** Obsluga klucza obcego jednokolumnowego: przeliczenie ID przez klucz naturalny albo sprawdzenie istnienia */
  private static class Ref {
    int no;
    Table parent;
    String col;
    /** ID przeliczane przez klucz naturalny nadrzednej */
    boolean byNatural;
    List<String> natural;
    String sync;
    String map;
  }

  /**
   * Skrypt PL/SQL dla wczytanego modelu.
   */
  public static String script(Model m, Options o) {
    return new Script(m, o).build();
  }

  private static class Script {
    final Model m;
    final Options o;
    final Map<String, Ref> refs = new LinkedHashMap<String, Ref>();
    final Set<String> names = new HashSet<String>();
    final List<String> notes = new ArrayList<String>();

    Script(Model m, Options o) {
      this.m = m;
      this.o = o;
    }

    String build() {
      Table t = m.target;
      Set<String> avail = new LinkedHashSet<String>(m.availableCols());
      List<String> key = o.key != null ? new ArrayList<String>(o.key) : m.defaultKey();
      key.retainAll(m.keyCols());
      Map<String, Ref> mainRefs = refsFor(t, avail, true);
      StringBuilder sb = new StringBuilder();
      // naglowek
      String src = m.source == null ? "?" : (m.source.owner == null ? "" : m.source.owner + ".") + m.source.table + (m.source.link == null ? "" : "@" + m.source.link);
      comment(sb, "", msg("datasync-c-title", sqlName(t), src));
      describeTable(sb, t, avail, key, true);
      List<String> skipped = new ArrayList<String>();
      for (String c : m.queryCols) {
        if (!t.byName.containsKey(c)) {
          skipped.add(c);
        }
      }
      if (!skipped.isEmpty()) {
        comment(sb, "", msg("datasync-c-notarget", join(skipped)));
      }
      for (Ref r : refs.values()) {
        if (r.parent != t) {
          describeTable(sb, r.parent, new HashSet<String>(r.parent.colNames()), defaultKey(r.parent, new HashSet<String>(r.parent.colNames())), false);
        }
      }
      for (Ref r : refs.values()) {
        String parent = sqlName(r.parent) + "." + r.col;
        if (r.byNatural) {
          comment(sb, "", msg(o.syncParents ? "datasync-c-fk-map-sync" : "datasync-c-fk-map", parent, join(r.natural)));
        } else {
          comment(sb, "", msg("datasync-c-fk-check", parent));
        }
      }
      for (String n : notes) {
        comment(sb, "", n);
      }
      comment(sb, "", msg("datasync-c-commit"));
      sb.append("declare\n");
      if (!refs.isEmpty()) {
        sb.append("  type t_busy is table of pls_integer index by varchar2(4000);\n");
        sb.append("  g_busy t_busy;\n");
        sb.append("  g_parents pls_integer := 0;\n");
      }
      sb.append("  g_rows pls_integer := 0;\n");
      Map<String, String> mainVars = vars(sb, "  ", t, mainRefs);
      if (!refs.isEmpty()) {
        sb.append("\n");
        for (Ref r : refs.values()) {
          if (o.syncParents) {
            sb.append("  ").append(syncHeader(r)).append(";\n");
          }
          sb.append("  ").append(mapHeader(r)).append(";\n");
        }
        for (Ref r : refs.values()) {
          if (o.syncParents) {
            sb.append("\n");
            sync(sb, r);
          }
          sb.append("\n");
          map(sb, r);
        }
      }
      sb.append("begin\n");
      sb.append("  for r in (\n");
      for (String line : m.query.split("\r?\n")) {
        sb.append("    ").append(line).append("\n");
      }
      sb.append("  ) loop\n");
      // wiersz w trakcie kopiowania - cykl przez klucz obcy do tej samej tabeli nie kopiuje go drugi raz
      List<String> busy = new ArrayList<String>();
      for (Ref r : refs.values()) {
        if (r.parent == t && avail.contains(r.col)) {
          busy.add(busyKey(r, "r." + id(r.col)));
        }
      }
      for (String b : busy) {
        sb.append("    g_busy(").append(b).append(") := 1;\n");
      }
      assign(sb, "    ", mainRefs, mainVars, "r");
      dml(sb, "    ", t, avail, key, mainVars, "r", o.update);
      sb.append("    g_rows := g_rows + sql%rowcount;\n");
      for (String b : busy) {
        sb.append("    g_busy.delete(").append(b).append(");\n");
      }
      sb.append("  end loop;\n");
      sb.append("  dbms_output.put_line(").append(lit(msg("datasync-out-rows") + ": ")).append(" || g_rows");
      if (!refs.isEmpty()) {
        sb.append(" || ").append(lit(", " + msg("datasync-out-parents") + ": ")).append(" || g_parents");
      }
      sb.append(");\n");
      sb.append("end;\n/\n");
      return sb.toString();
    }

    /** Klucze obce tabeli z obsluga (rekurencyjnie tabele nadrzedne); pozostale - uwaga w naglowku */
    Map<String, Ref> refsFor(Table t, Set<String> avail, boolean main) {
      Map<String, Ref> result = new LinkedHashMap<String, Ref>();
      for (Fk fk : t.fks) {
        if (!avail.containsAll(fk.cols)) {
          continue;
        }
        String desc = fk.name + " (" + sqlName(t) + "." + join(fk.cols) + " -> " + sqlName(fk.rOwner, fk.rTable) + ")";
        if (!m.remote) {
          if (main) {
            note(msg("datasync-c-fk-copy", desc, msg("datasync-r-local")));
          }
          continue;
        }
        Table p = m.tables.get(fk.rOwner + "." + fk.rTable);
        if (fk.cols.size() != 1 || p == null) {
          note(msg("datasync-c-fk-copy", desc, msg(p == null ? "datasync-r-limit" : "datasync-r-multi")));
          continue;
        }
        String rc = fk.rCols.get(0);
        Set<String> all = new HashSet<String>(p.colNames());
        boolean byNatural = p.isSurrogate() && p.pk.cols.get(0).equals(rc) && naturalKey(p, all) != null;
        if (p.isSurrogate() && p.pk.cols.get(0).equals(rc) && !byNatural) {
          note(msg("datasync-c-fk-copy", desc, msg("datasync-r-nonk", sqlName(p))));
          if (!o.syncParents) {
            continue;
          }
        }
        if (!byNatural && !o.syncParents) {
          note(msg("datasync-c-fk-copy", desc, msg("datasync-r-nosync")));
          continue;
        }
        String id = p.id() + "." + rc;
        Ref r = refs.get(id);
        if (r == null) {
          r = new Ref();
          r.no = refs.size() + 1;
          r.parent = p;
          r.col = rc;
          r.byNatural = byNatural;
          r.natural = byNatural ? naturalKey(p, all).cols : null;
          r.sync = name("sync_" + p.name.toLowerCase(Locale.ENGLISH), rc);
          r.map = name("map_" + p.name.toLowerCase(Locale.ENGLISH), rc);
          refs.put(id, r);
          // podprogramy tabeli nadrzednej: wszystkie jej klucze obce (sync) albo tylko z klucza naturalnego (map)
          refsFor(p, o.syncParents ? all : new HashSet<String>(r.natural == null ? new ArrayList<String>() : r.natural), false);
        }
        result.put(fk.cols.get(0), r);
      }
      return result;
    }

    void note(String s) {
      if (!notes.contains(s)) {
        notes.add(s);
      }
    }

    void describeTable(StringBuilder sb, Table t, Set<String> avail, List<String> key, boolean main) {
      String prefix = main ? "" : sqlName(t) + ": ";
      if (key.isEmpty()) {
        comment(sb, "", prefix + msg("datasync-c-nokey"));
      } else {
        comment(sb, "", prefix + msg("datasync-c-key", join(key)));
        if (t.isSurrogate() && key.contains(t.pk.cols.get(0)) && main) {
          comment(sb, "", msg("datasync-c-pk-match", sqlName(t), t.pk.cols.get(0)));
        }
      }
      if (t.pk != null && t.pk.cols.size() == 1 && !key.contains(t.pk.cols.get(0)) && avail.contains(t.pk.cols.get(0))) {
        Col c = t.byName.get(t.pk.cols.get(0));
        if (c.identity) {
          comment(sb, "", prefix + msg("datasync-c-identity", c.name));
        } else if (c.seqDefault) {
          comment(sb, "", prefix + msg("datasync-c-default", c.name));
        } else if (t.seq != null) {
          comment(sb, "", prefix + msg("datasync-c-seq", c.name, seqName(t)));
        } else if (main) {
          comment(sb, "", prefix + msg("datasync-c-pk-copy", c.name));
        }
      }
      List<String> skipped = new ArrayList<String>();
      boolean lob = false;
      for (Col c : t.cols) {
        if (avail.contains(c.name) && (c.virtual || c.isLong() || (c.identity && !key.contains(c.name) && !isPk(t, c.name)))) {
          skipped.add(c.name);
        }
        lob |= avail.contains(c.name) && c.isLob();
      }
      if (!skipped.isEmpty()) {
        comment(sb, "", prefix + msg("datasync-c-skip", join(skipped)));
      }
      if (lob && m.source != null && m.source.link != null) {
        note(msg("datasync-c-lob"));
      }
    }

    /** Zmienne z ID przeliczonymi przez klucze obce */
    Map<String, String> vars(StringBuilder sb, String ind, Table t, Map<String, Ref> tRefs) {
      Map<String, String> result = new LinkedHashMap<String, String>();
      Set<String> local = new HashSet<String>();
      for (String col : tRefs.keySet()) {
        String v = unique(local, "v_" + col.toLowerCase(Locale.ENGLISH));
        result.put(col, v);
        sb.append(ind).append(v).append(" ").append(colType(t, col)).append(";\n");
      }
      return result;
    }

    void assign(StringBuilder sb, String ind, Map<String, Ref> tRefs, Map<String, String> vars, String rec) {
      for (Map.Entry<String, Ref> e : tRefs.entrySet()) {
        sb.append(ind).append(vars.get(e.getKey())).append(" := ").append(e.getValue().map).append("(").append(rec).append(".").append(id(e.getKey())).append(");\n");
      }
    }

    String syncHeader(Ref r) {
      return "procedure " + r.sync + "(p_val " + colType(r.parent, r.col) + ")";
    }

    String mapHeader(Ref r) {
      return "function " + r.map + "(p_val " + colType(r.parent, r.col) + ") return " + colType(r.parent, r.col);
    }

    String busyKey(Ref r, String expr) {
      return lit(r.no + ":") + " || " + expr;
    }

    /** Kopia wiersza tabeli nadrzednej ze zrodla (wraz z jej wierszami nadrzednymi) */
    void sync(StringBuilder sb, Ref r) {
      Table p = r.parent;
      Set<String> all = new LinkedHashSet<String>(p.colNames());
      Map<String, Ref> pRefs = refsFor(p, all, false);
      String bk = busyKey(r, "p_val");
      sb.append("  ").append(syncHeader(r)).append(" is\n");
      Map<String, String> pVars = vars(sb, "    ", p, pRefs);
      sb.append("  begin\n");
      sb.append("    if p_val is null or g_busy.exists(").append(bk).append(") then\n");
      sb.append("      return;\n");
      sb.append("    end if;\n");
      sb.append("    g_busy(").append(bk).append(") := 1;\n");
      sb.append("    for r in (select * from ").append(sourceName(p)).append(" where ").append(id(r.col)).append(" = p_val) loop\n");
      assign(sb, "      ", pRefs, pVars, "r");
      dml(sb, "      ", p, all, defaultKey(p, all), pVars, "r", o.update);
      sb.append("      g_parents := g_parents + sql%rowcount;\n");
      sb.append("    end loop;\n");
      sb.append("    g_busy.delete(").append(bk).append(");\n");
      sb.append("  exception\n");
      sb.append("    when others then\n");
      sb.append("      g_busy.delete(").append(bk).append(");\n");
      sb.append("      raise;\n");
      sb.append("  end;\n");
    }

    /** ID wiersza nadrzednego w tej bazie dla ID ze zrodla */
    void map(StringBuilder sb, Ref r) {
      Table p = r.parent;
      String bk = busyKey(r, "p_val");
      String type = colType(p, r.col);
      sb.append("  ").append(mapHeader(r)).append(" is\n");
      if (!r.byNatural) {
        // ten sam klucz w obu bazach - tylko sprawdzenie istnienia i kopia brakujacego
        sb.append("    v_count pls_integer;\n");
        sb.append("  begin\n");
        sb.append("    if p_val is null then\n");
        sb.append("      return null;\n");
        sb.append("    end if;\n");
        sb.append("    select count(*) into v_count from ").append(sqlName(p)).append(" t where t.").append(id(r.col)).append(" = p_val and rownum = 1;\n");
        sb.append("    if v_count = 0 then\n");
        sb.append("      if g_busy.exists(").append(bk).append(") then\n");
        sb.append("        return null; -- ").append(msg("datasync-c-cycle")).append("\n");
        sb.append("      end if;\n");
        sb.append("      ").append(r.sync).append("(p_val);\n");
        sb.append("    end if;\n");
        sb.append("    return p_val;\n");
        sb.append("  end;\n");
        return;
      }
      Set<String> nkSet = new LinkedHashSet<String>(r.natural);
      Map<String, Ref> nkRefs = refsFor(p, nkSet, false);
      sb.append("    v_result ").append(type).append(";\n");
      Map<String, String> nkVars = new LinkedHashMap<String, String>();
      Set<String> local = new HashSet<String>();
      local.add("v_result");
      for (String c : r.natural) {
        String v = unique(local, "v_" + c.toLowerCase(Locale.ENGLISH));
        nkVars.put(c, v);
        sb.append("    ").append(v).append(" ").append(colType(p, c)).append(";\n");
      }
      sb.append("  begin\n");
      sb.append("    if p_val is null then\n");
      sb.append("      return null;\n");
      sb.append("    end if;\n");
      sb.append("    begin\n");
      sb.append("      select ");
      List<String> sel = new ArrayList<String>();
      for (String c : r.natural) {
        sel.add("s." + id(c));
      }
      sb.append(join(sel)).append("\n");
      sb.append("        into ").append(join(new ArrayList<String>(nkVars.values()))).append("\n");
      sb.append("        from ").append(sourceName(p)).append(" s where s.").append(id(r.col)).append(" = p_val;\n");
      sb.append("    exception\n");
      sb.append("      when no_data_found then\n");
      sb.append("        raise_application_error(-20001, ").append(lit(sourceName(p) + ": " + msg("datasync-e-source") + " " + r.col + " = ")).append(" || p_val);\n");
      sb.append("    end;\n");
      // klucz naturalny z kluczem obcym (np. unique (kraj_id, kod)) - ID nadrzednej tez przeliczone
      for (Map.Entry<String, Ref> e : nkRefs.entrySet()) {
        String v = nkVars.get(e.getKey());
        sb.append("    ").append(v).append(" := ").append(e.getValue().map).append("(").append(v).append(");\n");
      }
      List<String> where = new ArrayList<String>();
      for (String c : r.natural) {
        where.add(equal(p, c, "t." + id(c), nkVars.get(c)));
      }
      String select = "select t." + id(r.col) + " into v_result from " + sqlName(p) + " t where " + joinWith(where, " and ") + ";";
      String error = "raise_application_error(-20002, " + lit(sqlName(p) + ": " + msg("datasync-e-target") + " " + r.col + " = ") + " || p_val);";
      if (!o.syncParents) {
        sb.append("    ").append(select).append("\n");
        sb.append("    return v_result;\n");
        sb.append("  exception\n");
        sb.append("    when no_data_found then\n");
        sb.append("      ").append(error).append("\n");
        sb.append("  end;\n");
        return;
      }
      sb.append("    for i in 1 .. 2 loop\n");
      sb.append("      begin\n");
      sb.append("        ").append(select).append("\n");
      sb.append("        return v_result;\n");
      sb.append("      exception\n");
      sb.append("        when no_data_found then\n");
      sb.append("          if g_busy.exists(").append(bk).append(") then\n");
      sb.append("            return null; -- ").append(msg("datasync-c-cycle")).append("\n");
      sb.append("          end if;\n");
      sb.append("          if i = 2 then\n");
      sb.append("            ").append(error).append("\n");
      sb.append("          end if;\n");
      sb.append("          ").append(r.sync).append("(p_val);\n");
      sb.append("      end;\n");
      sb.append("    end loop;\n");
      sb.append("    return null;\n");
      sb.append("  end;\n");
    }

    /** MERGE wiersza (bez klucza - INSERT) */
    void dml(StringBuilder sb, String ind, Table t, Set<String> avail, List<String> key, Map<String, String> vars, String rec, boolean update) {
      String pk = t.pk != null && t.pk.cols.size() == 1 ? t.pk.cols.get(0) : null;
      List<String> used = new ArrayList<String>();
      String seqCol = null;
      for (Col c : t.cols) {
        if (!avail.contains(c.name) || c.virtual || c.isLong()) {
          continue;
        }
        boolean inKey = key.contains(c.name);
        if (c.identity && !inKey) {
          continue;
        }
        if (c.name.equals(pk) && !inKey && t.isSurrogate()) {
          // PK zastepczy poza kluczem dopasowania: IDENTITY / DEFAULT nextval - baza nada; sekwencja wg nazwy - nextval
          if (!c.identity && !c.seqDefault && t.seq != null) {
            seqCol = c.name;
          }
          continue;
        }
        used.add(c.name);
      }
      if (seqCol == null && pk != null && t.isSurrogate() && t.seq != null && !key.contains(pk) && !avail.contains(pk)) {
        // zapytanie bez kolumny PK - nowe wiersze i tak z sekwencji
        seqCol = pk;
      }
      String target = sqlName(t);
      if (key.isEmpty()) {
        List<String> cols = new ArrayList<String>();
        List<String> vals = new ArrayList<String>();
        if (seqCol != null) {
          cols.add(id(seqCol));
          vals.add(seqName(t) + ".nextval");
        }
        for (String c : used) {
          cols.add(id(c));
          vals.add(value(c, vars, rec));
        }
        sb.append(ind).append("insert into ").append(target).append(" (").append(wrap(cols, ind + "  ")).append(")\n");
        sb.append(ind).append("values (").append(wrap(vals, ind + "  ")).append(");\n");
        return;
      }
      List<String> using = new ArrayList<String>();
      for (String c : used) {
        using.add(value(c, vars, rec) + " " + id(c));
      }
      List<String> on = new ArrayList<String>();
      for (String c : key) {
        on.add(equal(t, c, "t." + id(c), "s." + id(c)));
      }
      List<String> set = new ArrayList<String>();
      for (String c : used) {
        if (!key.contains(c) && !c.equals(pk) && !(t.pk != null && t.pk.cols.contains(c))) {
          set.add("t." + id(c) + " = s." + id(c));
        }
      }
      List<String> cols = new ArrayList<String>();
      List<String> vals = new ArrayList<String>();
      if (seqCol != null) {
        cols.add(id(seqCol));
        vals.add(seqName(t) + ".nextval");
      }
      for (String c : used) {
        cols.add(id(c));
        vals.add("s." + id(c));
      }
      sb.append(ind).append("merge into ").append(target).append(" t\n");
      sb.append(ind).append("using (select ").append(wrap(using, ind + "         ")).append("\n");
      sb.append(ind).append("         from dual) s\n");
      sb.append(ind).append("on (").append(joinWith(on, " and ")).append(")\n");
      if (update && !set.isEmpty()) {
        sb.append(ind).append("when matched then update set\n");
        sb.append(ind).append("  ").append(joinWith(set, ",\n" + ind + "  ")).append("\n");
      }
      sb.append(ind).append("when not matched then insert (").append(wrap(cols, ind + "  ")).append(")\n");
      sb.append(ind).append("  values (").append(wrap(vals, ind + "  ")).append(");\n");
    }

    String value(String col, Map<String, String> vars, String rec) {
      String v = vars.get(col);
      return v != null ? v : rec + "." + id(col);
    }

    /** Porownanie; kolumny NULL przez DECODE (NULL = NULL) */
    String equal(Table t, String col, String a, String b) {
      Col c = t.byName.get(col);
      if (c != null && c.nullable) {
        return "decode(" + a + ", " + b + ", 1, 0) = 1";
      }
      return a + " = " + b;
    }

    String name(String base, String col) {
      String n = base;
      for (Ref r : refs.values()) {
        if ((r.sync.equals(trim(n)) || r.map.equals(trim(n)))) {
          n = base + "_" + col.toLowerCase(Locale.ENGLISH);
          break;
        }
      }
      return unique(names, n);
    }

    String sourceName(Table p) {
      // tabela nadrzedna w zrodle: ten sam wlasciciel co tabela docelowa -> wlasciciel tabeli z zapytania
      String owner;
      if (p.owner.equals(m.target.owner)) {
        owner = m.source.owner;
      } else {
        owner = p.owner;
      }
      String s = (owner == null ? "" : id(owner) + ".") + id(p.name);
      return m.source.link == null ? s : s + "@" + m.source.link;
    }

    String sqlName(Table t) {
      return sqlName(t.owner, t.name);
    }

    String sqlName(String owner, String name) {
      return (owner.equals(m.currentSchema) ? "" : id(owner) + ".") + id(name);
    }

    String seqName(Table t) {
      int dot = t.seq.indexOf('.');
      return sqlName(t.seq.substring(0, dot), t.seq.substring(dot + 1));
    }

    String colType(Table t, String col) {
      return sqlName(t) + "." + id(col) + "%type";
    }
  }

  // ---------------------------------------------------------------- pomocnicze

  static boolean isPk(Table t, String col) {
    return t.pk != null && t.pk.cols.contains(col);
  }

  /** Identyfikator w SQL: prosty malymi literami, inny w cudzyslowie */
  public static String id(String name) {
    if (name.matches("[A-Z][A-Z0-9_$#]*") && !RESERVED.contains(name)) {
      return name.toLowerCase(Locale.ENGLISH);
    }
    return "\"" + name.replace("\"", "\"\"") + "\"";
  }

  public static String lit(String s) {
    return "'" + s.replace("'", "''") + "'";
  }

  static String trim(String n) {
    return n.length() > MAX_NAME ? n.substring(0, MAX_NAME) : n;
  }

  static String unique(Set<String> used, String n) {
    String base = trim(n);
    String result = base;
    for (int i = 2; used.contains(result.toUpperCase(Locale.ENGLISH)); i++) {
      String suffix = "_" + i;
      result = (base.length() + suffix.length() > MAX_NAME ? base.substring(0, MAX_NAME - suffix.length()) : base) + suffix;
    }
    used.add(result.toUpperCase(Locale.ENGLISH));
    return result;
  }

  static String join(List<String> list) {
    return joinWith(list, ", ");
  }

  static String joinWith(List<String> list, String sep) {
    StringBuilder sb = new StringBuilder();
    for (String s : list) {
      if (sb.length() > 0) {
        sb.append(sep);
      }
      sb.append(s);
    }
    return sb.toString();
  }

  /** Lista po przecinku lamana co ok. 100 znakow */
  static String wrap(List<String> list, String ind) {
    StringBuilder sb = new StringBuilder();
    int line = 0;
    for (int i = 0; i < list.size(); i++) {
      String s = list.get(i);
      if (i > 0) {
        if (line + s.length() > 90) {
          sb.append(",\n").append(ind);
          line = 0;
        } else {
          sb.append(", ");
          line += 2;
        }
      }
      sb.append(s);
      line += s.length();
    }
    return sb.toString();
  }

  static void comment(StringBuilder sb, String ind, String text) {
    for (String line : text.split("\n")) {
      sb.append(ind).append("-- ").append(line).append("\n");
    }
  }

}
