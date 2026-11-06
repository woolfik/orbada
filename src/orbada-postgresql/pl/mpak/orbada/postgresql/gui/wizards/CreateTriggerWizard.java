package pl.mpak.orbada.postgresql.gui.wizards;

import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import pl.mpak.sky.gui.swing.comp.TextField;
import pl.mpak.usedb.core.Database;

/**
 * ORBADA 1.3.2: wyzwalacz tabeli albo widoku (INSTEAD OF) z istniejaca albo nowa funkcja wyzwalacza (szkielet PL/pgSQL)
 * - odpowiednik CreateTableTriggerWizard i CreateViewTriggerWizard Oracle.
 */
public class CreateTriggerWizard extends PgWizard {

  private final JComboBox<String> comboTable;
  private final TextField textName;
  private final JComboBox<String> comboTiming;
  private final JCheckBox checkInsert = new JCheckBox("INSERT", true);
  private final JCheckBox checkUpdate = new JCheckBox("UPDATE", true);
  private final JCheckBox checkDelete = new JCheckBox("DELETE");
  private final JCheckBox checkTruncate = new JCheckBox("TRUNCATE");
  private final TextField textUpdateOf;
  private final JComboBox<String> comboLevel;
  private final TextField textWhen;
  private final JCheckBox checkNewFunction;
  private final JComboBox<String> comboFunction;
  private final TextField textArguments;
  private final JTextArea textBody;
  private final JCheckBox checkReplace;

  /**
   * @param table tabela/widok (bez schematu) albo null - wybor z listy
   */
  public CreateTriggerWizard(Database database, String schemaName, String table) {
    super(database, schemaName);
    info("schema-name", this.schemaName);
    comboTable = combo("trigger-table", true, list(
      "select c.relname from pg_catalog.pg_class c join pg_catalog.pg_namespace n on n.oid = c.relnamespace" +
      " where n.nspname = :SCHEMA_NAME and c.relkind in ('r', 'p', 'v', 'f') order by 1", this.schemaName));
    if (table != null) {
      comboTable.setSelectedItem(table);
      comboTable.setEnabled(false);
    }
    textName = text("trigger-name", table != null ? table + "_trg" : "");
    checkReplace = atLeast(14, 0) ? check("or-replace") : null;
    comboTiming = combo("trigger-timing", false, "BEFORE", "AFTER", "INSTEAD OF");
    boolean view = table != null && "v".equals(firstOf(list("select c.relkind::text from pg_catalog.pg_class c join pg_catalog.pg_namespace n on n.oid = c.relnamespace" +
        " where n.nspname = :SCHEMA_NAME and c.relname = :TABLE_NAME", this.schemaName, table)));
    if (view) {
      // wyzwalacz widoku: INSTEAD OF ... FOR EACH ROW, bez TRUNCATE
      comboTiming.setSelectedItem("INSTEAD OF");
      checkTruncate.setEnabled(false);
    }
    JPanel events = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
    events.add(checkInsert);
    events.add(checkUpdate);
    events.add(checkDelete);
    events.add(checkTruncate);
    row("trigger-events", events);
    textUpdateOf = text("update-of-columns", "");
    comboLevel = combo("trigger-level", false, "FOR EACH ROW", "FOR EACH STATEMENT");
    textWhen = text("trigger-when", "");
    checkNewFunction = check("trigger-new-function");
    comboFunction = combo("trigger-function", true, list(
      "select quote_ident(n.nspname)||'.'||quote_ident(p.proname) from pg_catalog.pg_proc p join pg_catalog.pg_namespace n on n.oid = p.pronamespace" +
      " where p.prorettype = 'pg_catalog.trigger'::regtype and n.nspname not like 'pg\\_%' and n.nspname <> 'information_schema'" +
      " order by n.nspname <> :SCHEMA_NAME, 1", this.schemaName));
    textArguments = text("trigger-arguments", "");
    textBody = area("trigger-function-body", "BEGIN\n  -- NEW, OLD, TG_OP\n  RETURN NEW;\nEND;");
    textBody.setEnabled(false);
    checkNewFunction.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        textBody.setEnabled(checkNewFunction.isSelected());
        if (checkNewFunction.isSelected() && value(comboFunction).length() == 0) {
          comboFunction.setSelectedItem(name(CreateTriggerWizard.this.schemaName, value(comboTable) + "_trg_fn"));
        }
      }
    });
  }

  public void setTrigger(String name, int timing, boolean insert, boolean update, boolean delete, boolean row, String function, String body) {
    textName.setText(name);
    comboTiming.setSelectedIndex(timing);
    checkInsert.setSelected(insert);
    checkUpdate.setSelected(update);
    checkDelete.setSelected(delete);
    comboLevel.setSelectedIndex(row ? 0 : 1);
    checkNewFunction.setSelected(body != null);
    comboFunction.setSelectedItem(function);
    if (body != null) {
      textBody.setText(body);
    }
  }

  @Override
  protected void build(List<String> sql) {
    require(value(textName).length() > 0, "wizard-name-required");
    require(value(comboTable).length() > 0, "wizard-table-required");
    require(value(comboFunction).length() > 0, "wizard-function-required");
    require(checkInsert.isSelected() || checkUpdate.isSelected() || checkDelete.isSelected() || checkTruncate.isSelected(), "wizard-event-required");
    // INSTEAD OF (widoki): tylko FOR EACH ROW i bez TRUNCATE
    boolean insteadOf = "INSTEAD OF".equals(value(comboTiming));
    require(!insteadOf || comboLevel.getSelectedIndex() == 0, "wizard-instead-of-row");
    require(!insteadOf || !checkTruncate.isSelected(), "wizard-instead-of-truncate");
    result("object_name", value(textName));
    String function = value(comboFunction);
    if (function.length() > 0 && function.indexOf('.') < 0 && !function.startsWith("\"")) {
      function = name(schemaName, function);
    }
    if (checkNewFunction.isSelected()) {
      sql.add("CREATE OR REPLACE FUNCTION " + function + "()\n  RETURNS trigger\n  LANGUAGE plpgsql\nAS " +
        CreateRoutineWizard.dollarQuote(value(textBody)));
    }
    StringBuilder events = new StringBuilder();
    for (JCheckBox c : new JCheckBox[] {checkInsert, checkUpdate, checkDelete, checkTruncate}) {
      if (c.isSelected()) {
        events.append(events.length() > 0 ? " OR " : "").append(c.getText());
        if (c == checkUpdate && value(textUpdateOf).length() > 0) {
          events.append(" OF ").append(identList(value(textUpdateOf)));
        }
      }
    }
    sql.add("CREATE " + (checkReplace != null && checkReplace.isSelected() ? "OR REPLACE " : "") + "TRIGGER " + ident(value(textName)) + "\n  " +
      value(comboTiming) + " " + events + "\n  ON " + name(schemaName, value(comboTable)) + "\n  " +
      value(comboLevel) +
      (value(textWhen).length() > 0 ? "\n  WHEN (" + value(textWhen) + ")" : "") +
      "\n  EXECUTE " + (atLeast(11, 0) ? "FUNCTION " : "PROCEDURE ") + function + "(" + value(textArguments) + ")");
  }

}
