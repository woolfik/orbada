package pl.mpak.orbada.postgresql.gui.wizards;

import java.util.List;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import pl.mpak.sky.gui.swing.comp.TextField;
import pl.mpak.usedb.core.Database;

/**
 * ORBADA 1.3.2: kopia tabeli albo widoku jako nowej tabeli - CREATE TABLE ... (LIKE ... INCLUDING ALL) z opcjonalnym
 * skopiowaniem danych albo CREATE TABLE ... AS SELECT. Odpowiednik CopyTableWizard i CopyViewAsTableWizard Oracle.
 */
public class CopyTableWizard extends PgWizard {

  private final String source;
  private final boolean view;
  private final JComboBox<String> comboSchema;
  private final TextField textName;
  private final JComboBox<String> comboMode;
  private final JCheckBox checkData;

  /**
   * @param source zrodlo w SQL (schemat.nazwa)
   * @param view zrodlo jest widokiem - tylko AS SELECT
   */
  public CopyTableWizard(Database database, String schemaName, String source, String sourceName, boolean view) {
    super(database, schemaName);
    this.source = source;
    this.view = view;
    this.titleKey = view ? "CopyViewAsTableWizard-title" : "CopyTableWizard-title";
    info("source-object", source);
    comboSchema = combo("schema-name", true, schemas());
    comboSchema.setSelectedItem(this.schemaName);
    textName = text("table-name", sourceName + "_copy");
    comboMode = view
      ? combo("copy-mode", false, stringManager.getString("copy-as-select"))
      : combo("copy-mode", false, stringManager.getString("copy-like"), stringManager.getString("copy-as-select"));
    checkData = check("copy-data");
    checkData.setSelected(true);
  }

  private boolean like() {
    return !view && comboMode.getSelectedIndex() == 0;
  }

  @Override
  protected void build(List<String> sql) {
    require(value(textName).length() > 0, "wizard-name-required");
    result("object_name", value(textName));
    String target = name(value(comboSchema), value(textName));
    if (like()) {
      // LIKE ... INCLUDING ALL: kolumny z DEFAULT/IDENTITY/GENERATED, ograniczenia, indeksy, komentarze
      sql.add("CREATE TABLE " + target + " (LIKE " + source + " INCLUDING ALL)");
      if (checkData.isSelected()) {
        // kolumny generowane sa wyliczane, IDENTITY ALWAYS wymaga OVERRIDING SYSTEM VALUE
        String columns = columns();
        sql.add("INSERT INTO " + target + " (" + columns + ")" + (atLeast(10, 0) ? " OVERRIDING SYSTEM VALUE" : "") +
          "\nSELECT " + columns + " FROM " + source);
      }
    } else {
      sql.add("CREATE TABLE " + target + " AS\nSELECT * FROM " + source + (checkData.isSelected() ? "" : "\nWITH NO DATA"));
    }
  }

  private String columns() {
    StringBuilder sb = new StringBuilder();
    for (String c : list("select a.attname from pg_catalog.pg_attribute a where a.attrelid = (:NAME)::regclass and a.attnum > 0" +
        " and not a.attisdropped" + (atLeast(12, 0) ? " and a.attgenerated = ''" : "") + " order by a.attnum", source)) {
      sb.append(sb.length() > 0 ? ", " : "").append(ident(c));
    }
    return sb.toString();
  }

}
