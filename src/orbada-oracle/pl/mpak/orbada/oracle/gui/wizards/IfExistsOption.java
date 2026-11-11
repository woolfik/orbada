package pl.mpak.orbada.oracle.gui.wizards;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import pl.mpak.orbada.oracle.OrbadaOraclePlugin;
import pl.mpak.usedb.core.Database;
import pl.mpak.util.StringManager;
import pl.mpak.util.StringManagerFactory;

/**
 * ORBADA 1.3.4: opcja IF NOT EXISTS (tworzenie) / IF EXISTS (usuwanie) w kreatorach Oracle - od Oracle 23ai
 * polecenie nie konczy sie bledem, gdy obiekt juz istnieje / nie istnieje. Na starszych bazach opcji nie ma.
 * <p>Kreator pokazuje pole przez SqlCodeWizardPanel.getExtraOptions() i przepuszcza swoje polecenie przez
 * {@link #apply(String)}, ktore dopisuje klauzule po typie obiektu w kazdym CREATE/DROP na poczatku linii.
 * CREATE OR REPLACE zostaje bez zmian (Oracle nie laczy go z IF NOT EXISTS - ORA-11541, a i tak nie zglasza bledu).
 * ALTER TABLE ... DROP COLUMN/CONSTRAINT nie maja IF EXISTS.
 */
public class IfExistsOption {

  private final static StringManager stringManager = StringManagerFactory.getStringManager(OrbadaOraclePlugin.class);

  /** Typy obiektow sprawdzone na Oracle 26ai (CREATE ... IF NOT EXISTS i DROP ... IF EXISTS) */
  private final static String types =
    "TABLE|INDEX|SEQUENCE|SYNONYM|VIEW|MATERIALIZED\\s+VIEW|TRIGGER|TYPE(?:\\s+BODY)?|PROCEDURE|FUNCTION|" +
    "PACKAGE(?:\\s+BODY)?|DATABASE\\s+LINK|DIRECTORY|JAVA\\s+SOURCE";
  private final static Pattern create = Pattern.compile(
    "(?im)^(\\s*CREATE\\s+(?:(?:GLOBAL\\s+TEMPORARY|PRIVATE\\s+TEMPORARY|UNIQUE|BITMAP|EDITIONABLE|NONEDITIONABLE|PUBLIC|SHARED)\\s+)*(?:" + types + "))\\s+(?!IF\\s+NOT\\s+EXISTS\\b)");
  private final static Pattern drop = Pattern.compile(
    "(?im)^(\\s*DROP\\s+(?:PUBLIC\\s+)?(?:" + types + "))\\s+(?!IF\\s+EXISTS\\b)");

  private final boolean creating;
  private final JCheckBox check;

  /**
   * @param creating true - IF NOT EXISTS (CREATE), false - IF EXISTS (DROP)
   */
  public IfExistsOption(Database database, boolean creating) {
    this.creating = creating;
    this.check = isSupported(database) ? new JCheckBox(stringManager.getString(creating ? "if-not-exists" : "if-exists")) : null;
  }

  /** Oracle 23ai i nowsze */
  public static boolean isSupported(Database database) {
    try {
      return database != null && database.getConnection() != null && database.getConnection().getMetaData().getDatabaseMajorVersion() >= 23;
    } catch (Exception ex) {
      return false;
    }
  }

  /** Pole do SqlCodeWizardPanel.getExtraOptions() albo null, gdy baza nie zna IF [NOT] EXISTS */
  public JComponent getComponent() {
    return check;
  }

  public boolean isSelected() {
    return check != null && check.isSelected();
  }

  public void setSelected(boolean selected) {
    if (check != null) {
      check.setSelected(selected);
    }
  }

  /** Polecenie z IF NOT EXISTS / IF EXISTS po typie obiektu, gdy pole jest zaznaczone */
  public String apply(String sql) {
    if (!isSelected() || sql == null) {
      return sql;
    }
    Matcher m = (creating ? create : drop).matcher(sql);
    return m.replaceAll("$1" + (creating ? " IF NOT EXISTS " : " IF EXISTS "));
  }

}
