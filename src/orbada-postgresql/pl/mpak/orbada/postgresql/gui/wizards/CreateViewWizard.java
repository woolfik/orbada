package pl.mpak.orbada.postgresql.gui.wizards;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import javax.swing.JCheckBox;
import javax.swing.JTextArea;
import pl.mpak.sky.gui.swing.comp.TextField;
import pl.mpak.usedb.core.Database;

/**
 * ORBADA 1.3.2: widok albo widok zmaterializowany (CREATE [OR REPLACE] VIEW, CREATE MATERIALIZED VIEW ... [WITH NO DATA]).
 */
public class CreateViewWizard extends PgWizard {

  private final TextField textName;
  private final JCheckBox checkMaterialized;
  private final JCheckBox checkReplace;
  private final JCheckBox checkNoData;
  private final TextField textComment;
  private final JTextArea textQuery;

  public CreateViewWizard(Database database, String schemaName) {
    super(database, schemaName);
    info("schema-name", this.schemaName);
    textName = text("view-name", "");
    checkMaterialized = check("materialized-view");
    checkReplace = check("or-replace");
    checkNoData = check("with-no-data");
    checkNoData.setEnabled(false);
    checkMaterialized.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        checkReplace.setEnabled(!checkMaterialized.isSelected());
        checkNoData.setEnabled(checkMaterialized.isSelected());
      }
    });
    checkMaterialized.setEnabled(atLeast(9, 3));
    textComment = text("comment", "");
    textQuery = area("view-query", "SELECT\n  \nFROM ");
  }

  public void setView(String name, boolean materialized, String query) {
    textName.setText(name);
    checkMaterialized.setSelected(materialized);
    textQuery.setText(query);
  }

  @Override
  protected void build(List<String> sql) {
    require(value(textName).length() > 0, "wizard-name-required");
    result("object_name", value(textName));
    String view = name(schemaName, value(textName));
    String query = value(textQuery);
    if (query.endsWith(";")) {
      query = query.substring(0, query.length() - 1);
    }
    if (checkMaterialized.isSelected()) {
      sql.add("CREATE MATERIALIZED VIEW " + view + " AS\n" + query + (checkNoData.isSelected() ? "\nWITH NO DATA" : ""));
    } else {
      sql.add("CREATE " + (checkReplace.isSelected() ? "OR REPLACE " : "") + "VIEW " + view + " AS\n" + query);
    }
    if (value(textComment).length() > 0) {
      sql.add("COMMENT ON " + (checkMaterialized.isSelected() ? "MATERIALIZED VIEW " : "VIEW ") + view + " IS " + literal(value(textComment)));
    }
  }

}
