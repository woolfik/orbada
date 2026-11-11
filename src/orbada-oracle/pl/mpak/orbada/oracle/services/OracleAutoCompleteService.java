/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package pl.mpak.orbada.oracle.services;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import pl.mpak.sky.gui.swing.AutoCompleteLoadingItem;
import pl.mpak.util.ExceptionUtil;
import pl.mpak.orbada.Consts;
import pl.mpak.orbada.gui.comps.OrbadaSyntaxTextArea;
import pl.mpak.orbada.oracle.OrbadaOraclePlugin;
import pl.mpak.orbada.plugins.providers.SyntaxEditorAutoCompleteProvider;
import pl.mpak.orbada.plugins.queue.PluginMessage;
import pl.mpak.sky.gui.swing.AutoCompleteItem;
import pl.mpak.sky.gui.swing.ParametrizedAutoCompleteItem;
import pl.mpak.sky.gui.swing.syntax.SyntaxTextArea;
import pl.mpak.usedb.core.Database;
import pl.mpak.util.StringManager;
import pl.mpak.util.StringManagerFactory;

/**
 *
 * @author akaluza
 */
public class OracleAutoCompleteService extends SyntaxEditorAutoCompleteProvider {

  private final StringManager stringManager = StringManagerFactory.getStringManager(OrbadaOraclePlugin.class);

  private OrbadaSyntaxTextArea syntaxTextArea;

  @Override
  public boolean isForEditor(SyntaxTextArea syntaxTextArea) {
    if (syntaxTextArea instanceof OrbadaSyntaxTextArea) {
      this.syntaxTextArea = (OrbadaSyntaxTextArea)syntaxTextArea;
      return true;
    }
    return false;
  }

  public String getDescription() {
    return stringManager.getString("OracleAutoCompleteService-description");
  }

  public String getGroupName() {
    return OrbadaOraclePlugin.oracleDriverType;
  }

  /** Klucze list pobieranych w tle (jedno pobieranie na obiekt) */
  private final static Set<String> loading = new HashSet<String>();

  /** Najwyzej dwa rownolegle pobrania, reszta czeka w kolejce */
  private final static ExecutorService loader = Executors.newFixedThreadPool(2, new ThreadFactory() {
    public Thread newThread(Runnable r) {
      Thread t = new Thread(r, "orbada-oracle-autocomplete");
      t.setDaemon(true);
      return t;
    }
  });

  private void startLoading(final Database database, final String[] words) {
    final String key = OracleDbInfoProvider.instance.getAutoCompleteKey(database, words);
    synchronized (loading) {
      if (!loading.add(key)) {
        return;
      }
    }
    final OrbadaSyntaxTextArea area = syntaxTextArea;
    loader.execute(new Runnable() {
      public void run() {
        try {
          OracleDbInfoProvider.instance.loadAutoCompleteList(database, words);
        } catch (Throwable ex) {
          ExceptionUtil.processException(ex);
        } finally {
          synchronized (loading) {
            loading.remove(key);
          }
          java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
              // lista nadal otwarta - pokaz pobrane pozycje dla aktualnego tekstu
              if (area.getEditorArea() != null && area.getEditorArea().getAutoComplete() != null) {
                area.getEditorArea().getAutoComplete().refresh();
              }
            }
          });
        }
      }
    });
  }

  public AutoCompleteItem[] populate(String[] words, boolean bracketMode, int commaCount) {
    Database database = syntaxTextArea.getDatabase();
    if (database == null || !OrbadaOraclePlugin.oracleDriverType.equals(database.getDriverType())) {
      return null;
    }
    if (database.isExecutingSql()) {
      application.postPluginMessage(new PluginMessage(Consts.orbadaSystemPluginId, "status-text", stringManager.getString("OracleAutoCompleteService-no-available-info")));
      return null;
    }
    // ORBADA 1.3.4: lista ze slownika (np. metody pakietu po kropce) pobierana w tle - do tego czasu w liscie
    // "wczytywanie...", a pisac mozna dalej; po pobraniu lista odswieza sie sama (z filtrem po tym, co dopisano)
    ArrayList<AutoCompleteItem> items = OracleDbInfoProvider.instance.getCachedAutoCompleteList(database, words);
    if (items == null) {
      startLoading(database, words);
      return new AutoCompleteItem[] {new AutoCompleteLoadingItem(stringManager.getString("OracleAutoCompleteService-loading"))};
    }
    ArrayList<AutoCompleteItem> populated = new ArrayList<AutoCompleteItem>();
    if (words.length == 0 || items.size() == 0) {
      populated = items;
    }
    else {
      populated = new ArrayList<AutoCompleteItem>();
      String word = words[words.length -1].toUpperCase();
      if (bracketMode) {
        for (AutoCompleteItem item : items) {
          if (item.getWord().equals(word)) {
            if (item instanceof ParametrizedAutoCompleteItem) {
              ((ParametrizedAutoCompleteItem)item).setCommaCount(commaCount);
            }
            populated.add(item);
          }
        }
      }
      else {
        for (AutoCompleteItem item : items) {
          if (item.getWord().startsWith(word)) {
            if (item instanceof ParametrizedAutoCompleteItem) {
              ((ParametrizedAutoCompleteItem)item).setCommaCount(-1);
            }
            populated.add(item);
          }
        }
      }
    }
    return populated.toArray(new AutoCompleteItem[populated.size()]);
  }

}
