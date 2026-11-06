package pl.mpak.orbada.postgresql.gui.wizards;

import java.util.List;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import pl.mpak.usedb.core.Database;

/**
 * ORBADA 1.3.2: blokada tabeli do konca transakcji (LOCK TABLE ... IN ... MODE [NOWAIT]). Dziala tylko bez autocommit.
 */
public class LockTableWizard extends PgWizard {

  private static final String[] MODES = {"ACCESS SHARE", "ROW SHARE", "ROW EXCLUSIVE", "SHARE UPDATE EXCLUSIVE", "SHARE",
    "SHARE ROW EXCLUSIVE", "EXCLUSIVE", "ACCESS EXCLUSIVE"};

  private final String table;
  private final JComboBox<String> comboMode;
  private final JCheckBox checkNoWait;

  public LockTableWizard(Database database, String table) {
    super(database, null);
    this.table = table;
    info("table-name", table);
    comboMode = combo("lock-mode", false, MODES);
    comboMode.setSelectedItem("EXCLUSIVE");
    checkNoWait = check("nowait");
    if (database.isAutoCommit()) {
      note("lock-autocommit-hint");
    }
  }

  @Override
  protected void build(List<String> sql) {
    sql.add("LOCK TABLE " + table + " IN " + value(comboMode) + " MODE" + (checkNoWait.isSelected() ? " NOWAIT" : ""));
  }

}
