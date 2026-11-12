package pl.mpak.orbada.oracle.dba.gui.users;

import pl.mpak.orbada.oracle.dba.OrbadaOracleDbaPlugin;
import pl.mpak.orbada.plugins.IViewAccesibilities;
import pl.mpak.orbada.universal.gui.filter.SqlFilter;
import pl.mpak.orbada.universal.gui.filter.SqlFilterDefComponent;
import pl.mpak.orbada.universal.gui.tabbed.UniversalPropTab;
import pl.mpak.usedb.gui.swing.QueryTableColumn;
import pl.mpak.util.StringManager;
import pl.mpak.util.StringManagerFactory;

/**
 * ORBADA 1.3.5: zakladka widoku "Uzytkownicy" (role, przywileje, limity, obiekty, sesje, proxy) - lista z zapytania
 * z parametrem :username. Akcje zakladki: typ "nazwa-panelu-actions" (OracleUserActionsService).
 */
public abstract class UserPropTab extends UniversalPropTab {

  protected final StringManager stringManager = StringManagerFactory.getStringManager(OrbadaOracleDbaPlugin.class);

  public UserPropTab(IViewAccesibilities accesibilities) {
    super(accesibilities);
  }

  /** Kolumny listy bez pozycji null (kolumna nieobecna w danej wersji slownika) */
  protected static QueryTableColumn[] cols(QueryTableColumn... c) {
    java.util.List<QueryTableColumn> r = new java.util.ArrayList<QueryTableColumn>();
    for (QueryTableColumn q : c) {
      if (q != null) {
        r.add(q);
      }
    }
    return r.toArray(new QueryTableColumn[r.size()]);
  }

  /** Kolumna tylko gdy istnieje w widoku slownika */
  protected QueryTableColumn opt(String table, String column, String name, String titleKey, int width) {
    return UsersSql.hasCol(getDatabase(), table, column) ? col(name, titleKey, width) : null;
  }

  /** Klucz i18n tytulu: "NazwaKlasy-title" */
  public String getTitle() {
    return stringManager.getString(getClass().getSimpleName() + "-title");
  }

  @Override
  public String getSchemaColumnName() {
    return null;
  }

  @Override
  public String getObjectColumnName() {
    return "username";
  }

  /** Tekst filtra albo null (zakladka bez filtra) */
  protected static String f(SqlFilter filter) {
    return filter == null ? null : filter.getSqlText();
  }

  protected QueryTableColumn col(String name, String titleKey, int width) {
    return new QueryTableColumn(name, stringManager.getString(titleKey), width);
  }

  protected SqlFilterDefComponent text(String column, String titleKey) {
    return new SqlFilterDefComponent(column, stringManager.getString(titleKey), (String[])null);
  }

  // ------------------------------------------------------------------ zakladki

  public static class Roles extends UserPropTab {
    public Roles(IViewAccesibilities a) { super(a); }
    public String getPanelName() { return "oracle-user-roles"; }
    public String getPropColumnName() { return "granted_role"; }
    public QueryTableColumn[] getTableColumns() {
      return cols(col("granted_role", "users-role", 200), col("admin_option", "users-admin-option", 80),
        col("default_role", "users-default-role", 80), opt("DBA_ROLE_PRIVS", "COMMON", "common", "users-common", 60),
        opt("DBA_ROLE_PRIVS", "INHERITED", "inherited", "users-inherited", 60));
    }
    public String getSql(SqlFilter filter) { return UsersSql.getRoles(f(filter), getDatabase()); }
    public SqlFilterDefComponent[] getFilterDefComponent() {
      return new SqlFilterDefComponent[] {text("granted_role", "users-role")};
    }
  }

  public static class SysPrivs extends UserPropTab {
    public SysPrivs(IViewAccesibilities a) { super(a); }
    public String getPanelName() { return "oracle-user-sysprivs"; }
    public String getPropColumnName() { return "privilege"; }
    public QueryTableColumn[] getTableColumns() {
      return cols(col("privilege", "users-privilege", 220), col("admin_option", "users-admin-option", 80),
        opt("DBA_SYS_PRIVS", "COMMON", "common", "users-common", 60), opt("DBA_SYS_PRIVS", "INHERITED", "inherited", "users-inherited", 60));
    }
    public String getSql(SqlFilter filter) { return UsersSql.getSysPrivs(f(filter), getDatabase()); }
    public SqlFilterDefComponent[] getFilterDefComponent() {
      return new SqlFilterDefComponent[] {text("privilege", "users-privilege")};
    }
  }

  public static class ObjPrivs extends UserPropTab {
    public ObjPrivs(IViewAccesibilities a) { super(a); }
    public String getPanelName() { return "oracle-user-objprivs"; }
    public String getPropColumnName() { return "priv_id"; }
    public QueryTableColumn[] getTableColumns() {
      return cols(col("owner", "users-owner", 100), col("table_name", "users-object", 180),
        col("privilege", "users-privilege", 120), col("grantable", "users-grantable", 60), col("grantor", "users-grantor", 100),
        opt("DBA_TAB_PRIVS", "TYPE", "type", "users-object-type", 90), opt("DBA_TAB_PRIVS", "COMMON", "common", "users-common", 60));
    }
    public String getSql(SqlFilter filter) { return UsersSql.getObjPrivs(f(filter), getDatabase()); }
    public SqlFilterDefComponent[] getFilterDefComponent() {
      return new SqlFilterDefComponent[] {text("owner", "users-owner"), text("table_name", "users-object"), text("privilege", "users-privilege")};
    }
  }

  public static class Quotas extends UserPropTab {
    public Quotas(IViewAccesibilities a) { super(a); }
    public String getPanelName() { return "oracle-user-quotas"; }
    public String getPropColumnName() { return "tablespace_name"; }
    public QueryTableColumn[] getTableColumns() {
      return new QueryTableColumn[] {col("tablespace_name", "users-tablespace", 180), col("used_mb", "users-used-mb", 100),
        col("max_mb", "users-max-mb", 100)};
    }
    public String getSql(SqlFilter filter) { return UsersSql.getQuotas(f(filter)); }
    public SqlFilterDefComponent[] getFilterDefComponent() { return null; }
  }

  public static class Objects extends UserPropTab {
    public Objects(IViewAccesibilities a) { super(a); }
    public String getPanelName() { return "oracle-user-objects"; }
    public String getPropColumnName() { return "object_type"; }
    public QueryTableColumn[] getTableColumns() {
      return new QueryTableColumn[] {col("object_type", "users-object-type", 160), col("cnt", "users-count", 70),
        col("invalid_cnt", "users-invalid-count", 90), col("last_ddl_time", "users-last-ddl", 130)};
    }
    public String getSql(SqlFilter filter) { return UsersSql.getObjects(f(filter)); }
    public SqlFilterDefComponent[] getFilterDefComponent() { return null; }
  }

  public static class Sessions extends UserPropTab {
    public Sessions(IViewAccesibilities a) { super(a); }
    public String getPanelName() { return "oracle-user-sessions"; }
    public String getPropColumnName() { return "sid"; }
    public QueryTableColumn[] getTableColumns() {
      return new QueryTableColumn[] {col("sid", "sid", 50), col("serial#", "serial-hash", 60), col("status", "status", 70),
        col("osuser", "os-user-name", 100), col("machine", "machine", 120), col("program", "user-program", 150),
        col("module", "application-module", 120), col("logon_time", "login-time", 120)};
    }
    public String getSql(SqlFilter filter) { return UsersSql.getSessions(f(filter)); }
    public SqlFilterDefComponent[] getFilterDefComponent() { return null; }
  }

  public static class Proxies extends UserPropTab {
    public Proxies(IViewAccesibilities a) { super(a); }
    public String getPanelName() { return "oracle-user-proxies"; }
    public String getPropColumnName() { return "proxy"; }
    public QueryTableColumn[] getTableColumns() {
      return new QueryTableColumn[] {col("proxy", "users-proxy", 120), col("client", "users-client", 120),
        col("authentication", "users-authentication", 90), col("flags", "users-flags", 250)};
    }
    public String getSql(SqlFilter filter) { return UsersSql.getProxies(f(filter)); }
    public SqlFilterDefComponent[] getFilterDefComponent() { return null; }
  }

}
