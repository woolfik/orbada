package pl.mpak.orbada.postgresql.gui.wizards;

import java.util.List;
import javax.swing.JComboBox;
import pl.mpak.usedb.core.Database;

/**
 * ORBADA 1.3.2: parametr sesji (SET) - odpowiednik kreatorow parametrow Oracle (ALTER SESSION).
 * search_path - lista schematow (nazwy w cudzyslowach gdy trzeba), pozostale wartosci jako literal.
 * Bez autocommit SET jest czescia transakcji (ROLLBACK go cofa).
 */
public class SetParameterWizard extends PgWizard {

  private final String parameter;
  private final JComboBox<String> comboValue;

  public SetParameterWizard(Database database, String titleKey, String parameter, boolean editable, String... values) {
    super(database, null);
    this.titleKey = titleKey;
    this.parameter = parameter;
    // parametr modulu (plpgsql.*) moze nie byc jeszcze zarejestrowany - wtedy pusty
    String[] current = list(atLeast(9, 6) ? "select coalesce(current_setting(:NAME, true), '')" : "select current_setting(:NAME)", parameter);
    info("parameter-name", parameter);
    info("current-value", current.length > 0 ? current[0] : "");
    comboValue = combo("new-value", editable, "search_path".equals(parameter) ? schemas() : values);
    comboValue.setSelectedItem(current.length > 0 ? current[0] : null);
    if (!database.isAutoCommit()) {
      note("set-in-transaction-hint");
    }
  }

  @Override
  protected void build(List<String> sql) {
    String v = value(comboValue);
    if ("DEFAULT".equalsIgnoreCase(v)) {
      sql.add("SET " + parameter + " TO DEFAULT");
    } else if ("search_path".equals(parameter)) {
      StringBuilder sb = new StringBuilder();
      for (String s : v.split(",")) {
        s = s.trim();
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() > 1) {
          s = s.substring(1, s.length() - 1).replace("\"\"", "\"");
        }
        if (s.length() > 0) {
          // "$user" to nazwa specjalna, nie identyfikator
          sb.append(sb.length() > 0 ? ", " : "").append("$user".equals(s) ? "\"$user\"" : ident(s));
        }
      }
      sql.add("SET search_path TO " + sb);
    } else {
      sql.add("SET " + parameter + " TO " + literal(v));
    }
  }

}
