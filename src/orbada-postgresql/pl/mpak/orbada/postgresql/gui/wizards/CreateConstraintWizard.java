package pl.mpak.orbada.postgresql.gui.wizards;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import pl.mpak.sky.gui.swing.comp.TextField;
import pl.mpak.usedb.core.Database;

/**
 * ORBADA 1.3.2: ograniczenie tabeli - PRIMARY KEY, UNIQUE, FOREIGN KEY (ON DELETE/UPDATE), CHECK; DEFERRABLE, NOT VALID.
 */
public class CreateConstraintWizard extends PgWizard {

  private static final String[] TYPES = {"PRIMARY KEY", "UNIQUE", "FOREIGN KEY", "CHECK"};
  private static final String[] RULES = {"NO ACTION", "RESTRICT", "CASCADE", "SET NULL", "SET DEFAULT"};
  private static final String[] DEFERRABLE = {"NOT DEFERRABLE", "DEFERRABLE INITIALLY IMMEDIATE", "DEFERRABLE INITIALLY DEFERRED"};
  private static final String[] SUFFIX = {"_pkey", "_key", "_fkey", "_check"};

  private final String table;
  private final String tableName;
  private final JComboBox<String> comboType;
  private final TextField textName;
  private final TextField textColumns;
  private final JComboBox<String> comboRefTable;
  private final TextField textRefColumns;
  private final JComboBox<String> comboOnDelete;
  private final JComboBox<String> comboOnUpdate;
  private final TextField textCheck;
  private final JComboBox<String> comboDeferrable;
  private final JCheckBox checkNotValid;

  public CreateConstraintWizard(Database database, String schemaName, String table) {
    super(database, schemaName);
    this.table = table;
    this.tableName = name(this.schemaName, table);
    info("table-name", tableName);
    comboType = combo("constraint-type", false, TYPES);
    textName = text("constraint-name", table + SUFFIX[0]);
    textColumns = text("constraint-columns", "");
    comboRefTable = combo("referenced-table", true, list(
      "select quote_ident(n.nspname)||'.'||quote_ident(c.relname) from pg_catalog.pg_class c join pg_catalog.pg_namespace n on n.oid = c.relnamespace" +
      " where c.relkind in ('r', 'p') and n.nspname not like 'pg\\_%' and n.nspname <> 'information_schema'" +
      " order by n.nspname <> :SCHEMA_NAME, 1", this.schemaName));
    textRefColumns = text("referenced-columns", "");
    comboOnDelete = combo("on-delete", false, RULES);
    comboOnUpdate = combo("on-update", false, RULES);
    textCheck = text("check-condition", "");
    comboDeferrable = combo("deferrable", false, DEFERRABLE);
    checkNotValid = check("not-valid");
    comboType.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        String name = value(textName);
        for (String s : SUFFIX) {
          if (name.equals(CreateConstraintWizard.this.table + s)) {
            textName.setText(CreateConstraintWizard.this.table + SUFFIX[comboType.getSelectedIndex()]);
          }
        }
        updateFields();
      }
    });
    updateFields();
  }

  public void setConstraint(int type, String name, String columns, String refTable, String refColumns, String check) {
    comboType.setSelectedIndex(type);
    textName.setText(name);
    textColumns.setText(columns);
    comboRefTable.setSelectedItem(refTable);
    textRefColumns.setText(refColumns);
    textCheck.setText(check);
  }

  private void updateFields() {
    int type = comboType.getSelectedIndex();
    textColumns.setEnabled(type != 3);
    comboRefTable.setEnabled(type == 2);
    textRefColumns.setEnabled(type == 2);
    comboOnDelete.setEnabled(type == 2);
    comboOnUpdate.setEnabled(type == 2);
    textCheck.setEnabled(type == 3);
    comboDeferrable.setEnabled(type != 3);
    // NOT VALID tylko dla FOREIGN KEY i CHECK
    checkNotValid.setEnabled(type >= 2);
  }

  @Override
  protected void build(List<String> sql) {
    int type = comboType.getSelectedIndex();
    require(type == 3 || value(textColumns).length() > 0, "wizard-columns-required");
    require(type != 3 || value(textCheck).length() > 0, "wizard-condition-required");
    require(type != 2 || value(comboRefTable).length() > 0, "wizard-ref-table-required");
    StringBuilder sb = new StringBuilder("ALTER TABLE " + tableName + "\n  ADD ");
    if (value(textName).length() > 0) {
      sb.append("CONSTRAINT ").append(ident(value(textName))).append(" ");
    }
    sb.append(TYPES[type]);
    if (type == 3) {
      sb.append(" (").append(value(textCheck)).append(")");
    } else {
      sb.append(" (").append(identList(value(textColumns))).append(")");
    }
    if (type == 2) {
      sb.append("\n  REFERENCES ").append(value(comboRefTable));
      if (value(textRefColumns).length() > 0) {
        sb.append(" (").append(identList(value(textRefColumns))).append(")");
      }
      if (comboOnDelete.getSelectedIndex() > 0) {
        sb.append(" ON DELETE ").append(value(comboOnDelete));
      }
      if (comboOnUpdate.getSelectedIndex() > 0) {
        sb.append(" ON UPDATE ").append(value(comboOnUpdate));
      }
    }
    if (type != 3 && comboDeferrable.getSelectedIndex() > 0) {
      sb.append(" ").append(value(comboDeferrable));
    }
    if (type >= 2 && checkNotValid.isSelected()) {
      sb.append(" NOT VALID");
    }
    sql.add(sb.toString());
  }

}
