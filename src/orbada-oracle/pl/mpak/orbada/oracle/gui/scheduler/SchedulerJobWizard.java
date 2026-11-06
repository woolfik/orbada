package pl.mpak.orbada.oracle.gui.scheduler;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import pl.mpak.orbada.gui.comps.OrbadaSyntaxTextArea;
import pl.mpak.usedb.core.Database;
import pl.mpak.usedb.core.Query;
import pl.mpak.util.ExceptionUtil;
import pl.mpak.util.variant.Variant;

/**
 * ORBADA Fuerteventura (F5): kreator joba (CREATE_JOB) - co uruchamiac (blok PL/SQL, procedura, program,
 * chain, program zewnetrzny), wartosci argumentow, kiedy (raz, o dacie, cyklicznie z podgladem, wedlug
 * harmonogramu, w oknie) i opcje (limity, priorytet, logowanie, uruchomienie od razu).
 */
class SchedulerJobWizard extends SchedulerWizard {

  private final static String[] actions = {"PLSQL_BLOCK", "STORED_PROCEDURE", "PROGRAM", "CHAIN", "EXECUTABLE"};
  private final static int A_PLSQL_BLOCK = 0;
  private final static int A_STORED_PROCEDURE = 1;
  private final static int A_PROGRAM = 2;
  private final static int A_CHAIN = 3;
  private final static int A_EXECUTABLE = 4;
  private final static String[] whens = {"immediate", "once", "calendar", "schedule", "window"};
  private final static int W_IMMEDIATE = 0;
  private final static int W_ONCE = 1;
  private final static int W_CALENDAR = 2;
  private final static int W_SCHEDULE = 3;
  private final static int W_WINDOW = 4;
  /** DEFAULT_VALUE w ALL_SCHEDULER_PROGRAM_ARGS dla argumentu bez wartosci domyslnej */
  private final static String NO_VALUE = "<NO VALUE>";
  private final static String[] loggingLevels = {null, "logging_off", "logging_failed_runs", "logging_runs", "logging_full"};

  // ogolne
  private final JTextField textName = new JTextField();
  private final JTextField textComments = new JTextField();
  private final JComboBox<String> comboClass;
  private final JCheckBox checkEnabled = new JCheckBox(text("SchedWiz-enabled"), true);
  private final JCheckBox checkAutoDrop = new JCheckBox(text("SchedWiz-auto-drop"), false);
  // co uruchamiac
  private final JRadioButton[] radioAction = new JRadioButton[actions.length];
  private final CardLayout actionCards = new CardLayout();
  private final JPanel actionPanel = new JPanel(actionCards);
  private final OrbadaSyntaxTextArea textBlock = new OrbadaSyntaxTextArea();
  private final JComboBox<String> comboProcedure;
  private final JComboBox<String> comboProgram;
  private final JComboBox<String> comboChain;
  private final JTextField textExecutable = new JTextField();
  private final JComboBox<String> comboCredential;
  // argumenty: nazwa, typ, domyslna, wartosc
  private final SchedulerTablePanel args = new SchedulerTablePanel(
    new String[] {"SchedWiz-arg-name", "SchedWiz-arg-type", "SchedWiz-arg-default", "SchedWiz-arg-value"},
    new boolean[] {false, false, false, true}, false);
  /** Wiersze argumentow: "Y" - ma wartosc domyslna (mozna pominac) */
  private final List<Boolean> argDefaulted = new ArrayList<Boolean>();
  /** Zrodlo wczytanych argumentow (rodzaj + nazwa) - wczytanie ponowne tylko po zmianie */
  private String argsSource;
  // kiedy
  private final JRadioButton[] radioWhen = new JRadioButton[whens.length];
  private final CardLayout whenCards = new CardLayout();
  private final JPanel whenPanel = new JPanel(whenCards);
  private final JTextField textOnce = new JTextField();
  private final SchedulerCalendarPanel calendar;
  private final JComboBox<String> comboSchedule;
  private final JComboBox<String> comboWindow;
  // opcje
  private final JSpinner spinMaxFailures = new JSpinner(new SpinnerNumberModel(0, 0, 1000000, 1));
  private final JSpinner spinMaxRuns = new JSpinner(new SpinnerNumberModel(0, 0, 1000000, 1));
  private final JSpinner spinPriority = new JSpinner(new SpinnerNumberModel(3, 1, 5, 1));
  private final JCheckBox checkRestartable = new JCheckBox(text("SchedWiz-restartable"), false);
  private final JComboBox<String> comboLogging = new JComboBox<String>();
  private final JCheckBox checkRunNow = new JCheckBox(text("SchedWiz-run-now"), false);

  SchedulerJobWizard(Database database, String schema) {
    super(database, schema);
    List<String> classes = list("select job_class_name from all_scheduler_job_classes\n"
      + " order by case when job_class_name = 'DEFAULT_JOB_CLASS' then 0 else 1 end, job_class_name");
    comboClass = editableCombo(classes);
    comboClass.setSelectedItem(classes.contains("DEFAULT_JOB_CLASS") ? "DEFAULT_JOB_CLASS" : "");
    addPage(new Page("SchedWiz-page-general", new Form()
        .row("SchedWiz-name", textName)
        .row("SchedWiz-comments", textComments)
        .row("SchedWiz-job-class", comboClass)
        .full(checkEnabled, false)
        .full(checkAutoDrop, false)
        .hint("SchedWiz-job-hint")
        .end()) {
      @Override
      String validate() {
        return checkName(textName.getText(), "SchedWiz-name");
      }
    });

    textBlock.setDatabase(database);
    textBlock.setText("begin\n  null;\nend;");
    comboProcedure = editableCombo(procedures());
    comboProgram = editableCombo(objects("all_scheduler_programs", "program_name"));
    comboChain = editableCombo(objects("all_scheduler_chains", "chain_name"));
    comboCredential = editableCombo(objects("all_credentials", "credential_name"));
    actionPanel.add(new Form().full(textBlock, true), actions[A_PLSQL_BLOCK]);
    actionPanel.add(new Form().row("SchedWiz-procedure", comboProcedure).hint("SchedWiz-procedure-hint").end(), actions[A_STORED_PROCEDURE]);
    actionPanel.add(new Form().row("SchedWiz-program", comboProgram).hint("SchedWiz-job-program-hint").end(), actions[A_PROGRAM]);
    actionPanel.add(new Form().row("SchedWiz-chain", comboChain).hint("SchedWiz-job-chain-hint").end(), actions[A_CHAIN]);
    actionPanel.add(new Form()
      .row("SchedWiz-executable", textExecutable)
      .row("SchedWiz-credential", comboCredential)
      .hint("SchedWiz-executable-hint").end(), actions[A_EXECUTABLE]);
    addPage(new Page("SchedWiz-page-action", radioPanel(radioAction, actions, "SchedWiz-type-", actionCards, actionPanel)) {
      @Override
      String validate() {
        return validateAction();
      }
    });

    // wiersze dodaje sie tylko dla programu zewnetrznego - procedura i program maja argumenty z deklaracji
    JButton buttonAdd = args.addButton("SchedWiz-row-add", () -> {
      args.addRow(new Object[] {"", "VARCHAR2", "", ""});
      argDefaulted.add(Boolean.FALSE);
    });
    JButton buttonRemove = args.addButton("SchedWiz-row-remove", () -> {
      int r = args.table.getSelectedRow();
      if (r >= 0) {
        args.stopEditing();
        ((javax.swing.table.DefaultTableModel)args.table.getModel()).removeRow(r);
        argDefaulted.remove(r);
      }
    });
    addPage(new Page("SchedWiz-page-arguments", new Form().hint("SchedWiz-job-args-hint").full(args, true)) {
      @Override
      String validate() {
        return validateArguments();
      }
      @Override
      void shown() {
        loadArguments();
        buttonAdd.setEnabled(action() == A_EXECUTABLE);
        buttonRemove.setEnabled(action() == A_EXECUTABLE);
      }
    });

    comboSchedule = editableCombo(objects("all_scheduler_schedules", "schedule_name"));
    comboWindow = editableCombo(list("select owner || '.' || window_name from all_scheduler_windows order by window_name"));
    calendar = new SchedulerCalendarPanel(database);
    whenPanel.add(new Form().hint("SchedWiz-when-immediate-hint").end(), whens[W_IMMEDIATE]);
    whenPanel.add(new Form().row("SchedWiz-when-date", textOnce).hint("SchedWiz-when-once-hint").end(), whens[W_ONCE]);
    whenPanel.add(calendar, whens[W_CALENDAR]);
    whenPanel.add(new Form().row("SchedWiz-schedule", comboSchedule).hint("SchedWiz-when-schedule-hint").end(), whens[W_SCHEDULE]);
    whenPanel.add(new Form().row("SchedWiz-window", comboWindow).hint("SchedWiz-when-window-hint").end(), whens[W_WINDOW]);
    addPage(new Page("SchedWiz-page-when", radioPanel(radioWhen, whens, "SchedWiz-when-", whenCards, whenPanel)) {
      @Override
      String validate() {
        return validateWhen();
      }
      @Override
      void shown() {
        calendar.shown();
      }
    });
    radioWhen[W_CALENDAR].setSelected(true);
    whenCards.show(whenPanel, whens[W_CALENDAR]);
    for (JRadioButton r : radioWhen) {
      r.addActionListener(e -> calendar.shown());
    }

    for (int i = 0; i < loggingLevels.length; i++) {
      comboLogging.addItem(text("SchedWiz-logging-" + (loggingLevels[i] == null ? "default" : loggingLevels[i].substring(8))));
    }
    addPage(new Page("SchedWiz-page-options", new Form()
        .row("SchedWiz-max-failures", spinMaxFailures)
        .row("SchedWiz-max-runs", spinMaxRuns)
        .row("SchedWiz-priority", spinPriority)
        .row("SchedWiz-logging", comboLogging)
        .full(checkRestartable, false)
        .full(checkRunNow, false)
        .hint("SchedWiz-options-hint")
        .end()) {
      @Override
      void shown() {
        // job bez harmonogramu i tak uruchamia sie od razu po wlaczeniu
        checkRunNow.setEnabled(checkEnabled.isSelected() && when() != W_IMMEDIATE);
      }
    });
  }

  /** Radio na gorze, pod nimi karta z polami wybranej opcji */
  private static JPanel radioPanel(JRadioButton[] radios, String[] ids, String keyPrefix, CardLayout cards, JPanel cardPanel) {
    JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    ButtonGroup group = new ButtonGroup();
    for (int i = 0; i < ids.length; i++) {
      final String id = ids[i];
      radios[i] = new JRadioButton(text(keyPrefix + id.toLowerCase()));
      radios[i].addActionListener(e -> cards.show(cardPanel, id));
      group.add(radios[i]);
      top.add(radios[i]);
    }
    radios[0].setSelected(true);
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(top, BorderLayout.NORTH);
    panel.add(cardPanel, BorderLayout.CENTER);
    return panel;
  }

  private static int selected(JRadioButton[] radios) {
    for (int i = 0; i < radios.length; i++) {
      if (radios[i].isSelected()) {
        return i;
      }
    }
    return 0;
  }

  private int action() {
    return selected(radioAction);
  }

  private int when() {
    return selected(radioWhen);
  }

  /** Nazwa wybrana dla akcji (procedura, program, chain, plik) albo tresc bloku */
  private String actionText() {
    switch (action()) {
      case A_PLSQL_BLOCK:
        return textBlock.getText().trim();
      case A_STORED_PROCEDURE:
        return comboText(comboProcedure);
      case A_PROGRAM:
        return comboText(comboProgram);
      case A_CHAIN:
        return comboText(comboChain);
      default:
        return fieldText(textExecutable);
    }
  }

  private String validateAction() {
    if (actionText().length() == 0) {
      return text("SchedWiz-err-action");
    }
    if (action() == A_STORED_PROCEDURE) {
      try {
        if (procedureArguments(actionText()) == null) {
          return text("SchedWiz-err-procedure", actionText());
        }
      } catch (IllegalArgumentException ex) {
        return ex.getMessage();
      }
    }
    return null;
  }

  /** Argumenty procedury albo programu; dla programu zewnetrznego wiersze dodaje uzytkownik */
  private void loadArguments() {
    String source = action() + ":" + actionText().toUpperCase();
    if (source.equals(argsSource)) {
      return;
    }
    argsSource = source;
    args.clear();
    argDefaulted.clear();
    if (action() == A_STORED_PROCEDURE) {
      List<String[]> list = procedureArguments(actionText());
      if (list != null) {
        for (String[] a : list) {
          boolean defaulted = "Y".equals(a[2]);
          args.addRow(new Object[] {a[0], a[1], defaulted ? text("SchedWiz-arg-defaulted") : "", ""});
          argDefaulted.add(defaulted);
        }
      }
    } else if (action() == A_PROGRAM) {
      String program = reference(actionText());
      int dot = program.lastIndexOf('.');
      Query q = database.createQuery();
      try {
        q.setSqlText("select argument_position, argument_name, argument_type, default_value\n"
          + "  from all_scheduler_program_args where owner = :OWNER and program_name = :NAME order by argument_position");
        q.paramByName("OWNER").setString(unquote(program.substring(0, dot)));
        q.paramByName("NAME").setString(unquote(program.substring(dot + 1)));
        q.open();
        while (!q.eof()) {
          // argument bez wartosci domyslnej ma w slowniku tekst '<NO VALUE>' (sprawdzone na 26ai), a nie NULL;
          // NULL to domyslna wartosc NULL - argument mozna pominac
          Variant v = q.fieldByName("DEFAULT_VALUE").getValue();
          String def = v.isNullValue() ? null : v.getString();
          boolean defaulted = !NO_VALUE.equals(def);
          args.addRow(new Object[] {q.fieldByName("ARGUMENT_NAME").getValue().getString(),
            q.fieldByName("ARGUMENT_TYPE").getValue().getString(), defaulted ? (def == null ? "NULL" : def) : "", ""});
          argDefaulted.add(defaulted);
          q.next();
        }
      } catch (Exception ex) {
        ExceptionUtil.processException(ex);
      } finally {
        q.close();
      }
    }
  }

  private static String unquote(String name) {
    return name.startsWith("\"") && name.endsWith("\"") && name.length() > 1 ? name.substring(1, name.length() - 1) : name;
  }

  /** Ile argumentow przekazac: do ostatniego z wartoscia albo bez wartosci domyslnej (pozycyjnie, bez dziur) */
  private int argumentCount() {
    int n = 0;
    for (int r = 0; r < args.getRowCount(); r++) {
      if (args.get(r, 3).length() > 0 || !argDefaulted.get(r)) {
        n = r + 1;
      }
    }
    return n;
  }

  private String validateArguments() {
    args.stopEditing();
    if (action() == A_STORED_PROCEDURE) {
      // procedura dostaje argumenty pozycyjnie - nie mozna pominac argumentu w srodku
      for (int r = 0; r < argumentCount(); r++) {
        if (args.get(r, 3).length() == 0) {
          return text("SchedWiz-err-arg-value", args.get(r, 0));
        }
      }
    } else if (action() == A_PROGRAM) {
      for (int r = 0; r < args.getRowCount(); r++) {
        if (args.get(r, 3).length() == 0 && !argDefaulted.get(r)) {
          return text("SchedWiz-err-arg-value", args.get(r, 0));
        }
      }
    } else if (action() == A_EXECUTABLE) {
      // pusty wiersz to zwykle pomylka - pusty argument wiersza polecen trzeba usunac
      for (int r = 0; r < args.getRowCount(); r++) {
        if (args.get(r, 3).length() == 0) {
          return text("SchedWiz-err-arg-value", String.valueOf(r + 1));
        }
      }
    }
    return null;
  }

  private String validateWhen() {
    switch (when()) {
      case W_ONCE:
        if (fieldText(textOnce).length() == 0) {
          return text("SchedWiz-err-required", text("SchedWiz-when-date"));
        }
        return SchedulerCalendarPanel.checkDate(textOnce, "SchedWiz-when-date");
      case W_CALENDAR:
        return calendar.validateInput();
      case W_SCHEDULE:
        return comboText(comboSchedule).length() == 0 ? text("SchedWiz-err-required", text("SchedWiz-schedule")) : null;
      case W_WINDOW:
        return comboText(comboWindow).length() == 0 ? text("SchedWiz-err-required", text("SchedWiz-window")) : null;
      default:
        return null;
    }
  }

  @Override
  String getTitleKey() {
    return "SchedWiz-title-job";
  }

  @Override
  String getTypedName() {
    return textName.getText();
  }

  // --- edycja

  /** Stan wczytany ze slownika: atrybuty, wartosci argumentow (z rodzajem akcji) i wlaczenie */
  private Map<String, String> loadedAttributes;
  private List<String> loadedArguments;
  private boolean loadedEnabled;

  @Override
  void load(String name) {
    Map<String, String> r = loadRow("select job_type, job_action, program_owner, program_name, schedule_type,\n"
      + "       schedule_owner, schedule_name, " + dateColumn("start_date") + ", repeat_interval, " + dateColumn("end_date") + ",\n"
      + "       job_class, enabled, auto_drop, comments, credential_owner, credential_name, max_failures, max_runs,\n"
      + "       job_priority, restartable, logging_level\n"
      + "  from all_scheduler_jobs where owner = :OWNER and job_name = :NAME", name);
    textName.setText(name);
    textName.setEditable(false);
    textComments.setText(nvl(r.get("COMMENTS")));
    comboClass.setSelectedItem(nvl(r.get("JOB_CLASS")));
    checkEnabled.setSelected("TRUE".equals(r.get("ENABLED")));
    checkAutoDrop.setSelected("TRUE".equals(r.get("AUTO_DROP")));

    int action;
    if (r.get("PROGRAM_NAME") != null) {
      action = A_PROGRAM;
      comboProgram.setSelectedItem(objectRef(r.get("PROGRAM_OWNER"), r.get("PROGRAM_NAME")));
    } else {
      action = java.util.Arrays.asList(actions).indexOf(r.get("JOB_TYPE"));
      if (action < 0 || action == A_PROGRAM) {
        throw new IllegalArgumentException(text("SchedWiz-err-edit-type", nvl(r.get("JOB_TYPE"))));
      }
      String jobAction = nvl(r.get("JOB_ACTION"));
      if (action == A_PLSQL_BLOCK) {
        textBlock.setText(jobAction);
      } else if (action == A_STORED_PROCEDURE) {
        comboProcedure.setSelectedItem(jobAction);
      } else if (action == A_CHAIN) {
        comboChain.setSelectedItem(jobAction);
      } else {
        textExecutable.setText(jobAction);
        comboCredential.setSelectedItem(objectRef(r.get("CREDENTIAL_OWNER"), r.get("CREDENTIAL_NAME")));
      }
    }
    radioAction[action].setSelected(true);
    actionCards.show(actionPanel, actions[action]);

    String scheduleType = nvl(r.get("SCHEDULE_TYPE"));
    int when;
    if ("NAMED".equals(scheduleType)) {
      when = W_SCHEDULE;
      comboSchedule.setSelectedItem(objectRef(r.get("SCHEDULE_OWNER"), r.get("SCHEDULE_NAME")));
    } else if ("WINDOW".equals(scheduleType) || "WINDOW_GROUP".equals(scheduleType)) {
      when = W_WINDOW;
      comboWindow.setSelectedItem(objectRef(r.get("SCHEDULE_OWNER"), r.get("SCHEDULE_NAME")));
    } else if ("CALENDAR".equals(scheduleType) || "PLSQL".equals(scheduleType)) {
      when = W_CALENDAR;
      calendar.setValues(r.get("REPEAT_INTERVAL"), r.get("START_DATE"), r.get("END_DATE"));
    } else if ("ONCE".equals(scheduleType)) {
      when = W_ONCE;
      textOnce.setText(nvl(r.get("START_DATE")));
    } else if ("IMMEDIATE".equals(scheduleType)) {
      when = W_IMMEDIATE;
    } else {
      throw new IllegalArgumentException(text("SchedWiz-err-edit-schedule", scheduleType));
    }
    radioWhen[when].setSelected(true);
    whenCards.show(whenPanel, whens[when]);

    spinMaxFailures.setValue(number(r.get("MAX_FAILURES"), 0));
    spinMaxRuns.setValue(number(r.get("MAX_RUNS"), 0));
    spinPriority.setValue(Math.max(1, Math.min(5, number(r.get("JOB_PRIORITY"), 3))));
    checkRestartable.setSelected("TRUE".equals(r.get("RESTARTABLE")));
    // LOGGING_LEVEL w slowniku: OFF, FAILED RUNS, RUNS, FULL
    String logging = "logging_" + nvl(r.get("LOGGING_LEVEL")).toLowerCase().replace(' ', '_');
    comboLogging.setSelectedIndex(Math.max(0, java.util.Arrays.asList(loggingLevels).indexOf(logging)));

    // argumenty: deklaracja procedury/programu, wartosci z ALL_SCHEDULER_JOB_ARGS
    loadArguments();
    Query q = database.createQuery();
    try {
      q.setSqlText("select argument_position, value from all_scheduler_job_args\n"
        + " where owner = :OWNER and job_name = :NAME order by argument_position");
      q.paramByName("OWNER").setString(schema);
      q.paramByName("NAME").setString(name);
      q.open();
      while (!q.eof()) {
        int pos = q.fieldByName("ARGUMENT_POSITION").getValue().getInteger();
        Variant v = q.fieldByName("VALUE").getValue();
        String value = v.isNullValue() ? "" : v.getString();
        if (action == A_EXECUTABLE) {
          while (args.getRowCount() < pos) {
            args.addRow(new Object[] {"", "VARCHAR2", "", ""});
            argDefaulted.add(Boolean.FALSE);
          }
        }
        if (pos >= 1 && pos <= args.getRowCount()) {
          args.table.getModel().setValueAt(value, pos - 1, 3);
        }
        q.next();
      }
    } catch (Exception ex) {
      ExceptionUtil.processException(ex);
    } finally {
      q.close();
    }

    loadedAttributes = attributes();
    loadedArguments = argumentValues();
    loadedEnabled = checkEnabled.isSelected();
  }

  private static int number(String s, int def) {
    try {
      return s == null ? def : Integer.parseInt(s.trim());
    } catch (NumberFormatException ex) {
      return def;
    }
  }

  /**
   * Atrybuty joba w postaci literalow PL/SQL ("null" - brak wartosci); te nieuzywane przez wybrany rodzaj
   * akcji i harmonogramu sa "null", zeby przy zmianie rodzaju zostaly wyzerowane przed ustawieniem nowych.
   */
  private Map<String, String> attributes() {
    Map<String, String> m = new java.util.LinkedHashMap<String, String>();
    int action = action();
    boolean program = action == A_PROGRAM;
    m.put("program_name", program ? lit(reference(actionText())) : "null");
    m.put("job_type", program ? "null" : lit(actions[action]));
    m.put("job_action", program ? "null" : lit(action == A_CHAIN ? reference(actionText()) : actionText()));
    m.put("number_of_arguments", program ? "null"
      : String.valueOf(action == A_STORED_PROCEDURE ? argumentCount() : action == A_EXECUTABLE ? args.getRowCount() : 0));
    int when = when();
    m.put("schedule_name", when == W_SCHEDULE ? lit(reference(comboText(comboSchedule)))
      : when == W_WINDOW ? lit(reference(comboText(comboWindow))) : "null");
    m.put("start_date", when == W_ONCE ? timestampLit(fieldText(textOnce))
      : when == W_CALENDAR ? timestampLit(calendar.getStart()) : "null");
    m.put("repeat_interval", when == W_CALENDAR ? lit(calendar.getExpression()) : "null");
    m.put("end_date", when == W_CALENDAR && calendar.getEnd().length() > 0 ? timestampLit(calendar.getEnd()) : "null");
    m.put("job_class", lit(comboText(comboClass).length() == 0 ? "DEFAULT_JOB_CLASS" : comboText(comboClass)));
    m.put("auto_drop", checkAutoDrop.isSelected() ? "true" : "false");
    m.put("comments", lit(fieldText(textComments)));
    m.put("credential_name", action == A_EXECUTABLE && comboText(comboCredential).length() > 0
      ? lit(reference(comboText(comboCredential))) : "null");
    int maxFailures = ((Number)spinMaxFailures.getValue()).intValue();
    m.put("max_failures", maxFailures > 0 ? String.valueOf(maxFailures) : "null");
    int maxRuns = ((Number)spinMaxRuns.getValue()).intValue();
    m.put("max_runs", maxRuns > 0 ? String.valueOf(maxRuns) : "null");
    m.put("job_priority", String.valueOf(((Number)spinPriority.getValue()).intValue()));
    m.put("restartable", checkRestartable.isSelected() ? "true" : "false");
    String logging = loggingLevels[comboLogging.getSelectedIndex()];
    // "domyslny" - NULL, poziom z klasy joba (SET_ATTRIBUTE_NULL sprawdzone na 26ai)
    m.put("logging_level", logging == null ? "null" : "dbms_scheduler." + logging);
    return m;
  }

  /** Wartosci argumentow z rodzajem akcji i jej nazwa - po zmianie akcji wszystkie sa ustawiane od nowa */
  private List<String> argumentValues() {
    List<String> result = new ArrayList<String>();
    result.add(action() + ":" + actionText().toUpperCase());
    args.stopEditing();
    for (int r = 0; r < args.getRowCount(); r++) {
      result.add(args.get(r, 3));
    }
    return result;
  }

  private String buildEdit() {
    String name = nameLit();
    int action = action();
    List<String> now = argumentValues();
    boolean sameAction = now.get(0).equals(loadedArguments.get(0));
    int count = action == A_STORED_PROCEDURE ? argumentCount()
      : action == A_EXECUTABLE || action == A_PROGRAM ? args.getRowCount() : 0;
    // wartosci starej akcji (albo pozycji, ktorych juz nie ma) - kasowane przed zmiana atrybutow, przy starej
    // liczbie argumentow; inaczej ENABLE odrzuca job (ORA-27465 ARGUMENT_POSITION, sprawdzone na 26ai)
    StringBuilder changes = new StringBuilder();
    for (int r = 0; r + 1 < loadedArguments.size(); r++) {
      if ((!sameAction || r >= count) && loadedArguments.get(r + 1).length() > 0) {
        changes.append("  dbms_scheduler.reset_job_argument_value(").append(name).append(", ").append(r + 1).append(");\n");
      }
    }
    changes.append(changedAttributes(name, loadedAttributes, attributes()));
    if (count > 0) {
      for (int r = 0; r < count; r++) {
        String value = now.get(r + 1);
        String before = sameAction && r + 1 < loadedArguments.size() ? loadedArguments.get(r + 1) : null;
        if (value.equals(before)) {
          continue;
        }
        if (value.length() > 0 || action == A_EXECUTABLE) {
          changes.append("  dbms_scheduler.set_job_argument_value(").append(name).append(", ").append(r + 1)
            .append(", ").append(lit(value)).append(");\n");
        } else if (before != null) {
          changes.append("  dbms_scheduler.reset_job_argument_value(").append(name).append(", ").append(r + 1).append(");\n");
        }
      }
    }
    String run = checkEnabled.isSelected() && checkRunNow.isSelected() && when() != W_IMMEDIATE
      ? "  dbms_scheduler.run_job(" + name + ", use_current_session => false);\n" : "";
    return editBlock(name, changes.toString(), loadedEnabled, checkEnabled.isSelected(), run);
  }

  @Override
  String buildPlsql() {
    // strona argumentow mogla zostac pominieta (skok z listy krokow) po zmianie akcji - argumenty nowej akcji
    loadArguments();
    if (isEdit()) {
      return buildEdit();
    }
    String name = nameLit();
    List<String> params = new ArrayList<String>();
    add(params, "job_name", name);
    int action = action();
    int argCount = 0;
    if (action == A_PROGRAM) {
      add(params, "program_name", lit(reference(actionText())));
    } else {
      String jobAction = action == A_CHAIN ? reference(actionText()) : actionText();
      add(params, "job_type", lit(actions[action]));
      add(params, "job_action", lit(jobAction));
      argCount = action == A_STORED_PROCEDURE ? argumentCount() : action == A_EXECUTABLE ? args.getRowCount() : 0;
      add(params, "number_of_arguments", String.valueOf(argCount));
    }
    switch (when()) {
      case W_SCHEDULE:
        add(params, "schedule_name", lit(reference(comboText(comboSchedule))));
        break;
      case W_WINDOW:
        add(params, "schedule_name", lit(reference(comboText(comboWindow))));
        break;
      default:
        // bez daty startu scheduler liczy kalendarz w strefie DEFAULT_TIMEZONE bazy, nie uzytkownika
        add(params, "start_date", when() == W_IMMEDIATE ? "null"
          : timestampLit(when() == W_ONCE ? fieldText(textOnce) : calendar.getStart()));
        add(params, "repeat_interval", when() == W_CALENDAR ? lit(calendar.getExpression()) : "null");
        add(params, "end_date", when() == W_CALENDAR && calendar.getEnd().length() > 0 ? timestampLit(calendar.getEnd()) : "null");
    }
    // pusta klasa - parametr pominiety, Oracle przyjmie DEFAULT_JOB_CLASS
    add(params, "job_class", comboText(comboClass).length() == 0 ? null : lit(comboText(comboClass)));
    add(params, "enabled", "false");
    add(params, "auto_drop", checkAutoDrop.isSelected() ? "true" : "false");
    add(params, "comments", lit(fieldText(textComments)));

    StringBuilder sb = new StringBuilder("begin\n");
    sb.append(call("create_job", params.toArray(new String[params.size()])));
    if (action == A_EXECUTABLE && comboText(comboCredential).length() > 0) {
      sb.append(setAttribute(name, "credential_name", lit(reference(comboText(comboCredential)))));
    }
    if (action == A_STORED_PROCEDURE || action == A_EXECUTABLE || action == A_PROGRAM) {
      int count = action == A_PROGRAM ? args.getRowCount() : argCount;
      for (int r = 0; r < count; r++) {
        String value = args.get(r, 3);
        if (value.length() > 0 || action == A_EXECUTABLE) {
          sb.append("  dbms_scheduler.set_job_argument_value(").append(name).append(", ").append(r + 1)
            .append(", ").append(lit(value)).append(");\n");
        }
      }
    }
    int maxFailures = ((Number)spinMaxFailures.getValue()).intValue();
    if (maxFailures > 0) {
      sb.append(setAttribute(name, "max_failures", String.valueOf(maxFailures)));
    }
    int maxRuns = ((Number)spinMaxRuns.getValue()).intValue();
    if (maxRuns > 0) {
      sb.append(setAttribute(name, "max_runs", String.valueOf(maxRuns)));
    }
    int priority = ((Number)spinPriority.getValue()).intValue();
    if (priority != 3) {
      sb.append(setAttribute(name, "job_priority", String.valueOf(priority)));
    }
    if (checkRestartable.isSelected()) {
      sb.append(setAttribute(name, "restartable", "true"));
    }
    String logging = loggingLevels[comboLogging.getSelectedIndex()];
    if (logging != null) {
      sb.append(setAttribute(name, "logging_level", "dbms_scheduler." + logging));
    }
    if (checkEnabled.isSelected()) {
      sb.append("  dbms_scheduler.enable(").append(name).append(");\n");
      if (checkRunNow.isSelected() && when() != W_IMMEDIATE) {
        sb.append("  dbms_scheduler.run_job(").append(name).append(", use_current_session => false);\n");
      }
    }
    return sb.append("end;").toString();
  }

  private static void add(List<String> params, String name, String value) {
    params.add(name);
    params.add(value);
  }

  @Override
  void dispose() {
    calendar.dispose();
  }

}
