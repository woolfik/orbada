package pl.mpak.orbada.sqlite.gui.ext;

import pl.mpak.orbada.gui.comps.AbsOrbadaSyntaxTextArea;
import pl.mpak.orbada.gui.comps.OrbadaSyntaxTextArea;
import pl.mpak.orbada.plugins.IViewAccesibilities;
import pl.mpak.orbada.sqlite.OrbadaSQLitePlugin;
import pl.mpak.orbada.sqlite.SQLiteSql;
import pl.mpak.orbada.universal.gui.filter.SqlFilter;
import pl.mpak.orbada.universal.gui.filter.SqlFilterDefComponent;
import pl.mpak.orbada.universal.gui.tabbed.UniversalPropDetailsTab;
import pl.mpak.orbada.universal.gui.tabbed.UniversalPropTab;
import pl.mpak.orbada.universal.gui.tabbed.UniversalSourceTab;
import pl.mpak.usedb.gui.swing.QueryTableCellRenderer;
import pl.mpak.usedb.gui.swing.QueryTableColumn;
import pl.mpak.util.StringManager;
import pl.mpak.util.StringManagerFactory;

/**
 * ORBADA 1.3.8: zakladki SQLite (tabele, widoki, indeksy, obiekty) - zapytania w SQLiteSql. Baza (main, temp,
 * dolaczona przez ATTACH) w currentSchemaName, obiekt w currentObjectName. Tytul: klucz "NazwaKlasy-title".
 * Akcje zakladki: typ "nazwa-panelu-actions" (SQLiteActionsService).
 */
public class SQLiteTabs {

  final static StringManager stringManager = StringManagerFactory.getStringManager(OrbadaSQLitePlugin.class);

  static QueryTableColumn col(String name, String titleKey, int width) {
    return new QueryTableColumn(name, stringManager.getString(titleKey), width);
  }

  static QueryTableColumn bold(String name, String titleKey, int width) {
    return new QueryTableColumn(name, stringManager.getString(titleKey), width, new QueryTableCellRenderer(java.awt.Font.BOLD));
  }

  /** Lista z zapytania SQLiteSql; bez parametrow (nazwy jako literaly) */
  public abstract static class PropTab extends UniversalPropTab {

    public PropTab(IViewAccesibilities accesibilities) {
      super(accesibilities);
    }

    public String getTitle() {
      return stringManager.getString(getClass().getSimpleName() + "-title");
    }

    @Override
    public String getSchemaColumnName() {
      return null;
    }

    @Override
    public String getObjectColumnName() {
      return null;
    }

    @Override
    public SqlFilterDefComponent[] getFilterDefComponent() {
      return null;
    }

    @Override
    protected boolean executeSql() {
      return true;
    }

    public String getSql(SqlFilter filter) {
      return getSql();
    }

    public abstract String getSql();
  }

  /** Klucze obce tabeli (z ON UPDATE / ON DELETE) */
  public static class ForeignKeys extends PropTab {
    public ForeignKeys(IViewAccesibilities a) { super(a); }
    public String getPanelName() { return "sqlite-table-foreign-keys"; }
    public String getPropColumnName() { return "from"; }
    public QueryTableColumn[] getTableColumns() {
      return new QueryTableColumn[] {
        col("id", "sqlite-fk-id", 30), bold("table", "table", 150), col("from", "column", 120), col("to", "ref-column", 120),
        col("on_update", "sqlite-on-update", 90), col("on_delete", "sqlite-on-delete", 90), col("match", "sqlite-match", 60)};
    }
    public String getSql() { return SQLiteSql.getForeignKeys(currentSchemaName, currentObjectName); }
  }

  /** Tabele odwolujace sie do tabeli kluczem obcym */
  public static class ReferencedFrom extends PropTab {
    public ReferencedFrom(IViewAccesibilities a) { super(a); }
    public String getPanelName() { return "sqlite-table-referenced-from"; }
    public String getPropColumnName() { return "name"; }
    public QueryTableColumn[] getTableColumns() {
      return new QueryTableColumn[] {
        bold("name", "table", 150), col("from", "column", 120), col("to", "ref-column", 120),
        col("on_update", "sqlite-on-update", 90), col("on_delete", "sqlite-on-delete", 90)};
    }
    public String getSql() { return SQLiteSql.getReferencedFrom(currentSchemaName, currentObjectName); }
  }

  /** Obiekty, w ktorych tresci wystepuje nazwa obiektu */
  public static class UsedBy extends PropTab {
    public UsedBy(IViewAccesibilities a) { super(a); }
    public String getPanelName() { return "sqlite-object-used-by"; }
    public String getPropColumnName() { return "name"; }
    public QueryTableColumn[] getTableColumns() {
      return new QueryTableColumn[] {col("type", "sqlite-type", 70), bold("name", "sqlite-name", 200), col("tbl_name", "table", 150)};
    }
    public String getSql() { return SQLiteSql.getUsedBy(currentSchemaName, currentObjectName); }
  }

  /** Statystyki ANALYZE (sqlite_stat1) */
  public static class Stats extends PropTab {
    public Stats(IViewAccesibilities a) { super(a); }
    public String getPanelName() { return "sqlite-table-stats"; }
    public String getPropColumnName() { return "idx"; }
    public QueryTableColumn[] getTableColumns() {
      return new QueryTableColumn[] {bold("idx", "index-name", 200), col("stat", "sqlite-stat", 300)};
    }
    public String getSql() { return SQLiteSql.getTableStats(getDatabase(), currentSchemaName, currentObjectName); }
  }

  /** Indeksy tabeli (zastepuje stara zakladke: pochodzenie, czesciowy, kolumny) */
  public static class TableIndexes extends PropTab {
    public TableIndexes(IViewAccesibilities a) { super(a); }
    public String getPanelName() { return "sqlite-table-indexes"; }
    public String getPropColumnName() { return "name"; }
    public QueryTableColumn[] getTableColumns() {
      return new QueryTableColumn[] {
        bold("name", "index-name", 200), col("unique", "unique", 60), col("origin", "sqlite-origin", 60),
        col("partial", "sqlite-partial", 60), col("columns", "sqlite-columns", 250)};
    }
    public String getSql() { return SQLiteSql.getTableIndexes(currentSchemaName, currentObjectName); }
  }

  /** Kolumny tabeli lub widoku z wartoscia domyslna i kolumnami generowanymi */
  public static class Columns extends PropTab {
    private final String panelName;
    public Columns(IViewAccesibilities a, String panelName) { super(a); this.panelName = panelName; }
    public String getTitle() { return stringManager.getString("TableColumnsPanel-title"); }
    public String getPanelName() { return panelName; }
    public String getPropColumnName() { return "name"; }
    public QueryTableColumn[] getTableColumns() {
      return new QueryTableColumn[] {
        col("cid", "pos", 30), bold("name", "column-name", 150),
        new QueryTableColumn("type", stringManager.getString("column-type"), 120, new QueryTableCellRenderer(pl.mpak.sky.gui.swing.SwingUtil.Color.GREEN)),
        col("notnull", "sqlite-not-null", 60), col("dflt_value", "sqlite-default", 120), col("pk", "pk", 30),
        col("generated", "sqlite-generated", 80)};
    }
    public String getSql() { return SQLiteSql.getColumns(currentSchemaName, currentObjectName); }
  }

  /** Kolumny indeksu (z kolumnami dodatkowymi: rowid, klucz tabeli WITHOUT ROWID) */
  public static class IndexColumns extends PropTab {
    public IndexColumns(IViewAccesibilities a) { super(a); }
    public String getPanelName() { return "sqlite-index-columns"; }
    public String getPropColumnName() { return "name"; }
    public QueryTableColumn[] getTableColumns() {
      return new QueryTableColumn[] {
        col("seqno", "pos", 30), bold("name", "column-name", 150), col("desc", "sqlite-order", 50),
        col("coll", "sqlite-collation", 80), col("key", "sqlite-key", 40)};
    }
    public String getSql() { return SQLiteSql.getIndexColumns(currentSchemaName, currentObjectName); }
  }

  /** Szczegoly tabeli w ukladzie pionowym */
  public static class TableDetails extends UniversalPropDetailsTab {
    public TableDetails(IViewAccesibilities a) { super(a); }
    public String getTitle() { return stringManager.getString("TableDetails-title"); }
    public String getPanelName() { return "sqlite-table-details"; }
    public String getSql() { return SQLiteSql.getTableDetails(getDatabase(), currentSchemaName, currentObjectName); }
  }

  /** Zrodlo dowolnego obiektu z sqlite_master (indeks, obiekt z listy obiektow) */
  public static class Source extends UniversalSourceTab {
    private final String panelName;
    public Source(IViewAccesibilities a, String panelName) { super(a); this.panelName = panelName; }
    public String getTitle() { return stringManager.getString("TableSourcePanel-title"); }
    public String getPanelName() { return panelName; }
    public boolean isStorable() { return false; }
    public void updateBody(final AbsOrbadaSyntaxTextArea textArea) {
      final String sql = SQLiteSql.source(getDatabase(), currentSchemaName, currentObjectName);
      java.awt.EventQueue.invokeLater(new Runnable() {
        public void run() {
          String text = sql == null ? "-- " + stringManager.getString("sqlite-no-source") : sql;
          if (textArea instanceof OrbadaSyntaxTextArea) {
            ((OrbadaSyntaxTextArea)textArea).setDatabaseObject(currentSchemaName, null, currentObjectName, text);
          } else {
            textArea.setText(text);
          }
          textArea.getEditorArea().setCaretPosition(0);
        }
      });
    }
  }

}
