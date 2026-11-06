package pl.mpak.orbada.postgresql.gui.wizards;

import java.util.List;
import javax.swing.JCheckBox;
import pl.mpak.usedb.core.Database;

/**
 * ORBADA 1.3.2: przebudowa indeksu albo wszystkich indeksow tabeli (REINDEX [CONCURRENTLY]) - odpowiednik
 * RebuildIndex/CoalesceIndex Oracle.
 */
public class ReindexWizard extends PgWizard {

  private final String kind;
  private final String object;
  private final JCheckBox checkConcurrently;

  /** kind: INDEX albo TABLE */
  public ReindexWizard(Database database, String kind, String object) {
    super(database, null);
    this.kind = kind;
    this.object = object;
    info("object-name", object);
    checkConcurrently = check("concurrently");
    checkConcurrently.setEnabled(atLeast(12, 0) && database.isAutoCommit());
    note("concurrently-hint");
  }

  @Override
  protected void build(List<String> sql) {
    sql.add("REINDEX " + kind + " " + (checkConcurrently.isSelected() ? "CONCURRENTLY " : "") + object);
  }

}
