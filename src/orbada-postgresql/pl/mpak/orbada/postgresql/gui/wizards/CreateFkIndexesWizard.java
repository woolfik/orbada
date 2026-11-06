package pl.mpak.orbada.postgresql.gui.wizards;

import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import pl.mpak.usedb.core.Database;
import pl.mpak.usedb.core.Query;
import pl.mpak.util.ExceptionUtil;

/**
 * ORBADA 1.3.2: indeksy dla kluczy obcych tabeli, ktore nie maja indeksu zaczynajacego sie od ich kolumn
 * (PostgreSQL nie tworzy ich sam) - odpowiednik CreateIndexesFromConstraintsWizard Oracle.
 */
public class CreateFkIndexesWizard extends PgWizard {

  private final String tableName;
  private final List<JCheckBox> checks = new ArrayList<JCheckBox>();
  private final List<String> statements = new ArrayList<String>();

  public CreateFkIndexesWizard(Database database, String schemaName, String table) {
    super(database, schemaName);
    tableName = name(this.schemaName, table);
    info("table-name", tableName);
    JPanel panel = new JPanel(new GridLayout(0, 1));
    Query query = database.createQuery();
    try {
      // indeks pasuje, gdy jego pierwsze kolumny to kolumny klucza (w dowolnej kolejnosci)
      query.setSqlText(
        "select c.conname, (select string_agg(quote_ident(a.attname), ', ' order by k.n)\n" +
        "                     from unnest(c.conkey) with ordinality k(attnum, n) join pg_catalog.pg_attribute a on a.attrelid = c.conrelid and a.attnum = k.attnum) columns\n" +
        "  from pg_catalog.pg_constraint c\n" +
        " where c.contype = 'f' and c.conrelid = (:TABLE_NAME)::regclass\n" +
        "   and not exists (select null from pg_catalog.pg_index i\n" +
        "                    where i.indrelid = c.conrelid\n" +
        "                      and (string_to_array(i.indkey::text, ' ')::int2[])[1 : array_length(c.conkey, 1)] @> c.conkey\n" +
        "                      and (string_to_array(i.indkey::text, ' ')::int2[])[1 : array_length(c.conkey, 1)] <@ c.conkey)\n" +
        " order by 1");
      query.paramByName("TABLE_NAME").setString(tableName);
      query.open();
      while (!query.eof()) {
        String constraint = query.fieldByName("conname").getString();
        String columns = query.fieldByName("columns").getString();
        String index = constraint.endsWith("_fkey") ? constraint.substring(0, constraint.length() - 5) + "_idx" : constraint + "_idx";
        JCheckBox check = new JCheckBox(constraint + " (" + columns + ")", true);
        checks.add(check);
        statements.add("CREATE INDEX " + ident(index) + " ON " + tableName + " (" + columns + ")");
        panel.add(check);
        query.next();
      }
    } catch (Exception ex) {
      ExceptionUtil.processException(ex);
    } finally {
      query.close();
    }
    if (checks.isEmpty()) {
      panel.add(new JLabel(stringManager.getString("no-fk-without-index")));
    }
    row("foreign-keys", panel);
  }

  @Override
  protected void build(List<String> sql) {
    for (int i = 0; i < checks.size(); i++) {
      if (checks.get(i).isSelected()) {
        sql.add(statements.get(i));
      }
    }
  }

}
