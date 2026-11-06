package pl.mpak.orbada.postgresql.gui.wizards;

import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import pl.mpak.sky.gui.mr.ModalResult;
import pl.mpak.sky.gui.swing.MessageBox;
import pl.mpak.usedb.core.Database;
import pl.mpak.usedb.core.Query;
import pl.mpak.util.ExceptionUtil;

/**
 * ORBADA 1.3.2: wywolanie funkcji (SELECT * FROM f(...)) albo procedury (CALL p(...)) z wartosciami argumentow
 * (wyrazenia SQL) i pokazanie wyniku - odpowiednik CallObjectWizard Oracle. Argumenty nazwane przekazywane jako
 * nazwa => wartosc, puste wartosci argumentow z DEFAULT pomijane.
 */
public class CallRoutineWizard extends PgWizard {

  private static final int MAX_ROWS = 100;

  private final String signature;
  private String routine = "";
  private boolean procedure;
  private final List<String> modes = new ArrayList<String>();
  private final List<Boolean> defaults = new ArrayList<Boolean>();
  private final DefaultTableModel arguments;
  private final JTable table;
  private String result;

  /** signature: schemat.nazwa(typy argumentow) jak w liscie funkcji/procedur */
  public CallRoutineWizard(Database database, String signature) {
    super(database, null);
    this.signature = signature;
    info("object-name", signature);
    arguments = new DefaultTableModel(new Object[] {
      stringManager.getString("argument-name"), stringManager.getString("argument-mode"),
      stringManager.getString("data-type"), stringManager.getString("argument-value")}, 0) {
      @Override
      public boolean isCellEditable(int row, int column) {
        return column == 3 && !"o".equals(modes.get(row));
      }
    };
    load();
    table = new JTable(arguments);
    JScrollPane scroll = new JScrollPane(table);
    scroll.setPreferredSize(new Dimension(500, 150));
    if (arguments.getRowCount() > 0) {
      row("arguments", scroll, true);
      note("argument-value-hint");
    }
  }

  private void load() {
    Query query = database.createQuery();
    try {
      // sygnatura z listy (pg_get_function_identity_arguments) ma nazwy i tryby argumentow - regprocedure ich nie zna,
      // wiec najpierw porownanie z nia, dopiero potem rzutowanie (np. "schemat.f(integer)")
      String[] oid = list("select p.oid::text from pg_catalog.pg_proc p join pg_catalog.pg_namespace n on n.oid = p.pronamespace" +
        " where quote_ident(n.nspname)||'.'||quote_ident(p.proname)||'('||coalesce(pg_catalog.pg_get_function_identity_arguments(p.oid), '')||')' = :NAME", signature);
      if (oid.length == 0) {
        oid = list("select (cast(:NAME as text))::regprocedure::oid::text", signature);
      }
      if (oid.length == 0) {
        return;
      }
      query.setSqlText(
        "select quote_ident(n.nspname)||'.'||quote_ident(p.proname) routine_name, " +
        (atLeast(11, 0) ? "case when p.prokind = 'p' then 'Y' else 'N' end" : "'N'") + " is_procedure,\n" +
        "       coalesce(p.proargnames[a.n], '') arg_name, coalesce(p.proargmodes[a.n]::text, 'i') arg_mode,\n" +
        "       pg_catalog.format_type(a.t, null) arg_type, p.pronargdefaults, a.n\n" +
        "  from pg_catalog.pg_proc p join pg_catalog.pg_namespace n on n.oid = p.pronamespace\n" +
        "       left join lateral unnest(coalesce(p.proallargtypes, p.proargtypes::oid[])) with ordinality a(t, n) on true\n" +
        " where p.oid = cast(:OID as text)::oid\n" +
        " order by a.n");
      query.paramByName("OID").setString(oid[0]);
      query.open();
      int nargDefaults = 0;
      while (!query.eof()) {
        routine = query.fieldByName("routine_name").getString();
        procedure = "Y".equals(query.fieldByName("is_procedure").getString());
        nargDefaults = query.fieldByName("pronargdefaults").getInteger();
        if (!query.fieldByName("n").isNull()) {
          String mode = query.fieldByName("arg_mode").getString();
          modes.add(mode);
          arguments.addRow(new Object[] {query.fieldByName("arg_name").getString(),
            "i".equals(mode) ? "IN" : "o".equals(mode) ? "OUT" : "b".equals(mode) ? "INOUT" : "v".equals(mode) ? "VARIADIC" : "TABLE",
            query.fieldByName("arg_type").getString(), "o".equals(mode) || "t".equals(mode) ? "" : "NULL"});
        }
        query.next();
      }
      // DEFAULT maja ostatnie argumenty wejsciowe
      int inputs = 0;
      for (String m : modes) {
        inputs += isInput(m) ? 1 : 0;
      }
      int index = 0;
      for (String m : modes) {
        boolean d = false;
        if (isInput(m)) {
          d = index >= inputs - nargDefaults;
          index++;
        }
        defaults.add(d);
        if (d) {
          arguments.setValueAt("", defaults.size() - 1, 3);
        }
      }
    } catch (Exception ex) {
      ExceptionUtil.processException(ex);
    } finally {
      query.close();
    }
  }

  private static boolean isInput(String mode) {
    return "i".equals(mode) || "b".equals(mode) || "v".equals(mode);
  }

  public void setValue(int row, String value) {
    arguments.setValueAt(value, row, 3);
  }

  public String getResult() {
    return result;
  }

  @Override
  protected void build(List<String> sql) {
    if (table != null && table.isEditing()) {
      table.getCellEditor().stopCellEditing();
    }
    // argumenty wywolania: OUT tylko dla procedury (PostgreSQL 14+, przekazywany NULL), bez kolumn RETURNS TABLE
    List<Integer> rows = new ArrayList<Integer>();
    boolean allNamed = true;
    for (int r = 0; r < arguments.getRowCount(); r++) {
      String mode = modes.get(r);
      if ("t".equals(mode) || ("o".equals(mode) && !procedure)) {
        continue;
      }
      rows.add(r);
      allNamed &= String.valueOf(arguments.getValueAt(r, 0)).length() > 0;
    }
    // notacja nazwana (nazwa => wartosc) pozwala pominac dowolny argument z DEFAULT; pozycyjna - tylko koncowe
    List<String> values = new ArrayList<String>();
    List<Boolean> skip = new ArrayList<Boolean>();
    for (int r : rows) {
      Object v = arguments.getValueAt(r, 3);
      String value = "o".equals(modes.get(r)) ? "NULL" : v == null ? "" : v.toString().trim();
      skip.add(value.length() == 0 && defaults.get(r));
      values.add((("v".equals(modes.get(r))) ? "VARIADIC " : "") +
        (allNamed ? ident(String.valueOf(arguments.getValueAt(r, 0))) + " => " : "") +
        (value.length() == 0 ? "NULL" : value));
    }
    int last = values.size();
    while (!allNamed && last > 0 && skip.get(last - 1)) {
      last--;
    }
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < last; i++) {
      if (allNamed && skip.get(i)) {
        continue;
      }
      sb.append(sb.length() > 0 ? ", " : "").append(values.get(i));
    }
    sql.add((procedure ? "CALL " : "SELECT * FROM ") + routine + "(" + sb + ")");
  }

  @Override
  protected void executeStatement(String sql) throws Exception {
    Query query = database.createQuery();
    try {
      query.setParamCheck(false);
      query.setSqlText(sql);
      if (procedure && !hasOutput()) {
        run(sql);
        result = "";
      } else {
        query.open();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < query.getFieldCount(); i++) {
          sb.append(i > 0 ? " | " : "").append(query.getField(i).getFieldName());
        }
        int rows = 0;
        while (!query.eof() && rows < MAX_ROWS) {
          sb.append("\n");
          for (int i = 0; i < query.getFieldCount(); i++) {
            sb.append(i > 0 ? " | " : "").append(query.getField(i).isNull() ? "NULL" : query.getField(i).getString());
          }
          rows++;
          query.next();
        }
        if (!query.eof()) {
          sb.append("\n...");
        }
        result = sb.toString();
      }
    } finally {
      query.close();
    }
  }

  private boolean hasOutput() {
    for (String m : modes) {
      if ("o".equals(m) || "b".equals(m)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean execute() {
    if (!super.execute()) {
      return false;
    }
    MessageBox.show(this, getDialogTitle(), result == null || result.length() == 0 ? stringManager.getString("call-done") : result,
      ModalResult.OK, MessageBox.INFORMATION);
    return true;
  }

}
