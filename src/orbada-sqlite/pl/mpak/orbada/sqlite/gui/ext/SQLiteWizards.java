package pl.mpak.orbada.sqlite.gui.ext;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import pl.mpak.orbada.sqlite.SQLiteSql;
import pl.mpak.sky.gui.mr.ModalResult;
import pl.mpak.sky.gui.swing.MessageBox;
import pl.mpak.sky.gui.swing.comp.TextField;
import pl.mpak.usedb.core.Database;

/**
 * ORBADA 1.3.8: kreatory SQLite z podgladem SQL - tabele (tworzenie, kopia, zmiana nazwy), kolumny (dodanie, zmiana
 * nazwy, usuniecie), indeksy, widoki, wyzwalacze, pragmy, liczniki AUTOINCREMENT, kopia bazy (VACUUM INTO) i
 * polecenia z potwierdzeniem (usuniecie obiektu, ANALYZE, REINDEX...).
 */
public class SQLiteWizards {

  private final static String[] TYPES = {"INTEGER", "TEXT", "REAL", "NUMERIC", "BLOB", "ANY", "INT", "VARCHAR(100)", "DATE", "DATETIME", "BOOLEAN"};

  private static String ifNotExists(JCheckBox check) {
    return check.isSelected() ? "IF NOT EXISTS " : "";
  }

  // ---------------------------------------------------------------- tabele

  public static class CreateTable extends SQLiteWizard {
    final TextField name;
    final JCheckBox temp;
    final JCheckBox ifNotExists;
    final JTextArea columns;
    final JCheckBox strict;
    final JCheckBox withoutRowid;

    public CreateTable(Database database, String dbName) {
      super(database, dbName);
      info("sqlite-database", this.dbName);
      name = text("sqlite-table-name", "");
      temp = check("sqlite-temporary");
      ifNotExists = check("sqlite-if-not-exists");
      columns = area("sqlite-columns-definition", "id INTEGER PRIMARY KEY,\nname TEXT NOT NULL", 8);
      note("sqlite-columns-definition-hint");
      strict = check("sqlite-strict");
      withoutRowid = check("sqlite-without-rowid");
    }

    protected void build(List<String> sql) {
      String n = typed(value(name));
      require(n.length() > 0, "sqlite-name-required");
      require(value(columns).length() > 0, "sqlite-columns-required");
      StringBuilder options = new StringBuilder();
      if (strict.isSelected()) {
        options.append(" STRICT");
      }
      if (withoutRowid.isSelected()) {
        options.append(options.length() > 0 ? "," : "").append(" WITHOUT ROWID");
      }
      String body = value(columns);
      if (body.endsWith(",")) {
        body = body.substring(0, body.length() - 1);
      }
      sql.add("CREATE " + (temp.isSelected() ? "TEMP " : "") + "TABLE " + ifNotExists(ifNotExists) +
        (temp.isSelected() ? SQLiteSql.ident(n) : name(n)) + " (\n  " + body.replace("\n", "\n  ") + "\n)" + options);
    }
  }

  /** Kopia tabeli: z definicja (ograniczenia, klucze) albo CREATE TABLE AS SELECT; opcjonalnie z danymi i indeksami */
  public static class CopyTable extends SQLiteWizard {
    final String table;
    final TextField name;
    final JCheckBox data;
    final JCheckBox indexes;

    public CopyTable(Database database, String dbName, String table) {
      super(database, dbName);
      this.table = table;
      info("sqlite-database", this.dbName);
      info("table", table);
      name = text("sqlite-new-name", table + "_copy");
      data = check("sqlite-copy-data");
      data.setSelected(true);
      indexes = check("sqlite-copy-indexes");
      indexes.setSelected(true);
    }

    protected void build(List<String> sql) {
      String n = typed(value(name));
      require(n.length() > 0, "sqlite-name-required");
      require(!n.equalsIgnoreCase(table), "sqlite-name-same");
      String ddl = SQLiteSql.one(database, "select sql from " + SQLiteSql.q(dbName) + ".sqlite_master where type = 'table' and name = " + SQLiteSql.lit(table));
      String created = ddl == null ? null : renameCreate(ddl, "TABLE", name(n));
      sql.add(created != null ? created : "CREATE TABLE " + name(n) + " AS SELECT * FROM " + name(table) + " WHERE 0");
      if (data.isSelected()) {
        String cols = columnList(database, dbName, table);
        sql.add("INSERT INTO " + name(n) + " (" + cols + ") SELECT " + cols + " FROM " + name(table));
      }
      if (indexes.isSelected() && created != null) {
        for (String idx : SQLiteSql.list(database, "select name from " + SQLiteSql.q(dbName) + ".sqlite_master where type = 'index' and tbl_name = " +
            SQLiteSql.lit(table) + " and sql is not null order by name")) {
          String isql = SQLiteSql.one(database, "select sql from " + SQLiteSql.q(dbName) + ".sqlite_master where name = " + SQLiteSql.lit(idx));
          String renamed = isql == null ? null : renameIndex(isql, name(n + "_" + idx), SQLiteSql.ident(n));
          if (renamed != null) {
            sql.add(renamed);
          }
        }
      }
    }
  }

  /** Kolumny do kopiowania danych (bez kolumn generowanych i ukrytych) */
  public static String columnList(Database database, String dbName, String table) {
    StringBuilder sb = new StringBuilder();
    for (String c : SQLiteSql.list(database, "select name from pragma_table_xinfo(" + SQLiteSql.lit(table) + ", " + SQLiteSql.lit(dbName) + ") where hidden = 0 order by cid")) {
      sb.append(sb.length() > 0 ? ", " : "").append(SQLiteSql.ident(c));
    }
    return sb.toString();
  }

  private final static String NAME = "(?:\"(?:[^\"]|\"\")*\"|\\[[^\\]]*\\]|`[^`]*`|[^\\s(.]+)";

  /** CREATE [TEMP] TABLE|VIEW [IF NOT EXISTS] nazwa -> nowa nazwa (z prefiksem bazy); null gdy nie rozpoznano */
  public static String renameCreate(String ddl, String type, String newName) {
    Matcher m = Pattern.compile("(?is)^(\\s*CREATE\\s+(?:TEMP\\s+|TEMPORARY\\s+)?" + type + "\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?)(" + NAME + "(?:\\s*\\.\\s*" + NAME + ")?)").matcher(ddl);
    if (!m.find()) {
      return null;
    }
    return m.group(1) + newName + ddl.substring(m.end(2));
  }

  /** CREATE [UNIQUE] INDEX nazwa ON tabela -> nowa nazwa indeksu i tabeli; null gdy nie rozpoznano */
  public static String renameIndex(String ddl, String newName, String newTable) {
    Matcher m = Pattern.compile("(?is)^(\\s*CREATE\\s+(?:UNIQUE\\s+)?INDEX\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?)(" + NAME + "(?:\\s*\\.\\s*" + NAME + ")?)(\\s+ON\\s+)(" + NAME + ")").matcher(ddl);
    if (!m.find()) {
      return null;
    }
    return m.group(1) + newName + m.group(3) + newTable + ddl.substring(m.end(4));
  }

  public static class RenameTable extends SQLiteWizard {
    final String table;
    final TextField name;

    public RenameTable(Database database, String dbName, String table) {
      super(database, dbName);
      this.table = table;
      info("sqlite-database", this.dbName);
      info("table", table);
      name = text("sqlite-new-name", table);
      note("sqlite-rename-table-hint");
    }

    protected void build(List<String> sql) {
      String n = typed(value(name));
      require(n.length() > 0, "sqlite-name-required");
      require(!n.equals(table), "sqlite-name-same");
      sql.add("ALTER TABLE " + name(table) + " RENAME TO " + SQLiteSql.ident(n));
    }
  }

  // ---------------------------------------------------------------- kolumny

  public static class AddColumn extends SQLiteWizard {
    final String table;
    final TextField name;
    final JComboBox<String> type;
    final JCheckBox notNull;
    final TextField defaultValue;
    final TextField constraints;

    public AddColumn(Database database, String dbName, String table) {
      super(database, dbName);
      this.table = table;
      info("table", table);
      name = text("column-name", "");
      type = combo("column-type", true, TYPES);
      notNull = check("sqlite-not-null");
      defaultValue = text("sqlite-default", "");
      constraints = text("sqlite-constraints", "");
      note("sqlite-add-column-hint");
    }

    protected void build(List<String> sql) {
      String n = typed(value(name));
      require(n.length() > 0, "sqlite-name-required");
      String d = value(defaultValue);
      require(!notNull.isSelected() || d.length() > 0, "sqlite-not-null-default");
      sql.add("ALTER TABLE " + name(table) + " ADD COLUMN " + SQLiteSql.ident(n) +
        (value(type).length() > 0 ? " " + value(type) : "") + (notNull.isSelected() ? " NOT NULL" : "") +
        (d.length() > 0 ? " DEFAULT " + d : "") + (value(constraints).length() > 0 ? " " + value(constraints) : ""));
    }
  }

  public static class RenameColumn extends SQLiteWizard {
    final String table;
    final JComboBox<String> column;
    final TextField name;

    public RenameColumn(Database database, String dbName, String table, String columnName) {
      super(database, dbName);
      this.table = table;
      info("table", table);
      List<String> cols = columns(table);
      column = combo("column", false, cols.toArray(new String[cols.size()]));
      set(column, columnName);
      name = text("sqlite-new-name", columnName == null ? "" : columnName);
      note("sqlite-rename-column-hint");
    }

    protected void build(List<String> sql) {
      String n = typed(value(name));
      require(value(column).length() > 0, "sqlite-column-required");
      require(n.length() > 0, "sqlite-name-required");
      require(!n.equals(value(column)), "sqlite-name-same");
      sql.add("ALTER TABLE " + name(table) + " RENAME COLUMN " + SQLiteSql.ident(value(column)) + " TO " + SQLiteSql.ident(n));
    }
  }

  public static class DropColumn extends SQLiteWizard {
    final String table;
    final JComboBox<String> column;

    public DropColumn(Database database, String dbName, String table, String columnName) {
      super(database, dbName);
      this.table = table;
      info("table", table);
      List<String> cols = columns(table);
      column = combo("column", false, cols.toArray(new String[cols.size()]));
      set(column, columnName);
      note("sqlite-drop-column-hint");
    }

    protected void build(List<String> sql) {
      require(value(column).length() > 0, "sqlite-column-required");
      sql.add("ALTER TABLE " + name(table) + " DROP COLUMN " + SQLiteSql.ident(value(column)));
    }
  }

  // ---------------------------------------------------------------- indeksy

  public static class CreateIndex extends SQLiteWizard {
    final TextField name;
    final JComboBox<String> table;
    final JCheckBox unique;
    final JCheckBox ifNotExists;
    final JList<String> columns;
    final TextField expression;
    final JCheckBox desc;
    final TextField where;
    private String lastTable;

    public CreateIndex(Database database, String dbName, String tableName) {
      super(database, dbName);
      info("sqlite-database", this.dbName);
      List<String> tl = tables();
      table = combo("table", false, tl.toArray(new String[tl.size()]));
      set(table, tableName);
      name = text("index-name", tableName == null ? "" : tableName + "_idx");
      unique = check("unique");
      ifNotExists = check("sqlite-if-not-exists");
      columns = multi("sqlite-columns", tableName == null ? new ArrayList<String>() : columns(tableName));
      desc = check("sqlite-descending");
      expression = text("sqlite-index-expression", "");
      where = text("sqlite-index-where", "");
      note("sqlite-index-hint");
      lastTable = tableName;
      table.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          String t = value(table);
          if (!t.equals(lastTable)) {
            lastTable = t;
            javax.swing.DefaultListModel<String> model = new javax.swing.DefaultListModel<String>();
            for (String c : columns(t)) {
              model.addElement(c);
            }
            columns.setModel(model);
            name.setText(t + "_idx");
          }
        }
      });
    }

    protected void build(List<String> sql) {
      String n = typed(value(name));
      require(n.length() > 0, "sqlite-name-required");
      require(value(table).length() > 0, "sqlite-table-required");
      StringBuilder cols = new StringBuilder();
      for (String c : columns.getSelectedValuesList()) {
        cols.append(cols.length() > 0 ? ", " : "").append(SQLiteSql.ident(c)).append(desc.isSelected() ? " DESC" : "");
      }
      if (value(expression).length() > 0) {
        cols.append(cols.length() > 0 ? ", " : "").append(value(expression));
      }
      require(cols.length() > 0, "sqlite-columns-required");
      sql.add("CREATE " + (unique.isSelected() ? "UNIQUE " : "") + "INDEX " + ifNotExists(ifNotExists) + name(n) +
        " ON " + SQLiteSql.ident(value(table)) + " (" + cols + ")" + (value(where).length() > 0 ? " WHERE " + value(where) : ""));
    }
  }

  // ---------------------------------------------------------------- widoki i wyzwalacze

  public static class CreateView extends SQLiteWizard {
    final TextField name;
    final JCheckBox temp;
    final JCheckBox ifNotExists;
    final TextField columnNames;
    final JTextArea select;

    public CreateView(Database database, String dbName, String sourceTable) {
      super(database, dbName);
      info("sqlite-database", this.dbName);
      name = text("sqlite-view-name", sourceTable == null ? "" : sourceTable + "_v");
      temp = check("sqlite-temporary");
      ifNotExists = check("sqlite-if-not-exists");
      columnNames = text("sqlite-view-columns", "");
      String cols = sourceTable == null ? "*" : columnList(database, this.dbName, sourceTable);
      select = area("sqlite-select", sourceTable == null ? "SELECT " : "SELECT " + cols + "\n  FROM " + SQLiteSql.ident(sourceTable), 8);
    }

    protected void build(List<String> sql) {
      String n = typed(value(name));
      require(n.length() > 0, "sqlite-name-required");
      String s = value(select);
      if (s.endsWith(";")) {
        s = s.substring(0, s.length() - 1).trim();
      }
      require(s.length() > "SELECT".length(), "sqlite-select-required");
      sql.add("CREATE " + (temp.isSelected() ? "TEMP " : "") + "VIEW " + ifNotExists(ifNotExists) +
        (temp.isSelected() ? SQLiteSql.ident(n) : name(n)) + (value(columnNames).length() > 0 ? " (" + value(columnNames) + ")" : "") + " AS\n" + s);
    }
  }

  public static class CreateTrigger extends SQLiteWizard {
    final TextField name;
    final JComboBox<String> table;
    final JComboBox<String> timing;
    final JComboBox<String> event;
    final TextField ofColumns;
    final TextField when;
    final JCheckBox ifNotExists;
    final JTextArea body;

    public CreateTrigger(Database database, String dbName, String tableName, boolean view) {
      super(database, dbName);
      info("sqlite-database", this.dbName);
      List<String> tl = view
        ? SQLiteSql.list(database, "select name from " + SQLiteSql.q(this.dbName) + ".sqlite_master where type = 'view' order by name")
        : tables();
      table = combo(view ? "sqlite-view" : "table", false, tl.toArray(new String[tl.size()]));
      set(table, tableName);
      name = text("sqlite-trigger-name", tableName == null ? "" : tableName + "_trg");
      timing = combo("sqlite-trigger-timing", false, view ? new String[] {"INSTEAD OF"} : new String[] {"BEFORE", "AFTER"});
      event = combo("sqlite-trigger-event", false, "INSERT", "UPDATE", "DELETE");
      ofColumns = text("sqlite-trigger-of", "");
      when = text("sqlite-trigger-when", "");
      ifNotExists = check("sqlite-if-not-exists");
      body = area("sqlite-trigger-body", "  SELECT RAISE(ABORT, 'TODO');", 8);
      note("sqlite-trigger-hint");
    }

    protected void build(List<String> sql) {
      String n = typed(value(name));
      require(n.length() > 0, "sqlite-name-required");
      require(value(table).length() > 0, "sqlite-table-required");
      require(value(body).length() > 0, "sqlite-body-required");
      String b = value(body);
      if (!b.endsWith(";")) {
        b = b + ";";
      }
      boolean update = "UPDATE".equals(value(event));
      sql.add("CREATE TRIGGER " + ifNotExists(ifNotExists) + name(n) + "\n  " + value(timing) + " " + value(event) +
        (update && value(ofColumns).length() > 0 ? " OF " + value(ofColumns) : "") + " ON " + SQLiteSql.ident(value(table)) +
        "\n  FOR EACH ROW" + (value(when).length() > 0 ? "\n  WHEN " + value(when) : "") + "\nBEGIN\n  " + b.replace("\n", "\n  ").trim() + "\nEND");
    }
  }

  // ---------------------------------------------------------------- pragmy, AUTOINCREMENT, kopia bazy

  public static class SetPragma extends SQLiteWizard {
    final JComboBox<String> pragma;
    final JComboBox<String> value;
    final JCheckBox vacuum;

    public SetPragma(Database database, String dbName, String pragmaName) {
      super(database, dbName);
      info("sqlite-database", this.dbName);
      List<String> names = new ArrayList<String>();
      for (SQLiteSql.Pragma p : SQLiteSql.PRAGMAS) {
        if (p.settable) {
          names.add(p.name);
        }
      }
      pragma = combo("sqlite-pragma", false, names.toArray(new String[names.size()]));
      value = combo("sqlite-value", true);
      vacuum = check("sqlite-pragma-vacuum");
      note("sqlite-pragma-hint");
      pragma.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          fill();
        }
      });
      if (pragmaName != null) {
        set(pragma, pragmaName);
      }
      fill();
    }

    private void fill() {
      SQLiteSql.Pragma p = SQLiteSql.pragma(value(pragma));
      if (p == null) {
        return;
      }
      javax.swing.DefaultComboBoxModel<String> model = new javax.swing.DefaultComboBoxModel<String>(p.values == null ? new String[0] : p.values);
      value.setModel(model);
      String current = SQLiteSql.describe(p, SQLiteSql.one(database, SQLiteSql.readPragma(dbName, p)));
      set(value, current);
      boolean needsVacuum = "page_size".equals(p.name) || "auto_vacuum".equals(p.name);
      vacuum.setEnabled(needsVacuum);
      vacuum.setSelected(needsVacuum);
      value.setToolTipText(SQLiteViews.descriptions().get(p.name));
    }

    protected void build(List<String> sql) {
      SQLiteSql.Pragma p = SQLiteSql.pragma(value(pragma));
      require(p != null && p.settable, "sqlite-pragma-required");
      require(value(value).length() > 0, "sqlite-value-required");
      sql.add(SQLiteSql.setPragma(dbName, p, value(value)));
      if (vacuum.isEnabled() && vacuum.isSelected()) {
        sql.add("VACUUM " + SQLiteSql.ident(dbName));
      }
    }

    /** Pragmy i VACUUM poza transakcja (journal_mode = WAL, VACUUM nie dzialaja w transakcji) */
    @Override
    public boolean execute() {
      try {
        for (String s : statements()) {
          runOutsideTransaction(database, s);
        }
        return true;
      } catch (Exception ex) {
        MessageBox.show(this, stringManager.getString("error"), ex.getMessage(), ModalResult.OK, MessageBox.ERROR);
        return false;
      }
    }
  }

  public static class SetSequence extends SQLiteWizard {
    final String table;
    final TextField seq;

    public SetSequence(Database database, String dbName, String table) {
      super(database, dbName);
      this.table = table;
      info("table", table);
      String current = SQLiteSql.one(database, "select seq from " + SQLiteSql.q(this.dbName) + ".sqlite_sequence where name = " + SQLiteSql.lit(table));
      String max = SQLiteSql.one(database, "select max(rowid) from " + name(table));
      info("sqlite-seq", current);
      info("sqlite-max-rowid", max);
      seq = text("sqlite-new-value", max == null ? "0" : max);
      note("sqlite-sequence-hint");
    }

    protected void build(List<String> sql) {
      require(value(seq).matches("-?[0-9]+"), "sqlite-number-required");
      sql.add("UPDATE " + SQLiteSql.q(dbName) + ".sqlite_sequence SET seq = " + value(seq) + " WHERE name = " + SQLiteSql.lit(table));
    }
  }

  public static class Backup extends SQLiteWizard {
    final TextField file;

    public Backup(Database database, String dbName) {
      super(database, dbName);
      info("sqlite-database", this.dbName);
      file = new TextField();
      String current = SQLiteSql.one(database, "select file from pragma_database_list where name = " + SQLiteSql.lit(this.dbName));
      if (current != null && current.length() > 0) {
        File f = new File(current);
        String n = f.getName();
        int dot = n.lastIndexOf('.');
        String stamp = new java.text.SimpleDateFormat("yyyyMMdd-HHmmss").format(new java.util.Date());
        file.setText(new File(f.getParentFile(), (dot > 0 ? n.substring(0, dot) : n) + "-" + stamp + (dot > 0 ? n.substring(dot) : ".db")).getPath());
      }
      JPanel p = new JPanel(new BorderLayout(4, 0));
      p.add(file, BorderLayout.CENTER);
      JButton choose = new JButton("...");
      choose.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          JFileChooser fc = new JFileChooser();
          if (value(file).length() > 0) {
            fc.setSelectedFile(new File(value(file)));
          }
          if (fc.showSaveDialog(Backup.this) == JFileChooser.APPROVE_OPTION) {
            file.setText(fc.getSelectedFile().getPath());
          }
        }
      });
      p.add(choose, BorderLayout.EAST);
      row("sqlite-backup-file", p, false);
      note("sqlite-backup-hint");
    }

    protected void build(List<String> sql) {
      require(value(file).length() > 0, "sqlite-file-required");
      require(!new File(value(file)).exists(), "sqlite-file-exists");
      File parent = new File(value(file)).getAbsoluteFile().getParentFile();
      require(parent != null && parent.isDirectory(), "sqlite-dir-missing");
      sql.add("VACUUM " + SQLiteSql.ident(dbName) + " INTO " + SQLiteSql.lit(value(file)));
    }

    @Override
    public boolean execute() {
      try {
        for (String s : statements()) {
          runOutsideTransaction(database, s);
        }
        return true;
      } catch (Exception ex) {
        MessageBox.show(this, stringManager.getString("error"), ex.getMessage(), ModalResult.OK, MessageBox.ERROR);
        return false;
      }
    }
  }

  // ---------------------------------------------------------------- polecenia z potwierdzeniem

  /** Gotowe polecenia z pytaniem (usuniecie, ANALYZE, REINDEX, VACUUM); outside - poza transakcja (VACUUM) */
  public static class Statement extends SQLiteWizard {
    private final String titleKey;
    private final List<String> sql;
    private final boolean outside;

    public Statement(Database database, String dbName, String titleKey, String question, boolean outside, String... sql) {
      super(database, dbName);
      this.titleKey = titleKey;
      this.outside = outside;
      this.sql = new ArrayList<String>();
      for (String s : sql) {
        this.sql.add(s);
      }
      plain(question);
    }

    @Override
    public String getDialogTitle() {
      return stringManager.getString(titleKey);
    }

    protected void build(List<String> list) {
      list.addAll(sql);
    }

    @Override
    public boolean execute() {
      try {
        if (outside) {
          for (String s : sql) {
            runOutsideTransaction(database, s);
          }
        } else {
          run(database, sql);
        }
        return true;
      } catch (Exception ex) {
        MessageBox.show(this, stringManager.getString("error"), ex.getMessage(), ModalResult.OK, MessageBox.ERROR);
        return false;
      }
    }
  }

}
