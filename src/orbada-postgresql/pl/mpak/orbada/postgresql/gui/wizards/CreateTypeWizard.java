package pl.mpak.orbada.postgresql.gui.wizards;

import java.util.List;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JTextArea;
import pl.mpak.orbada.postgresql.services.PostgreSQLDbInfoProvider;
import pl.mpak.sky.gui.swing.comp.TextField;
import pl.mpak.usedb.core.Database;

/**
 * ORBADA 1.3.2: typ zlozony (CREATE TYPE ... AS (...)), wyliczeniowy (AS ENUM), domena (CREATE DOMAIN) i zakres
 * (AS RANGE) - odpowiednik CreateObjectTypeWizard, CreateTableTypeWizard i CreateVarrayTypeWizard Oracle.
 */
public class CreateTypeWizard extends PgWizard {

  public static final int COMPOSITE = 0;
  public static final int ENUM = 1;
  public static final int DOMAIN = 2;
  public static final int RANGE = 3;
  private static final String[] TITLES = {
    "CreateCompositeTypeWizard-title", "CreateEnumTypeWizard-title", "CreateDomainWizard-title", "CreateRangeTypeWizard-title"};

  private final int kind;
  private final TextField textName;
  private final JTextArea textItems;
  private final JComboBox<String> comboBaseType;
  private final TextField textDefault;
  private final JCheckBox checkNotNull;
  private final TextField textCheck;
  private final TextField textComment;

  public CreateTypeWizard(Database database, String schemaName, int kind) {
    super(database, schemaName);
    this.kind = kind;
    this.titleKey = TITLES[kind];
    info("schema-name", this.schemaName);
    textName = text(kind == DOMAIN ? "domain-name" : "type-name", "");
    if (kind == DOMAIN || kind == RANGE) {
      comboBaseType = combo(kind == DOMAIN ? "base-type" : "range-subtype", true, PostgreSQLDbInfoProvider.instance.getDataTypes(database));
      comboBaseType.setSelectedItem(kind == DOMAIN ? "text" : "numeric");
    } else {
      comboBaseType = null;
    }
    if (kind == DOMAIN) {
      textDefault = text("default-value", "");
      checkNotNull = check("not-null");
      textCheck = text("check-condition", "");
      note("domain-check-hint");
    } else {
      textDefault = null;
      checkNotNull = null;
      textCheck = null;
    }
    textComment = text("comment", "");
    if (kind == COMPOSITE) {
      textItems = area("type-attributes", "id integer\nname text");
      note("type-attributes-hint");
    } else if (kind == ENUM) {
      textItems = area("enum-values", "");
      note("enum-values-hint");
    } else {
      textItems = null;
    }
  }

  public void setItems(String name, String items) {
    textName.setText(name);
    if (textItems != null) {
      textItems.setText(items);
    }
  }

  public void setDomain(String baseType, String defaultValue, boolean notNull, String check) {
    comboBaseType.setSelectedItem(baseType);
    textDefault.setText(defaultValue);
    checkNotNull.setSelected(notNull);
    textCheck.setText(check);
  }

  @Override
  protected void build(List<String> sql) {
    require(value(textName).length() > 0, "wizard-name-required");
    result("object_name", value(textName));
    String type = name(schemaName, value(textName));
    if (kind == COMPOSITE) {
      StringBuilder sb = new StringBuilder();
      for (String line : textItems.getText().split("\n")) {
        line = line.trim();
        if (line.endsWith(",")) {
          line = line.substring(0, line.length() - 1).trim();
        }
        if (line.length() > 0) {
          // pierwsze slowo to nazwa atrybutu, reszta typ
          String[] parts = line.split("\\s+", 2);
          sb.append(sb.length() > 0 ? ",\n  " : "\n  ").append(ident(parts[0])).append(" ").append(parts.length > 1 ? parts[1] : "text");
        }
      }
      require(sb.length() > 0, "wizard-items-required");
      sql.add("CREATE TYPE " + type + " AS (" + sb + "\n)");
    } else if (kind == ENUM) {
      StringBuilder sb = new StringBuilder();
      for (String line : textItems.getText().split("\n")) {
        if (line.trim().length() > 0) {
          sb.append(sb.length() > 0 ? ", " : "").append(literal(line.trim()));
        }
      }
      require(sb.length() > 0, "wizard-items-required");
      sql.add("CREATE TYPE " + type + " AS ENUM (" + sb + ")");
    } else if (kind == DOMAIN) {
      sql.add("CREATE DOMAIN " + type + " AS " + value(comboBaseType) +
        (value(textDefault).length() > 0 ? "\n  DEFAULT " + value(textDefault) : "") +
        (checkNotNull.isSelected() ? "\n  NOT NULL" : "") +
        (value(textCheck).length() > 0 ? "\n  CHECK (" + value(textCheck) + ")" : ""));
    } else {
      sql.add("CREATE TYPE " + type + " AS RANGE (SUBTYPE = " + value(comboBaseType) + ")");
    }
    if (value(textComment).length() > 0) {
      sql.add("COMMENT ON " + (kind == DOMAIN ? "DOMAIN " : "TYPE ") + type + " IS " + literal(value(textComment)));
    }
  }

}
