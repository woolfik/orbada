package pl.mpak.orbada.oracle.dba.gui.users;

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import pl.mpak.orbada.oracle.dba.OrbadaOracleDbaPlugin;
import pl.mpak.orbada.universal.gui.wizards.SqlCodeWizardPanel;
import pl.mpak.sky.gui.mr.ModalResult;
import pl.mpak.sky.gui.swing.MessageBox;
import pl.mpak.sky.gui.swing.comp.TextField;
import pl.mpak.usedb.core.Command;
import pl.mpak.usedb.core.Database;
import pl.mpak.usedb.core.Query;
import pl.mpak.util.ExceptionUtil;
import pl.mpak.util.StringManager;
import pl.mpak.util.StringManagerFactory;
import pl.mpak.util.StringUtil;

/**
 * ORBADA 1.3.5: wspolna baza kreatorow widoku "Uzytkownicy". Formularz etykieta + pole (GridBagLayout), polecenia
 * budowane w build() i wykonywane po kolei do pierwszego bledu. Hasla w podgladzie SQL zamaskowane (mask = true),
 * do bazy idzie prawdziwe. Tytul okna: klucz "NazwaKlasy-title" w i18n (albo titleKey).
 */
public abstract class UserWizard extends SqlCodeWizardPanel {

  protected final StringManager stringManager = StringManagerFactory.getStringManager(OrbadaOracleDbaPlugin.class);

  public final static String MASK = "********";

  protected final Database database;
  protected String titleKey;
  /** Wykonanie wszystkich polecen mimo bledow (np. konczenie wielu sesji) - bledy zbiorczo na koniec */
  private boolean continueOnError = false;

  public void setContinueOnError(boolean continueOnError) {
    this.continueOnError = continueOnError;
  }
  private final JPanel form = new JPanel(new GridBagLayout());
  private int row = 0;

  protected UserWizard(Database database) {
    this.database = database;
    setLayout(new BorderLayout());
    form.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
    add(form, BorderLayout.CENTER);
    GridBagConstraints gc = new GridBagConstraints();
    gc.gridy = 1000;
    gc.weighty = 1e-6;
    form.add(new JPanel(), gc);
  }

  // ---------------------------------------------------------------- formularz

  protected <T extends JComponent> T row(String labelKey, T field) {
    return row(labelKey, field, false);
  }

  protected <T extends JComponent> T row(String labelKey, T field, boolean fill) {
    GridBagConstraints gc = new GridBagConstraints();
    gc.gridy = row++;
    gc.insets = new Insets(2, 2, 2, 2);
    if (labelKey != null) {
      JLabel label = new JLabel(stringManager.getString(labelKey) + ":");
      label.setHorizontalAlignment(SwingConstants.RIGHT);
      gc.gridx = 0;
      gc.anchor = fill ? GridBagConstraints.NORTHEAST : GridBagConstraints.EAST;
      form.add(label, gc);
    }
    gc.gridx = 1;
    gc.weightx = 1;
    gc.anchor = GridBagConstraints.WEST;
    if (fill) {
      gc.fill = GridBagConstraints.BOTH;
      gc.weighty = 1;
    } else {
      gc.fill = field instanceof JCheckBox || field instanceof JLabel ? GridBagConstraints.NONE : GridBagConstraints.HORIZONTAL;
    }
    form.add(field instanceof JList ? new JScrollPane(field) : field, gc);
    return field;
  }

  protected TextField text(String labelKey, String value) {
    TextField field = new TextField();
    field.setText(value);
    return row(labelKey, field);
  }

  protected TextField info(String labelKey, String value) {
    TextField field = text(labelKey, value);
    field.setEditable(false);
    field.setFocusable(false);
    return field;
  }

  protected JPasswordField password(String labelKey) {
    return row(labelKey, new JPasswordField());
  }

  protected JLabel note(String key) {
    return row(null, new JLabel("<html>" + stringManager.getString(key) + "</html>"));
  }

  protected JComboBox<String> combo(String labelKey, boolean editable, String... items) {
    JComboBox<String> combo = new JComboBox<String>(items == null ? new String[0] : items);
    combo.setEditable(editable);
    return row(labelKey, combo);
  }

  protected JCheckBox check(String key) {
    return row(null, new JCheckBox(stringManager.getString(key)));
  }

  /** Lista wielokrotnego wyboru (role, przywileje) */
  protected JList<String> multi(String labelKey, String[] items) {
    DefaultListModel<String> model = new DefaultListModel<String>();
    for (String s : items) {
      model.addElement(s);
    }
    JList<String> list = new JList<String>(model);
    list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    list.setVisibleRowCount(10);
    return row(labelKey, list, true);
  }

  protected static void select(JList<String> list, List<String> values) {
    List<Integer> idx = new ArrayList<Integer>();
    for (int i = 0; i < list.getModel().getSize(); i++) {
      if (values.contains(list.getModel().getElementAt(i))) {
        idx.add(i);
      }
    }
    int[] a = new int[idx.size()];
    for (int i = 0; i < a.length; i++) {
      a[i] = idx.get(i);
    }
    list.setSelectedIndices(a);
    if (a.length > 0) {
      list.ensureIndexIsVisible(a[0]);
    }
  }

  protected static String value(JComboBox<?> combo) {
    Object o = combo.isEditable() ? combo.getEditor().getItem() : combo.getSelectedItem();
    return o == null ? "" : o.toString().trim();
  }

  protected static String value(javax.swing.text.JTextComponent text) {
    return text.getText() == null ? "" : text.getText().trim();
  }

  protected static String value(JPasswordField field) {
    return new String(field.getPassword());
  }

  /** Wartosc combo albo pierwszego elementu, gdy ustawiana jest pozycja spoza listy */
  protected static void set(JComboBox<String> combo, String value) {
    if (value == null) {
      return;
    }
    combo.setSelectedItem(value);
    if (combo.isEditable()) {
      combo.getEditor().setItem(value);
    }
  }

  // ---------------------------------------------------------------- SQL

  /** Nazwa w SQL: bez cudzyslowu tylko WIELKIE litery, cyfry, _ $ # (jak zwracaja slowniki) */
  public static String ident(String name) {
    if (name == null) {
      return null;
    }
    if (name.matches("[A-Z][A-Z0-9_$#]*")) {
      return name;
    }
    return "\"" + name.replace("\"", "") + "\"";
  }

  /** Nazwa wpisana przez uzytkownika: prosta - wielkimi literami (jak bez cudzyslowu w SQL), inna dokladnie */
  public static String typed(String name) {
    name = name == null ? "" : name.trim();
    if (name.length() > 1 && name.startsWith("\"") && name.endsWith("\"")) {
      return name.substring(1, name.length() - 1);
    }
    if (name.matches("[A-Za-z][A-Za-z0-9_$#]*")) {
      return name.toUpperCase();
    }
    return name;
  }

  /** Haslo w IDENTIFIED BY: zawsze w cudzyslowie (wielkosc liter, znaki specjalne) */
  protected static String pwd(String password, boolean mask) {
    return "\"" + (mask ? MASK : password) + "\"";
  }

  /** Lista identyfikatorow ze slownika (role, przywileje systemowe bez cudzyslowu - to slowa kluczowe) */
  protected static String join(List<String> items, boolean quote) {
    StringBuilder sb = new StringBuilder();
    for (String s : items) {
      sb.append(sb.length() > 0 ? ", " : "").append(quote ? ident(s) : s);
    }
    return sb.toString();
  }

  /** Wartosci pierwszej kolumny zapytania; parametry :NAZWA kolejno z args; blad - pusta lista (brak uprawnien) */
  protected String[] list(String sql, String... args) {
    List<String> result = new ArrayList<String>();
    Query query = database.createQuery();
    try {
      query.setSqlText(sql);
      for (int i = 0; i < args.length; i++) {
        query.getParameter(i).setString(args[i]);
      }
      query.open();
      while (!query.eof()) {
        result.add(query.getField(0).getString());
        query.next();
      }
    } catch (Exception ex) {
      ExceptionUtil.processException(ex);
    } finally {
      query.close();
    }
    return result.toArray(new String[result.size()]);
  }

  /** Pierwsza niepusta lista z kolejnych zapytan (np. DBA_ albo USER_ gdy brak dostepu do slownika DBA) */
  protected String[] firstList(String[] sqls, String... args) {
    for (String sql : sqls) {
      String[] r = listQuiet(sql, args);
      if (r != null && r.length > 0) {
        return r;
      }
    }
    return new String[0];
  }

  private String[] listQuiet(String sql, String... args) {
    List<String> result = new ArrayList<String>();
    Query query = database.createQuery();
    try {
      query.setSqlText(sql);
      for (int i = 0; i < args.length; i++) {
        query.getParameter(i).setString(args[i]);
      }
      query.open();
      while (!query.eof()) {
        result.add(query.getField(0).getString());
        query.next();
      }
      return result.toArray(new String[result.size()]);
    } catch (Exception ex) {
      return null;
    } finally {
      query.close();
    }
  }

  protected String[] tablespaces(boolean temporary) {
    String c = temporary ? "= 'TEMPORARY'" : "not in ('TEMPORARY', 'UNDO')";
    return firstList(new String[] {
      "select tablespace_name from dba_tablespaces where contents " + c + " order by 1",
      "select tablespace_name from user_tablespaces where contents " + c + " order by 1"});
  }

  protected String[] profiles() {
    // bez dostepu do DBA_PROFILES (brak odpowiednika USER_) - przynajmniej profil domyslny, pole jest edytowalne
    String[] r = firstList(new String[] {"select distinct profile from dba_profiles order by 1"});
    return r.length > 0 ? r : new String[] {"DEFAULT"};
  }

  protected String[] users() {
    return list("select username from all_users order by 1");
  }

  protected void require(boolean condition, String messageKey) {
    if (!condition) {
      throw new IllegalArgumentException(stringManager.getString(messageKey));
    }
  }

  /** Polecenia kreatora; mask - hasla zamaskowane (podglad) */
  protected abstract void build(List<String> sql, boolean mask);

  protected List<String> statements(boolean mask) {
    List<String> sql = new ArrayList<String>();
    build(sql, mask);
    return sql;
  }

  @Override
  public String getDialogTitle() {
    return stringManager.getString(titleKey != null ? titleKey : getClass().getSimpleName() + "-title");
  }

  @Override
  public String getTabTitle() {
    return getDialogTitle();
  }

  @Override
  public void wizardShow() {
  }

  @Override
  public String getSqlCode() {
    StringBuilder sb = new StringBuilder();
    try {
      for (String s : statements(true)) {
        sb.append(s).append(";\n");
      }
    } catch (IllegalArgumentException ex) {
      return "-- " + ex.getMessage();
    }
    return sb.toString();
  }

  @Override
  public boolean execute() {
    List<String> sql;
    List<String> shown;
    try {
      sql = statements(false);
      shown = statements(true);
    } catch (IllegalArgumentException ex) {
      MessageBox.show(this, stringManager.getString("error"), ex.getMessage(), ModalResult.OK, MessageBox.ERROR);
      return false;
    }
    if (continueOnError) {
      StringBuilder errors = new StringBuilder();
      int done = 0;
      for (int i = 0; i < sql.size(); i++) {
        try {
          run(database, sql.get(i));
          done++;
        } catch (Exception ex) {
          errors.append(shown.get(i)).append("\n  ").append(ex.getMessage()).append("\n");
        }
      }
      if (errors.length() > 0) {
        MessageBox.show(this, stringManager.getString("error"), errors.toString(), ModalResult.OK, MessageBox.ERROR);
      }
      // nic sie nie udalo - okno zostaje otwarte jak przy bledzie pojedynczego polecenia
      return done > 0;
    }
    int i = 0;
    try {
      for (; i < sql.size(); i++) {
        run(database, sql.get(i));
      }
      return true;
    } catch (Exception ex) {
      String done = i > 0 ? "\n\n" + stringManager.getString("users-done-before-error", Integer.toString(i)) : "";
      MessageBox.show(this, stringManager.getString("error"), ex.getMessage() + "\n\n" + shown.get(i) + done, ModalResult.OK, MessageBox.ERROR);
      return false;
    }
  }

  /** Polecenie bez analizy parametrow (: w hasle) */
  public static void run(Database database, String sql) throws Exception {
    Command command = database.createCommand();
    command.setParamCheck(false);
    command.setSqlText(sql);
    command.execute();
  }

  protected static boolean isEmpty(String s) {
    return StringUtil.isEmpty(s);
  }

}
