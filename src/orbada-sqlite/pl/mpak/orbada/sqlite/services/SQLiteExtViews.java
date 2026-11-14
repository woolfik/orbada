package pl.mpak.orbada.sqlite.services;

import java.awt.Component;
import javax.swing.Icon;
import pl.mpak.orbada.plugins.IViewAccesibilities;
import pl.mpak.orbada.plugins.providers.ViewProvider;
import pl.mpak.orbada.sqlite.OrbadaSQLitePlugin;
import pl.mpak.orbada.sqlite.SQLiteSql;
import pl.mpak.orbada.sqlite.gui.ext.SQLiteViews;
import pl.mpak.usedb.core.Database;
import pl.mpak.util.StringManager;
import pl.mpak.util.StringManagerFactory;

/**
 * ORBADA 1.3.8: dostawcy nowych widokow SQLite (menu perspektywy, grupa SQLite). Nazwa i opis z i18n:
 * "NazwaKlasy-public-name", "NazwaKlasy-description".
 */
public class SQLiteExtViews {

  private final static StringManager stringManager = StringManagerFactory.getStringManager(OrbadaSQLitePlugin.class);

  public abstract static class ExtView extends ViewProvider {

    private final String id;
    private final String icon;

    protected ExtView(String id, String icon) {
      this.id = id;
      this.icon = icon;
    }

    public String getPublicName() {
      return stringManager.getString(getClass().getSimpleName() + "-public-name");
    }

    public String getDescription() {
      return stringManager.getString(getClass().getSimpleName() + "-description");
    }

    public String getViewId() {
      return id;
    }

    public Icon getIcon() {
      return pl.mpak.sky.gui.swing.ImageManager.getImage("/pl/mpak/res/icons/" + icon);
    }

    public boolean isForDatabase(Database database) {
      // funkcje pragma_* od SQLite 3.16 - ze starym sterownikiem (zentus, SQLite 3.5) widokow nie ma w menu
      return database != null && OrbadaSQLitePlugin.driverType.equals(database.getDriverType()) && SQLiteSql.hasPragmaFunctions(database);
    }

    public String getGroupName() {
      return OrbadaSQLitePlugin.driverType;
    }
  }

  public static class SQLiteObjectsView extends ExtView {
    public SQLiteObjectsView() { super("orbada-sqlite-objects-view", "find_object16.gif"); }
    public Component createView(IViewAccesibilities a) { return new SQLiteViews.Objects(a); }
  }

  public static class SQLiteIndexesView extends ExtView {
    public SQLiteIndexesView() { super("orbada-sqlite-indexes-view", "index.gif"); }
    public Component createView(IViewAccesibilities a) { return new SQLiteViews.Indexes(a); }
  }

  public static class SQLiteSequencesView extends ExtView {
    public SQLiteSequencesView() { super("orbada-sqlite-sequences-view", "sequence.gif"); }
    public Component createView(IViewAccesibilities a) { return new SQLiteViews.Sequences(a); }
  }

  public static class SQLitePragmasView extends ExtView {
    public SQLitePragmasView() { super("orbada-sqlite-pragmas-view", "preferences16.gif"); }
    public Component createView(IViewAccesibilities a) { return new SQLiteViews.Pragmas(a); }
  }

  public static class SQLiteFunctionsView extends ExtView {
    public SQLiteFunctionsView() { super("orbada-sqlite-functions-view", "function.gif"); }
    public Component createView(IViewAccesibilities a) { return new SQLiteViews.Functions(a); }
  }

  public static class SQLiteSizesView extends ExtView {
    public SQLiteSizesView() { super("orbada-sqlite-sizes-view", "tablespace.gif"); }
    public Component createView(IViewAccesibilities a) { return new SQLiteViews.Sizes(a); }
  }

}
