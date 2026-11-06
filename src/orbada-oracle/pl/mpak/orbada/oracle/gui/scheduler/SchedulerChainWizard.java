package pl.mpak.orbada.oracle.gui.scheduler;

import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import pl.mpak.usedb.core.Database;

/**
 * ORBADA Fuerteventura (F5): kreator chaina (CREATE_CHAIN, DEFINE_CHAIN_STEP, DEFINE_CHAIN_RULE) - kroki
 * (programy lub chainy) i reguly; reguly mozna ulozyc z list ("gdy krok X zakonczy sie sukcesem, uruchom Y")
 * albo jednym przyciskiem jako sekwencje krokow.
 */
class SchedulerChainWizard extends SchedulerWizard {

  private final static String[] outcomes = {"SUCCEEDED", "FAILED", "COMPLETED"};

  private final JTextField textName = new JTextField();
  private final JTextField textComments = new JTextField();
  private final JCheckBox checkEnabled = new JCheckBox(text("SchedWiz-enabled"), true);
  private final SchedulerTablePanel steps = new SchedulerTablePanel(
    new String[] {"SchedWiz-step-name", "SchedWiz-step-object"}, new boolean[] {true, true}, true);
  private final SchedulerTablePanel rules = new SchedulerTablePanel(
    new String[] {"SchedWiz-rule-condition", "SchedWiz-rule-action", "SchedWiz-comments"}, new boolean[] {true, true, true}, true);
  private final JComboBox<String> comboWhen = new JComboBox<String>();
  private final JComboBox<String> comboOutcome = new JComboBox<String>();
  private final JComboBox<String> comboThen = new JComboBox<String>();

  SchedulerChainWizard(Database database, String schema) {
    super(database, schema);
    addPage(new Page("SchedWiz-page-general", new Form()
        .row("SchedWiz-name", textName)
        .row("SchedWiz-comments", textComments)
        .full(checkEnabled, false)
        .hint("SchedWiz-chain-hint")
        .end()) {
      @Override
      String validate() {
        return checkName(textName.getText(), "SchedWiz-name");
      }
    });

    List<String> objects = new ArrayList<String>(objects("all_scheduler_programs", "program_name"));
    objects.addAll(objects("all_scheduler_chains", "chain_name"));
    JComboBox<String> objectEditor = new JComboBox<String>(objects.toArray(new String[objects.size()]));
    objectEditor.setEditable(true);
    objectEditor.setMaximumRowCount(20);
    steps.table.getColumnModel().getColumn(1).setCellEditor(new DefaultCellEditor(objectEditor));
    steps.table.getColumnModel().getColumn(0).setPreferredWidth(150);
    steps.table.getColumnModel().getColumn(1).setPreferredWidth(350);
    addPage(new Page("SchedWiz-page-steps", new Form().hint("SchedWiz-steps-hint").full(steps, true)) {
      @Override
      String validate() {
        return validateSteps();
      }
    });

    for (String o : outcomes) {
      comboOutcome.addItem(text("SchedWiz-rule-" + o.toLowerCase()));
    }
    comboWhen.addActionListener(e -> comboOutcome.setEnabled(comboWhen.getSelectedIndex() > 0));
    JPanel builder = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
    builder.add(new JLabel(text("SchedWiz-rule-when")));
    builder.add(comboWhen);
    builder.add(comboOutcome);
    builder.add(new JLabel(text("SchedWiz-rule-then")));
    builder.add(comboThen);
    JButton add = new JButton(text("SchedWiz-rule-add"));
    add.addActionListener(e -> addBuiltRule());
    builder.add(add);
    rules.addButton("SchedWiz-rule-sequence", this::sequenceRules);
    rules.table.getColumnModel().getColumn(0).setPreferredWidth(250);
    rules.table.getColumnModel().getColumn(1).setPreferredWidth(150);
    addPage(new Page("SchedWiz-page-rules", new Form().hint("SchedWiz-rules-hint").full(builder, false).full(rules, true)) {
      @Override
      void shown() {
        fillBuilder();
        if (rules.getRowCount() == 0) {
          sequenceRules();
        }
      }
      @Override
      String validate() {
        return validateRules();
      }
    });
  }

  private List<String> stepNames() {
    List<String> names = new ArrayList<String>();
    for (String[] row : steps.getRows()) {
      names.add(ident(row[0]));
    }
    return names;
  }

  private String validateSteps() {
    List<String[]> rows = steps.getRows();
    if (rows.isEmpty()) {
      return text("SchedWiz-err-no-steps");
    }
    Set<String> names = new HashSet<String>();
    for (String[] row : rows) {
      String msg = checkName(row[0], "SchedWiz-step-name");
      if (msg != null) {
        return msg;
      }
      if (!ident(row[0]).matches("[A-Z][A-Z0-9_$#]*")) {
        return text("SchedWiz-err-step-name", row[0]);
      }
      if (!names.add(ident(row[0]))) {
        return text("SchedWiz-err-step-duplicate", ident(row[0]));
      }
      if (row[1].length() == 0) {
        return text("SchedWiz-err-step-object", ident(row[0]));
      }
    }
    return null;
  }

  private void fillBuilder() {
    comboWhen.removeAllItems();
    comboThen.removeAllItems();
    comboWhen.addItem(text("SchedWiz-rule-start"));
    for (String s : stepNames()) {
      comboWhen.addItem(s);
      comboThen.addItem(text("SchedWiz-rule-start-step", s));
    }
    comboThen.addItem(text("SchedWiz-rule-end"));
    comboOutcome.setEnabled(false);
  }

  private void addBuiltRule() {
    List<String> names = stepNames();
    int w = comboWhen.getSelectedIndex();
    int t = comboThen.getSelectedIndex();
    if (w < 0 || t < 0) {
      return;
    }
    String condition = w == 0 ? "TRUE" : names.get(w - 1) + " " + outcomes[comboOutcome.getSelectedIndex()];
    String action = t < names.size() ? "START " + names.get(t) : "END";
    rules.addRow(new Object[] {condition, action, ""});
  }

  /** Reguly dla krokow po kolei: start pierwszego, kazdy nastepny po sukcesie poprzedniego, koniec po ostatnim lub bledzie */
  private void sequenceRules() {
    List<String> names = stepNames();
    if (names.isEmpty()) {
      return;
    }
    rules.clear();
    rules.addRow(new Object[] {"TRUE", "START " + names.get(0), ""});
    for (int i = 1; i < names.size(); i++) {
      rules.addRow(new Object[] {names.get(i - 1) + " SUCCEEDED", "START " + names.get(i), ""});
    }
    StringBuilder end = new StringBuilder(names.get(names.size() - 1) + " COMPLETED");
    for (int i = 0; i < names.size() - 1; i++) {
      end.append(" OR ").append(names.get(i)).append(" FAILED");
    }
    rules.addRow(new Object[] {end.toString(), "END", ""});
  }

  private String validateRules() {
    List<String[]> rows = rules.getRows();
    if (rows.isEmpty()) {
      return text("SchedWiz-err-no-rules");
    }
    for (int i = 0; i < rows.size(); i++) {
      if (rows.get(i)[0].length() == 0 || rows.get(i)[1].length() == 0) {
        return text("SchedWiz-err-rule", i + 1);
      }
    }
    return null;
  }

  @Override
  String getTitleKey() {
    return "SchedWiz-title-chain";
  }

  @Override
  String getTypedName() {
    return textName.getText();
  }

  @Override
  String buildPlsql() {
    String name = nameLit();
    StringBuilder sb = new StringBuilder("begin\n");
    // ostrzezenia jako komentarze w kodzie - chain bez reguly startowej lub END formalnie jest poprawny
    boolean start = false;
    boolean end = false;
    for (String[] r : rules.getRows()) {
      start |= r[0].equalsIgnoreCase("TRUE");
      end |= r[1].toUpperCase().startsWith("END");
    }
    if (!start) {
      sb.append("  -- ").append(text("SchedWiz-warn-no-start")).append("\n");
    }
    if (!end) {
      sb.append("  -- ").append(text("SchedWiz-warn-no-end")).append("\n");
    }
    sb.append(call("create_chain",
      "chain_name", name,
      "rule_set_name", "null",
      "evaluation_interval", "null",
      "comments", lit(fieldText(textComments))));
    for (String[] s : steps.getRows()) {
      sb.append(call("define_chain_step",
        "chain_name", name,
        "step_name", lit(ident(s[0])),
        "program_name", lit(reference(s[1]))));
    }
    for (String[] r : rules.getRows()) {
      sb.append(call("define_chain_rule",
        "chain_name", name,
        "condition", lit(r[0]),
        "action", lit(r[1]),
        "rule_name", "null",
        "comments", lit(r[2])));
    }
    if (checkEnabled.isSelected()) {
      sb.append("  dbms_scheduler.enable(").append(name).append(");\n");
    }
    return sb.append("end;").toString();
  }

}
