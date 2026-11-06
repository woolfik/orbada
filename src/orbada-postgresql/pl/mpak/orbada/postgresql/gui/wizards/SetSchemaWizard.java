package pl.mpak.orbada.postgresql.gui.wizards;

import java.util.List;
import javax.swing.JComboBox;
import pl.mpak.usedb.core.Database;

/**
 * ORBADA 1.3.2: przeniesienie obiektu do innego schematu - head SET SCHEMA s, np. "ALTER TABLE s.t".
 */
public class SetSchemaWizard extends PgWizard {

  private final String head;
  private final JComboBox<String> comboSchema;

  public SetSchemaWizard(Database database, String head, String objectName, String currentSchema) {
    super(database, currentSchema);
    this.head = head;
    info("object-name", objectName);
    comboSchema = combo("schema-name", true, schemas());
    comboSchema.setSelectedItem(currentSchema);
  }

  @Override
  protected void build(List<String> sql) {
    require(value(comboSchema).length() > 0, "wizard-name-required");
    sql.add(head + " SET SCHEMA " + ident(value(comboSchema)));
  }

}
