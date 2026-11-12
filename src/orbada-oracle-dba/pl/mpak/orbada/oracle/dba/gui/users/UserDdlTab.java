package pl.mpak.orbada.oracle.dba.gui.users;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import pl.mpak.orbada.gui.comps.AbsOrbadaSyntaxTextArea;
import pl.mpak.orbada.oracle.dba.OrbadaOracleDbaPlugin;
import pl.mpak.orbada.plugins.IViewAccesibilities;
import pl.mpak.orbada.universal.gui.tabbed.UniversalSourceTab;
import pl.mpak.usedb.core.Database;
import pl.mpak.usedb.core.Query;
import pl.mpak.util.StringManager;
import pl.mpak.util.StringManagerFactory;
import pl.mpak.util.StringUtil;

/**
 * ORBADA 1.3.5: skrypt odtworzenia uzytkownika z DBMS_METADATA (CREATE USER, limity, role, przywileje systemowe
 * i do obiektow, role domyslne, proxy). Pobierany w tle - slownik bywa wolny.
 */
public class UserDdlTab extends UniversalSourceTab {

  private final StringManager stringManager = StringManagerFactory.getStringManager(OrbadaOracleDbaPlugin.class);

  private final static String[] GRANTED = {"TABLESPACE_QUOTA", "ROLE_GRANT", "SYSTEM_GRANT", "OBJECT_GRANT", "DEFAULT_ROLE", "PROXY"};

  /** Poczatek kolejnego polecenia w wyniku DBMS_METADATA (polecenia bez srednikow, oddzielone pustymi liniami) */
  private final static Pattern STATEMENT = Pattern.compile("(?m)^\\s*(?=(CREATE USER|ALTER USER|GRANT|DECLARE)\\b)");

  /** Numer pobrania - wynik starszego (inny uzytkownik) nie nadpisuje nowszego */
  private int loadNo = 0;

  public UserDdlTab(IViewAccesibilities accesibilities) {
    super(accesibilities);
  }

  public String getTitle() {
    return stringManager.getString("UserDdlTab-title");
  }

  @Override
  public String getPanelName() {
    return "oracle-user-ddl";
  }

  @Override
  public boolean isStorable() {
    return false;
  }

  @Override
  public void updateBody(final AbsOrbadaSyntaxTextArea textArea) {
    final String user = currentObjectName;
    final Database database = getDatabase();
    final int no = ++loadNo;
    if (StringUtil.isEmpty(user) || database == null) {
      textArea.setText("");
      return;
    }
    textArea.setText("-- " + stringManager.getString("users-loading"));
    Thread t = new Thread("orbada-oracle-user-ddl") {
      @Override
      public void run() {
        final String ddl = load(database, user);
        java.awt.EventQueue.invokeLater(new Runnable() {
          public void run() {
            if (no == loadNo) {
              textArea.setDatabaseObject("", "USER", user, ddl);
              textArea.getEditorArea().setCaretPosition(0);
            }
          }
        });
      }
    };
    t.setDaemon(true);
    t.start();
  }

  /** Skrypt uzytkownika; bledy (brak uprawnien) jako komentarz */
  public static String load(Database database, String user) {
    StringBuilder sb = new StringBuilder();
    Query query = database.createQuery();
    try {
      query.setSqlText("select dbms_metadata.get_ddl('USER', :username) ddl from dual");
      query.paramByName("username").setString(user);
      query.open();
      append(sb, query.fieldByName("ddl").getString());
    } catch (Exception ex) {
      sb.append("-- DBMS_METADATA.GET_DDL('USER'): ").append(firstLine(ex.getMessage())).append("\n");
    } finally {
      query.close();
    }
    for (String type : GRANTED) {
      try {
        query.setSqlText("select dbms_metadata.get_granted_ddl('" + type + "', :username) ddl from dual");
        query.paramByName("username").setString(user);
        query.open();
        append(sb, query.fieldByName("ddl").getString());
      } catch (Exception ex) {
        // ORA-31608: brak obiektow danego rodzaju (np. uzytkownik bez rol) - nic do dopisania
        if (ex.getMessage() == null || !ex.getMessage().contains("ORA-31608")) {
          sb.append("-- DBMS_METADATA.GET_GRANTED_DDL('").append(type).append("'): ").append(firstLine(ex.getMessage())).append("\n");
        }
      } finally {
        query.close();
      }
    }
    return sb.toString();
  }

  private static String firstLine(String s) {
    if (s == null) {
      return "";
    }
    int i = s.indexOf('\n');
    return (i >= 0 ? s.substring(0, i) : s).trim();
  }

  /** Polecenia z wyniku DBMS_METADATA rozdzielone: SQL ze srednikiem, blok PL/SQL z "/" */
  static void append(StringBuilder sb, String ddl) {
    if (StringUtil.isEmpty(ddl)) {
      return;
    }
    Matcher m = STATEMENT.matcher(ddl);
    int start = -1;
    while (m.find()) {
      if (start >= 0) {
        statement(sb, ddl.substring(start, m.start()));
      }
      start = m.end();
    }
    statement(sb, start >= 0 ? ddl.substring(start) : ddl);
  }

  private static void statement(StringBuilder sb, String s) {
    s = s.trim();
    if (s.length() == 0) {
      return;
    }
    if (s.startsWith("DECLARE")) {
      sb.append(s).append("\n/\n\n");
    } else {
      sb.append(s.endsWith(";") ? s : s + ";").append("\n\n");
    }
  }

}
