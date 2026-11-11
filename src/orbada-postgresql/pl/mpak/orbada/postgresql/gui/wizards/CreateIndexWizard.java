package pl.mpak.orbada.postgresql.gui.wizards;

import java.util.List;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import pl.mpak.sky.gui.swing.comp.TextField;
import pl.mpak.usedb.core.Database;

/**
 * ORBADA 1.3.2: indeks tabeli albo widoku zmaterializowanego - UNIQUE, metoda (btree, hash, gist, gin, ...),
 * kolumny/wyrazenia, INCLUDE, WHERE (indeks czesciowy), NULLS NOT DISTINCT, CONCURRENTLY.
 */
public class CreateIndexWizard extends PgWizard {

  private final JComboBox<String> comboTable;
  private final TextField textName;
  private final JCheckBox checkUnique;
  private final JComboBox<String> comboMethod;
  private final TextField textColumns;
  private final TextField textInclude;
  private final TextField textWhere;
  private final JCheckBox checkNullsNotDistinct;
  private final JCheckBox checkConcurrently;
  // ORBADA 1.3.4: IF NOT EXISTS - bez bledu, gdy obiekt juz istnieje
  private final JCheckBox checkIfNotExists;

  /**
   * @param table tabela (bez schematu) albo null - wybor z listy
   */
  public CreateIndexWizard(Database database, String schemaName, String table) {
    super(database, schemaName);
    info("schema-name", this.schemaName);
    comboTable = combo("table-name", true, list(
      "select c.relname from pg_catalog.pg_class c join pg_catalog.pg_namespace n on n.oid = c.relnamespace" +
      " where n.nspname = :SCHEMA_NAME and c.relkind in ('r', 'p', 'm') order by 1", this.schemaName));
    if (table != null) {
      comboTable.setSelectedItem(table);
      comboTable.setEnabled(false);
    }
    textName = text("new-index-name", table != null ? table + "_idx" : "");
    checkUnique = check("index-unique");
    comboMethod = combo("index-access-method", true, list("select amname from pg_catalog.pg_am" + (atLeast(9, 6) ? " where amtype = 'i'" : "") + " order by amname <> 'btree', 1"));
    textColumns = text("index-columns", "");
    note("index-columns-hint");
    textInclude = atLeast(11, 0) ? text("index-include", "") : null;
    textWhere = text("index-where", "");
    checkNullsNotDistinct = atLeast(15, 0) ? check("nulls-not-distinct") : null;
    checkIfNotExists = atLeast(9, 5) ? check("if-not-exists") : null;
    checkConcurrently = check("concurrently");
    checkConcurrently.setEnabled(database.isAutoCommit());
    note("concurrently-hint");
  }

  public void setIndex(String name, boolean unique, String method, String columns, String where) {
    textName.setText(name);
    checkUnique.setSelected(unique);
    comboMethod.setSelectedItem(method);
    textColumns.setText(columns);
    textWhere.setText(where);
  }

  @Override
  protected void build(List<String> sql) {
    require(value(comboTable).length() > 0, "wizard-table-required");
    require(value(textColumns).length() > 0, "wizard-columns-required");
    boolean ifNotExists = checkIfNotExists != null && checkIfNotExists.isSelected();
    // IF NOT EXISTS wymaga nazwy indeksu
    require(!ifNotExists || value(textName).length() > 0, "wizard-name-required");
    result("object_name", value(textName));
    sql.add("CREATE " + (checkUnique.isSelected() ? "UNIQUE " : "") + "INDEX " +
      (checkConcurrently.isSelected() ? "CONCURRENTLY " : "") +
      (ifNotExists ? "IF NOT EXISTS " : "") +
      (value(textName).length() > 0 ? ident(value(textName)) + " " : "") +
      "ON " + name(schemaName, value(comboTable)) +
      (!"btree".equals(value(comboMethod)) && value(comboMethod).length() > 0 ? " USING " + value(comboMethod) : "") +
      " (" + value(textColumns) + ")" +
      (textInclude != null && value(textInclude).length() > 0 ? " INCLUDE (" + identList(value(textInclude)) + ")" : "") +
      (checkNullsNotDistinct != null && checkNullsNotDistinct.isSelected() ? " NULLS NOT DISTINCT" : "") +
      (value(textWhere).length() > 0 ? " WHERE " + value(textWhere) : ""));
  }

}
