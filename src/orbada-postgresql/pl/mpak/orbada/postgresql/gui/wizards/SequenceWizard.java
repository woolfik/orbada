package pl.mpak.orbada.postgresql.gui.wizards;

import java.util.ArrayList;
import java.util.List;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import pl.mpak.sky.gui.swing.comp.TextField;
import pl.mpak.usedb.core.Database;
import pl.mpak.usedb.core.Query;
import pl.mpak.util.ExceptionUtil;

/**
 * ORBADA 1.3.2: utworzenie (CREATE SEQUENCE) albo zmiana sekwencji (ALTER SEQUENCE - tylko zmienione opcje, RESTART)
 * - odpowiednik CreateSequenceWizard, AlterSequenceWizard i RecreateSequenceWizard Oracle.
 */
public class SequenceWizard extends PgWizard {

  private final String sequence;
  private final TextField textName;
  private final JComboBox<String> comboType;
  private final TextField textStart;
  private final TextField textIncrement;
  private final TextField textMin;
  private final TextField textMax;
  private final TextField textCache;
  private final JCheckBox checkCycle;
  private final JCheckBox checkRestart;
  private final TextField textRestart;
  private final TextField textOwnedBy;
  // ORBADA 1.3.4: IF NOT EXISTS - bez bledu, gdy obiekt juz istnieje
  private final JCheckBox checkIfNotExists;
  /** Wartosci przed zmiana: typ, start, przyrost, min, max, cache, cykl, owned by */
  private final String[] old = {"", "", "", "", "", "", "N", ""};

  /** sequence: sekwencja w SQL do zmiany albo null - nowa w schemacie schemaName */
  public SequenceWizard(Database database, String schemaName, String sequence) {
    super(database, schemaName);
    this.sequence = sequence;
    this.titleKey = sequence == null ? "CreateSequenceWizard-title" : "AlterSequenceWizard-title";
    if (sequence != null) {
      load();
      info("sequence-name", sequence);
      textName = null;
    } else {
      info("schema-name", this.schemaName);
      textName = text("sequence-name", "");
    }
    comboType = atLeast(10, 0) ? combo("data-type", false, "bigint", "integer", "smallint") : null;
    if (comboType != null && old[0].length() > 0) {
      comboType.setSelectedItem(old[0]);
    }
    textStart = text("start-with", sequence == null ? "1" : old[1]);
    textIncrement = text("increment-by", sequence == null ? "1" : old[2]);
    textMin = text("minimum-value", old[3]);
    textMax = text("maximum-value", old[4]);
    textCache = text("cache-size", sequence == null ? "1" : old[5]);
    checkCycle = check("cycle");
    checkCycle.setSelected("Y".equals(old[6]));
    if (sequence != null) {
      checkRestart = check("restart-sequence");
      textRestart = text("restart-with", "");
    } else {
      checkRestart = null;
      textRestart = null;
    }
    textOwnedBy = text("owned-by", old[7]);
    checkIfNotExists = sequence == null && atLeast(9, 5) ? check("if-not-exists") : null;
    note("owned-by-hint");
  }

  private void load() {
    if (!atLeast(10, 0)) {
      return;
    }
    Query query = database.createQuery();
    try {
      query.setSqlText(
        "select pg_catalog.format_type(s.seqtypid, null) data_type, s.seqstart::text, s.seqincrement::text, s.seqmin::text, s.seqmax::text,\n" +
        "       s.seqcache::text, case when s.seqcycle then 'Y' else 'N' end, \n" +
        "       coalesce((select quote_ident(n.nspname)||'.'||quote_ident(c.relname)||'.'||quote_ident(a.attname) from pg_catalog.pg_depend d\n" +
        "                   join pg_catalog.pg_class c on c.oid = d.refobjid join pg_catalog.pg_namespace n on n.oid = c.relnamespace\n" +
        "                   join pg_catalog.pg_attribute a on a.attrelid = d.refobjid and a.attnum = d.refobjsubid\n" +
        "                  where d.objid = s.seqrelid and d.classid = 'pg_class'::regclass and d.refclassid = 'pg_class'::regclass and d.deptype in ('a', 'i')), '')\n" +
        "  from pg_catalog.pg_sequence s where s.seqrelid = (:NAME)::regclass");
      query.paramByName("NAME").setString(sequence);
      query.open();
      if (!query.eof()) {
        for (int i = 0; i < old.length; i++) {
          old[i] = query.getField(i).getString();
        }
      }
    } catch (Exception ex) {
      ExceptionUtil.processException(ex);
    } finally {
      query.close();
    }
  }

  private String ownedBy() {
    String v = value(textOwnedBy);
    if (v.length() == 0 || "NONE".equalsIgnoreCase(v)) {
      return "NONE";
    }
    // tabela.kolumna albo schemat.tabela.kolumna - nazwy w cudzyslowach zostaja bez zmian
    return v.indexOf('"') >= 0 ? v : identList(v.replace('.', ',')).replace(", ", ".");
  }

  @Override
  protected void build(List<String> sql) {
    List<String> options = new ArrayList<String>();
    if (comboType != null && (sequence == null || !value(comboType).equals(old[0]))) {
      options.add("AS " + value(comboType));
    }
    option(options, "START WITH ", value(textStart), old[1], null);
    option(options, "INCREMENT BY ", value(textIncrement), old[2], null);
    option(options, "MINVALUE ", value(textMin), old[3], "NO MINVALUE");
    option(options, "MAXVALUE ", value(textMax), old[4], "NO MAXVALUE");
    option(options, "CACHE ", value(textCache), old[5], null);
    if (sequence == null ? checkCycle.isSelected() : checkCycle.isSelected() != "Y".equals(old[6])) {
      options.add(checkCycle.isSelected() ? "CYCLE" : "NO CYCLE");
    }
    if (checkRestart != null && checkRestart.isSelected()) {
      options.add("RESTART" + (value(textRestart).length() > 0 ? " WITH " + value(textRestart) : ""));
    }
    if (sequence == null ? value(textOwnedBy).length() > 0 : !value(textOwnedBy).equals(old[7])) {
      options.add("OWNED BY " + ownedBy());
    }
    if (sequence == null) {
      require(value(textName).length() > 0, "wizard-name-required");
      result("object_name", value(textName));
      sql.add("CREATE SEQUENCE " + (checkIfNotExists != null && checkIfNotExists.isSelected() ? "IF NOT EXISTS " : "") +
        name(schemaName, value(textName)) + join(options));
    } else if (!options.isEmpty()) {
      sql.add("ALTER SEQUENCE " + sequence + join(options));
    }
  }

  private void option(List<String> options, String keyword, String value, String old, String none) {
    if (sequence == null) {
      if (value.length() > 0) {
        options.add(keyword + value);
      }
    } else if (!value.equals(old)) {
      if (value.length() > 0) {
        options.add(keyword + value);
      } else if (none != null) {
        options.add(none);
      }
    }
  }

  private static String join(List<String> options) {
    StringBuilder sb = new StringBuilder();
    for (String o : options) {
      sb.append("\n  ").append(o);
    }
    return sb.toString();
  }

}
