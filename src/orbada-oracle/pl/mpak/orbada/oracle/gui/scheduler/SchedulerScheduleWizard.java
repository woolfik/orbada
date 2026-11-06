package pl.mpak.orbada.oracle.gui.scheduler;

import java.util.Map;
import javax.swing.JTextField;
import pl.mpak.usedb.core.Database;

/**
 * ORBADA Fuerteventura (F5): kreator harmonogramu (DBMS_SCHEDULER.CREATE_SCHEDULE) - nazwa i kalendarz
 * z podgladem najblizszych uruchomien.
 */
class SchedulerScheduleWizard extends SchedulerWizard {

  private final JTextField textName = new JTextField();
  private final JTextField textComments = new JTextField();
  private final SchedulerCalendarPanel calendar;

  SchedulerScheduleWizard(Database database, String schema) {
    super(database, schema);
    calendar = new SchedulerCalendarPanel(database);
    addPage(new Page("SchedWiz-page-general", new Form()
        .row("SchedWiz-name", textName)
        .row("SchedWiz-comments", textComments)
        .hint("SchedWiz-schedule-hint")
        .end()) {
      @Override
      String validate() {
        return checkName(textName.getText(), "SchedWiz-name");
      }
    });
    addPage(new Page("SchedWiz-page-calendar", calendar) {
      @Override
      String validate() {
        return calendar.validateInput();
      }
      @Override
      void shown() {
        calendar.shown();
      }
    });
  }

  @Override
  String getTitleKey() {
    return "SchedWiz-title-schedule";
  }

  @Override
  String getTypedName() {
    return textName.getText();
  }

  // --- edycja

  private Map<String, String> loadedAttributes;

  @Override
  void load(String name) {
    Map<String, String> r = loadRow("select schedule_type, " + SchedulerWizard.dateColumn("start_date") + ", repeat_interval, "
      + SchedulerWizard.dateColumn("end_date") + ", comments\n"
      + "  from all_scheduler_schedules where owner = :OWNER and schedule_name = :NAME", name);
    if (!"CALENDAR".equals(r.get("SCHEDULE_TYPE")) && !"PLSQL".equals(r.get("SCHEDULE_TYPE"))) {
      throw new IllegalArgumentException(text("SchedWiz-err-edit-schedule", nvl(r.get("SCHEDULE_TYPE"))));
    }
    textName.setText(name);
    textName.setEditable(false);
    textComments.setText(nvl(r.get("COMMENTS")));
    calendar.setValues(r.get("REPEAT_INTERVAL"), r.get("START_DATE"), r.get("END_DATE"));
    loadedAttributes = attributes();
  }

  private Map<String, String> attributes() {
    Map<String, String> m = new java.util.LinkedHashMap<String, String>();
    m.put("start_date", timestampLit(calendar.getStart()));
    m.put("repeat_interval", lit(calendar.getExpression()));
    m.put("end_date", calendar.getEnd().length() == 0 ? "null" : timestampLit(calendar.getEnd()));
    m.put("comments", lit(fieldText(textComments)));
    return m;
  }

  @Override
  String buildPlsql() {
    if (isEdit()) {
      // harmonogram nie ma wlaczania - zmiany od razu
      return editBlock(nameLit(), changedAttributes(nameLit(), loadedAttributes, attributes()), false, false, "");
    }
    return "begin\n"
      + call("create_schedule",
        "schedule_name", nameLit(),
        "start_date", timestampLit(calendar.getStart()),
        "repeat_interval", lit(calendar.getExpression()),
        "end_date", calendar.getEnd().length() == 0 ? "null" : timestampLit(calendar.getEnd()),
        "comments", lit(fieldText(textComments)))
      + "end;";
  }

  @Override
  void dispose() {
    calendar.dispose();
  }

}
