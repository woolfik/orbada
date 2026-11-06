package pl.mpak.orbada.postgresql.gui.wizards;

import java.util.ArrayList;
import java.util.List;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import pl.mpak.orbada.postgresql.services.PostgreSQLDbInfoProvider;
import pl.mpak.sky.gui.swing.comp.TextField;
import pl.mpak.usedb.core.Database;
import pl.mpak.usedb.core.Query;
import pl.mpak.util.ExceptionUtil;

/**
 * ORBADA 1.3.2: zmiana kolumny tabeli - typ (z USING), NOT NULL, DEFAULT, IDENTITY. W poleceniu tylko zmienione
 * wlasciwosci (jedno ALTER TABLE z kilkoma ALTER COLUMN). Odpowiednik AlterTableNullWizardPanel i zmian kolumn Oracle.
 */
public class AlterColumnWizard extends PgWizard {

  private static final String[] IDENTITY = {"", "ALWAYS", "BY DEFAULT"};

  private final String tableName;
  private final String column;
  private String oldType = "";
  private boolean oldNotNull;
  private String oldDefault = "";
  private int oldIdentity;
  private boolean generated;
  private final JComboBox<String> comboType;
  private final TextField textUsing;
  private final JCheckBox checkNotNull;
  private final TextField textDefault;
  private final JComboBox<String> comboIdentity;

  public AlterColumnWizard(Database database, String schemaName, String table, String column) {
    super(database, schemaName);
    this.tableName = name(this.schemaName, table);
    this.column = column;
    load();
    info("table-name", tableName);
    info("column-name", column);
    comboType = combo("data-type", true, PostgreSQLDbInfoProvider.instance.getDataTypes(database));
    comboType.setSelectedItem(oldType);
    textUsing = text("using-expression", "");
    checkNotNull = check("not-null");
    checkNotNull.setSelected(oldNotNull);
    textDefault = text("default-value", oldDefault);
    comboIdentity = atLeast(10, 0) ? combo("identity", false, IDENTITY) : null;
    if (comboIdentity != null) {
      comboIdentity.setSelectedIndex(oldIdentity);
    }
    if (generated) {
      // kolumna GENERATED ... AS (wyrazenie) - DEFAULT i IDENTITY nie dotycza
      textDefault.setEnabled(false);
      if (comboIdentity != null) {
        comboIdentity.setEnabled(false);
      }
    }
  }

  private void load() {
    Query query = database.createQuery();
    try {
      query.setSqlText(
        "select pg_catalog.format_type(a.atttypid, a.atttypmod) data_type, case when a.attnotnull then 'Y' else 'N' end not_null,\n" +
        "       coalesce(pg_catalog.pg_get_expr(d.adbin, d.adrelid), '') default_value" +
        (atLeast(10, 0) ? ", a.attidentity::text attidentity" : ", '' attidentity") +
        (atLeast(12, 0) ? ", a.attgenerated::text attgenerated" : ", '' attgenerated") + "\n" +
        "  from pg_catalog.pg_attribute a left join pg_catalog.pg_attrdef d on d.adrelid = a.attrelid and d.adnum = a.attnum\n" +
        " where a.attrelid = (:TABLE_NAME)::regclass and a.attname = :COLUMN_NAME");
      query.paramByName("TABLE_NAME").setString(tableName);
      query.paramByName("COLUMN_NAME").setString(column);
      query.open();
      if (!query.eof()) {
        oldType = query.fieldByName("data_type").getString();
        oldNotNull = "Y".equals(query.fieldByName("not_null").getString());
        String identity = query.fieldByName("attidentity").getString();
        oldIdentity = "a".equals(identity) ? 1 : "d".equals(identity) ? 2 : 0;
        generated = query.fieldByName("attgenerated").getString().length() > 0;
        if (oldIdentity == 0 && !generated) {
          oldDefault = query.fieldByName("default_value").getString();
        }
      }
    } catch (Exception ex) {
      ExceptionUtil.processException(ex);
    } finally {
      query.close();
    }
  }

  @Override
  protected void build(List<String> sql) {
    List<String> changes = new ArrayList<String>();
    String col = "ALTER COLUMN " + ident(column);
    if (!value(comboType).equals(oldType) || value(textUsing).length() > 0) {
      changes.add(col + " TYPE " + value(comboType) + (value(textUsing).length() > 0 ? " USING " + value(textUsing) : ""));
    }
    int identity = comboIdentity == null ? 0 : comboIdentity.getSelectedIndex();
    // DEFAULT usuwany przed dodaniem IDENTITY (kolumna z DEFAULT nie moze byc IDENTITY)
    String defaultValue = identity != 0 ? "" : value(textDefault);
    if (!defaultValue.equals(oldDefault)) {
      changes.add(col + (defaultValue.length() == 0 ? " DROP DEFAULT" : " SET DEFAULT " + defaultValue));
    }
    // IDENTITY wymaga NOT NULL przed ADD GENERATED
    boolean notNull = checkNotNull.isSelected() || identity != 0;
    if (notNull != oldNotNull) {
      changes.add(col + (notNull ? " SET NOT NULL" : " DROP NOT NULL"));
    }
    if (identity != oldIdentity) {
      if (identity == 0) {
        changes.add(col + " DROP IDENTITY");
      } else if (oldIdentity == 0) {
        changes.add(col + " ADD GENERATED " + IDENTITY[identity] + " AS IDENTITY");
      } else {
        changes.add(col + " SET GENERATED " + IDENTITY[identity]);
      }
    }
    if (changes.isEmpty()) {
      return;
    }
    StringBuilder sb = new StringBuilder("ALTER TABLE " + tableName);
    String separator = "\n  ";
    for (String c : changes) {
      sb.append(separator).append(c);
      separator = ",\n  ";
    }
    sql.add(sb.toString());
  }

}
