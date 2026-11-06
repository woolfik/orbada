package pl.mpak.orbada.postgresql.gui.wizards;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.table.DefaultTableModel;
import pl.mpak.orbada.postgresql.services.PostgreSQLDbInfoProvider;
import pl.mpak.sky.gui.swing.comp.TextField;
import pl.mpak.usedb.core.Database;

/**
 * ORBADA 1.3.2: funkcja, procedura (PostgreSQL 11+) albo funkcja wyzwalacza - argumenty (tryb, typ, DEFAULT), typ
 * wyniku, jezyk, zmiennosc, STRICT, SECURITY DEFINER, komentarz i tresc - odpowiednik CreateFunctionWizard,
 * CreateProcedureWizard i FunctionParameterDefinitionWizard Oracle.
 */
public class CreateRoutineWizard extends PgWizard {

  public static final int FUNCTION = 0;
  public static final int PROCEDURE = 1;
  public static final int TRIGGER = 2;

  private static final String[] TEMPLATES = {
    "BEGIN\n  RETURN NULL;\nEND;", "BEGIN\n  NULL;\nEND;", "BEGIN\n  -- NEW, OLD, TG_OP\n  RETURN NEW;\nEND;", "SELECT NULL"};

  private final int kind;
  private final TextField textName;
  private final JCheckBox checkReplace;
  private final DefaultTableModel arguments;
  private final JTable tableArguments;
  private final JComboBox<String> comboReturns;
  private final JComboBox<String> comboLanguage;
  private final JComboBox<String> comboVolatility;
  private final JCheckBox checkStrict;
  private final JCheckBox checkSecurityDefiner;
  private final TextField textComment;
  private final JTextArea textBody;

  public CreateRoutineWizard(Database database, String schemaName, int kind) {
    super(database, schemaName);
    this.kind = kind;
    this.titleKey = kind == PROCEDURE ? "CreateProcedureWizard-title" : kind == TRIGGER ? "CreateTriggerFunctionWizard-title" : "CreateFunctionWizard-title";
    info("schema-name", this.schemaName);
    textName = text(kind == PROCEDURE ? "procedure-name" : "function-name", "");
    checkReplace = check("or-replace");
    checkReplace.setSelected(true);
    String[] types = PostgreSQLDbInfoProvider.instance.getDataTypes(database);
    if (types == null) {
      types = new String[0];
    }
    if (kind != TRIGGER) {
      arguments = new DefaultTableModel(new Object[] {
        stringManager.getString("argument-name"), stringManager.getString("argument-mode"),
        stringManager.getString("data-type"), stringManager.getString("default-value")}, 0);
      tableArguments = new JTable(arguments);
      tableArguments.setRowHeight(Math.max(tableArguments.getRowHeight(), new JComboBox<String>().getPreferredSize().height));
      JComboBox<String> comboMode = new JComboBox<String>(kind == PROCEDURE && !atLeast(14, 0)
        ? new String[] {"IN", "INOUT", "VARIADIC"} : new String[] {"IN", "OUT", "INOUT", "VARIADIC"});
      tableArguments.getColumnModel().getColumn(1).setCellEditor(new DefaultCellEditor(comboMode));
      JComboBox<String> comboType = new JComboBox<String>(types);
      comboType.setEditable(true);
      tableArguments.getColumnModel().getColumn(2).setCellEditor(new DefaultCellEditor(comboType));
      JPanel panel = new JPanel(new BorderLayout(0, 4));
      JScrollPane scroll = new JScrollPane(tableArguments);
      scroll.setPreferredSize(new Dimension(500, 90));
      panel.add(scroll, BorderLayout.CENTER);
      JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
      JButton add = new JButton(stringManager.getString("argument-add"));
      add.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          stopEditing();
          arguments.addRow(new Object[] {"p" + (arguments.getRowCount() + 1), "IN", "integer", ""});
        }
      });
      JButton remove = new JButton(stringManager.getString("argument-remove"));
      remove.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          stopEditing();
          if (tableArguments.getSelectedRow() >= 0) {
            arguments.removeRow(tableArguments.getSelectedRow());
          }
        }
      });
      buttons.add(add);
      buttons.add(remove);
      panel.add(buttons, BorderLayout.SOUTH);
      row("arguments", panel);
    } else {
      arguments = null;
      tableArguments = null;
    }
    if (kind == FUNCTION) {
      comboReturns = combo("returns-type", true, types);
      comboReturns.insertItemAt("void", 0);
      comboReturns.insertItemAt("SETOF record", 1);
      comboReturns.insertItemAt("TABLE (id integer)", 2);
      comboReturns.setSelectedItem("integer");
    } else {
      comboReturns = null;
    }
    comboLanguage = combo("language", true, list("select lanname from pg_catalog.pg_language where lanname not in ('internal', 'c') order by lanname <> 'plpgsql', 1"));
    comboLanguage.setSelectedItem("plpgsql");
    comboVolatility = kind == FUNCTION ? combo("volatility", false, "VOLATILE", "STABLE", "IMMUTABLE") : null;
    checkStrict = kind == FUNCTION ? check("strict") : null;
    checkSecurityDefiner = check("security-definer");
    textComment = text("comment", "");
    textBody = area("routine-body", TEMPLATES[kind]);
    if (comboReturns != null) {
      comboReturns.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          // RETURNS void w PL/pgSQL - szablon bez RETURN NULL (tylko gdy tresc nie byla zmieniana)
          String body = textBody.getText();
          boolean isVoid = "void".equalsIgnoreCase(value(comboReturns));
          if (isVoid && TEMPLATES[0].equals(body)) {
            textBody.setText(TEMPLATES[1]);
          } else if (!isVoid && TEMPLATES[1].equals(body)) {
            textBody.setText(TEMPLATES[0]);
          }
        }
      });
    }
    comboLanguage.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        // szablon tresci pasujacy do jezyka - tylko gdy tresc nie byla zmieniana
        for (String t : TEMPLATES) {
          if (t.equals(textBody.getText())) {
            textBody.setText("sql".equals(value(comboLanguage)) && CreateRoutineWizard.this.kind != TRIGGER
              ? (CreateRoutineWizard.this.kind == PROCEDURE ? "SELECT 1" : TEMPLATES[3]) : TEMPLATES[CreateRoutineWizard.this.kind]);
            break;
          }
        }
      }
    });
  }

  private void stopEditing() {
    if (tableArguments != null && tableArguments.isEditing()) {
      tableArguments.getCellEditor().stopCellEditing();
    }
  }

  public void setRoutine(String name, String returns, String language, String body) {
    textName.setText(name);
    if (comboReturns != null) {
      comboReturns.setSelectedItem(returns);
    }
    comboLanguage.setSelectedItem(language);
    textBody.setText(body);
  }

  public void addArgument(String name, String mode, String type, String defaultValue) {
    arguments.addRow(new Object[] {name, mode, type, defaultValue});
  }

  public void setOptions(String volatility, boolean strict, boolean securityDefiner, String comment) {
    if (comboVolatility != null) {
      comboVolatility.setSelectedItem(volatility);
      checkStrict.setSelected(strict);
    }
    checkSecurityDefiner.setSelected(securityDefiner);
    textComment.setText(comment);
  }

  private static String cell(DefaultTableModel model, int row, int column) {
    Object o = model.getValueAt(row, column);
    return o == null ? "" : o.toString().trim();
  }

  /** Argumenty: withDefaults=false - sygnatura (COMMENT ON) */
  private String argumentList(boolean withDefaults) {
    if (arguments == null) {
      return "";
    }
    stopEditing();
    StringBuilder sb = new StringBuilder();
    for (int r = 0; r < arguments.getRowCount(); r++) {
      String type = cell(arguments, r, 2);
      if (type.length() == 0) {
        continue;
      }
      String mode = cell(arguments, r, 1);
      String name = cell(arguments, r, 0);
      sb.append(sb.length() > 0 ? ", " : "")
        .append("IN".equals(mode) || mode.length() == 0 ? "" : mode + " ")
        .append(name.length() > 0 ? ident(name) + " " : "")
        .append(type);
      if (withDefaults && cell(arguments, r, 3).length() > 0) {
        sb.append(" DEFAULT ").append(cell(arguments, r, 3));
      }
    }
    return sb.toString();
  }

  /** Tresc w $body$ ... $body$ (inny znacznik, gdy tresc go zawiera) */
  public static String dollarQuote(String body) {
    String tag = "$body$";
    for (int i = 1; body.contains(tag); i++) {
      tag = "$body" + i + "$";
    }
    return tag + "\n" + body + "\n" + tag;
  }

  @Override
  protected void build(List<String> sql) {
    require(value(textName).length() > 0, "wizard-name-required");
    result("object_name", value(textName));
    String routine = name(schemaName, value(textName));
    String keyword = kind == PROCEDURE ? "PROCEDURE" : "FUNCTION";
    StringBuilder sb = new StringBuilder("CREATE ");
    if (checkReplace.isSelected()) {
      sb.append("OR REPLACE ");
    }
    sb.append(keyword).append(" ").append(routine).append("(").append(argumentList(true)).append(")");
    if (kind == FUNCTION) {
      sb.append("\n  RETURNS ").append(value(comboReturns));
    } else if (kind == TRIGGER) {
      sb.append("\n  RETURNS trigger");
    }
    sb.append("\n  LANGUAGE ").append(value(comboLanguage));
    if (kind == FUNCTION) {
      if (comboVolatility.getSelectedIndex() > 0) {
        sb.append("\n  ").append(value(comboVolatility));
      }
      if (checkStrict.isSelected()) {
        sb.append("\n  STRICT");
      }
    }
    if (checkSecurityDefiner.isSelected()) {
      sb.append("\n  SECURITY DEFINER");
    }
    sb.append("\nAS ").append(dollarQuote(textBody.getText()));
    sql.add(sb.toString());
    if (value(textComment).length() > 0) {
      sql.add("COMMENT ON " + keyword + " " + routine + "(" + argumentList(false) + ") IS " + literal(value(textComment)));
    }
  }

}
