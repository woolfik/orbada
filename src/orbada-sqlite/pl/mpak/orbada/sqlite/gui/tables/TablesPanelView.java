package pl.mpak.orbada.sqlite.gui.tables;

import java.awt.Component;
import pl.mpak.orbada.plugins.IViewAccesibilities;
import pl.mpak.orbada.sqlite.OrbadaSQLitePlugin;
import pl.mpak.orbada.sqlite.Sql;
import pl.mpak.orbada.sqlite.services.SQLiteDbInfoProvider;
import pl.mpak.orbada.universal.gui.filter.SqlFilter;
import pl.mpak.orbada.universal.gui.filter.SqlFilterDefComponent;
import pl.mpak.orbada.universal.gui.tabbed.UniversalViewTabs;
import pl.mpak.usedb.gui.swing.QueryTableCellRenderer;
import pl.mpak.usedb.gui.swing.QueryTableColumn;
import pl.mpak.util.StringManager;
import pl.mpak.util.StringManagerFactory;

/**
 *
 * @author  akaluza
 */
public class TablesPanelView extends UniversalViewTabs {
  
  private final StringManager stringManager = StringManagerFactory.getStringManager(OrbadaSQLitePlugin.class);

  public TablesPanelView(IViewAccesibilities accesibilities) {
    super(accesibilities);
  }
  
  @Override
  public Component getTabbedPane() {
    return new TableTabbedPane(accesibilities);
  }

  @Override
  public String getCurrentSchemaName() {
    return "main";
  }

  @Override
  public String getPanelName() {
    return "sqlite-tables";
  }

  @Override
  public String getObjectColumnName() {
    return "name";
  }

  @Override
  public String getSchemaColumnName() {
    // ORBADA 1.3.8: wybor bazy (main, temp, ATTACH) - kolumna database i parametr :database
    return "database";
  }

  @Override
  public String getDescriptionColumnName() {
    return null;
  }

  @Override
  public QueryTableColumn[] getTableColumns() {
    return new QueryTableColumn[] {
      new QueryTableColumn("name", stringManager.getString("table-name"), 150, new QueryTableCellRenderer(java.awt.Font.BOLD))
    };
  }

  @Override
  public String getSql(SqlFilter filter) {
    return "select * from (\n" +Sql.getTableList(currentSchemaName) +"\n) where :database is not null";
  }

  @Override
  public SqlFilterDefComponent[] getFilterDefComponent() {
    return null;
  }

  @Override
  public String[] getSchemaList() {
    // ORBADA 1.3.8: lista biezaca (po ATTACH/DETACH), nie z pamieci podrecznej DbInfo
    java.util.List<String> list = pl.mpak.orbada.sqlite.SQLiteSql.databases(getDatabase());
    return list.isEmpty() ? SQLiteDbInfoProvider.instance.getSchemas(getDatabase()) : list.toArray(new String[list.size()]);
  }

}
