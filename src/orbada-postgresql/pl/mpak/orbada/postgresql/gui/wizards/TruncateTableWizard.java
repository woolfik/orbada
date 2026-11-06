package pl.mpak.orbada.postgresql.gui.wizards;

import java.util.List;
import javax.swing.JCheckBox;
import pl.mpak.usedb.core.Database;

/**
 * ORBADA 1.3.2: TRUNCATE tabeli - RESTART IDENTITY (sekwencje kolumn od poczatku), CASCADE (tabele z kluczami obcymi).
 */
public class TruncateTableWizard extends PgWizard {

  private final String table;
  private final JCheckBox checkRestart;
  private final JCheckBox checkCascade;

  public TruncateTableWizard(Database database, String table) {
    super(database, null);
    this.table = table;
    info("table-name", table);
    checkRestart = check("restart-identity");
    checkCascade = check("truncate-cascade");
  }

  @Override
  protected void build(List<String> sql) {
    sql.add("TRUNCATE TABLE " + table + (checkRestart.isSelected() ? " RESTART IDENTITY" : "") + (checkCascade.isSelected() ? " CASCADE" : ""));
  }

}
