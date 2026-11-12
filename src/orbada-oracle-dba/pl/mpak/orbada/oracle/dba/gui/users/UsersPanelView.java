package pl.mpak.orbada.oracle.dba.gui.users;

import java.awt.Color;
import java.awt.Component;
import java.util.ArrayList;
import javax.swing.JLabel;
import javax.swing.JTable;
import pl.mpak.orbada.gui.OrbadaTabbedPane;
import pl.mpak.orbada.oracle.dba.OrbadaOracleDbaPlugin;
import pl.mpak.orbada.plugins.IViewAccesibilities;
import pl.mpak.orbada.universal.gui.filter.SqlFilter;
import pl.mpak.orbada.universal.gui.filter.SqlFilterDefComponent;
import pl.mpak.orbada.universal.gui.tabbed.UniversalViewTabs;
import pl.mpak.sky.gui.swing.SwingUtil;
import pl.mpak.usedb.gui.swing.QueryTableCellRenderer;
import pl.mpak.usedb.gui.swing.QueryTableColumn;
import pl.mpak.util.StringManager;
import pl.mpak.util.StringManagerFactory;
import pl.mpak.util.StringUtil;

/**
 * ORBADA 1.3.5: widok "Uzytkownicy" (jak Toad): lista uzytkownikow bazy z zakladkami szczegolow; akcje
 * (tworzenie, zmiana hasla, blokowanie, przywileje, ...) dostarcza OracleUserActionsService.
 * Bez dostepu do DBA_USERS - lista z ALL_USERS (same nazwy) i bez zakladek slownika DBA.
 */
public class UsersPanelView extends UniversalViewTabs {

  private final StringManager stringManager = StringManagerFactory.getStringManager(OrbadaOracleDbaPlugin.class);

  private boolean dba;
  /** Widok slownika listy (kolumny zalezne od wersji) */
  private String dict;

  public UsersPanelView(IViewAccesibilities accesibilities) {
    super(accesibilities);
  }

  @Override
  protected void init() {
    dba = UsersSql.isDbaView(getDatabase());
    dict = dba ? "DBA_USERS" : "ALL_USERS";
    super.init();
  }

  @Override
  public Component getTabbedPane() {
    boolean sessions = StringUtil.toBoolean(getDatabase().getUserProperties().getProperty("session-view-role", "false"));
    ArrayList<Component> tabs = new ArrayList<Component>();
    tabs.add(new UserDetailsTab(accesibilities));
    if (dba) {
      tabs.add(new UserPropTab.Roles(accesibilities));
      tabs.add(new UserPropTab.SysPrivs(accesibilities));
      tabs.add(new UserPropTab.ObjPrivs(accesibilities));
      tabs.add(new UserPropTab.Quotas(accesibilities));
      tabs.add(new UserPropTab.Objects(accesibilities));
      if (sessions) {
        tabs.add(new UserPropTab.Sessions(accesibilities));
      }
      tabs.add(new UserPropTab.Proxies(accesibilities));
    }
    tabs.add(new UserDdlTab(accesibilities));
    return new OrbadaTabbedPane("USER", tabs.toArray(new Component[tabs.size()]));
  }

  @Override
  public String getCurrentSchemaName() {
    return null;
  }

  @Override
  public String getPanelName() {
    return "oracle-users";
  }

  @Override
  public String getSchemaColumnName() {
    return null;
  }

  @Override
  public String getObjectColumnName() {
    return "username";
  }

  @Override
  public String getDescriptionColumnName() {
    return null;
  }

  @Override
  public boolean isHorizontal() {
    return false;
  }

  @Override
  public String[] getSchemaList() {
    return null;
  }

  private QueryTableColumn col(String name, String titleKey, int width, QueryTableCellRenderer renderer) {
    return new QueryTableColumn(name, stringManager.getString(titleKey), width, renderer);
  }

  @Override
  public QueryTableColumn[] getTableColumns() {
    QueryTableCellRenderer renderer = new UserRenderer(false);
    QueryTableCellRenderer status = new UserRenderer(true);
    ArrayList<QueryTableColumn> c = new ArrayList<QueryTableColumn>();
    c.add(col("username", "users-username", 160, renderer));
    if (dba) {
      c.add(col("account_status", "users-account-status", 130, status));
      c.add(col("lock_date", "users-lock-date", 120, renderer));
      c.add(col("expiry_date", "users-expiry-date", 120, renderer));
      c.add(col("default_tablespace", "users-default-tablespace", 110, renderer));
      c.add(col("temporary_tablespace", "users-temporary-tablespace", 110, renderer));
      c.add(col("profile", "users-profile", 100, renderer));
    }
    c.add(col("created", "created", 120, renderer));
    if (dba) {
      c.add(col("authentication_type", "users-authentication", 90, renderer));
      if (UsersSql.hasCol(getDatabase(), dict, "LAST_LOGIN")) {
        c.add(col("last_login", "users-last-login", 130, renderer));
      }
    }
    if (UsersSql.hasCol(getDatabase(), dict, "COMMON")) {
      c.add(col("common", "users-common", 60, renderer));
    }
    if (UsersSql.hasCol(getDatabase(), dict, "ORACLE_MAINTAINED")) {
      c.add(col("oracle_maintained", "users-oracle-maintained", 70, renderer));
    }
    c.add(col("user_id", "users-user-id", 60, renderer));
    return c.toArray(new QueryTableColumn[c.size()]);
  }

  /** Uzytkownik sesji pogrubiony; w kolumnie stanu: otwarte zielone, zablokowane czerwone, wygasle pomaranczowe */
  private class UserRenderer extends QueryTableCellRenderer {
    private final boolean status;

    UserRenderer(boolean status) {
      this.status = status;
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      JLabel label = (JLabel)super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      try {
        String name = getQuery().fieldByName("username").getString();
        if (name != null && name.equals(UsersSql.currentUser(getDatabase()))) {
          label.setFont(label.getFont().deriveFont(java.awt.Font.BOLD));
        }
        if (status && !isSelected) {
          String s = value == null ? "" : value.toString();
          if (s.equals("OPEN")) {
            label.setForeground(SwingUtil.Color.GREEN);
          } else if (s.contains("LOCKED")) {
            label.setForeground(Color.RED);
          } else if (s.contains("EXPIRED")) {
            label.setForeground(new Color(220, 120, 0));
          }
        }
      } catch (Exception ex) {
        // rekord niedostepny w trakcie odswiezania - bez wyroznien
      }
      return label;
    }
  }

  @Override
  public String getSql(SqlFilter filter) {
    return UsersSql.getUserList(filter == null ? null : filter.getSqlText(), dba, getDatabase());
  }

  @Override
  public SqlFilterDefComponent[] getFilterDefComponent() {
    ArrayList<SqlFilterDefComponent> f = new ArrayList<SqlFilterDefComponent>();
    if (UsersSql.hasCol(getDatabase(), dict, "ORACLE_MAINTAINED")) {
      f.add(new SqlFilterDefComponent("u.oracle_maintained = 'N'", stringManager.getString("users-filter-application")));
    }
    if (dba) {
      f.add(new SqlFilterDefComponent("u.account_status <> 'OPEN'", stringManager.getString("users-filter-not-open")));
    }
    f.add(new SqlFilterDefComponent("u.username", stringManager.getString("users-username"), (String[])null));
    if (dba) {
      f.add(new SqlFilterDefComponent("u.profile", stringManager.getString("users-profile"), (String[])null));
      f.add(new SqlFilterDefComponent("u.default_tablespace", stringManager.getString("users-default-tablespace"), (String[])null));
    }
    return f.toArray(new SqlFilterDefComponent[f.size()]);
  }

}
