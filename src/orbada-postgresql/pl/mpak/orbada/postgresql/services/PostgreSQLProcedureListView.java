package pl.mpak.orbada.postgresql.services;

import java.awt.Component;
import javax.swing.Icon;
import pl.mpak.orbada.plugins.IViewAccesibilities;
import pl.mpak.orbada.plugins.providers.ViewProvider;
import pl.mpak.orbada.postgresql.OrbadaPostgreSQLPlugin;
import pl.mpak.orbada.postgresql.gui.functions.ProcedureListView;
import pl.mpak.usedb.core.Database;
import pl.mpak.util.StringManager;
import pl.mpak.util.StringManagerFactory;
import pl.mpak.util.id.VersionID;

/**
 * ORBADA 1.3.2: widok "Procedury" - procedury sa w PostgreSQL od wersji 11.
 */
public class PostgreSQLProcedureListView extends ViewProvider {

  private final StringManager stringManager = StringManagerFactory.getStringManager(OrbadaPostgreSQLPlugin.class);

  @Override
  public Component createView(IViewAccesibilities accesibilities) {
    return new ProcedureListView(accesibilities);
  }

  @Override
  public String getPublicName() {
    return stringManager.getString("PostgreSQLProcedureListView-public-name");
  }

  @Override
  public String getViewId() {
    return "orbada-postgresql-procedures-view";
  }

  @Override
  public Icon getIcon() {
    return pl.mpak.sky.gui.swing.ImageManager.getImage("/pl/mpak/res/icons/procedure.gif");
  }

  @Override
  public boolean isForDatabase(Database database) {
    if (database == null || !OrbadaPostgreSQLPlugin.driverType.equals(database.getDriverType())) {
      return false;
    }
    String version = PostgreSQLDbInfoProvider.instance.getVersion(database);
    return version != null && new VersionID(version).compare(11, 0) >= 0;
  }

  @Override
  public boolean isDefaultView() {
    return true;
  }

  @Override
  public String getDescription() {
    return stringManager.getString("PostgreSQLProcedureListView-description");
  }

  @Override
  public String getGroupName() {
    return OrbadaPostgreSQLPlugin.driverType;
  }

}
