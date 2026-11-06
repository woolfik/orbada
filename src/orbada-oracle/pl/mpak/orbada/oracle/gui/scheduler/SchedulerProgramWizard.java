package pl.mpak.orbada.oracle.gui.scheduler;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import pl.mpak.orbada.gui.comps.OrbadaSyntaxTextArea;
import pl.mpak.usedb.core.Database;
import pl.mpak.usedb.core.Query;
import pl.mpak.util.ExceptionUtil;
import pl.mpak.util.variant.Variant;

/**
 * ORBADA Fuerteventura (F5): kreator programu (CREATE_PROGRAM + DEFINE_PROGRAM_ARGUMENT) - blok PL/SQL,
 * procedura skladowana albo program zewnetrzny; argumenty mozna wczytac z deklaracji procedury.
 */
class SchedulerProgramWizard extends SchedulerWizard {

  private final static String[] types = {"PLSQL_BLOCK", "STORED_PROCEDURE", "EXECUTABLE"};
  private final static int PLSQL_BLOCK = 0;
  private final static int STORED_PROCEDURE = 1;

  private final JTextField textName = new JTextField();
  private final JTextField textComments = new JTextField();
  private final JCheckBox checkEnabled = new JCheckBox(text("SchedWiz-enabled"), true);
  private final JRadioButton[] radioType = new JRadioButton[types.length];
  private final CardLayout actionCards = new CardLayout();
  private final JPanel actionPanel = new JPanel(actionCards);
  private final OrbadaSyntaxTextArea textBlock = new OrbadaSyntaxTextArea();
  private final JComboBox<String> comboProcedure;
  private final JTextField textExecutable = new JTextField();
  private final SchedulerTablePanel args = new SchedulerTablePanel(
    new String[] {"SchedWiz-arg-name", "SchedWiz-arg-type", "SchedWiz-arg-default"}, new boolean[] {true, true, true}, true);
  /** Procedura, z ktorej ostatnio wczytano argumenty - wczytanie automatyczne tylko po zmianie */
  private String argsLoadedFrom;

  SchedulerProgramWizard(Database database, String schema) {
    super(database, schema);
    addPage(new Page("SchedWiz-page-general", new Form()
        .row("SchedWiz-name", textName)
        .row("SchedWiz-comments", textComments)
        .full(checkEnabled, false)
        .hint("SchedWiz-program-hint")
        .end()) {
      @Override
      String validate() {
        return checkName(textName.getText(), "SchedWiz-name");
      }
    });

    textBlock.setDatabase(database);
    textBlock.setText("begin\n  null;\nend;");
    comboProcedure = editableCombo(procedures());
    actionPanel.add(new Form().full(textBlock, true), types[0]);
    actionPanel.add(new Form().row("SchedWiz-procedure", comboProcedure).hint("SchedWiz-procedure-hint").end(), types[1]);
    actionPanel.add(new Form().row("SchedWiz-executable", textExecutable).hint("SchedWiz-executable-hint").end(), types[2]);
    JPanel typePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    ButtonGroup group = new ButtonGroup();
    for (int i = 0; i < types.length; i++) {
      final int type = i;
      radioType[i] = new JRadioButton(text("SchedWiz-type-" + types[i].toLowerCase()));
      radioType[i].addActionListener(e -> actionCards.show(actionPanel, types[type]));
      group.add(radioType[i]);
      typePanel.add(radioType[i]);
    }
    radioType[PLSQL_BLOCK].setSelected(true);
    JPanel action = new JPanel(new BorderLayout());
    action.add(typePanel, BorderLayout.NORTH);
    action.add(actionPanel, BorderLayout.CENTER);
    addPage(new Page("SchedWiz-page-action", action) {
      @Override
      String validate() {
        return validateAction();
      }
    });

    args.addButton("SchedWiz-args-load", () -> loadArguments(true));
    addPage(new Page("SchedWiz-page-arguments", new Form().hint("SchedWiz-program-args-hint").full(args, true)) {
      @Override
      String validate() {
        return validateArguments();
      }
      @Override
      void shown() {
        args.table.setEnabled(type() != PLSQL_BLOCK);
        if (type() == STORED_PROCEDURE && !comboText(comboProcedure).equalsIgnoreCase(String.valueOf(argsLoadedFrom))) {
          loadArguments(false);
        }
      }
    });
  }

  private int type() {
    for (int i = 0; i < types.length; i++) {
      if (radioType[i].isSelected()) {
        return i;
      }
    }
    return PLSQL_BLOCK;
  }

  private String action() {
    switch (type()) {
      case PLSQL_BLOCK:
        return textBlock.getText().trim();
      case STORED_PROCEDURE:
        return comboText(comboProcedure);
      default:
        return fieldText(textExecutable);
    }
  }

  private String validateAction() {
    if (action().length() == 0) {
      return text("SchedWiz-err-action");
    }
    if (type() == STORED_PROCEDURE) {
      try {
        if (procedureArguments(action()) == null) {
          return text("SchedWiz-err-procedure", action());
        }
      } catch (IllegalArgumentException ex) {
        return ex.getMessage();
      }
    }
    return null;
  }

  /** Argumenty z deklaracji procedury (nadpisuja tabele) */
  private void loadArguments(boolean explicit) {
    if (type() != STORED_PROCEDURE) {
      return;
    }
    String procedure = comboText(comboProcedure);
    argsLoadedFrom = procedure;
    List<String[]> list;
    try {
      list = procedureArguments(procedure);
    } catch (IllegalArgumentException ex) {
      list = null;
    }
    if (list == null) {
      return;
    }
    if (!explicit && list.isEmpty() && args.getRowCount() == 0) {
      return;
    }
    args.clear();
    for (String[] a : list) {
      args.addRow(new Object[] {a[0], a[1], ""});
    }
  }

  private String validateArguments() {
    if (type() == PLSQL_BLOCK) {
      return null;
    }
    List<String[]> rows = args.getRows();
    for (int i = 0; i < rows.size(); i++) {
      if (rows.get(i)[1].length() == 0) {
        return text("SchedWiz-err-arg-type", i + 1);
      }
    }
    return null;
  }

  @Override
  String getTitleKey() {
    return "SchedWiz-title-program";
  }

  @Override
  String getTypedName() {
    return textName.getText();
  }

  // --- edycja

  /** DEFAULT_VALUE w ALL_SCHEDULER_PROGRAM_ARGS dla argumentu bez wartosci domyslnej */
  private final static String NO_VALUE = "<NO VALUE>";
  private Map<String, String> loadedAttributes;
  /** Argumenty wczytane: wiersze "nazwa|typ|domyslna" */
  private List<String> loadedArguments;
  private boolean loadedEnabled;

  @Override
  void load(String name) {
    Map<String, String> r = loadRow("select program_type, program_action, enabled, comments\n"
      + "  from all_scheduler_programs where owner = :OWNER and program_name = :NAME", name);
    int type = java.util.Arrays.asList(types).indexOf(r.get("PROGRAM_TYPE"));
    if (type < 0) {
      throw new IllegalArgumentException(text("SchedWiz-err-edit-type", nvl(r.get("PROGRAM_TYPE"))));
    }
    textName.setText(name);
    textName.setEditable(false);
    textComments.setText(nvl(r.get("COMMENTS")));
    checkEnabled.setSelected("TRUE".equals(r.get("ENABLED")));
    radioType[type].setSelected(true);
    actionCards.show(actionPanel, types[type]);
    String action = nvl(r.get("PROGRAM_ACTION"));
    if (type == PLSQL_BLOCK) {
      textBlock.setText(action);
    } else if (type == STORED_PROCEDURE) {
      comboProcedure.setSelectedItem(action);
      // argumenty programu ze slownika, nie z deklaracji procedury
      argsLoadedFrom = action;
    } else {
      textExecutable.setText(action);
    }
    Query q = database.createQuery();
    try {
      q.setSqlText("select argument_name, argument_type, default_value from all_scheduler_program_args\n"
        + " where owner = :OWNER and program_name = :NAME order by argument_position");
      q.paramByName("OWNER").setString(schema);
      q.paramByName("NAME").setString(name);
      q.open();
      while (!q.eof()) {
        Variant def = q.fieldByName("DEFAULT_VALUE").getValue();
        String d = def.isNullValue() ? "NULL" : NO_VALUE.equals(def.getString()) ? "" : def.getString();
        args.addRow(new Object[] {nvl(q.fieldByName("ARGUMENT_NAME").getValue().getString()),
          nvl(q.fieldByName("ARGUMENT_TYPE").getValue().getString()), d});
        q.next();
      }
    } catch (Exception ex) {
      ExceptionUtil.processException(ex);
    } finally {
      q.close();
    }
    loadedAttributes = attributes();
    loadedArguments = argumentRows();
    loadedEnabled = checkEnabled.isSelected();
  }

  private List<String[]> rows() {
    return type() == PLSQL_BLOCK ? java.util.Collections.<String[]>emptyList() : args.getRows();
  }

  private Map<String, String> attributes() {
    Map<String, String> m = new java.util.LinkedHashMap<String, String>();
    m.put("program_type", lit(types[type()]));
    m.put("program_action", lit(action()));
    m.put("number_of_arguments", String.valueOf(rows().size()));
    m.put("comments", lit(fieldText(textComments)));
    return m;
  }

  private List<String> argumentRows() {
    List<String> result = new ArrayList<String>();
    for (String[] a : rows()) {
      result.add(ident(a[0]) + "|" + a[1].toUpperCase() + "|" + a[2]);
    }
    return result;
  }

  private String defineArguments(String name, List<String[]> rows) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < rows.size(); i++) {
      String[] a = rows.get(i);
      sb.append(call("define_program_argument",
        "program_name", name,
        "argument_position", String.valueOf(i + 1),
        "argument_name", a[0].length() == 0 ? null : lit(ident(a[0])),
        "argument_type", lit(a[1].toUpperCase()),
        // bez default_value - inna wersja procedury (argument obowiazkowy); NULL - wartosc domyslna NULL
        "default_value", a[2].length() == 0 ? null : a[2].equalsIgnoreCase("NULL") ? "null" : lit(a[2])));
    }
    return sb.toString();
  }

  /** Zmienione argumenty: stare usuwane (od konca), atrybuty, nowe definiowane od poczatku */
  private String buildEdit() {
    String name = nameLit();
    StringBuilder changes = new StringBuilder();
    boolean argsChanged = !argumentRows().equals(loadedArguments);
    if (argsChanged) {
      for (int i = loadedArguments.size(); i >= 1; i--) {
        changes.append("  dbms_scheduler.drop_program_argument(").append(name).append(", ").append(i).append(");\n");
      }
    }
    changes.append(changedAttributes(name, loadedAttributes, attributes()));
    if (argsChanged) {
      changes.append(defineArguments(name, rows()));
    }
    return editBlock(name, changes.toString(), loadedEnabled, checkEnabled.isSelected(), "");
  }

  @Override
  String buildPlsql() {
    if (isEdit()) {
      return buildEdit();
    }
    String name = nameLit();
    List<String[]> rows = rows();
    StringBuilder sb = new StringBuilder("begin\n");
    sb.append(call("create_program",
      "program_name", name,
      "program_type", lit(types[type()]),
      "program_action", lit(action()),
      "number_of_arguments", String.valueOf(rows.size()),
      "enabled", "false",
      "comments", lit(fieldText(textComments))));
    sb.append(defineArguments(name, rows));
    if (checkEnabled.isSelected()) {
      sb.append("  dbms_scheduler.enable(").append(name).append(");\n");
    }
    return sb.append("end;").toString();
  }

}
