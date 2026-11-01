package pl.mpak.orbada.oracle.gui.scheduler;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.util.List;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import pl.mpak.orbada.gui.comps.OrbadaSyntaxTextArea;
import pl.mpak.usedb.core.Database;

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
  String getObjectName() {
    return ident(textName.getText());
  }

  @Override
  String buildPlsql() {
    String name = lit(qualified(textName.getText()));
    List<String[]> rows = type() == PLSQL_BLOCK ? java.util.Collections.<String[]>emptyList() : args.getRows();
    StringBuilder sb = new StringBuilder("begin\n");
    sb.append(call("create_program",
      "program_name", name,
      "program_type", lit(types[type()]),
      "program_action", lit(action()),
      "number_of_arguments", String.valueOf(rows.size()),
      "enabled", "false",
      "comments", lit(fieldText(textComments))));
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
    if (checkEnabled.isSelected()) {
      sb.append("  dbms_scheduler.enable(").append(name).append(");\n");
    }
    return sb.append("end;").toString();
  }

}
