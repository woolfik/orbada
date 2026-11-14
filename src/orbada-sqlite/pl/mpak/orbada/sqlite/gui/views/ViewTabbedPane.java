package pl.mpak.orbada.sqlite.gui.views;

import pl.mpak.orbada.sqlite.gui.ext.SQLiteTabs;

import java.awt.Component;
import pl.mpak.orbada.gui.OrbadaTabbedPane;
import pl.mpak.orbada.plugins.IViewAccesibilities;
import pl.mpak.orbada.gui.ContentPanel;

/**
 *
 * @author akaluza
 */
public class ViewTabbedPane extends OrbadaTabbedPane {
  
  public ViewTabbedPane(IViewAccesibilities accesibilities) {
    super("VIEW", tabs(accesibilities));
  }

  /** ORBADA 1.3.8: kolumny z pragma_table_xinfo i zakladka "Uzywany przez" (SQLite 3.16+) */
  private static Component[] tabs(IViewAccesibilities accesibilities) {
    boolean ext = pl.mpak.orbada.sqlite.SQLiteSql.hasPragmaFunctions(accesibilities.getDatabase());
    java.util.List<Component> list = new java.util.ArrayList<Component>();
    list.add(ext ? new SQLiteTabs.Columns(accesibilities, "sqlite-view-columns") : new ViewColumnsPanel(accesibilities));
    list.add(new ViewTriggersPanel(accesibilities));
    if (ext) {
      list.add(new SQLiteTabs.UsedBy(accesibilities));
    }
    list.add(new ContentPanel(accesibilities));
    list.add(new ViewSourcePanel(accesibilities));
    return list.toArray(new Component[list.size()]);
  }
  
}
