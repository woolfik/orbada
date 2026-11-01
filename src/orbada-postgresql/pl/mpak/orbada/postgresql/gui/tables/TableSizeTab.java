/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.mpak.orbada.postgresql.gui.tables;

import java.util.HashMap;
import pl.mpak.orbada.gui.ITabObjectUserData;
import pl.mpak.orbada.plugins.IViewAccesibilities;
import pl.mpak.orbada.postgresql.OrbadaPostgreSQLPlugin;
import pl.mpak.orbada.postgresql.Sql;
import pl.mpak.orbada.universal.gui.tabbed.UniversalPropDetailsTab;
import pl.mpak.usedb.UseDBException;
import pl.mpak.usedb.core.ParametrizedCommand;
import pl.mpak.util.StringManager;
import pl.mpak.util.StringManagerFactory;
import pl.mpak.util.variant.Variant;

/**
 *
 * @author akaluza
 */
public class TableSizeTab extends UniversalPropDetailsTab implements ITabObjectUserData {

  private final StringManager stringManager = StringManagerFactory.getStringManager(OrbadaPostgreSQLPlugin.class);
  private boolean foreignTable;

  public TableSizeTab(IViewAccesibilities accesibilities) {
    super(accesibilities);
  }

  @Override
  public String getPanelName() {
    return "postgresql-table-size";
  }

  @Override
  public String getSql() {
    if (foreignTable) {
      return Sql.getForeignTableSizeList();
    }
    else {
      return Sql.getTableSizeList(null);
    }
  }

  @Override
  public void extraSqlParameters(ParametrizedCommand qc) throws UseDBException {
    qc.paramByName("schema_name").setString(currentSchemaName);
    qc.paramByName("table_name").setString(currentObjectName);
  }
  
  @Override
  public String getTitle() {
    return stringManager.getString("size");
  }

  @Override
  public void userData(HashMap<String, Variant> values) {
    foreignTable = values != null && !values.get("foreign_table").isNullValue();
  }
  
}
