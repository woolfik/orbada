package pl.mpak.orbada.oracle.dba.services;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import pl.mpak.orbada.gui.IRootTabObjectInfo;
import pl.mpak.orbada.gui.ITabObjectInfo;
import pl.mpak.orbada.gui.comps.table.ViewTable;
import pl.mpak.orbada.oracle.OrbadaOraclePlugin;
import pl.mpak.orbada.oracle.dba.OrbadaOracleDbaPlugin;
import pl.mpak.orbada.oracle.dba.gui.users.UserWizard;
import pl.mpak.orbada.oracle.dba.gui.users.UserWizards;
import pl.mpak.orbada.oracle.dba.gui.users.UsersSql;
import pl.mpak.orbada.plugins.ComponentAction;
import pl.mpak.orbada.plugins.providers.ComponentActionsProvider;
import pl.mpak.orbada.universal.gui.tabbed.UniversalPropTab;
import pl.mpak.orbada.universal.gui.wizards.SqlCodeWizardDialog;
import pl.mpak.sky.gui.mr.ModalResult;
import pl.mpak.sky.gui.swing.MessageBox;
import pl.mpak.sky.gui.swing.SwingUtil;
import pl.mpak.usedb.core.Database;
import pl.mpak.usedb.core.Query;
import pl.mpak.util.StringManager;
import pl.mpak.util.StringManagerFactory;
import pl.mpak.util.StringUtil;

/**
 * ORBADA 1.3.5: akcje widoku "Uzytkownicy" (lista i zakladki): tworzenie, zmiana hasla, blokowanie/odblokowanie,
 * wygaszenie hasla, wlasciwosci, limity, role, przywileje systemowe i do obiektow, proxy, sesje, usuwanie.
 * Akcja bez wymaganego przywileju sesji (UsersSql.has) jest wylaczona.
 */
public class OracleUserActionsService extends ComponentActionsProvider {

  private final static StringManager stringManager = StringManagerFactory.getStringManager(OrbadaOracleDbaPlugin.class);

  /** Wywolanie akcji dla uzytkownika; true - odswiezyc widok */
  interface Handler {
    boolean run(Database database, String user, Query row, Component parent) throws Exception;
  }

  /** Akcja na uzytkowniku zaznaczonym w liscie albo uzytkowniku zakladki (row - wiersz zakladki) */
  static class UserAction extends ComponentAction {

    private final Handler handler;
    private final boolean needsUser;

    UserAction(String textKey, String icon, boolean needsUser, Handler handler) {
      super();
      this.handler = handler;
      this.needsUser = needsUser;
      setText(stringManager.getString(textKey));
      if (icon != null) {
        setSmallIcon(pl.mpak.sky.gui.swing.ImageManager.getImage("/pl/mpak/res/icons/" + icon));
      }
      setActionCommandKey("OracleUserAction-" + textKey);
      addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          perform();
        }
      });
    }

    /** Wylaczenie, gdy sesja nie ma zadnego z przywilejow (bez przywilejow - zawsze dostepna) */
    UserAction requires(Database database, String... privileges) {
      if (privileges.length == 0) {
        return this;
      }
      for (String p : privileges) {
        if (UsersSql.has(database, p)) {
          return this;
        }
      }
      setEnabled(false);
      StringBuilder sb = new StringBuilder();
      for (String p : privileges) {
        sb.append(sb.length() > 0 ? ", " : "").append(p);
      }
      setTooltip(stringManager.getString("users-no-privilege", sb.toString()));
      return this;
    }

    private void perform() {
      Component parent = getComponent();
      try {
        String user = null;
        Query row = null;
        UniversalPropTab tab = parent == null ? null : (UniversalPropTab)SwingUtil.getOwnerComponent(UniversalPropTab.class, parent);
        if (parent instanceof ViewTable) {
          ViewTable vt = (ViewTable)parent;
          if (vt.getSelectedRow() >= 0 && vt.getQuery().isActive() && !vt.getQuery().isEmpty()) {
            vt.getQuery().getRecord(vt.getSelectedRow());
            row = vt.getQuery();
          }
        }
        if (tab != null) {
          user = tab.getCurrentObjectName();
        } else if (row != null && row.findFieldByName("username") != null) {
          user = row.fieldByName("username").getString();
        }
        if (needsUser && StringUtil.isEmpty(user)) {
          MessageBox.show(stringManager.getString("error"), stringManager.getString("users-select-user"), ModalResult.OK);
          return;
        }
        if (handler.run(getDatabase(), user, row, parent)) {
          final IRootTabObjectInfo root = parent == null ? null : (IRootTabObjectInfo)SwingUtil.getOwnerComponent(IRootTabObjectInfo.class, parent);
          final ITabObjectInfo t = tab;
          java.awt.EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
              if (root != null) {
                root.refresh();
              }
              if (t != null) {
                t.refresh();
              }
            }
          });
        }
      } catch (Exception ex) {
        MessageBox.show(stringManager.getString("error"), ex.getMessage(), ModalResult.OK);
      }
    }
  }

  @Override
  public ComponentAction[] getForComponent(Database database, String actionType) {
    if (database == null || !OrbadaOraclePlugin.oracleDriverType.equals(database.getDriverType()) || actionType == null) {
      return null;
    }
    List<ComponentAction> a = new ArrayList<ComponentAction>();
    boolean dba = UsersSql.isDbaView(database);
    if ("oracle-users-actions".equals(actionType)) {
      a.add(new UserAction("users-action-create", "new_user.gif", false, wizard(new WizardFactory() {
        public UserWizard create(Database db, String user, Query row) { return new UserWizards.CreateUser(db, null); }
      })).requires(database, "CREATE USER"));
      if (dba) {
        a.add(new UserAction("users-action-create-like", "copy.gif", true, wizard(new WizardFactory() {
          public UserWizard create(Database db, String user, Query row) { return new UserWizards.CreateUser(db, user); }
        })).requires(database, "CREATE USER"));
      }
      a.add(ComponentAction.Separator);
      a.add(changePassword(database));
      a.add(new UserAction("users-action-lock", "lock16.gif", true, alter("users-action-lock", "users-lock-q", "ACCOUNT LOCK")).requires(database, "ALTER USER"));
      a.add(new UserAction("users-action-unlock", "check.gif", true, alter("users-action-unlock", "users-unlock-q", "ACCOUNT UNLOCK")).requires(database, "ALTER USER"));
      a.add(new UserAction("users-action-expire", "clock.png", true, alter("users-action-expire", "users-expire-q", "PASSWORD EXPIRE")).requires(database, "ALTER USER"));
      if (dba) {
        a.add(ComponentAction.Separator);
        a.add(alterUser(database));
        a.add(quota(database, false));
        a.add(grant(database, false, false));
        a.add(grant(database, false, true));
        a.add(grant(database, true, false));
        a.add(grant(database, true, true));
        a.add(proxy(database, false));
      }
      if (StringUtil.toBoolean(database.getUserProperties().getProperty("session-view-role", "false"))) {
        a.add(ComponentAction.Separator);
        a.add(killSessions(database));
      }
      a.add(ComponentAction.Separator);
      a.add(new UserAction("users-action-drop", "trash.gif", true, wizard(new WizardFactory() {
        public UserWizard create(Database db, String user, Query row) { return new UserWizards.DropUser(db, user); }
      })).requires(database, "DROP USER"));
    } else if ("oracle-user-roles-actions".equals(actionType)) {
      a.add(grant(database, false, false));
      a.add(grant(database, false, true));
    } else if ("oracle-user-sysprivs-actions".equals(actionType)) {
      a.add(grant(database, true, false));
      a.add(grant(database, true, true));
    } else if ("oracle-user-objprivs-actions".equals(actionType)) {
      a.add(revokeObjectPrivilege(database));
    } else if ("oracle-user-quotas-actions".equals(actionType)) {
      a.add(quota(database, true));
    } else if ("oracle-user-proxies-actions".equals(actionType)) {
      a.add(proxy(database, false));
      a.add(proxy(database, true));
    } else if ("oracle-user-sessions-actions".equals(actionType)) {
      a.add(killSession(database));
    }
    if (a.isEmpty()) {
      return null;
    }
    // bez separatora na poczatku - ComponentActionsAction sam go dodaje przed pierwsza akcja, gdy menu nie jest puste
    return a.toArray(new ComponentAction[a.size()]);
  }

  // ---------------------------------------------------------------- kreatory

  interface WizardFactory {
    UserWizard create(Database database, String user, Query row) throws Exception;
  }

  private static Handler wizard(final WizardFactory factory) {
    return new Handler() {
      public boolean run(Database database, String user, Query row, Component parent) throws Exception {
        UserWizard w = factory.create(database, user, row);
        return w != null && SqlCodeWizardDialog.show(w, true) != null;
      }
    };
  }

  private static String field(Query row, String name) throws Exception {
    return row == null || row.findFieldByName(name) == null ? null : row.fieldByName(name).getString();
  }

  private static UserAction changePassword(final Database database) {
    return new UserAction("users-action-password", "primary_key16.gif", true, new Handler() {
      public boolean run(Database db, String user, Query row, Component parent) throws Exception {
        if (!UsersSql.has(db, "ALTER USER") && !user.equals(UsersSql.currentUser(db))) {
          MessageBox.show(stringManager.getString("error"), stringManager.getString("users-password-own-only"), ModalResult.OK);
          return false;
        }
        return SqlCodeWizardDialog.show(new UserWizards.ChangePassword(db, user), true) != null;
      }
    });
  }

  private static UserAction alterUser(Database database) {
    return new UserAction("users-action-alter", "edit_user.gif", true, wizard(new WizardFactory() {
      public UserWizard create(Database db, String user, Query row) { return new UserWizards.AlterUser(db, user); }
    })).requires(database, "ALTER USER");
  }

  private static UserAction quota(Database database, final boolean fromTab) {
    return new UserAction("users-action-quota", "tablespace.gif", true, wizard(new WizardFactory() {
      public UserWizard create(Database db, String user, Query row) throws Exception {
        return new UserWizards.Quota(db, user, fromTab ? field(row, "tablespace_name") : null);
      }
    })).requires(database, "ALTER USER");
  }

  private static UserAction grant(Database database, final boolean sysPriv, final boolean revoke) {
    String key = "users-action-" + (revoke ? "revoke" : "grant") + (sysPriv ? "-sys-priv" : "-role");
    String icon = revoke ? "revoke_class.gif" : (sysPriv ? "grant_class.gif" : "group_add.gif");
    return new UserAction(key, icon, true, wizard(new WizardFactory() {
      public UserWizard create(Database db, String user, Query row) throws Exception {
        return new UserWizards.Grant(db, user, sysPriv, revoke, field(row, sysPriv ? "privilege" : "granted_role"));
      }
    })).requires(database, sysPriv ? "GRANT ANY PRIVILEGE" : "GRANT ANY ROLE", sysPriv ? UsersSql.ADMIN_SYS_PRIV : UsersSql.ADMIN_ROLE);
  }

  private static UserAction proxy(Database database, final boolean revoke) {
    return new UserAction(revoke ? "users-action-proxy-revoke" : "users-action-proxy", "user.gif", true, wizard(new WizardFactory() {
      public UserWizard create(Database db, String user, Query row) throws Exception {
        String proxy = field(row, "proxy");
        String client = field(row, "client");
        // wiersz, w ktorym zaznaczony uzytkownik jest proxy: kreator dla klienta (CONNECT THROUGH zaznaczonego)
        if (client != null && !client.equals(user)) {
          return new UserWizards.Proxy(db, client, user, revoke);
        }
        return new UserWizards.Proxy(db, user, proxy, revoke);
      }
    })).requires(database, "ALTER USER");
  }

  // ---------------------------------------------------------------- proste polecenia

  /**
   * ORBADA 1.3.6: okno potwierdzenia z zakladka SQL (polecenie do skopiowania, np. do Liquibase) - jak pozostale
   * kreatory. true - wykonano
   */
  private static boolean statement(Database db, String titleKey, String user, String message, String sql) {
    List<String> list = new ArrayList<String>();
    list.add(sql);
    return SqlCodeWizardDialog.show(new UserWizards.Statement(db, titleKey, user, message, list), true) != null;
  }

  /** ALTER USER ... clause */
  private static Handler alter(final String titleKey, final String questionKey, final String clause) {
    return new Handler() {
      public boolean run(Database db, String user, Query row, Component parent) throws Exception {
        return statement(db, titleKey, user, stringManager.getString(questionKey, user),
          "ALTER USER " + UserWizard.ident(user) + " " + clause);
      }
    };
  }

  private static UserAction revokeObjectPrivilege(Database database) {
    return new UserAction("users-action-revoke-obj-priv", "revoke_class.gif", true, new Handler() {
      public boolean run(Database db, String user, Query row, Component parent) throws Exception {
        String owner = field(row, "owner");
        String table = field(row, "table_name");
        String privilege = field(row, "privilege");
        if (owner == null || table == null || privilege == null) {
          return false;
        }
        String type = field(row, "type");
        String on = "DIRECTORY".equals(type) ? "DIRECTORY " + UserWizard.ident(table) : UserWizard.ident(owner) + "." + UserWizard.ident(table);
        return statement(db, "users-action-revoke-obj-priv", user,
          stringManager.getString("users-revoke-obj-q", new Object[] {privilege, on}),
          "REVOKE " + privilege + " ON " + on + " FROM " + UserWizard.ident(user));
      }
    }).requires(database, "GRANT ANY OBJECT PRIVILEGE", UsersSql.OBJ_GRANTOR);
  }

  private static UserAction killSessions(Database database) {
    return new UserAction("users-action-kill-sessions", "stop16.gif", true, new Handler() {
      public boolean run(Database db, String user, Query row, Component parent) throws Exception {
        List<String> sql = new ArrayList<String>();
        Query q = db.createQuery();
        try {
          q.setSqlText(UsersSql.getUserSessionsToKill());
          q.paramByName("username").setString(user);
          q.open();
          while (!q.eof()) {
            sql.add("ALTER SYSTEM KILL SESSION '" + q.fieldByName("sid").getString() + ", " + q.fieldByName("serial#").getString() + "' IMMEDIATE");
            q.next();
          }
        } finally {
          q.close();
        }
        if (sql.isEmpty()) {
          MessageBox.show(stringManager.getString("users-title"), stringManager.getString("users-no-sessions", user), ModalResult.OK);
          return false;
        }
        UserWizards.Statement w = new UserWizards.Statement(db, "users-action-kill-sessions", user,
          stringManager.getString("users-kill-sessions-q", new Object[] {user, Integer.toString(sql.size())}), sql);
        // sesja mogla sie juz zakonczyc - pozostale i tak konczymy
        w.setContinueOnError(true);
        return SqlCodeWizardDialog.show(w, true) != null;
      }
    }).requires(database, "ALTER SYSTEM");
  }

  private static UserAction killSession(Database database) {
    return new UserAction("users-action-kill-session", "stop16.gif", true, new Handler() {
      public boolean run(Database db, String user, Query row, Component parent) throws Exception {
        String sid = field(row, "sid");
        String serial = field(row, "serial#");
        if (sid == null || serial == null) {
          return false;
        }
        String s = sid + ", " + serial;
        return statement(db, "users-action-kill-session", user, stringManager.getString("kill-selected-session-q", s),
          "ALTER SYSTEM KILL SESSION '" + s + "' IMMEDIATE");
      }
    }).requires(database, "ALTER SYSTEM");
  }

  public String getDescription() {
    return stringManager.getString("OracleUserActionsService-description");
  }

  public String getGroupName() {
    return OrbadaOraclePlugin.oracleDriverType;
  }

}
