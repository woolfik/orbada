package pl.mpak.orbada.postgresql.gui.wizards;

import java.util.List;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import pl.mpak.sky.gui.swing.comp.TextField;
import pl.mpak.usedb.core.Database;

/**
 * ORBADA 1.3.2: nowy schemat (CREATE SCHEMA ... AUTHORIZATION) z komentarzem.
 */
public class CreateSchemaWizard extends PgWizard {

  private final TextField textName;
  private final JComboBox<String> comboOwner;
  private final TextField textComment;
  // ORBADA 1.3.4: IF NOT EXISTS - bez bledu, gdy obiekt juz istnieje
  private final JCheckBox checkIfNotExists;

  public CreateSchemaWizard(Database database) {
    super(database, null);
    textName = text("schema-name", "");
    String[] roles = roles();
    roles[0] = "";
    comboOwner = combo("owner-name", true, roles);
    textComment = text("comment", "");
    checkIfNotExists = atLeast(9, 3) ? check("if-not-exists") : null;
  }

  public void setSchema(String name, String owner) {
    textName.setText(name);
    comboOwner.setSelectedItem(owner);
  }

  @Override
  protected void build(List<String> sql) {
    require(value(textName).length() > 0, "wizard-name-required");
    result("object_name", value(textName));
    sql.add("CREATE SCHEMA " + (checkIfNotExists != null && checkIfNotExists.isSelected() ? "IF NOT EXISTS " : "") + ident(value(textName)) + (value(comboOwner).length() > 0 ? " AUTHORIZATION " + ident(value(comboOwner)) : ""));
    if (value(textComment).length() > 0) {
      sql.add("COMMENT ON SCHEMA " + ident(value(textName)) + " IS " + literal(value(textComment)));
    }
  }

}
