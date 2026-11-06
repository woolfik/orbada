package pl.mpak.orbada.postgresql.gui.wizards;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import pl.mpak.orbada.postgresql.OrbadaPostgreSQLPlugin;
import pl.mpak.orbada.postgresql.services.PostgreSQLDbInfoProvider;
import pl.mpak.orbada.universal.gui.wizards.SqlCodeWizardPanel;
import pl.mpak.sky.gui.mr.ModalResult;
import pl.mpak.sky.gui.swing.MessageBox;
import pl.mpak.sky.gui.swing.comp.TextField;
import pl.mpak.usedb.core.Command;
import pl.mpak.usedb.core.Database;
import pl.mpak.usedb.core.Query;
import pl.mpak.util.StringManager;
import pl.mpak.util.StringManagerFactory;
import pl.mpak.util.StringUtil;
import pl.mpak.util.id.VersionID;

/**
 * ORBADA 1.3.2: wspolna baza kreatorow PostgreSQL (odpowiedniki kreatorow Oracle).
 * Formularz etykieta + pole w GridBagLayout, polecenia SQL budowane w build() i wykonywane po kolei do
 * pierwszego bledu. Gdy polaczenie nie ma autocommit, kreator dziala w SAVEPOINT - blad nie przerywa
 * transakcji uzytkownika, a zmiany zatwierdza on sam (jak polecenia z edytora SQL).
 * Tytul okna: klucz "NazwaKlasy-title" w i18n.
 */
public abstract class PgWizard extends SqlCodeWizardPanel {

  protected final StringManager stringManager = StringManagerFactory.getStringManager(OrbadaPostgreSQLPlugin.class);

  /** Slowa zastrzezone PostgreSQL (kategoria "reserved" w dokumentacji) - jako nazwy wymagaja cudzyslowu */
  private static final Set<String> RESERVED = new HashSet<String>(Arrays.asList(
    "all", "analyse", "analyze", "and", "any", "array", "as", "asc", "asymmetric", "authorization", "binary", "both",
    "case", "cast", "check", "collate", "collation", "column", "concurrently", "constraint", "create", "cross",
    "current_catalog", "current_date", "current_role", "current_schema", "current_time", "current_timestamp",
    "current_user", "default", "deferrable", "desc", "distinct", "do", "else", "end", "except", "false", "fetch",
    "for", "foreign", "freeze", "from", "full", "grant", "group", "having", "ilike", "in", "initially", "inner",
    "intersect", "into", "is", "isnull", "join", "lateral", "leading", "left", "like", "limit", "localtime",
    "localtimestamp", "natural", "not", "notnull", "null", "offset", "on", "only", "or", "order", "outer",
    "overlaps", "placing", "primary", "references", "returning", "right", "select", "session_user", "similar",
    "some", "symmetric", "system_user", "table", "tablesample", "then", "to", "trailing", "true", "union", "unique",
    "user", "using", "variadic", "verbose", "when", "where", "window", "with"));

  protected final Database database;
  protected String schemaName;
  /** Klucz i18n tytulu, gdy jedna klasa obsluguje kilka kreatorow (domyslnie "NazwaKlasy-title") */
  protected String titleKey;
  private final JPanel form = new JPanel(new GridBagLayout());
  private int row = 0;
  private JComponent firstField;
  private VersionID version;

  protected PgWizard(Database database, String schemaName) {
    this.database = database;
    this.schemaName = StringUtil.isEmpty(schemaName) ? PostgreSQLDbInfoProvider.getConnectedSchema(database) : schemaName;
    setLayout(new BorderLayout());
    form.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
    add(form, BorderLayout.CENTER);
    // wypelniacz - bez pol rozciaganych w pionie formularz zostaje u gory
    GridBagConstraints gc = new GridBagConstraints();
    gc.gridy = 1000;
    gc.weighty = 1e-6;
    form.add(new JPanel(), gc);
  }

  // ---------------------------------------------------------------- formularz

  protected <T extends JComponent> T row(String labelKey, T field) {
    return row(labelKey, field, false);
  }

  /** Wiersz formularza; fill - pole rozciagane w pionie (siatka, tekst wielowierszowy) */
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
      gc.fill = field instanceof JCheckBox ? GridBagConstraints.NONE : GridBagConstraints.HORIZONTAL;
    }
    form.add(field instanceof JTextArea ? new JScrollPane(field) : field, gc);
    if (firstField == null && field.isFocusable() && !(field instanceof JLabel)) {
      firstField = field;
    }
    return field;
  }

  protected TextField text(String labelKey, String value) {
    TextField field = new TextField();
    field.setText(value);
    return row(labelKey, field);
  }

  /** Pole tylko do odczytu (nazwa obiektu, ktorego dotyczy kreator) */
  protected TextField info(String labelKey, String value) {
    TextField field = text(labelKey, value);
    field.setEditable(false);
    field.setFocusable(false);
    if (firstField == field) {
      firstField = null;
    }
    return field;
  }

  /** Uwaga pod polami (np. ograniczenia polecenia) */
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

  protected JTextArea area(String labelKey, String value) {
    JTextArea area = new JTextArea(value, 8, 50);
    area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
    return row(labelKey, area, true);
  }

  protected static String value(JComboBox<?> combo) {
    Object o = combo.isEditable() ? combo.getEditor().getItem() : combo.getSelectedItem();
    return o == null ? "" : o.toString().trim();
  }

  protected static String value(javax.swing.text.JTextComponent text) {
    return text.getText() == null ? "" : text.getText().trim();
  }

  // ---------------------------------------------------------------- SQL

  /** Nazwa w SQL: bez cudzyslowu tylko male litery, cyfry, _ i $ (poza slowami zastrzezonymi) - jak quote_ident() */
  public static String ident(String name) {
    if (name == null) {
      return null;
    }
    if (name.matches("[a-z_][a-z0-9_$]*") && !RESERVED.contains(name)) {
      return name;
    }
    return "\"" + name.replace("\"", "\"\"") + "\"";
  }

  public static String name(String schema, String object) {
    return StringUtil.isEmpty(schema) ? ident(object) : ident(schema) + "." + ident(object);
  }

  public static String literal(String s) {
    return s == null ? "NULL" : "'" + s.replace("'", "''") + "'";
  }

  /** Lista identyfikatorow oddzielonych przecinkami (wpisana przez uzytkownika) w cudzyslowach gdy trzeba */
  public static String identList(String list) {
    StringBuilder sb = new StringBuilder();
    for (String s : list.split(",")) {
      if (s.trim().length() > 0) {
        sb.append(sb.length() > 0 ? ", " : "").append(ident(s.trim()));
      }
    }
    return sb.toString();
  }

  protected boolean atLeast(int major, int minor) {
    if (version == null) {
      version = new VersionID(PostgreSQLDbInfoProvider.instance.getVersion(database));
    }
    return version.compare(major, minor) >= 0;
  }

  /** Wartosci pierwszej kolumny zapytania (parametry :NAZWA kolejno z args) */
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
      pl.mpak.util.ExceptionUtil.processException(ex);
    } finally {
      query.close();
    }
    return result.toArray(new String[result.size()]);
  }

  protected static String firstOf(String[] values) {
    return values.length > 0 ? values[0] : null;
  }

  protected String[] schemas() {
    return list("select nspname from pg_catalog.pg_namespace where nspname not like 'pg\\_%' and nspname <> 'information_schema' order by 1");
  }

  protected String[] roles() {
    String[] roles = list("select rolname from pg_catalog.pg_roles where rolname not like 'pg\\_%' order by 1");
    String[] result = new String[roles.length + 1];
    result[0] = "PUBLIC";
    System.arraycopy(roles, 0, result, 1, roles.length);
    return result;
  }

  /** Tabele schematu (relkind r, p) */
  protected String[] tables(String schema) {
    return list("select c.relname from pg_catalog.pg_class c join pg_catalog.pg_namespace n on n.oid = c.relnamespace" +
      " where n.nspname = :SCHEMA_NAME and c.relkind in ('r', 'p') order by 1", schema);
  }

  /** Wynik dla wywolujacego (np. object_name) - mapa jest tylko w oknie kreatora */
  protected void result(String key, String value) {
    if (getResultMap() != null) {
      getResultMap().put(key, value);
    }
  }

  /** Brak wymaganej danej - komunikat dla uzytkownika zamiast niepoprawnego SQL */
  protected void require(boolean condition, String messageKey) {
    if (!condition) {
      throw new IllegalArgumentException(stringManager.getString(messageKey));
    }
  }

  /** Polecenia kreatora w kolejnosci wykonania; brak danych - IllegalArgumentException (require) */
  protected abstract void build(List<String> sql);

  protected List<String> statements() {
    List<String> sql = new ArrayList<String>();
    build(sql);
    return sql;
  }

  @Override
  public String getSqlCode() {
    StringBuilder sb = new StringBuilder();
    try {
      for (String s : statements()) {
        sb.append(s).append("\n/\n");
      }
    } catch (IllegalArgumentException ex) {
      return "-- " + ex.getMessage();
    }
    return sb.toString();
  }

  @Override
  public boolean execute() {
    List<String> sql;
    try {
      sql = statements();
    } catch (IllegalArgumentException ex) {
      MessageBox.show(this, stringManager.getString("error"), ex.getMessage(), ModalResult.OK, MessageBox.ERROR);
      return false;
    }
    boolean savepoint = !database.isAutoCommit();
    String current = null;
    try {
      if (savepoint) {
        run("SAVEPOINT orbada_wizard");
      }
      for (String s : sql) {
        current = s;
        executeStatement(s);
      }
      if (savepoint) {
        run("RELEASE SAVEPOINT orbada_wizard");
      }
      return true;
    } catch (Exception ex) {
      if (savepoint) {
        try {
          run("ROLLBACK TO SAVEPOINT orbada_wizard");
          run("RELEASE SAVEPOINT orbada_wizard");
        } catch (Exception ignore) {
        }
      }
      MessageBox.show(this, stringManager.getString("error"), ex.getMessage() + (current != null ? "\n\n" + current : ""), ModalResult.OK, MessageBox.ERROR);
      return false;
    }
  }

  /** Wykonanie jednego polecenia kreatora (np. wywolanie funkcji pokazuje wynik) */
  protected void executeStatement(String sql) throws Exception {
    run(sql);
  }

  /** Polecenie bez analizy parametrow (:: w rzutowaniach, : w tresci funkcji) */
  protected void run(String sql) throws Exception {
    Command command = database.createCommand();
    command.setParamCheck(false);
    command.setSqlText(sql);
    command.execute();
  }

  // ---------------------------------------------------------------- SqlCodeWizardPanel

  @Override
  public void wizardShow() {
    if (firstField != null) {
      java.awt.EventQueue.invokeLater(new Runnable() {
        @Override
        public void run() {
          firstField.requestFocusInWindow();
        }
      });
    }
  }

  @Override
  public String getDialogTitle() {
    return stringManager.getString(titleKey != null ? titleKey : getClass().getSimpleName() + "-title");
  }

  @Override
  public String getTabTitle() {
    return getDialogTitle();
  }

}
