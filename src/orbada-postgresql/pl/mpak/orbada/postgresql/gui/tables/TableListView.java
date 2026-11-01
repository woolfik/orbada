/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.mpak.orbada.postgresql.gui.tables;

import java.awt.Color;
import java.awt.Component;
import java.util.ArrayList;
import java.util.HashMap;
import javax.swing.JLabel;
import javax.swing.JTable;
import pl.mpak.orbada.plugins.IViewAccesibilities;
import pl.mpak.orbada.postgresql.OrbadaPostgreSQLPlugin;
import pl.mpak.orbada.postgresql.Sql;
import pl.mpak.orbada.postgresql.services.PostgreSQLDbInfoProvider;
import pl.mpak.orbada.universal.gui.filter.SqlFilter;
import pl.mpak.orbada.universal.gui.filter.SqlFilterDefComponent;
import pl.mpak.orbada.universal.gui.tabbed.UniversalViewTabs;
import pl.mpak.usedb.gui.swing.QueryTableCellRenderer;
import pl.mpak.usedb.gui.swing.QueryTableCellRendererFilter;
import pl.mpak.usedb.gui.swing.QueryTableColumn;
import pl.mpak.usedb.gui.swing.crf.ColorListCellRendererFilter;
import pl.mpak.util.ExceptionUtil;
import pl.mpak.util.StringManager;
import pl.mpak.util.StringManagerFactory;
import pl.mpak.util.id.VersionID;
import pl.mpak.util.variant.Variant;

/**
 *
 * @author akaluza
 */
public class TableListView extends UniversalViewTabs {

  private final StringManager stringManager = StringManagerFactory.getStringManager(OrbadaPostgreSQLPlugin.class);
  
  private String version;
  private HashMap<String, Variant> userData = new HashMap<String, Variant>();

  public TableListView(IViewAccesibilities accesibilities) {
    super(accesibilities);
  }
  
  @Override
  public void init() {
    version = PostgreSQLDbInfoProvider.instance.getVersion(getDatabase());
    super.init();
  }
  
  @Override
  public Component getTabbedPane() {
    return new TableTabbedPane(accesibilities);
  }

  @Override
  public String getCurrentSchemaName() {
    return PostgreSQLDbInfoProvider.getConnectedSchema(getDatabase());
  }

  @Override
  public String getPanelName() {
    return "postgresql-tables";
  }

  @Override
  public String getObjectColumnName() {
    return "table_name";
  }

  @Override
  public String getSchemaColumnName() {
    return "schema_name";
  }

  @Override
  public String getDescriptionColumnName() {
    return "description";
  }

  @Override
  public QueryTableColumn[] getTableColumns() {
    ArrayList<QueryTableColumn> columns = new ArrayList<QueryTableColumn>();
    columns.add(new QueryTableColumn("schema_name", stringManager.getString("schema-name"), 130, new QueryTableCellRenderer(java.awt.Font.BOLD)));
    columns.add(new QueryTableColumn("table_name", stringManager.getString("table-name"), 130, new QueryTableCellRenderer(java.awt.Font.BOLD)));
    columns.add(new QueryTableColumn("owner_name", stringManager.getString("owner-name"), 130));
    columns.add(new QueryTableColumn("table_space", stringManager.getString("table-space"), 130));
    columns.add(new QueryTableColumn("accessible", stringManager.getString("accessible"), 100, new QueryTableCellRenderer(new ColorListCellRendererFilter(new String[] {"NO"}, new Color[] {Color.RED}))));
    columns.add(new QueryTableColumn("description", stringManager.getString("description"), 250));
    columns.add(new QueryTableColumn("inheritance", stringManager.getString("inheritance"), 100, new QueryTableCellRenderer(new QueryTableCellRendererFilter() {
      @Override
      public void cellRendererPerformed(JTable table, Component renderer, Object value, boolean isSelected, boolean hasFocus) {
        if (value != null && !"".equals(value.toString())) {
          ((JLabel)renderer).setText(stringManager.getString("table-" +value.toString() +"-value"));
        }
      }
    })));
    if (new VersionID(version).compare(9, 1) >= 0) {
      columns.add(new QueryTableColumn("foreign_table", stringManager.getString("foreign-table"), 130));
    }
    return columns.toArray(new QueryTableColumn[columns.size()]);
  }

  @Override
  public String getSql(SqlFilter filter) {
    return Sql.getTableList(filter.getSqlText(), version);
  }

  @Override
  public SqlFilterDefComponent[] getFilterDefComponent() {
    ArrayList<SqlFilterDefComponent> filters = new ArrayList<SqlFilterDefComponent>();
    filters.add(new SqlFilterDefComponent("table_name", stringManager.getString("table-name"), (String[])null));
    filters.add(new SqlFilterDefComponent("owner_name", stringManager.getString("owner-name"), (String[])null));
    filters.add(new SqlFilterDefComponent("table_space", stringManager.getString("table-space"), (String[])null));
    filters.add(new SqlFilterDefComponent("description", stringManager.getString("description"), (String[])null));
    filters.add(new SqlFilterDefComponent("accessible", stringManager.getString("accessible"), (String[])null));
    filters.add(new SqlFilterDefComponent("inheritance", stringManager.getString("inheritance"), new String[] {"inherits", "inherited"}));
    filters.add(new SqlFilterDefComponent("coalesce(inheritance, '-') <> 'inherits'", stringManager.getString("skip-inherit")));
    if (new VersionID(version).compare(9, 1) >= 0) {
      filters.add(new SqlFilterDefComponent("foreign_table", stringManager.getString("foreign-table"), (String[])null));
    }
    return filters.toArray(new SqlFilterDefComponent[filters.size()]);
  }

  @Override
  public String[] getSchemaList() {
    return PostgreSQLDbInfoProvider.instance.getSchemas(getDatabase());
  }
  
  @Override
  protected HashMap<String, Variant> getUserData() {
    try {
      userData.put("foreign_table", getQuery().fieldByName("foreign_table").getValue());
      return userData;
    } catch (Exception ex) {
      ExceptionUtil.processException(ex);
      return null;
    }
  }
  
}
