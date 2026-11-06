package pl.mpak.orbada.postgresql.gui.wizards;

import java.util.List;
import pl.mpak.usedb.core.Database;

/**
 * ORBADA 1.3.2: kreator bez opcji - potwierdzenie gotowego polecenia (np. VALIDATE CONSTRAINT).
 */
public class StatementWizard extends PgWizard {

  private final String statement;

  public StatementWizard(Database database, String titleKey, String objectName, String statement) {
    super(database, null);
    this.titleKey = titleKey;
    this.statement = statement;
    info("object-name", objectName);
  }

  @Override
  protected void build(List<String> sql) {
    sql.add(statement);
  }

}
