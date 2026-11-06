package pl.mpak.orbada.postgresql.gui.wizards;

import java.util.List;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import pl.mpak.sky.gui.swing.comp.TextField;
import pl.mpak.usedb.core.Database;

/**
 * ORBADA 1.3.2: nowa wartosc typu wyliczeniowego (ALTER TYPE ... ADD VALUE [BEFORE|AFTER]). Do PostgreSQL 12 polecenie
 * nie dziala w bloku transakcji, a nowej wartosci nie mozna uzyc przed zatwierdzeniem.
 */
public class AddEnumValueWizard extends PgWizard {

  private final String type;
  private final TextField textValue;
  private final JComboBox<String> comboPosition;
  private final JComboBox<String> comboNeighbor;
  private final JCheckBox checkIfNotExists;

  public AddEnumValueWizard(Database database, String type) {
    super(database, null);
    this.type = type;
    info("type-name", type);
    textValue = text("enum-value", "");
    comboPosition = combo("enum-position", false, stringManager.getString("enum-position-end"), "BEFORE", "AFTER");
    comboNeighbor = combo("enum-neighbor", false, list(
      "select e.enumlabel from pg_catalog.pg_enum e where e.enumtypid = (:NAME)::regtype order by e.enumsortorder", type));
    checkIfNotExists = check("if-not-exists");
    if (!atLeast(12, 0) && !database.isAutoCommit()) {
      note("enum-add-autocommit-hint");
    }
  }

  public void setValue(String value, int position, String neighbor) {
    textValue.setText(value);
    comboPosition.setSelectedIndex(position);
    comboNeighbor.setSelectedItem(neighbor);
  }

  @Override
  protected void build(List<String> sql) {
    require(value(textValue).length() > 0, "wizard-name-required");
    require(comboPosition.getSelectedIndex() == 0 || value(comboNeighbor).length() > 0, "wizard-neighbor-required");
    sql.add("ALTER TYPE " + type + " ADD VALUE " + (checkIfNotExists.isSelected() ? "IF NOT EXISTS " : "") + literal(value(textValue)) +
      (comboPosition.getSelectedIndex() > 0 ? " " + value(comboPosition) + " " + literal(value(comboNeighbor)) : ""));
  }

}
