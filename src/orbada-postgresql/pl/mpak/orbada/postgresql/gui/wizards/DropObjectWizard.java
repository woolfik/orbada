package pl.mpak.orbada.postgresql.gui.wizards;

import java.util.List;
import javax.swing.JCheckBox;
import pl.mpak.usedb.core.Database;

/**
 * ORBADA 1.3.2: usuniecie obiektu PostgreSQL - DROP TABLE/VIEW/FUNCTION/..., ALTER TABLE ... DROP COLUMN/CONSTRAINT,
 * DROP TRIGGER ... ON. Polecenie: head [CONCURRENTLY] [IF EXISTS] target tail [CASCADE].
 */
public class DropObjectWizard extends PgWizard {

  private final String head;
  private final String target;
  private final String tail;
  private final JCheckBox checkIfExists;
  private final JCheckBox checkCascade;
  private final JCheckBox checkConcurrently;

  /**
   * @param titleKey klucz tytulu (np. "DropTable-title")
   * @param head np. "DROP TABLE", "ALTER TABLE s.t DROP COLUMN"
   * @param target nazwa w SQL (juz w cudzyslowach)
   * @param tail np. " ON s.t" dla wyzwalacza albo ""
   * @param cascade czy polecenie ma CASCADE/RESTRICT
   * @param concurrently czy jest DROP INDEX CONCURRENTLY
   */
  public DropObjectWizard(Database database, String titleKey, String head, String target, String tail, boolean cascade, boolean concurrently) {
    super(database, null);
    this.titleKey = titleKey;
    this.head = head;
    this.target = target;
    this.tail = tail == null ? "" : tail;
    info("object-name", target + this.tail);
    checkIfExists = check("if-exists");
    checkCascade = cascade ? check("drop-cascade") : null;
    if (concurrently) {
      checkConcurrently = check("concurrently");
      // CONCURRENTLY nie dziala w bloku transakcji
      checkConcurrently.setEnabled(database.isAutoCommit());
      note("concurrently-hint");
    } else {
      checkConcurrently = null;
    }
  }

  @Override
  protected void build(List<String> sql) {
    sql.add(head +
      (checkConcurrently != null && checkConcurrently.isSelected() ? " CONCURRENTLY" : "") +
      (checkIfExists.isSelected() ? " IF EXISTS" : "") +
      " " + target + tail +
      (checkCascade != null && checkCascade.isSelected() ? " CASCADE" : ""));
  }

}
