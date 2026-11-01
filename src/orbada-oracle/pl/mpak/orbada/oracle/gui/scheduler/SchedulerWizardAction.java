package pl.mpak.orbada.oracle.gui.scheduler;

import java.awt.event.ActionEvent;
import javax.swing.JMenu;
import pl.mpak.orbada.oracle.services.OracleDbInfoProvider;
import pl.mpak.sky.gui.swing.Action;
import pl.mpak.sky.gui.swing.SwingUtil;
import pl.mpak.usedb.core.Database;
import pl.mpak.util.ExceptionUtil;

/**
 * ORBADA (F5): kreator obiektu schedulera z menu Kreatory - w biezacym schemacie polaczenia.
 */
public class SchedulerWizardAction extends Action {

  private final static String[][] wizards = {
    {"jobs", "SchedWiz-title-job"}, {"programs", "SchedWiz-title-program"},
    {"schedules", "SchedWiz-title-schedule"}, {"chains", "SchedWiz-title-chain"}};

  private final Database database;
  private final String specId;

  private SchedulerWizardAction(Database database, String specId, String titleKey) {
    super();
    this.database = database;
    this.specId = specId;
    setText(SchedulerWizard.text(titleKey) + "...");
    setActionCommandKey("SchedulerWizardAction-" + specId);
    addActionListener(this::create);
  }

  /** Podmenu "Scheduler" z kreatorami joba, programu, harmonogramu i chaina */
  public static JMenu createMenu(Database database) {
    JMenu menu = new JMenu(SchedulerWizard.text("SchedWiz-menu"));
    menu.setIcon(pl.mpak.sky.gui.swing.ImageManager.getImage("/pl/mpak/res/icons/job.gif"));
    for (String[] w : wizards) {
      menu.add(new SchedulerWizardAction(database, w[0], w[1]));
    }
    return menu;
  }

  private void create(ActionEvent e) {
    try {
      SchedulerWizard wizard = SchedulerWizard.create(specId, database, OracleDbInfoProvider.getCurrentSchema(database));
      if (wizard != null) {
        wizard.show(SwingUtil.getRootFrame());
      }
    } catch (Exception ex) {
      // np. polaczenie zamkniete w trakcie - komunikat zamiast wyjatku na EDT
      ExceptionUtil.processException(ex);
    }
  }

}
