package pl.mpak.orbada.postgresql.gui.wizards;

import java.util.List;
import pl.mpak.sky.gui.swing.comp.TextField;
import pl.mpak.usedb.core.Database;

/**
 * ORBADA 1.3.2: zmiana nazwy obiektu - polecenie: head TO nowa_nazwa, np. "ALTER TABLE s.t RENAME",
 * "ALTER TABLE s.t RENAME COLUMN c", "ALTER TRIGGER t ON s.x RENAME".
 */
public class RenameObjectWizard extends PgWizard {

  private final String head;
  private final TextField textName;

  public RenameObjectWizard(Database database, String titleKey, String head, String currentName) {
    super(database, null);
    this.titleKey = titleKey;
    this.head = head;
    info("object-name", currentName);
    textName = text("new-name", currentName);
  }

  @Override
  protected void build(List<String> sql) {
    require(value(textName).length() > 0, "wizard-name-required");
    result("object_name", value(textName));
    sql.add(head + " TO " + ident(value(textName)));
  }

}
