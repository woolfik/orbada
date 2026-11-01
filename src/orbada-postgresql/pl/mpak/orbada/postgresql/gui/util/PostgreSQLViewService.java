package pl.mpak.orbada.postgresql.gui.util;

import java.awt.Component;
import javax.swing.Icon;
import pl.mpak.orbada.postgresql.OrbadaPostgreSQLPlugin;
import pl.mpak.orbada.plugins.IViewAccesibilities;
import pl.mpak.orbada.plugins.providers.ViewProvider;
import pl.mpak.usedb.core.Database;

/**
 *
 * @author akaluza
 */
public abstract class PostgreSQLViewService extends ViewProvider {

  @Override
  abstract public Component createView(IViewAccesibilities accesibilities);
  
  @Override
  abstract public String getPublicName();
  
  @Override
  public String getViewId() {
    return null;
  }
  
  @Override
  abstract public Icon getIcon();

  @Override
  public boolean isForDatabase(Database database) {
    return true;
  }

  @Override
  public String getDescription() {
    return "\"" +OrbadaPostgreSQLPlugin.driverType +"\"";
  }

  @Override
  public String getGroupName() {
    return OrbadaPostgreSQLPlugin.driverType;
  }
  
}
