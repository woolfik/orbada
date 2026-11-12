package pl.mpak.orbada.oracle.dba.services;

import java.awt.Component;
import javax.swing.Icon;
import pl.mpak.orbada.oracle.OrbadaOraclePlugin;
import pl.mpak.orbada.oracle.dba.OrbadaOracleDbaPlugin;
import pl.mpak.orbada.oracle.dba.gui.users.UsersPanelView;
import pl.mpak.orbada.plugins.IViewAccesibilities;
import pl.mpak.orbada.plugins.providers.ViewProvider;
import pl.mpak.usedb.core.Database;
import pl.mpak.util.StringManager;
import pl.mpak.util.StringManagerFactory;

/**
 * ORBADA 1.3.5: widok "Uzytkownicy" - zarzadzanie uzytkownikami bazy Oracle (jak w Toad).
 */
public class OracleUsersView extends ViewProvider {
 
  private final StringManager stringManager = StringManagerFactory.getStringManager(OrbadaOracleDbaPlugin.class);

  public Component createView(IViewAccesibilities accesibilities) {
    return new UsersPanelView(accesibilities);
  }
  
  public String getPublicName() {
    return stringManager.getString("OracleUsersView-public-name");
  }
  
  public String getViewId() {
    return "orbada-oracle-users-view";
  }
  
  public Icon getIcon() {
    return pl.mpak.sky.gui.swing.ImageManager.getImage("/pl/mpak/res/icons/users16.gif");
  }

  public boolean isForDatabase(Database database) {
    if (database == null) {
      return false;
    }
    // ORBADA 1.3.5: zawsze dla Oracle - bez dostepu do DBA_USERS lista z ALL_USERS, akcje wg przywilejow sesji
    return OrbadaOraclePlugin.oracleDriverType.equals(database.getDriverType());
  }

  public String getDescription() {
    return stringManager.getString("OracleUsersView-description");
  }

  public String getGroupName() {
    return OrbadaOraclePlugin.oracleDriverType;
  }
  
  @Override
  public String[] getSubmenu() {
    return new String[] {OrbadaOraclePlugin.adminGroup};
  }
  
}
