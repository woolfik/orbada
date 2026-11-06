package pl.mpak.orbada.postgresql.cm;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import pl.mpak.orbada.gui.IRootTabObjectInfo;
import pl.mpak.orbada.gui.ITabObjectInfo;
import pl.mpak.orbada.gui.comps.table.ViewTable;
import pl.mpak.orbada.plugins.ComponentAction;
import pl.mpak.orbada.postgresql.OrbadaPostgreSQLPlugin;
import pl.mpak.orbada.postgresql.services.PostgreSQLDbInfoProvider;
import pl.mpak.orbada.universal.gui.tabbed.UniversalPropTab;
import pl.mpak.orbada.universal.gui.tabbed.UniversalViewTabs;
import pl.mpak.orbada.universal.gui.wizards.SqlCodeWizardDialog;
import pl.mpak.orbada.universal.gui.wizards.SqlCodeWizardPanel;
import pl.mpak.sky.gui.mr.ModalResult;
import pl.mpak.sky.gui.swing.MessageBox;
import pl.mpak.sky.gui.swing.SwingUtil;
import pl.mpak.usedb.core.Database;
import pl.mpak.usedb.core.Query;
import pl.mpak.util.StringManager;
import pl.mpak.util.StringManagerFactory;
import pl.mpak.util.StringUtil;

/**
 * ORBADA 1.3.2: akcja otwierajaca kreator PostgreSQL z listy obiektow (UniversalViewTabs), z zakladki
 * wlasciwosci obiektu (UniversalPropTab) albo z menu/paska perspektywy (bez komponentu). Po wykonaniu
 * kreatora odswieza liste.
 */
public class PgWizardAction extends ComponentAction {

  private final static StringManager stringManager = StringManagerFactory.getStringManager(OrbadaPostgreSQLPlugin.class);

  /** Miejsce wywolania kreatora */
  public static class Context {
    /** Schemat: wybrany w liscie, zakladki albo biezacy polaczenia */
    public final String schema;
    /** Obiekt zakladki (np. tabela dla zakladki indeksow) albo null */
    public final String object;
    /** Wiersz zaznaczony w liscie (ustawiony rekord) albo null */
    public final Query row;

    Context(String schema, String object, Query row) {
      this.schema = schema;
      this.object = object;
      this.row = row;
    }

    /** Wartosc pola zaznaczonego wiersza albo null */
    public String get(String field) throws Exception {
      if (row == null || row.findFieldByName(field) == null) {
        return null;
      }
      return row.fieldByName(field).getString();
    }
  }

  public interface Factory {
    /** Kreator albo null, gdy nie dotyczy zaznaczonego obiektu */
    SqlCodeWizardPanel create(Database database, Context context) throws Exception;
  }

  private final Factory factory;
  private final boolean needsRow;

  /**
   * @param textKey klucz i18n tekstu akcji
   * @param icon ikona z /pl/mpak/res/icons/ albo null
   * @param needsRow true - tylko przy zaznaczonym wierszu listy
   */
  public PgWizardAction(String textKey, String icon, boolean needsRow, Factory factory) {
    super();
    this.factory = factory;
    this.needsRow = needsRow;
    setText(stringManager.getString(textKey));
    if (icon != null) {
      setSmallIcon(pl.mpak.sky.gui.swing.ImageManager.getImage("/pl/mpak/res/icons/" + icon));
    }
    setActionCommandKey("PgWizardAction-" + textKey);
    addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        perform();
      }
    });
  }

  /** Akcja z menu perspektywy - bez listy */
  public PgWizardAction(Database database, String textKey, String icon, Factory factory) {
    this(textKey, icon, false, factory);
    setDatabase(database);
  }

  private void perform() {
    try {
      Database db = getDatabase();
      Query row = null;
      String schema = null;
      String object = null;
      final boolean inView = getComponent() != null;
      final UniversalPropTab tab = inView ? (UniversalPropTab)SwingUtil.getOwnerComponent(UniversalPropTab.class, getComponent()) : null;
      final UniversalViewTabs view = inView && tab == null ? (UniversalViewTabs)SwingUtil.getOwnerComponent(UniversalViewTabs.class, getComponent()) : null;
      if (getComponent() instanceof ViewTable) {
        ViewTable vt = (ViewTable)getComponent();
        if (vt.getSelectedRow() >= 0 && vt.getQuery().isActive()) {
          vt.getQuery().getRecord(vt.getSelectedRow());
          row = vt.getQuery();
        }
      }
      if (needsRow && row == null) {
        MessageBox.show(stringManager.getString("error"), stringManager.getString("wizard-select-object"), ModalResult.OK);
        return;
      }
      if (tab != null) {
        schema = tab.getCurrentSchemaName();
        object = tab.getCurrentObjectName();
        if (StringUtil.isEmpty(object)) {
          return;
        }
      } else if (view != null) {
        schema = view.getSelectedSchemaName();
      }
      if (row != null && row.findFieldByName("schema_name") != null) {
        schema = row.fieldByName("schema_name").getString();
      }
      if (StringUtil.isEmpty(schema)) {
        schema = PostgreSQLDbInfoProvider.getConnectedSchema(db);
      }
      SqlCodeWizardPanel wizard = factory.create(db, new Context(schema, object, row));
      if (wizard == null) {
        return;
      }
      if (SqlCodeWizardDialog.show(wizard, true) != null) {
        final IRootTabObjectInfo root = inView ? (IRootTabObjectInfo)SwingUtil.getOwnerComponent(IRootTabObjectInfo.class, getComponent()) : null;
        java.awt.EventQueue.invokeLater(new Runnable() {
          @Override
          public void run() {
            if (tab != null) {
              ((ITabObjectInfo)tab).refresh();
            } else if (root != null) {
              root.refresh();
            }
          }
        });
      }
    } catch (Exception ex) {
      MessageBox.show(stringManager.getString("error"), ex.getMessage(), ModalResult.OK);
    }
  }

}
