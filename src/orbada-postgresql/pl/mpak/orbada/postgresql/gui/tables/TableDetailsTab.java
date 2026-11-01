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
import pl.mpak.orbada.postgresql.services.PostgreSQLDbInfoProvider;
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
public class TableDetailsTab extends UniversalPropDetailsTab implements ITabObjectUserData {

  private final StringManager stringManager = StringManagerFactory.getStringManager(OrbadaPostgreSQLPlugin.class);
  private boolean foreignTable;

  public TableDetailsTab(IViewAccesibilities accesibilities) {
    super(accesibilities);
  }

  @Override
  public String getPanelName() {
    return "postgresql-table-details";
  }

  @Override
  public String getSql() {
    if (foreignTable) {
      return Sql.getForeignTableDetail();
    }
    else {
      return Sql.getTableDetail(PostgreSQLDbInfoProvider.instance.getVersion(getDatabase()));
    }
  }

  @Override
  public void extraSqlParameters(ParametrizedCommand qc) throws UseDBException {
    qc.paramByName("schema_name").setString(currentSchemaName);
    qc.paramByName("table_name").setString(currentObjectName);
  }
  
  @Override
  public String getTitle() {
    return stringManager.getString("details");
  }

  @Override
  public void userData(HashMap<String, Variant> values) {
    foreignTable = values != null && !values.get("foreign_table").isNullValue();
  }
  
}
