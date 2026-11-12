package pl.mpak.orbada.oracle.dba.gui.users;

import pl.mpak.orbada.oracle.dba.OrbadaOracleDbaPlugin;
import pl.mpak.orbada.plugins.IViewAccesibilities;
import pl.mpak.orbada.universal.gui.tabbed.UniversalPropDetailsTab;
import pl.mpak.usedb.UseDBException;
import pl.mpak.usedb.core.ParametrizedCommand;
import pl.mpak.util.StringManager;
import pl.mpak.util.StringManagerFactory;

/**
 * ORBADA 1.3.5: wszystkie kolumny DBA_USERS (albo ALL_USERS bez dostepu do slownika DBA) zaznaczonego uzytkownika.
 */
public class UserDetailsTab extends UniversalPropDetailsTab {

  private final StringManager stringManager = StringManagerFactory.getStringManager(OrbadaOracleDbaPlugin.class);

  public UserDetailsTab(IViewAccesibilities accesibilities) {
    super(accesibilities);
  }

  public String getTitle() {
    return stringManager.getString("UserDetailsTab-title");
  }

  @Override
  public String getPanelName() {
    return "oracle-user-details";
  }

  @Override
  public String getSql() {
    return UsersSql.getUserDetails(UsersSql.isDbaView(getDatabase()));
  }

  @Override
  public void extraSqlParameters(ParametrizedCommand qc) throws UseDBException {
    qc.paramByName("username").setString(currentObjectName);
  }

}
