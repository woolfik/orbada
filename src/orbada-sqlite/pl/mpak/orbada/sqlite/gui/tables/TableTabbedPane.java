package pl.mpak.orbada.sqlite.gui.tables;

import pl.mpak.orbada.sqlite.gui.ext.SQLiteTabs;

import java.awt.Component;
import pl.mpak.orbada.gui.OrbadaTabbedPane;
import pl.mpak.orbada.plugins.IViewAccesibilities;
import pl.mpak.orbada.gui.ContentPanel;

/**
 *
 * @author akaluza
 */
public class TableTabbedPane extends OrbadaTabbedPane {
  
  public TableTabbedPane(IViewAccesibilities accesibilities) {
    super("TABLE", tabs(accesibilities));
  }

  /** ORBADA 1.3.8: nowe zakladki (szczegoly, klucze obce w obie strony, uzycie, statystyki) - funkcje pragma_* od SQLite 3.16 */
  private static Component[] tabs(IViewAccesibilities accesibilities) {
    if (!pl.mpak.orbada.sqlite.SQLiteSql.hasPragmaFunctions(accesibilities.getDatabase())) {
      return new Component[] {
        new TableColumnsPanel(accesibilities),
        new TableIndexesPanel(accesibilities),
        new TableTriggersPanel(accesibilities),
        new TableReferencedToPanel(accesibilities),
        new ContentPanel(accesibilities),
        new TableSourcePanel(accesibilities)
      };
    }
    return new Component[] {
      new SQLiteTabs.Columns(accesibilities, "sqlite-table-columns"),
      new SQLiteTabs.TableDetails(accesibilities),
      new SQLiteTabs.TableIndexes(accesibilities),
      new SQLiteTabs.ForeignKeys(accesibilities),
      new SQLiteTabs.ReferencedFrom(accesibilities),
      new TableTriggersPanel(accesibilities),
      new SQLiteTabs.UsedBy(accesibilities),
      new SQLiteTabs.Stats(accesibilities),
      new ContentPanel(accesibilities),
      new TableSourcePanel(accesibilities)
    };
  }
  
}
