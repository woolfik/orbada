package pl.mpak.orbada.oracle.gui.scheduler;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;
import java.util.regex.Pattern;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import pl.mpak.orbada.oracle.OrbadaOraclePlugin;
import pl.mpak.usedb.core.Database;
import pl.mpak.usedb.core.Query;
import pl.mpak.usedb.util.SQLUtil;
import pl.mpak.util.ExceptionUtil;
import pl.mpak.util.StringManager;
import pl.mpak.util.StringManagerFactory;

/**
 * ORBADA Fuerteventura (F5): kreator obiektu Oracle Scheduler - strony formularza, z ktorych powstaje
 * blok PL/SQL z wywolaniami DBMS_SCHEDULER (pokazywany przed wykonaniem na stronie "Kod PL/SQL").
 */
abstract class SchedulerWizard {

  final static StringManager stringManager = StringManagerFactory.getStringManager(OrbadaOraclePlugin.class);

  /** Prosta nazwa Oracle - zamieniana na wielkie litery jak przez sam DBMS_SCHEDULER */
  private final static Pattern simpleName = Pattern.compile("[A-Za-z][A-Za-z0-9_$#]*");
  /** Strefa jak "Europe/Warsaw" - Oracle zna nazwy regionow IANA */
  private final static Pattern regionName = Pattern.compile("[A-Za-z_]+(/[A-Za-z0-9_+-]+)+|UTC|GMT");

  /** Strona kreatora */
  static class Page {
    final String titleKey;
    final JComponent component;
    Page(String titleKey, JComponent component) {
      this.titleKey = titleKey;
      this.component = component;
    }
    /** @return komunikat bledu (strona nie zostanie opuszczona do przodu) albo null */
    String validate() {
      return null;
    }
    /** Wywolywane przy kazdym pokazaniu strony */
    void shown() {
    }
  }

  protected final Database database;
  protected final String schema;
  private final List<Page> pages = new ArrayList<Page>();

  SchedulerWizard(Database database, String schema) {
    this.database = database;
    this.schema = schema;
  }

  /** Kreator dla zakladki widoku (SchedulerSpec.id) albo null, gdy zakladka go nie ma */
  static SchedulerWizard create(String specId, Database database, String schema) {
    if ("jobs".equals(specId)) {
      return new SchedulerJobWizard(database, schema);
    } else if ("programs".equals(specId)) {
      return new SchedulerProgramWizard(database, schema);
    } else if ("schedules".equals(specId)) {
      return new SchedulerScheduleWizard(database, schema);
    } else if ("chains".equals(specId)) {
      return new SchedulerChainWizard(database, schema);
    }
    return null;
  }

  static boolean supports(String specId) {
    return "jobs".equals(specId) || "programs".equals(specId) || "schedules".equals(specId) || "chains".equals(specId);
  }

  abstract String getTitleKey();

  /** Nazwa tworzonego obiektu tak, jak zapisze ja slownik (do zaznaczenia na liscie po utworzeniu) */
  abstract String getObjectName();

  abstract String buildPlsql();

  /** Okno kreatora zamkniete */
  void dispose() {
  }

  final void addPage(Page page) {
    pages.add(page);
  }

  final List<Page> getPages() {
    return pages;
  }

  final Database getDatabase() {
    return database;
  }

  /** Pokazuje kreator (modalnie); true, gdy obiekt utworzono */
  final boolean show(Component parent) {
    SchedulerWizardDialog dialog = new SchedulerWizardDialog(parent, this);
    dialog.setVisible(true);
    return dialog.isCreated();
  }

  // --- teksty

  static String text(String key) {
    return stringManager.getString(key);
  }

  static String text(String key, Object... args) {
    return String.format(stringManager.getString(key), args);
  }

  // --- nazwy i literaly PL/SQL

  /** Nazwa tak, jak zapisze ja Oracle: prosta - wielkimi literami, inna - bez zmian (zostanie ujeta w cudzyslow) */
  static String ident(String name) {
    name = name == null ? "" : name.trim();
    return simpleName.matcher(name).matches() ? name.toUpperCase() : name;
  }

  /** @return komunikat bledu dla nazwy obiektu albo null */
  static String checkName(String name, String fieldKey) {
    name = name == null ? "" : name.trim();
    if (name.length() == 0) {
      return text("SchedWiz-err-required", text(fieldKey));
    }
    if (name.indexOf('"') >= 0 || name.indexOf('.') >= 0 || name.indexOf('\'') >= 0 || name.length() > 128) {
      return text("SchedWiz-err-name", text(fieldKey));
    }
    return null;
  }

  /** Pelna nazwa obiektu w schemacie kreatora, np. ORBADA.MOJ_JOB */
  String qualified(String name) {
    return SQLUtil.createSqlName(schema, ident(name));
  }

  /** Nazwa wybrana z listy lub wpisana: z kropka - bez zmian, bez kropki - w schemacie kreatora */
  String reference(String name) {
    name = name == null ? "" : name.trim();
    return name.indexOf('.') >= 0 ? name : qualified(name);
  }

  /** Literal tekstowy PL/SQL albo null */
  static String lit(String s) {
    if (s == null || s.length() == 0) {
      return "null";
    }
    return "'" + s.replace("'", "''") + "'";
  }

  /** Strefa czasowa uzytkownika dla dat w kodzie - jawna, bo bez niej scheduler bierze DEFAULT_TIMEZONE bazy */
  static String timeZone() {
    TimeZone tz = TimeZone.getDefault();
    if (regionName.matcher(tz.getID()).matches()) {
      return tz.getID();
    }
    int minutes = tz.getRawOffset() / 60000;
    return String.format("%s%02d:%02d", minutes < 0 ? "-" : "+", Math.abs(minutes) / 60, Math.abs(minutes) % 60);
  }

  /** Data "RRRR-MM-DD GG:MM" jako znacznik czasu ze strefa uzytkownika; pusta - teraz w tej strefie */
  static String timestampLit(String date) {
    if (date == null || date.trim().length() == 0) {
      return "systimestamp at time zone " + lit(timeZone());
    }
    return "to_timestamp_tz(" + lit(date.trim() + " " + timeZone()) + ", 'YYYY-MM-DD HH24:MI TZR')";
  }

  /**
   * Wywolanie procedury DBMS_SCHEDULER z parametrami nazwanymi (nazwy wyrownane), np.
   * call("create_job", "job_name", "'X'", "enabled", "false"); wartosc null - parametr pominiety.
   */
  static String call(String procedure, String... namesValues) {
    int width = 0;
    for (int i = 0; i < namesValues.length; i += 2) {
      if (namesValues[i + 1] != null) {
        width = Math.max(width, namesValues[i].length());
      }
    }
    StringBuilder sb = new StringBuilder("  dbms_scheduler.").append(procedure).append("(");
    boolean first = true;
    for (int i = 0; i < namesValues.length; i += 2) {
      if (namesValues[i + 1] == null) {
        continue;
      }
      sb.append(first ? "\n" : ",\n").append("    ").append(namesValues[i]);
      for (int n = namesValues[i].length(); n < width; n++) {
        sb.append(' ');
      }
      sb.append(" => ").append(namesValues[i + 1]);
      first = false;
    }
    return sb.append(");\n").toString();
  }

  // --- listy z bazy

  /** Procedury schematu kreatora: PROCEDURA albo PAKIET.PROCEDURA */
  List<String> procedures() {
    return list("select case when object_type = 'PACKAGE' then object_name || '.' || procedure_name else object_name end\n"
      + "  from all_procedures\n"
      + " where owner = :SCHEMA_NAME and (object_type = 'PROCEDURE' or object_type = 'PACKAGE' and procedure_name is not null)\n"
      + " group by object_type, object_name, procedure_name order by 1");
  }

  /**
   * Argumenty procedury (pierwsza wersja przeciazona): wiersze {nazwa, typ, "Y" gdy ma wartosc domyslna}.
   * Nazwa: PROC, PAKIET.PROC (w schemacie kreatora), WLASCICIEL.PROC albo WLASCICIEL.PAKIET.PROC.
   * @return null, gdy procedury nie znaleziono
   * @throws IllegalArgumentException funkcja albo argumenty OUT - scheduler ich nie obsluguje
   */
  List<String[]> procedureArguments(String procedure) {
    String[] parts = procedure.trim().split("\\.");
    for (int i = 0; i < parts.length; i++) {
      parts[i] = parts[i].startsWith("\"") && parts[i].endsWith("\"") && parts[i].length() > 1
        ? parts[i].substring(1, parts[i].length() - 1) : ident(parts[i]);
    }
    List<String[]> candidates = new ArrayList<String[]>(); // {owner, pakiet, nazwa}
    if (parts.length == 1) {
      candidates.add(new String[] {schema, " ", parts[0]});
    } else if (parts.length == 2) {
      candidates.add(new String[] {schema, parts[0], parts[1]});
      candidates.add(new String[] {parts[0], " ", parts[1]});
    } else if (parts.length == 3) {
      candidates.add(new String[] {parts[0], parts[1], parts[2]});
    }
    for (String[] c : candidates) {
      List<String[]> result = new ArrayList<String[]>();
      boolean found = false;
      Query q = database.createQuery();
      try {
        q.setSqlText("select position, argument_name, data_type, in_out, defaulted from all_arguments\n"
          + " where owner = :OWNER and object_name = :NAME and nvl(package_name, ' ') = :PKG and data_level = 0\n"
          + "   and nvl(overload, ' ') = (select nvl(min(overload), ' ') from all_arguments\n"
          + "                             where owner = :OWNER and object_name = :NAME and nvl(package_name, ' ') = :PKG)\n"
          + " order by position");
        q.paramByName("OWNER").setString(c[0]);
        q.paramByName("PKG").setString(c[1]);
        q.paramByName("NAME").setString(c[2]);
        q.open();
        while (!q.eof()) {
          found = true;
          String name = q.fieldByName("ARGUMENT_NAME").getValue().getString();
          if (q.fieldByName("POSITION").getValue().getInteger() == 0) {
            throw new IllegalArgumentException(text("SchedWiz-err-function", procedure));
          }
          if (name != null && name.length() > 0) {
            if (!"IN".equals(q.fieldByName("IN_OUT").getValue().getString())) {
              throw new IllegalArgumentException(text("SchedWiz-err-out-arg", procedure, name));
            }
            result.add(new String[] {name, q.fieldByName("DATA_TYPE").getValue().getString(),
              "Y".equals(q.fieldByName("DEFAULTED").getValue().getString()) ? "Y" : "N"});
          }
          q.next();
        }
      } catch (IllegalArgumentException ex) {
        throw ex;
      } catch (Exception ex) {
        ExceptionUtil.processException(ex);
        return null;
      } finally {
        q.close();
      }
      if (found) {
        return result;
      }
    }
    return null;
  }

  /** Pierwsza kolumna zapytania; parametr :SCHEMA_NAME (jesli jest) to schemat kreatora */
  List<String> list(String sql) {
    List<String> result = new ArrayList<String>();
    Query q = database.createQuery();
    try {
      q.setSqlText(sql);
      if (q.getParameterList().findParamByName("SCHEMA_NAME") != null) {
        q.paramByName("SCHEMA_NAME").setString(schema);
      }
      q.open();
      while (!q.eof()) {
        result.add(q.getField(0).getValue().getString());
        q.next();
      }
    } catch (Exception ex) {
      ExceptionUtil.processException(ex);
    } finally {
      q.close();
    }
    return result;
  }

  /** Obiekty schedulera z widoku ALL_SCHEDULER_*: ze schematu kreatora same nazwy, z innych OWNER.NAZWA */
  List<String> objects(String view, String nameColumn) {
    return list("select case when owner = :SCHEMA_NAME then " + nameColumn + " else owner || '.' || " + nameColumn + " end\n"
      + "  from " + view + "\n order by case when owner = :SCHEMA_NAME then 0 else 1 end, owner, " + nameColumn);
  }

  static JComboBox<String> editableCombo(List<String> items) {
    JComboBox<String> c = new JComboBox<String>(items.toArray(new String[items.size()]));
    c.setEditable(true);
    c.setMaximumRowCount(20);
    c.setSelectedItem("");
    return c;
  }

  static String comboText(JComboBox<String> c) {
    Object o = c.isEditable() ? c.getEditor().getItem() : c.getSelectedItem();
    return o == null ? "" : o.toString().trim();
  }

  static String fieldText(JTextField f) {
    return f.getText().trim();
  }

  // --- formularz

  /** Formularz w dwoch kolumnach: etykieta i pole */
  static class Form extends JPanel {
    private int row = 0;
    Form() {
      super(new GridBagLayout());
      setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
    }
    Form row(String labelKey, JComponent c) {
      return row(new JLabel(text(labelKey)), c);
    }
    Form row(JLabel label, JComponent c) {
      GridBagConstraints g = new GridBagConstraints();
      g.gridx = 0;
      g.gridy = row;
      g.anchor = GridBagConstraints.WEST;
      g.insets = new Insets(3, 0, 3, 8);
      add(label, g);
      g = new GridBagConstraints();
      g.gridx = 1;
      g.gridy = row++;
      g.fill = GridBagConstraints.HORIZONTAL;
      g.weightx = 1;
      g.insets = new Insets(3, 0, 3, 0);
      add(c, g);
      return this;
    }
    /** Element na cala szerokosc; grow - zajmuje wolne miejsce w pionie */
    Form full(JComponent c, boolean grow) {
      GridBagConstraints g = new GridBagConstraints();
      g.gridx = 0;
      g.gridy = row++;
      g.gridwidth = 2;
      g.fill = grow ? GridBagConstraints.BOTH : GridBagConstraints.HORIZONTAL;
      g.weightx = 1;
      g.weighty = grow ? 1 : 0;
      g.insets = new Insets(3, 0, 3, 0);
      add(c, g);
      return this;
    }
    Form hint(String key) {
      JLabel l = new JLabel("<html>" + text(key) + "</html>");
      l.setForeground(java.awt.Color.GRAY);
      return full(l, false);
    }
    /** Dopycha zawartosc do gory */
    Form end() {
      return full(new JPanel(), true);
    }
  }

}
