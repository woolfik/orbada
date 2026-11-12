package pl.mpak.orbada.oracle.dba.gui.users;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.JPasswordField;
import pl.mpak.sky.gui.swing.comp.TextField;
import pl.mpak.usedb.core.Database;
import pl.mpak.usedb.core.Query;
import pl.mpak.util.ExceptionUtil;

/**
 * ORBADA 1.3.5: kreatory widoku "Uzytkownicy" (Oracle) - CREATE/ALTER/DROP USER, hasla, limity, role, przywileje
 * systemowe, proxy.
 */
public final class UserWizards {

  private UserWizards() {
  }

  private final static String[] AUTH = {"PASSWORD", "EXTERNALLY", "GLOBALLY", "NO AUTHENTICATION"};

  // ================================================================= CREATE USER (takze na wzor innego)

  public static class CreateUser extends UserWizard {

    private final String like;
    private final TextField name;
    private final JComboBox<String> auth;
    private final JPasswordField password;
    private final JPasswordField confirm;
    private final TextField external;
    private final JComboBox<String> defaultTs;
    private final JComboBox<String> tempTs;
    private final JComboBox<String> profile;
    private final JComboBox<String> quota;
    private final JCheckBox expire;
    private final JCheckBox lock;
    private final JCheckBox createSession;
    private final JList<String> roles;
    private JCheckBox copySysPrivs;
    private JCheckBox copyQuotas;
    private JCheckBox copyObjPrivs;
    /** Wzorzec: rola -> z ADMIN OPTION */
    private final Map<String, Boolean> likeRoles = new LinkedHashMap<String, Boolean>();
    private final List<String> likeNonDefault = new ArrayList<String>();

    /** @param like uzytkownik-wzorzec albo null */
    public CreateUser(Database database, String like) {
      super(database);
      this.like = like;
      titleKey = like == null ? "CreateUser-title" : "CreateUserLike-title";
      if (like != null) {
        info("users-like", like);
      }
      name = text("users-username", "");
      auth = combo("users-authentication", false, AUTH);
      password = password("users-password");
      confirm = password("users-password-confirm");
      external = text("users-external-name", "");
      defaultTs = combo("users-default-tablespace", true, prepend("", tablespaces(false)));
      tempTs = combo("users-temporary-tablespace", true, prepend("", tablespaces(true)));
      profile = combo("users-profile", true, prepend("", profiles()));
      quota = combo("users-quota-default", true, "", "UNLIMITED", "100M", "1G");
      expire = check("users-password-expire");
      lock = check("users-account-lock");
      createSession = check("users-grant-create-session");
      createSession.setSelected(true);
      roles = multi("users-roles", grantableRoles(this));
      if (like != null) {
        copySysPrivs = check("users-copy-sys-privs");
        copyQuotas = check("users-copy-quotas");
        copyObjPrivs = check("users-copy-obj-privs");
        copySysPrivs.setSelected(true);
        copyQuotas.setSelected(true);
        loadLike();
      }
      auth.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          updateAuth();
        }
      });
      updateAuth();
    }

    private void updateAuth() {
      boolean pwd = "PASSWORD".equals(auth.getSelectedItem());
      password.setEnabled(pwd);
      confirm.setEnabled(pwd);
      expire.setEnabled(pwd);
      external.setEnabled("EXTERNALLY".equals(auth.getSelectedItem()) || "GLOBALLY".equals(auth.getSelectedItem()));
    }

    private void loadLike() {
      Query q = database.createQuery();
      try {
        q.setSqlText("select default_tablespace, temporary_tablespace, profile from dba_users where username = :username");
        q.paramByName("username").setString(like);
        q.open();
        if (!q.eof()) {
          set(defaultTs, q.fieldByName("default_tablespace").getString());
          set(tempTs, q.fieldByName("temporary_tablespace").getString());
          set(profile, q.fieldByName("profile").getString());
        }
        q.close();
        q.setSqlText("select granted_role, admin_option, default_role from dba_role_privs where grantee = :username order by 1");
        q.paramByName("username").setString(like);
        q.open();
        while (!q.eof()) {
          String role = q.fieldByName("granted_role").getString();
          likeRoles.put(role, "YES".equals(q.fieldByName("admin_option").getString()));
          if ("NO".equals(q.fieldByName("default_role").getString())) {
            likeNonDefault.add(role);
          }
          q.next();
        }
        select(roles, new ArrayList<String>(likeRoles.keySet()));
      } catch (Exception ex) {
        ExceptionUtil.processException(ex);
      } finally {
        q.close();
      }
    }

    @Override
    protected void build(List<String> sql, boolean mask) {
      String user = typed(value(name));
      require(!isEmpty(user), "users-name-required");
      String a = (String)auth.getSelectedItem();
      StringBuilder sb = new StringBuilder("CREATE USER ").append(ident(user));
      if ("PASSWORD".equals(a)) {
        checkPasswordStatic(this, value(password), value(confirm));
        sb.append(" IDENTIFIED BY ").append(pwd(value(password), mask));
      } else if ("NO AUTHENTICATION".equals(a)) {
        sb.append(" NO AUTHENTICATION");
      } else {
        sb.append(" IDENTIFIED ").append(a);
        if (!isEmpty(value(external))) {
          sb.append(" AS '").append(value(external).replace("'", "''")).append("'");
        }
      }
      String ts = value(defaultTs);
      if (!isEmpty(ts)) {
        sb.append("\n  DEFAULT TABLESPACE ").append(ident(ts));
      }
      if (!isEmpty(value(tempTs))) {
        sb.append("\n  TEMPORARY TABLESPACE ").append(ident(value(tempTs)));
      }
      if (!isEmpty(value(profile))) {
        sb.append("\n  PROFILE ").append(ident(value(profile)));
      }
      String q = value(quota);
      if (!isEmpty(q)) {
        require(!isEmpty(ts), "users-quota-needs-tablespace");
        sb.append("\n  QUOTA ").append(quotaSize(this, q)).append(" ON ").append(ident(ts));
      }
      if ("PASSWORD".equals(a) && expire.isSelected()) {
        sb.append("\n  PASSWORD EXPIRE");
      }
      if (lock.isSelected()) {
        sb.append("\n  ACCOUNT LOCK");
      }
      sql.add(sb.toString());
      String u = ident(user);
      if (createSession.isSelected()) {
        sql.add("GRANT CREATE SESSION TO " + u);
      }
      List<String> plain = new ArrayList<String>();
      List<String> admin = new ArrayList<String>();
      for (String r : roles.getSelectedValuesList()) {
        (Boolean.TRUE.equals(likeRoles.get(r)) ? admin : plain).add(r);
      }
      if (!plain.isEmpty()) {
        sql.add("GRANT " + join(plain, true) + " TO " + u);
      }
      if (!admin.isEmpty()) {
        sql.add("GRANT " + join(admin, true) + " TO " + u + " WITH ADMIN OPTION");
      }
      if (like != null) {
        List<String> nonDefault = new ArrayList<String>();
        for (String r : likeNonDefault) {
          if (plain.contains(r) || admin.contains(r)) {
            nonDefault.add(r);
          }
        }
        if (!nonDefault.isEmpty()) {
          sql.add("ALTER USER " + u + " DEFAULT ROLE ALL EXCEPT " + join(nonDefault, true));
        }
        if (copySysPrivs.isSelected()) {
          copySysPrivs(sql, u);
        }
        if (copyQuotas.isSelected()) {
          copyQuotas(sql, u, ts, q);
        }
        if (copyObjPrivs.isSelected()) {
          copyObjPrivs(sql, u);
        }
      }
    }

    private void copySysPrivs(List<String> sql, String u) {
      List<String> plain = new ArrayList<String>();
      List<String> admin = new ArrayList<String>();
      Query q = database.createQuery();
      try {
        q.setSqlText("select privilege, admin_option from dba_sys_privs where grantee = :username order by 1");
        q.paramByName("username").setString(like);
        q.open();
        while (!q.eof()) {
          String p = q.fieldByName("privilege").getString();
          if (!(createSession.isSelected() && "CREATE SESSION".equals(p))) {
            ("YES".equals(q.fieldByName("admin_option").getString()) ? admin : plain).add(p);
          }
          q.next();
        }
      } catch (Exception ex) {
        ExceptionUtil.processException(ex);
      } finally {
        q.close();
      }
      if (!plain.isEmpty()) {
        sql.add("GRANT " + join(plain, false) + " TO " + u);
      }
      if (!admin.isEmpty()) {
        sql.add("GRANT " + join(admin, false) + " TO " + u + " WITH ADMIN OPTION");
      }
    }

    private void copyQuotas(List<String> sql, String u, String ts, String quota) {
      Query q = database.createQuery();
      try {
        q.setSqlText("select tablespace_name, max_bytes from dba_ts_quotas where username = :username order by 1");
        q.paramByName("username").setString(like);
        q.open();
        while (!q.eof()) {
          String t = q.fieldByName("tablespace_name").getString();
          if (isEmpty(quota) || !t.equals(ts)) {
            long max = q.fieldByName("max_bytes").getLong();
            sql.add("ALTER USER " + u + " QUOTA " + (max < 0 ? "UNLIMITED" : Long.toString(max)) + " ON " + ident(t));
          }
          q.next();
        }
      } catch (Exception ex) {
        ExceptionUtil.processException(ex);
      } finally {
        q.close();
      }
    }

    private void copyObjPrivs(List<String> sql, String u) {
      Query q = database.createQuery();
      try {
        q.setSqlText("select * from dba_tab_privs where grantee = :username order by owner, table_name, privilege");
        q.paramByName("username").setString(like);
        q.open();
        while (!q.eof()) {
          String type = q.findFieldByName("type") != null ? q.fieldByName("type").getString() : null;
          String on = ("DIRECTORY".equals(type) ? "DIRECTORY " + ident(q.fieldByName("table_name").getString()) :
            ident(q.fieldByName("owner").getString()) + "." + ident(q.fieldByName("table_name").getString()));
          sql.add("GRANT " + q.fieldByName("privilege").getString() + " ON " + on + " TO " + u +
            ("YES".equals(q.fieldByName("grantable").getString()) ? " WITH GRANT OPTION" : ""));
          q.next();
        }
      } catch (Exception ex) {
        ExceptionUtil.processException(ex);
      } finally {
        q.close();
      }
    }

    @Override
    public void wizardShow() {
      name.requestFocusInWindow();
    }
  }

  // ================================================================= zmiana hasla

  public static class ChangePassword extends UserWizard {

    private final String user;
    private final JPasswordField password;
    private final JPasswordField confirm;
    private JPasswordField old;
    private final JCheckBox expire;
    private final JCheckBox unlock;

    public ChangePassword(Database database, String user) {
      super(database);
      this.user = user;
      boolean alter = UsersSql.has(database, "ALTER USER");
      info("users-username", user);
      if (!alter) {
        // zmiana wlasnego hasla bez ALTER USER: Oracle wymaga starego hasla (REPLACE)
        old = password("users-password-old");
      }
      password = password("users-password-new");
      confirm = password("users-password-confirm");
      expire = check("users-password-expire");
      unlock = check("users-account-unlock");
      expire.setEnabled(alter);
      unlock.setEnabled(alter);
    }

    @Override
    protected void build(List<String> sql, boolean mask) {
      checkPasswordStatic(this, value(password), value(confirm));
      StringBuilder sb = new StringBuilder("ALTER USER ").append(ident(user)).append(" IDENTIFIED BY ").append(pwd(value(password), mask));
      if (old != null) {
        require(!isEmpty(value(old)), "users-old-password-required");
        sb.append(" REPLACE ").append(pwd(value(old), mask));
      }
      if (expire.isSelected()) {
        sb.append(" PASSWORD EXPIRE");
      }
      if (unlock.isSelected()) {
        sb.append(" ACCOUNT UNLOCK");
      }
      sql.add(sb.toString());
    }

    @Override
    public void wizardShow() {
      (old != null ? old : password).requestFocusInWindow();
    }
  }

  // ================================================================= ALTER USER (przestrzenie, profil, role domyslne)

  public static class AlterUser extends UserWizard {

    private final String user;
    private final JComboBox<String> defaultTs;
    private final JComboBox<String> tempTs;
    private final JComboBox<String> profile;
    private final JComboBox<String> defaultRoles;
    private String curDefault;
    private String curTemp;
    private String curProfile;

    public AlterUser(Database database, String user) {
      super(database);
      this.user = user;
      info("users-username", user);
      defaultTs = combo("users-default-tablespace", true, tablespaces(false));
      tempTs = combo("users-temporary-tablespace", true, tablespaces(true));
      profile = combo("users-profile", true, profiles());
      defaultRoles = combo("users-default-roles", false,
        stringManager.getString("users-no-change"), "ALL", "NONE");
      Query q = database.createQuery();
      try {
        q.setSqlText("select default_tablespace, temporary_tablespace, profile from dba_users where username = :username");
        q.paramByName("username").setString(user);
        q.open();
        if (!q.eof()) {
          curDefault = q.fieldByName("default_tablespace").getString();
          curTemp = q.fieldByName("temporary_tablespace").getString();
          curProfile = q.fieldByName("profile").getString();
        }
      } catch (Exception ex) {
        ExceptionUtil.processException(ex);
      } finally {
        q.close();
      }
      set(defaultTs, curDefault);
      set(tempTs, curTemp);
      set(profile, curProfile);
    }

    @Override
    protected void build(List<String> sql, boolean mask) {
      StringBuilder sb = new StringBuilder();
      if (!isEmpty(value(defaultTs)) && !value(defaultTs).equals(curDefault)) {
        sb.append("\n  DEFAULT TABLESPACE ").append(ident(value(defaultTs)));
      }
      if (!isEmpty(value(tempTs)) && !value(tempTs).equals(curTemp)) {
        sb.append("\n  TEMPORARY TABLESPACE ").append(ident(value(tempTs)));
      }
      if (!isEmpty(value(profile)) && !value(profile).equals(curProfile)) {
        sb.append("\n  PROFILE ").append(ident(value(profile)));
      }
      if (sb.length() > 0) {
        sql.add("ALTER USER " + ident(user) + sb);
      }
      if (defaultRoles.getSelectedIndex() > 0) {
        sql.add("ALTER USER " + ident(user) + " DEFAULT ROLE " + defaultRoles.getSelectedItem());
      }
      require(!sql.isEmpty(), "users-nothing-changed");
    }
  }

  // ================================================================= limit na przestrzeni tabel

  public static class Quota extends UserWizard {

    private final String user;
    private final JComboBox<String> tablespace;
    private final JComboBox<String> quota;

    public Quota(Database database, String user, String selectedTablespace) {
      super(database);
      this.user = user;
      info("users-username", user);
      tablespace = combo("users-tablespace", true, tablespaces(false));
      quota = combo("users-quota", true, "UNLIMITED", "0", "100M", "1G", "10G");
      note("users-quota-note");
      set(tablespace, selectedTablespace);
    }

    @Override
    protected void build(List<String> sql, boolean mask) {
      require(!isEmpty(value(tablespace)), "users-tablespace-required");
      sql.add("ALTER USER " + ident(user) + " QUOTA " + quotaSize(this, value(quota)) + " ON " + ident(value(tablespace)));
    }
  }

  // ================================================================= role i przywileje systemowe

  public static class Grant extends UserWizard {

    private final String user;
    private final boolean sysPriv;
    private final boolean revoke;
    private final JList<String> items;
    private JCheckBox adminOption;

    /**
     * @param sysPriv true - przywileje systemowe, false - role
     * @param revoke true - odebranie (lista nadanych)
     * @param selected pozycja zaznaczona na starcie albo null
     */
    public Grant(Database database, String user, boolean sysPriv, boolean revoke, String selected) {
      super(database);
      this.user = user;
      this.sysPriv = sysPriv;
      this.revoke = revoke;
      titleKey = (revoke ? "Revoke" : "Grant") + (sysPriv ? "SysPriv" : "Role") + "-title";
      info("users-username", user);
      String[] list;
      if (revoke) {
        list = sysPriv ?
          list("select privilege from dba_sys_privs where grantee = :username order by 1", user) :
          list("select granted_role from dba_role_privs where grantee = :username order by 1", user);
      } else {
        list = sysPriv ? grantableSysPrivs(this) : grantableRoles(this);
      }
      items = multi(sysPriv ? "users-privileges" : "users-roles", list);
      if (!revoke) {
        adminOption = check("users-with-admin-option");
      }
      if (selected != null) {
        List<String> s = new ArrayList<String>();
        s.add(selected);
        select(items, s);
      }
    }

    @Override
    protected void build(List<String> sql, boolean mask) {
      List<String> sel = items.getSelectedValuesList();
      require(!sel.isEmpty(), sysPriv ? "users-select-privilege" : "users-select-role");
      String what = join(sel, !sysPriv);
      if (revoke) {
        sql.add("REVOKE " + what + " FROM " + ident(user));
      } else {
        sql.add("GRANT " + what + " TO " + ident(user) + (adminOption.isSelected() ? " WITH ADMIN OPTION" : ""));
      }
    }
  }

  // ================================================================= proxy (CONNECT THROUGH)

  public static class Proxy extends UserWizard {

    private final String user;
    private final JComboBox<String> mode;
    private final JComboBox<String> proxy;
    private final JComboBox<String> roles;
    private final JCheckBox authRequired;

    public Proxy(Database database, String user, String selectedProxy, boolean revoke) {
      super(database);
      this.user = user;
      info("users-client", user);
      mode = combo("users-proxy-mode", false, "GRANT", "REVOKE");
      proxy = combo("users-proxy", true, users());
      roles = combo("users-proxy-roles", false, stringManager.getString("users-proxy-default-roles"), "WITH NO ROLES");
      authRequired = check("users-proxy-auth-required");
      note("users-proxy-note");
      if (selectedProxy != null) {
        set(proxy, selectedProxy);
      }
      mode.setSelectedIndex(revoke ? 1 : 0);
      mode.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          update();
        }
      });
      update();
    }

    private void update() {
      boolean grant = mode.getSelectedIndex() == 0;
      roles.setEnabled(grant);
      authRequired.setEnabled(grant);
    }

    @Override
    protected void build(List<String> sql, boolean mask) {
      String p = typed(value(proxy));
      require(!isEmpty(p), "users-proxy-required");
      if (mode.getSelectedIndex() == 1) {
        sql.add("ALTER USER " + ident(user) + " REVOKE CONNECT THROUGH " + ident(p));
      } else {
        sql.add("ALTER USER " + ident(user) + " GRANT CONNECT THROUGH " + ident(p) +
          (roles.getSelectedIndex() == 1 ? " WITH NO ROLES" : "") +
          (authRequired.isSelected() ? " AUTHENTICATION REQUIRED" : ""));
      }
    }
  }

  // ================================================================= DROP USER

  public static class DropUser extends UserWizard {

    private final String user;
    private final JCheckBox cascade;

    public DropUser(Database database, String user) {
      super(database);
      this.user = user;
      info("users-username", user);
      String[] cnt = list("select to_char(count(*)) from dba_objects where owner = :username", user);
      if (cnt.length > 0) {
        info("users-object-count", cnt[0]);
      }
      cascade = check("users-drop-cascade");
      cascade.setSelected(cnt.length > 0 && !"0".equals(cnt[0]));
      note("users-drop-note");
    }

    @Override
    protected void build(List<String> sql, boolean mask) {
      sql.add("DROP USER " + ident(user) + (cascade.isSelected() ? " CASCADE" : ""));
    }
  }

  // ================================================================= gotowe polecenia (blokada, sesje, ...)

  /**
   * ORBADA 1.3.6: potwierdzenie prostych operacji (zablokuj, odblokuj, wygas haslo, zakoncz sesje, odbierz uprawnienie
   * do obiektu) w oknie kreatora - z zakladka SQL, z ktorej polecenie mozna skopiowac (np. do Liquibase).
   * Tylko polecenia bez sekretow - nie sa maskowane (hasla: kreatory CreateUser/ChangePassword).
   */
  public static class Statement extends UserWizard {

    private final List<String> statements;

    /** @param titleKey klucz i18n tytulu okna, @param message pytanie/opis operacji */
    public Statement(Database database, String titleKey, String user, String message, List<String> statements) {
      super(database);
      this.titleKey = titleKey;
      this.statements = statements;
      if (user != null) {
        info("users-username", user);
      }
      row(null, new javax.swing.JLabel("<html>" + html(message) + "</html>"));
    }

    private static String html(String s) {
      return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>");
    }

    @Override
    protected void build(List<String> sql, boolean mask) {
      sql.addAll(statements);
    }
  }

  // ================================================================= wspolne

  private static String[] prepend(String first, String[] items) {
    String[] r = new String[items.length + 1];
    r[0] = first;
    System.arraycopy(items, 0, r, 1, items.length);
    return r;
  }

  /** Haslo i potwierdzenie: niepuste, zgodne, bez cudzyslowu (nie da sie go zapisac w IDENTIFIED BY "...") */
  static void checkPasswordStatic(UserWizard w, String password, String confirm) {
    w.require(password.length() > 0, "users-password-required");
    w.require(password.equals(confirm), "users-password-mismatch");
    w.require(password.indexOf('"') < 0, "users-password-quote");
  }

  /** Wielkosc limitu: UNLIMITED albo liczba z opcjonalnym K/M/G/T */
  static String quotaSize(UserWizard w, String q) {
    String s = q.trim().toUpperCase();
    w.require(s.equals("UNLIMITED") || s.matches("\\d+[KMGT]?"), "users-quota-invalid");
    return s;
  }

  /** Role do nadania: wszystkie (GRANT ANY ROLE) albo te, ktore sesja ma z ADMIN OPTION */
  static String[] grantableRoles(UserWizard w) {
    if (UsersSql.has(w.database, "GRANT ANY ROLE")) {
      return w.firstList(new String[] {
        "select role from dba_roles order by 1",
        "select granted_role from user_role_privs where admin_option = 'YES' order by 1"});
    }
    return w.firstList(new String[] {"select granted_role from user_role_privs where admin_option = 'YES' order by 1"});
  }

  /** Przywileje systemowe do nadania: wszystkie (GRANT ANY PRIVILEGE) albo posiadane z ADMIN OPTION */
  static String[] grantableSysPrivs(UserWizard w) {
    if (UsersSql.has(w.database, "GRANT ANY PRIVILEGE")) {
      return w.firstList(new String[] {"select name from system_privilege_map order by 1"});
    }
    return w.firstList(new String[] {"select privilege from user_sys_privs where admin_option = 'YES' order by 1"});
  }

}
