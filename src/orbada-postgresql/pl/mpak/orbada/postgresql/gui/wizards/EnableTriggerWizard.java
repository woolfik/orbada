package pl.mpak.orbada.postgresql.gui.wizards;

import java.util.List;
import javax.swing.JComboBox;
import pl.mpak.usedb.core.Database;

/**
 * ORBADA 1.3.2: wlaczenie/wylaczenie wyzwalacza albo wszystkich wyzwalaczy tabeli (ALTER TABLE ... ENABLE|DISABLE TRIGGER
 * nazwa|ALL|USER, ENABLE REPLICA/ALWAYS) - odpowiednik EnableTriggerWizard i EnableAllTableTriggersWizard Oracle.
 */
public class EnableTriggerWizard extends PgWizard {

  private static final String[] MODES = {"ENABLE", "DISABLE", "ENABLE REPLICA", "ENABLE ALWAYS"};

  private final String table;
  private final String trigger;
  private final JComboBox<String> comboMode;
  private final JComboBox<String> comboScope;

  /**
   * @param table tabela w SQL
   * @param trigger nazwa wyzwalacza albo null - wszystkie wyzwalacze tabeli
   */
  public EnableTriggerWizard(Database database, String table, String trigger) {
    super(database, null);
    this.table = table;
    this.trigger = trigger;
    this.titleKey = trigger == null ? "EnableAllTriggersWizard-title" : "EnableTriggerWizard-title";
    info("table-name", table);
    if (trigger != null) {
      info("trigger-name", trigger);
      comboScope = null;
    } else {
      comboScope = combo("triggers-scope", false, stringManager.getString("triggers-user"), stringManager.getString("triggers-all"));
    }
    comboMode = combo("trigger-mode", false, MODES);
  }

  public void setMode(int mode) {
    comboMode.setSelectedIndex(mode);
  }

  @Override
  protected void build(List<String> sql) {
    // ALL obejmuje wyzwalacze systemowe kluczy obcych - wymaga superuzytkownika
    String target = trigger != null ? ident(trigger) : comboScope.getSelectedIndex() == 0 ? "USER" : "ALL";
    sql.add("ALTER TABLE " + table + " " + value(comboMode) + " TRIGGER " + target);
  }

}
