package pl.mpak.orbada.oracle.gui.scheduler;

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
  String getObjectName() {
    return ident(textName.getText());
  }

  @Override
  String buildPlsql() {
    return "begin\n"
      + call("create_schedule",
        "schedule_name", lit(qualified(textName.getText())),
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
