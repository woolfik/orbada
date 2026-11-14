package pl.mpak.orbada.sqlite.gui.ext;

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
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import pl.mpak.orbada.sqlite.OrbadaSQLitePlugin;
import pl.mpak.orbada.sqlite.SQLiteSql;
import pl.mpak.orbada.universal.gui.wizards.SqlCodeWizardPanel;
import pl.mpak.sky.gui.mr.ModalResult;
import pl.mpak.sky.gui.swing.MessageBox;
import pl.mpak.sky.gui.swing.comp.TextField;
import pl.mpak.usedb.core.Command;
import pl.mpak.usedb.core.Database;
import pl.mpak.util.StringManager;
import pl.mpak.util.StringManagerFactory;

/**
 * ORBADA 1.3.8: baza kreatorow SQLite (jak UserWizard z widoku Uzytkownicy Oracle): formularz etykieta + pole,
 * polecenia budowane w build(), podglad w zakladce SQL, wykonanie po kolei do pierwszego bledu. Kilka polecen
 * (np. kopia tabeli z danymi) w jednej transakcji. Tytul: klucz "NazwaKlasy-title" w i18n.
 */
public abstract class SQLiteWizard extends SqlCodeWizardPanel {

  protected final static StringManager stringManager = StringManagerFactory.getStringManager(OrbadaSQLitePlugin.class);

  protected final Database database;
  /** Baza (main, temp, dolaczona) */
  protected final String dbName;
  private final JPanel form = new JPanel(new GridBagLayout());
  private int row = 0;

  protected SQLiteWizard(Database database, String dbName) {
    this.database = database;
    this.dbName = dbName == null || dbName.length() == 0 ? "main" : dbName;
    setLayout(new BorderLayout());
    form.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
    add(form, BorderLayout.CENTER);
    GridBagConstraints gc = new GridBagConstraints();
    gc.gridy = 1000;
    gc.weighty = 1e-6;
    form.add(new JPanel(), gc);
  }

  // ---------------------------------------------------------------- formularz

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
    form.add(field instanceof JList || field instanceof JTextArea ? new JScrollPane(field) : field, gc);
    return field;
  }

  protected TextField text(String labelKey, String value) {
    TextField field = new TextField();
    field.setText(value);
    return row(labelKey, field, false);
  }

  protected TextField info(String labelKey, String value) {
    TextField field = text(labelKey, value);
    field.setEditable(false);
    field.setFocusable(false);
    return field;
  }

  protected JTextArea area(String labelKey, String value, int rows) {
    JTextArea area = new JTextArea(value, rows, 50);
    area.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, area.getFont().getSize()));
    return row(labelKey, area, true);
  }

  protected JLabel note(String key, Object... args) {
    String s = args.length == 0 ? stringManager.getString(key) : stringManager.getString(key, args);
    return row(null, new JLabel("<html>" + s + "</html>"), false);
  }

  /** Tekst bez klucza i18n (np. pytanie z nazwa obiektu) - znaki HTML zamienione */
  protected JLabel plain(String text) {
    String t = text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>");
    return row(null, new JLabel("<html>" + t + "</html>"), false);
  }

  protected JComboBox<String> combo(String labelKey, boolean editable, String... items) {
    JComboBox<String> combo = new JComboBox<String>(items == null ? new String[0] : items);
    combo.setEditable(editable);
    return row(labelKey, combo, false);
  }

  protected JCheckBox check(String key) {
    return row(null, new JCheckBox(stringManager.getString(key)), false);
  }

  protected JList<String> multi(String labelKey, List<String> items) {
    DefaultListModel<String> model = new DefaultListModel<String>();
    for (String s : items) {
      model.addElement(s);
    }
    JList<String> list = new JList<String>(model);
    list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    list.setVisibleRowCount(8);
    return row(labelKey, list, true);
  }

  protected static String value(JComboBox<?> combo) {
    Object o = combo.isEditable() ? combo.getEditor().getItem() : combo.getSelectedItem();
    return o == null ? "" : o.toString().trim();
  }

  protected static String value(javax.swing.text.JTextComponent text) {
    return text.getText() == null ? "" : text.getText().trim();
  }

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

  /** Nazwa obiektu w tej bazie (prefiks bazy poza main) */
  protected String name(String object) {
    return SQLiteSql.name(dbName, object);
  }

  /** Nazwa wpisana przez uzytkownika: w cudzyslowie wpisana dokladnie, inna bez zmian (SQLite nie zmienia wielkosci) */
  public static String typed(String name) {
    name = name == null ? "" : name.trim();
    if (name.length() > 1 && name.startsWith("\"") && name.endsWith("\"")) {
      return name.substring(1, name.length() - 1).replace("\"\"", "\"");
    }
    return name;
  }

  protected List<String> columns(String table) {
    return SQLiteSql.list(database, "select name from pragma_table_xinfo(" + SQLiteSql.lit(table) + ", " + SQLiteSql.lit(dbName) + ") order by cid");
  }

  protected List<String> tables() {
    return SQLiteSql.list(database, "select name from " + SQLiteSql.q(dbName) + ".sqlite_master where type = 'table' and name not like 'sqlite!_%' escape '!' order by name");
  }

  protected void require(boolean condition, String messageKey) {
    if (!condition) {
      throw new IllegalArgumentException(stringManager.getString(messageKey));
    }
  }

  /** Polecenia kreatora */
  protected abstract void build(List<String> sql);

  public List<String> statements() {
    List<String> sql = new ArrayList<String>();
    build(sql);
    return sql;
  }

  @Override
  public String getDialogTitle() {
    return stringManager.getString(getClass().getSimpleName() + "-title");
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
      for (String s : statements()) {
        sb.append(s).append(";\n");
      }
    } catch (IllegalArgumentException ex) {
      return "-- " + ex.getMessage();
    }
    return sb.toString();
  }

  @Override
  public boolean execute() {
    try {
      run(database, statements());
      return true;
    } catch (IllegalArgumentException ex) {
      MessageBox.show(this, stringManager.getString("error"), ex.getMessage(), ModalResult.OK, MessageBox.ERROR);
      return false;
    } catch (Exception ex) {
      MessageBox.show(this, stringManager.getString("error"), ex.getMessage(), ModalResult.OK, MessageBox.ERROR);
      return false;
    }
  }

  /**
   * Wykonanie polecen; kilka polecen w transakcji (SAVEPOINT - dziala takze, gdy polaczenie ma juz otwarta
   * transakcje), blad wycofuje wszystkie. VACUUM i niektore PRAGMA nie moga byc w transakcji - pojedyncze polecenie
   * bez SAVEPOINT.
   */
  public static void run(Database database, List<String> sql) throws Exception {
    if (sql.size() == 1) {
      run(database, sql.get(0));
      return;
    }
    run(database, "SAVEPOINT orbada_wizard");
    try {
      for (String s : sql) {
        run(database, s);
      }
      run(database, "RELEASE orbada_wizard");
    } catch (Exception ex) {
      try {
        run(database, "ROLLBACK TO orbada_wizard");
        run(database, "RELEASE orbada_wizard");
      } catch (Exception ignore) {
        // polaczenie zerwane - pierwotny blad wazniejszy
      }
      throw ex;
    }
  }

  /**
   * Polecenie, ktore SQLite odrzuca w transakcji (VACUUM, VACUUM INTO, zmiana journal_mode na WAL): przy otwartej
   * transakcji Orbady (autocommit wylaczony) czytelny blad zamiast "cannot VACUUM from within a transaction" -
   * bez zatwierdzania cudzych zmian za uzytkownika.
   */
  public static void runOutsideTransaction(Database database, String sql) throws Exception {
    if (!database.getConnection().getAutoCommit()) {
      throw new IllegalStateException(stringManager.getString("sqlite-transaction-open"));
    }
    try {
      run(database, sql);
    } catch (java.sql.SQLException ex) {
      // transakcja otwarta recznie (BEGIN w edytorze SQL) przy wlaczonym autocommit
      if (ex.getMessage() != null && ex.getMessage().contains("within a transaction")) {
        throw new IllegalStateException(stringManager.getString("sqlite-transaction-open"), ex);
      }
      throw ex;
    }
  }

  /** Polecenie bez analizy parametrow (dwukropek w nazwach i literalach) */
  public static void run(Database database, String sql) throws Exception {
    Command command = database.createCommand();
    command.setParamCheck(false);
    command.setSqlText(sql);
    command.execute();
    // polecenie zwracajace wiersze (PRAGMA x = y, journal_mode) zostawia otwarta instrukcje - SQLite odrzucilby
    // potem VACUUM/COMMIT ("SQL statements in progress")
    if (command.getStatement() != null) {
      command.getStatement().close();
    }
  }

}
