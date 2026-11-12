package pl.mpak.orbada.oracle.dba.gui.users;

import pl.mpak.usedb.core.Database;
import pl.mpak.util.StringUtil;

/**
 * ORBADA 1.3.5: zapytania i uprawnienia widoku "Uzytkownicy" (zarzadzanie uzytkownikami jak w Toad).
 * Uprawnienia sesji ustala OracleDbaDatabaseProvider.afterConnection (bez zapytan w watku GUI):
 * "users-dba-view" - dostep do DBA_USERS, "users-privs" - przywileje systemowe sesji potrzebne do akcji.
 */
public final class UsersSql {

  /** Przywileje sesji sprawdzane po polaczeniu */
  public final static String[] PRIVILEGES = {
    "CREATE USER", "ALTER USER", "DROP USER", "GRANT ANY ROLE", "GRANT ANY PRIVILEGE", "GRANT ANY OBJECT PRIVILEGE", "ALTER SYSTEM"
  };

  /** Pseudo-przywileje w "users-privs": sesja ma role / przywileje systemowe z ADMIN OPTION, nadala komus prawa do obiektow */
  public final static String ADMIN_ROLE = "ADMIN ROLE";
  public final static String ADMIN_SYS_PRIV = "ADMIN SYS PRIV";
  public final static String OBJ_GRANTOR = "OBJECT GRANTOR";

  public static String getAdminOptions() {
    return
      "select (select count(*) from user_role_privs where admin_option = 'YES') roles,\n" +
      "       (select count(*) from user_sys_privs where admin_option = 'YES') sys_privs,\n" +
      "       (select count(*) from user_tab_privs_made where rownum = 1) obj_grants,\n" +
      "       user username\n" +
      "  from dual";
  }

  private UsersSql() {
  }

  public static String getSessionPrivileges() {
    StringBuilder sb = new StringBuilder("select privilege from session_privs where privilege in (");
    for (int i = 0; i < PRIVILEGES.length; i++) {
      sb.append(i > 0 ? ", " : "").append('\'').append(PRIVILEGES[i]).append('\'');
    }
    return sb.append(")").toString();
  }

  public static String getDbaUsersAccess() {
    return "select count(*) cnt from dba_users where rownum = 1";
  }

  /** DBA_USERS dostepny (SELECT ANY DICTIONARY, SELECT_CATALOG_ROLE, DBA) */
  public static boolean isDbaView(Database database) {
    return StringUtil.toBoolean(database.getUserProperties().getProperty("users-dba-view", "false"));
  }

  /**
   * Czy sesja ma przywilej systemowy. Gdy przywilejow nie ustalono (np. polaczenie sprzed wlaczenia wtyczki) -
   * true: o uprawnieniach rozstrzygnie baza (ORA-01031).
   */
  public static boolean has(Database database, String privilege) {
    String privs = database.getUserProperties().getProperty("users-privs");
    if (privs == null) {
      return true;
    }
    return ("," + privs + ",").contains("," + privilege + ",");
  }

  /** Nazwa uzytkownika sesji (wielkie litery) */
  public static String currentUser(Database database) {
    return StringUtil.nvl(database.getUserProperties().getProperty("users-current-user"), "");
  }

  public static int major(Database database) {
    try {
      return database.getConnection().getMetaData().getDatabaseMajorVersion();
    } catch (Exception ex) {
      return 11;
    }
  }

  private static String where(String filter) {
    return filter != null ? " where " + filter + "\n" : "";
  }

  /** Kolumny slownika zalezne od wersji (rozne wydania 12c/18c/19c+) - sprawdzane po polaczeniu w ALL_TAB_COLUMNS */
  public final static String[] DICT_TABLES = {"DBA_USERS", "ALL_USERS", "DBA_ROLE_PRIVS", "DBA_SYS_PRIVS", "DBA_TAB_PRIVS"};
  public final static String[] DICT_COLUMNS = {"COMMON", "ORACLE_MAINTAINED", "LAST_LOGIN", "INHERITED", "TYPE"};

  public static String getDictColumns() {
    return
      "select table_name || '.' || column_name col from all_tab_columns\n" +
      " where owner = 'SYS' and table_name in (" + inList(DICT_TABLES) + ")\n" +
      "   and column_name in (" + inList(DICT_COLUMNS) + ")";
  }

  private static String inList(String[] items) {
    StringBuilder sb = new StringBuilder();
    for (String i : items) {
      sb.append(sb.length() > 0 ? ", " : "").append('\'').append(i).append('\'');
    }
    return sb.toString();
  }

  /**
   * Czy kolumna slownika istnieje (np. "ALL_USERS", "COMMON"). Bez wykrytych kolumn (polaczenie sprzed wtyczki) -
   * wedlug wersji: od 12c.
   */
  public static boolean hasCol(Database database, String table, String column) {
    String cols = database.getUserProperties().getProperty("users-dict-cols");
    if (cols == null) {
      return major(database) >= 12;
    }
    return ("," + cols + ",").contains("," + table + "." + column + ",");
  }

  /** Fragment SQL tylko gdy kolumna istnieje w widoku slownika */
  private static String opt(Database db, String table, String column, String sql) {
    return hasCol(db, table, column) ? sql : "";
  }

  public static String getUserList(String filter, boolean dba, Database db) {
    if (dba) {
      return
        "select u.username, u.account_status, u.lock_date, u.expiry_date, u.default_tablespace, u.temporary_tablespace,\n" +
        "       u.profile, u.created, u.authentication_type" +
        opt(db, "DBA_USERS", "LAST_LOGIN", ", to_char(u.last_login, 'YYYY-MM-DD HH24:MI:SS') last_login") +
        opt(db, "DBA_USERS", "COMMON", ", u.common") +
        opt(db, "DBA_USERS", "ORACLE_MAINTAINED", ", u.oracle_maintained") +
        ", u.user_id\n" +
        "  from dba_users u\n" +
        where(filter) +
        " order by u.username";
    }
    return
      "select u.username, u.created" +
      opt(db, "ALL_USERS", "COMMON", ", u.common") +
      opt(db, "ALL_USERS", "ORACLE_MAINTAINED", ", u.oracle_maintained") +
      ", u.user_id\n" +
      "  from all_users u\n" +
      where(filter) +
      " order by u.username";
  }

  public static String getUserDetails(boolean dba) {
    return dba ? "select * from dba_users where username = :username" : "select * from all_users where username = :username";
  }

  public static String getRoles(String filter, Database db) {
    return
      "select granted_role, admin_option, default_role" +
      opt(db, "DBA_ROLE_PRIVS", "COMMON", ", common") + opt(db, "DBA_ROLE_PRIVS", "INHERITED", ", inherited") + "\n" +
      "  from dba_role_privs\n" +
      " where grantee = :username\n" +
      (filter != null ? "   and " + filter + "\n" : "") +
      " order by granted_role";
  }

  public static String getSysPrivs(String filter, Database db) {
    return
      "select privilege, admin_option" +
      opt(db, "DBA_SYS_PRIVS", "COMMON", ", common") + opt(db, "DBA_SYS_PRIVS", "INHERITED", ", inherited") + "\n" +
      "  from dba_sys_privs\n" +
      " where grantee = :username\n" +
      (filter != null ? "   and " + filter + "\n" : "") +
      " order by privilege";
  }

  public static String getObjPrivs(String filter, Database db) {
    return
      "select owner, table_name, privilege, grantable, grantor" +
      opt(db, "DBA_TAB_PRIVS", "TYPE", ", type") + opt(db, "DBA_TAB_PRIVS", "COMMON", ", common") +
      ", owner || '.' || table_name || '.' || privilege priv_id\n" +
      "  from dba_tab_privs\n" +
      " where grantee = :username\n" +
      (filter != null ? "   and " + filter + "\n" : "") +
      " order by owner, table_name, privilege";
  }

  public static String getQuotas(String filter) {
    return
      "select tablespace_name, round(bytes / 1024 / 1024, 2) used_mb,\n" +
      "       decode(max_bytes, -1, 'UNLIMITED', to_char(round(max_bytes / 1024 / 1024, 2))) max_mb\n" +
      "  from dba_ts_quotas\n" +
      " where username = :username\n" +
      (filter != null ? "   and " + filter + "\n" : "") +
      " order by tablespace_name";
  }

  public static String getObjects(String filter) {
    return
      "select object_type, count(*) cnt, sum(decode(status, 'VALID', 0, 1)) invalid_cnt, max(last_ddl_time) last_ddl_time\n" +
      "  from dba_objects\n" +
      " where owner = :username\n" +
      (filter != null ? "   and " + filter + "\n" : "") +
      " group by object_type\n" +
      " order by object_type";
  }

  public static String getSessions(String filter) {
    return
      "select s.sid, s.serial#, s.status, s.osuser, s.machine, s.program, s.module, s.logon_time\n" +
      "  from v$session s\n" +
      " where s.username = :username\n" +
      (filter != null ? "   and " + filter + "\n" : "") +
      " order by s.logon_time";
  }

  public static String getProxies(String filter) {
    return
      "select proxy, client, authentication, flags\n" +
      "  from proxy_users\n" +
      " where (client = :username or proxy = :username)\n" +
      (filter != null ? "   and " + filter + "\n" : "") +
      " order by proxy, client";
  }

  public static String getUserSessionsToKill() {
    // bez wlasnej sesji (ORA-00027)
    return "select sid, serial# from v$session where username = :username and type <> 'BACKGROUND'\n" +
      "   and sid <> to_number(sys_context('USERENV', 'SID'))";
  }

}
