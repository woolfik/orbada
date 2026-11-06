package pl.mpak.orbada.postgresql.gui.wizards;

import java.util.List;
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

  public CreateSchemaWizard(Database database) {
    super(database, null);
    textName = text("schema-name", "");
    String[] roles = roles();
    roles[0] = "";
    comboOwner = combo("owner-name", true, roles);
    textComment = text("comment", "");
  }

  public void setSchema(String name, String owner) {
    textName.setText(name);
    comboOwner.setSelectedItem(owner);
  }

  @Override
  protected void build(List<String> sql) {
    require(value(textName).length() > 0, "wizard-name-required");
    result("object_name", value(textName));
    sql.add("CREATE SCHEMA " + ident(value(textName)) + (value(comboOwner).length() > 0 ? " AUTHORIZATION " + ident(value(comboOwner)) : ""));
    if (value(textComment).length() > 0) {
      sql.add("COMMENT ON SCHEMA " + ident(value(textName)) + " IS " + literal(value(textComment)));
    }
  }

}
