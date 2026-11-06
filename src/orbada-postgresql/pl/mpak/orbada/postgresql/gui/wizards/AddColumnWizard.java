package pl.mpak.orbada.postgresql.gui.wizards;

import java.util.List;
import pl.mpak.orbada.postgresql.services.PostgreSQLDbInfoProvider;
import pl.mpak.usedb.core.Database;

/**
 * ORBADA 1.3.2: dodanie kolumn do tabeli (ALTER TABLE ... ADD COLUMN) - ta sama siatka co w kreatorze tabeli.
 */
public class AddColumnWizard extends PgWizard {

  private final String tableName;
  private final ColumnGridPanel columns;

  public AddColumnWizard(Database database, String schemaName, String table) {
    super(database, schemaName);
    tableName = name(this.schemaName, table);
    info("table-name", tableName);
    columns = row(null, new ColumnGridPanel(stringManager, PostgreSQLDbInfoProvider.instance.getDataTypes(database),
      atLeast(10, 0), atLeast(12, 0), atLeast(18, 0)), true);
    columns.addColumn("", "integer", false, false, "", "", "");
  }

  public ColumnGridPanel getColumns() {
    return columns;
  }

  @Override
  protected void build(List<String> sql) {
    List<String> definitions = columns.definitions();
    require(!definitions.isEmpty(), "wizard-columns-required");
    StringBuilder sb = new StringBuilder("ALTER TABLE " + tableName);
    String separator = "\n  ";
    for (String column : definitions) {
      sb.append(separator).append("ADD COLUMN ").append(column);
      separator = ",\n  ";
    }
    String pk = columns.primaryKey();
    if (pk.length() > 0) {
      sb.append(separator).append("ADD PRIMARY KEY (").append(pk).append(")");
    }
    sql.add(sb.toString());
    columns.comments(tableName, sql);
  }

}
