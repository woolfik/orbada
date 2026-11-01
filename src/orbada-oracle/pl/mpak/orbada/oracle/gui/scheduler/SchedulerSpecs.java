package pl.mpak.orbada.oracle.gui.scheduler;

import pl.mpak.orbada.oracle.gui.scheduler.SchedulerSpec.Action;
import pl.mpak.orbada.oracle.gui.scheduler.SchedulerSpec.Column;
import pl.mpak.orbada.oracle.gui.scheduler.SchedulerSpec.Detail;

/**
 * ORBADA Fuerteventura (F5): zakladki widoku Oracle Scheduler (widoki ALL_SCHEDULER_*, Oracle 12.2+;
 * sprawdzone na 26ai). Kolejnosc jak w przegladarkach schematu (np. Toad): joby, programy, harmonogramy,
 * chainy, okna, grupy, klasy jobow, historia.
 */
final class SchedulerSpecs {

  private SchedulerSpecs() {
  }

  private static Column col(String name, int width) {
    return new Column(name, "Scheduler-col-" + name.toLowerCase(), width);
  }

  private static Detail det(String key, String sql) {
    return new Detail("Scheduler-tab-" + key, sql);
  }

  private static Action act(String key, String plsql, boolean confirm) {
    return new Action("Scheduler-act-" + key, plsql, confirm);
  }

  private final static Action enable = act("enable", "begin dbms_scheduler.enable(:NAME); end;", false);
  private final static Action disable = act("disable", "begin dbms_scheduler.disable(:NAME); end;", false);

  static SchedulerSpec[] all() {
    return new SchedulerSpec[] {
      new SchedulerSpec("jobs", "Scheduler-jobs", "all_scheduler_jobs", "job_name", true, null,
        new Column[] {
          col("job_name", 160), col("enabled", 55), col("state", 80), col("job_type", 100), col("program_name", 120),
          col("schedule_name", 120), col("repeat_interval", 180), col("next_run_date", 150), col("last_start_date", 150),
          col("last_run_duration", 110), col("run_count", 60), col("failure_count", 60), col("job_class", 120),
          col("comments", 200)},
        new Detail[] {
          det("arguments", "select argument_position, argument_name, argument_type, value\n"
            + "  from all_scheduler_job_args where owner = :OWNER and job_name = :NAME order by argument_position"),
          det("run-details", "select log_date, status, error#, actual_start_date, run_duration, cpu_used,\n"
            + "       instance_id, session_id, additional_info\n"
            + "  from all_scheduler_job_run_details where owner = :OWNER and job_name = :NAME order by log_date desc"),
          det("log", "select log_date, operation, status, user_name, additional_info\n"
            + "  from all_scheduler_job_log where owner = :OWNER and job_name = :NAME order by log_date desc"),
          det("running", "select session_id, running_instance, elapsed_time, cpu_used, resource_consumer_group\n"
            + "  from all_scheduler_running_jobs where owner = :OWNER and job_name = :NAME")},
        new Action[] {
          act("run", "begin dbms_scheduler.run_job(:NAME, use_current_session => false); end;", true),
          act("stop", "begin dbms_scheduler.stop_job(:NAME); end;", true),
          // gdy zwykle zatrzymanie nie dziala (wymaga uprawnienia MANAGE SCHEDULER)
          act("stop-force", "begin dbms_scheduler.stop_job(:NAME, force => true); end;", true),
          enable, disable,
          act("drop", "begin dbms_scheduler.drop_job(:NAME); end;", true)}),

      new SchedulerSpec("programs", "Scheduler-programs", "all_scheduler_programs", "program_name", true, null,
        new Column[] {
          col("program_name", 160), col("program_type", 120), col("program_action", 260), col("number_of_arguments", 60),
          col("enabled", 55), col("comments", 200)},
        new Detail[] {
          det("arguments", "select argument_position, argument_name, argument_type, metadata_attribute, default_value, out_argument\n"
            + "  from all_scheduler_program_args where owner = :OWNER and program_name = :NAME order by argument_position"),
          det("used-by-jobs", "select owner, job_name, enabled, state, next_run_date from all_scheduler_jobs\n"
            + " where program_owner = :OWNER and program_name = :NAME order by owner, job_name"),
          det("used-by-chains", "select owner, chain_name, step_name from all_scheduler_chain_steps\n"
            + " where program_owner = :OWNER and program_name = :NAME order by owner, chain_name, step_name")},
        new Action[] {
          enable, disable,
          act("drop", "begin dbms_scheduler.drop_program(:NAME); end;", true)}),

      new SchedulerSpec("schedules", "Scheduler-schedules", "all_scheduler_schedules", "schedule_name", true, null,
        new Column[] {
          col("schedule_name", 160), col("schedule_type", 100), col("start_date", 150), col("repeat_interval", 220),
          col("end_date", 150), col("comments", 200)},
        new Detail[] {
          det("used-by-jobs", "select owner, job_name, enabled, state, next_run_date from all_scheduler_jobs\n"
            + " where schedule_owner = :OWNER and schedule_name = :NAME order by owner, job_name")},
        new Action[] {
          act("drop", "begin dbms_scheduler.drop_schedule(:NAME); end;", true)}),

      new SchedulerSpec("chains", "Scheduler-chains", "all_scheduler_chains", "chain_name", true, null,
        new Column[] {
          col("chain_name", 160), col("enabled", 55), col("number_of_steps", 60), col("number_of_rules", 60),
          col("evaluation_interval", 120), col("rule_set_name", 140), col("comments", 200)},
        new Detail[] {
          det("steps", "select step_name, step_type, program_owner, program_name, event_schedule_name, skip, pause,\n"
            + "       restart_on_failure, restart_on_recovery, credential_name, destination\n"
            + "  from all_scheduler_chain_steps where owner = :OWNER and chain_name = :NAME order by step_name"),
          det("rules", "select rule_name, condition, action, comments\n"
            + "  from all_scheduler_chain_rules where owner = :OWNER and chain_name = :NAME order by rule_name"),
          det("running", "select job_name, job_subname, step_name, state, error_code, start_date, end_date, duration\n"
            + "  from all_scheduler_running_chains where owner = :OWNER and chain_name = :NAME order by job_name, step_name"),
          det("used-by-jobs", "select owner, job_name, enabled, state, next_run_date from all_scheduler_jobs\n"
            // job_action to tekst wpisany przy tworzeniu joba - porownanie bez wielkosci liter po obu stronach
            + " where job_type = 'CHAIN' and upper(job_action) in (upper(:NAME), upper(:OWNER) || '.' || upper(:NAME),\n"
            + "       '\"' || upper(:OWNER) || '\".\"' || upper(:NAME) || '\"') order by owner, job_name")},
        new Action[] {
          // start_steps => null: kroki startuja wedlug regul chaina; Oracle tworzy job RUN_CHAIN$...
          act("run", "begin dbms_scheduler.run_chain(:NAME, start_steps => null); end;", true),
          enable, disable,
          act("drop", "begin dbms_scheduler.drop_chain(:NAME); end;", true)}),

      new SchedulerSpec("windows", "Scheduler-windows", "all_scheduler_windows", "window_name", false, null,
        new Column[] {
          col("window_name", 170), col("enabled", 55), col("active", 55), col("resource_plan", 140),
          col("schedule_name", 120), col("repeat_interval", 220), col("duration", 120), col("next_start_date", 150),
          col("window_priority", 60), col("comments", 200)},
        new Detail[] {
          det("window-details", "select log_date, req_start_date, actual_start_date, window_duration, actual_duration, additional_info\n"
            + "  from all_scheduler_window_details where window_name = :NAME order by log_date desc"),
          det("groups", "select owner, group_name from all_scheduler_group_members\n"
            + " where member_name in (:NAME, :OWNER || '.' || :NAME, '\"' || :OWNER || '\".\"' || :NAME || '\"') order by group_name")},
        new Action[] {
          act("open-window", "begin dbms_scheduler.open_window(:NAME, null); end;", true),
          act("close-window", "begin dbms_scheduler.close_window(:NAME); end;", true),
          enable, disable}),

      new SchedulerSpec("groups", "Scheduler-groups", "all_scheduler_groups", "group_name", false, null,
        new Column[] {
          col("group_name", 170), col("group_type", 100), col("enabled", 55), col("number_of_members", 60),
          col("comments", 200)},
        new Detail[] {
          det("members", "select member_name from all_scheduler_group_members\n"
            + " where owner = :OWNER and group_name = :NAME order by member_name")},
        new Action[] {enable, disable}),

      new SchedulerSpec("job-classes", "Scheduler-job-classes", "all_scheduler_job_classes", "job_class_name", false, null,
        new Column[] {
          col("job_class_name", 170), col("resource_consumer_group", 160), col("service", 120),
          col("logging_level", 80), col("log_history", 60), col("comments", 200)},
        new Detail[] {
          det("used-by-jobs", "select owner, job_name, enabled, state, next_run_date from all_scheduler_jobs\n"
            + " where job_class in (:NAME, :OWNER || '.' || :NAME, '\"' || :OWNER || '\".\"' || :NAME || '\"') order by owner, job_name")},
        new Action[0]),

      // kluczem jest log_id - szczegoly dotycza zaznaczonego uruchomienia, nie wszystkich uruchomien joba
      new SchedulerSpec("history", "Scheduler-history", "all_scheduler_job_run_details", "log_id", true,
        "log_date desc",
        new Column[] {
          col("log_date", 150), col("log_id", 70), col("job_name", 160), col("job_subname", 100), col("status", 90), col("error#", 60),
          col("actual_start_date", 150), col("run_duration", 110), col("cpu_used", 110), col("additional_info", 300)},
        new Detail[0],
        new Action[0]),
    };
  }

}
