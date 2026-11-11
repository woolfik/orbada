package pl.mpak.orbada.postgresql.gui.wizards;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import pl.mpak.orbada.postgresql.services.PostgreSQLDbInfoProvider;
import pl.mpak.sky.gui.swing.comp.TextField;
import pl.mpak.usedb.core.Database;

/**
 * ORBADA 1.3.2: kreator tabeli PostgreSQL (zwykla, UNLOGGED, tymczasowa) z kolumnami IDENTITY i generowanymi,
 * kluczem glownym, partycjonowaniem i komentarzami - odpowiednik CreateNormalTableWizard/CreateTemporaryTableWizard Oracle.
 */
public class CreateTableWizard extends PgWizard {

  private final TextField textName;
  private final JComboBox<String> comboKind;
  private final JComboBox<String> comboOnCommit;
  private final TextField textPartition;
  private final TextField textComment;
  private final ColumnGridPanel columns;
  // ORBADA 1.3.4: IF NOT EXISTS - bez bledu, gdy obiekt juz istnieje
  private final JCheckBox checkIfNotExists;

  public CreateTableWizard(Database database, String schemaName) {
    super(database, schemaName);
    info("schema-name", this.schemaName);
    textName = text("table-name", "");
    comboKind = combo("table-kind", false,
      stringManager.getString("table-kind-normal"), "UNLOGGED", stringManager.getString("table-kind-temporary"));
    comboOnCommit = combo("on-commit", false, "PRESERVE ROWS", "DELETE ROWS", "DROP");
    comboOnCommit.setEnabled(false);
    comboKind.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        comboOnCommit.setEnabled(isTemporary());
      }
    });
    textPartition = atLeast(10, 0) ? text("partition-by", "") : null;
    textComment = text("comment", "");
    checkIfNotExists = atLeast(9, 1) ? check("if-not-exists") : null;
    columns = row(null, new ColumnGridPanel(stringManager, PostgreSQLDbInfoProvider.instance.getDataTypes(database),
      atLeast(10, 0), atLeast(12, 0), atLeast(18, 0)), true);
    columns.addColumn("id", "bigint", true, true, "", atLeast(10, 0) ? "GENERATED ALWAYS AS IDENTITY" : "", "");
  }

  public ColumnGridPanel getColumns() {
    return columns;
  }

  public void setTable(String name, int kind) {
    textName.setText(name);
    comboKind.setSelectedIndex(kind);
  }

  private boolean isTemporary() {
    return comboKind.getSelectedIndex() == 2;
  }

  private String tableName() {
    // tabela tymczasowa jest zawsze w schemacie pg_temp
    return isTemporary() ? ident(value(textName)) : name(schemaName, value(textName));
  }

  @Override
  protected void build(List<String> sql) {
    require(value(textName).length() > 0, "wizard-name-required");
    List<String> definitions = columns.definitions();
    require(!definitions.isEmpty(), "wizard-columns-required");
    result("object_name", value(textName));
    StringBuilder sb = new StringBuilder("CREATE ");
    if (comboKind.getSelectedIndex() == 1) {
      sb.append("UNLOGGED ");
    } else if (isTemporary()) {
      sb.append("TEMPORARY ");
    }
    sb.append("TABLE ").append(checkIfNotExists != null && checkIfNotExists.isSelected() ? "IF NOT EXISTS " : "").append(tableName()).append(" (");
    String separator = "\n  ";
    for (String column : definitions) {
      sb.append(separator).append(column);
      separator = ",\n  ";
    }
    String pk = columns.primaryKey();
    if (pk.length() > 0) {
      sb.append(separator).append("CONSTRAINT ").append(ident(value(textName) + "_pkey")).append(" PRIMARY KEY (").append(pk).append(")");
    }
    sb.append("\n)");
    if (textPartition != null && value(textPartition).length() > 0) {
      sb.append("\nPARTITION BY ").append(value(textPartition));
    }
    if (isTemporary()) {
      sb.append("\nON COMMIT ").append(value(comboOnCommit));
    }
    sql.add(sb.toString());
    if (value(textComment).length() > 0) {
      sql.add("COMMENT ON TABLE " + tableName() + " IS " + literal(value(textComment)));
    }
    columns.comments(tableName(), sql);
  }

}
