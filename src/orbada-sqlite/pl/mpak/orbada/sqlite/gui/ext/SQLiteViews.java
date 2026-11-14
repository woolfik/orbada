package pl.mpak.orbada.sqlite.gui.ext;

import java.awt.Component;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import pl.mpak.orbada.gui.OrbadaTabbedPane;
import pl.mpak.orbada.plugins.IViewAccesibilities;
import pl.mpak.orbada.sqlite.SQLiteSql;
import pl.mpak.orbada.universal.gui.filter.SqlFilter;
import pl.mpak.orbada.universal.gui.filter.SqlFilterDefComponent;
import pl.mpak.orbada.universal.gui.tabbed.UniversalViewTabs;
import pl.mpak.usedb.gui.swing.QueryTableColumn;
import pl.mpak.util.StringManager;

/**
 * ORBADA 1.3.8: nowe widoki SQLite - obiekty (wyszukiwanie po nazwie i tresci), indeksy, liczniki AUTOINCREMENT,
 * pragmy, funkcje SQL, rozmiar obiektow (dbstat). Wybor bazy (main, temp, ATTACH) przyciskiem schematu: kolumna
 * "database" i parametr :database (UniversalViewTabs wymaga parametru o nazwie kolumny schematu).
 */
public class SQLiteViews {

  final static StringManager stringManager = SQLiteTabs.stringManager;

  static QueryTableColumn col(String name, String titleKey, int width) {
    return SQLiteTabs.col(name, titleKey, width);
  }

  static QueryTableColumn bold(String name, String titleKey, int width) {
    return SQLiteTabs.bold(name, titleKey, width);
  }

  static SqlFilterDefComponent filter(String column, String titleKey) {
    return new SqlFilterDefComponent(column, stringManager.getString(titleKey), (String[])null);
  }

  public abstract static class ViewTabs extends UniversalViewTabs {

    public ViewTabs(IViewAccesibilities accesibilities) {
      super(accesibilities);
    }

    @Override
    public String getCurrentSchemaName() {
      return "main";
    }

    @Override
    public String getSchemaColumnName() {
      return isPerDatabase() ? "database" : null;
    }

    /** false - widok dotyczy polaczenia, nie bazy (funkcje) */
    protected boolean isPerDatabase() {
      return true;
    }

    @Override
    public String getObjectColumnName() {
      return "name";
    }

    @Override
    public String getDescriptionColumnName() {
      return null;
    }

    @Override
    public Component getTabbedPane() {
      return null;
    }

    @Override
    public SqlFilterDefComponent[] getFilterDefComponent() {
      return null;
    }

    @Override
    public String[] getSchemaList() {
      List<String> list = SQLiteSql.databases(getDatabase());
      return list.toArray(new String[list.size()]);
    }

    @Override
    protected boolean executeSql() {
      return true;
    }

    @Override
    public String getSql(SqlFilter filter) {
      String sql = getSql(filter == null ? null : filter.getSqlText());
      // parametr :database tylko dla mechanizmu wyboru bazy (UniversalViewTabs.refresh), baza jest juz w zapytaniu
      return isPerDatabase() ? "select * from (\n" + sql + "\n) where :database is not null" : sql;
    }

    /** Zapytanie dla bazy currentSchemaName z filtrem (null - bez filtra) */
    protected abstract String getSql(String filter);
  }

  // ---------------------------------------------------------------- obiekty

  public static class Objects extends ViewTabs {
    public Objects(IViewAccesibilities a) { super(a); }
    public String getPanelName() { return "sqlite-objects"; }
    public Component getTabbedPane() {
      return new OrbadaTabbedPane("SQLITE-OBJECT", new Component[] {
        new SQLiteTabs.Source(accesibilities, "sqlite-object-source"), new SQLiteTabs.UsedBy(accesibilities)});
    }
    public QueryTableColumn[] getTableColumns() {
      return new QueryTableColumn[] {col("type", "sqlite-type", 70), bold("name", "sqlite-name", 200), col("tbl_name", "table", 150)};
    }
    public SqlFilterDefComponent[] getFilterDefComponent() {
      return new SqlFilterDefComponent[] {
        filter("name", "sqlite-name"),
        new SqlFilterDefComponent("type", stringManager.getString("sqlite-type"), new String[] {"'table'", "'view'", "'index'", "'trigger'"}),
        filter("tbl_name", "table"),
        filter("sql", "sqlite-sql-text")};
    }
    protected String getSql(String filter) { return SQLiteSql.getObjects(currentSchemaName, filter); }
  }

  // ---------------------------------------------------------------- indeksy

  public static class Indexes extends ViewTabs {
    public Indexes(IViewAccesibilities a) { super(a); }
    public String getPanelName() { return "sqlite-indexes"; }
    public Component getTabbedPane() {
      return new OrbadaTabbedPane("SQLITE-INDEX", new Component[] {
        new SQLiteTabs.IndexColumns(accesibilities), new SQLiteTabs.Source(accesibilities, "sqlite-index-source")});
    }
    public QueryTableColumn[] getTableColumns() {
      return new QueryTableColumn[] {
        bold("name", "index-name", 200), col("tbl_name", "table", 150), col("unique", "unique", 60), col("origin", "sqlite-origin", 60),
        col("partial", "sqlite-partial", 60), col("columns", "sqlite-columns", 250)};
    }
    public SqlFilterDefComponent[] getFilterDefComponent() {
      return new SqlFilterDefComponent[] {
        filter("name", "index-name"), filter("tbl_name", "table"),
        new SqlFilterDefComponent("\"unique\" = 1", stringManager.getString("unique")),
        new SqlFilterDefComponent("origin = 'c'", stringManager.getString("sqlite-created-by-user"))};
    }
    protected String getSql(String filter) { return SQLiteSql.getIndexes(currentSchemaName, filter); }
  }

  // ---------------------------------------------------------------- AUTOINCREMENT

  public static class Sequences extends ViewTabs {
    public Sequences(IViewAccesibilities a) { super(a); }
    public String getPanelName() { return "sqlite-sequences"; }
    public QueryTableColumn[] getTableColumns() {
      return new QueryTableColumn[] {bold("name", "table", 200), col("seq", "sqlite-seq", 120)};
    }
    protected String getSql(String filter) { return SQLiteSql.getSequences(getDatabase(), currentSchemaName); }
  }

  // ---------------------------------------------------------------- pragmy

  public static class Pragmas extends ViewTabs {
    public Pragmas(IViewAccesibilities a) { super(a); }
    public String getPanelName() { return "sqlite-pragmas"; }
    public String getDescriptionColumnName() { return "description"; }
    public QueryTableColumn[] getTableColumns() {
      return new QueryTableColumn[] {
        bold("name", "sqlite-pragma", 180), col("value", "sqlite-value", 150), col("scope", "sqlite-scope", 100), col("settable", "sqlite-settable", 70)};
    }
    public SqlFilterDefComponent[] getFilterDefComponent() {
      return new SqlFilterDefComponent[] {filter("name", "sqlite-pragma"), new SqlFilterDefComponent("settable = 'YES'", stringManager.getString("sqlite-settable"))};
    }
    protected String getSql(String filter) { return SQLiteSql.getPragmas(getDatabase(), currentSchemaName, descriptions(), filter); }
  }

  /** Opisy pragm z i18n: "sqlite-pragma-nazwa" */
  public static Map<String, String> descriptions() {
    Map<String, String> map = new HashMap<String, String>();
    for (SQLiteSql.Pragma p : SQLiteSql.PRAGMAS) {
      try {
        map.put(p.name, stringManager.getString("sqlite-pragma-" + p.name));
      } catch (Exception ex) {
        // brak opisu
      }
    }
    return map;
  }

  // ---------------------------------------------------------------- funkcje

  public static class Functions extends ViewTabs {
    public Functions(IViewAccesibilities a) { super(a); }
    public String getPanelName() { return "sqlite-functions"; }
    protected boolean isPerDatabase() { return false; }
    public QueryTableColumn[] getTableColumns() {
      return new QueryTableColumn[] {
        bold("name", "sqlite-function", 200), col("type", "sqlite-type", 90), col("narg", "sqlite-narg", 60),
        col("builtin", "sqlite-builtin", 70), col("enc", "sqlite-enc", 70), col("flags", "sqlite-flags", 80)};
    }
    public SqlFilterDefComponent[] getFilterDefComponent() {
      return new SqlFilterDefComponent[] {
        filter("name", "sqlite-function"),
        new SqlFilterDefComponent("type", stringManager.getString("sqlite-type"), new String[] {"'SCALAR'", "'AGGREGATE'", "'WINDOW'"}),
        new SqlFilterDefComponent("builtin = 'NO'", stringManager.getString("sqlite-user-functions"))};
    }
    protected String getSql(String filter) { return SQLiteSql.getFunctions(filter); }
  }

  // ---------------------------------------------------------------- rozmiary

  public static class Sizes extends ViewTabs {
    public Sizes(IViewAccesibilities a) { super(a); }
    public String getPanelName() { return "sqlite-sizes"; }
    public QueryTableColumn[] getTableColumns() {
      return new QueryTableColumn[] {
        bold("name", "sqlite-name", 200), col("type", "sqlite-type", 70), col("tbl_name", "table", 150), col("pages", "sqlite-pages", 70),
        col("bytes", "sqlite-bytes", 100), col("unused", "sqlite-unused", 90), col("payload", "sqlite-payload", 90), col("percent", "sqlite-percent", 60)};
    }
    public SqlFilterDefComponent[] getFilterDefComponent() {
      return new SqlFilterDefComponent[] {filter("name", "sqlite-name"), filter("tbl_name", "table"),
        new SqlFilterDefComponent("type", stringManager.getString("sqlite-type"), new String[] {"'table'", "'index'"})};
    }
    protected String getSql(String filter) { return SQLiteSql.getSizes(getDatabase(), currentSchemaName, filter); }
  }

}
