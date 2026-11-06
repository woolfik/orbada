package pl.mpak.orbada.gui.comps.table;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import pl.mpak.orbada.Consts;
import pl.mpak.usedb.core.CacheRecord;
import pl.mpak.usedb.core.Database;
import pl.mpak.usedb.core.Parameter;
import pl.mpak.usedb.core.ParameterList;
import pl.mpak.usedb.core.Query;
import pl.mpak.util.ExceptionUtil;
import pl.mpak.util.StringManager;
import pl.mpak.util.StringManagerFactory;
import pl.mpak.util.StringUtil;
import pl.mpak.util.variant.Variant;

/**
 * ORBADA Fuerteventura: otwieranie zapytania listy obiektow (tabele, pakiety, ...) bez blokowania GUI.
 * <p>Zapytanie wykonuje sie na osobnym watku, wynik trafia do tabeli przez Query.setResultSet() w watku GUI
 * (zdarzenia Query obsluguja komponenty Swing), a kolejne paczki rekordow dociagane sa w tle - Query
 * zapisuje je do pliku tymczasowego, w pamieci trzyma tylko ostatnio uzywane. W trakcie na tabeli
 * widac postep, Esc albo klikniecie przerywa pobieranie (to, co juz pobrano, zostaje na liscie).
 * <p>Uzycie (w watku GUI): ustawic SQL i parametry query tabeli tak jak przed open(), potem
 * {@link #open(String, String, int, int, Runnable)} zamiast query.open().
 */
public class ViewTableLoader {

  private final static StringManager stringManager = StringManagerFactory.getStringManager(Consts.class);

  /** Ile wierszy przesyla serwer w jednej paczce, jesli polaczenie nie ma wlasnego ustawienia */
  private final static int defaultFetchSize = 500;

  private final ViewTable table;
  private volatile Loading current;

  public ViewTableLoader(ViewTable table) {
    this.table = table;
    table.setCancelLoadListener(new Runnable() {
      public void run() {
        cancel();
      }
    });
  }

  public boolean isLoading() {
    return current != null;
  }

  /** Przerywa pobieranie; to, co juz pobrano, zostaje w tabeli; afterLoad zostanie wywolane */
  public void cancel() {
    Loading l = current;
    if (l != null) {
      l.cancel();
    }
  }

  /**
   * Do close() widoku: przerywa pobieranie i odlacza je od widoku - afterLoad ani zaznaczenie wiersza
   * nie zostana juz wywolane (koncowy callback idzie przez kolejke zdarzen, wiec trafilby na zamkniety widok).
   * Tylko w watku GUI.
   */
  public void dispose() {
    cancel();
    current = null;
    table.setBusyText(null);
  }

  /**
   * Otwiera query tabeli w tle.
   * @param keyField pole, po ktorym szukamy obiektu do zaznaczenia (np. TABLE_NAME), moze byc null
   * @param keyValue nazwa obiektu do zaznaczenia po zaladowaniu, moze byc null
   * @param column kolumna do zaznaczenia
   * @param index wiersz do zaznaczenia, gdy obiektu keyValue nie znaleziono
   * @param afterLoad wywolywane w watku GUI po zakonczeniu (takze po bledzie i przerwaniu), moze byc null
   */
  public void open(String keyField, String keyValue, int column, int index, Runnable afterLoad) throws Exception {
    // Poprzedniego watku nie czekamy (join) - moze wlasnie czekac na watek GUI w attach(); po cancel() sam sie
    // zakonczy, a Query.close() i getRecord() synchronizuja sie na buforze rekordow
    cancel();
    current = null;
    table.setBusyText(null);
    Query query = table.getQuery();
    query.close();
    if (!query.getDatabase().fireExecutableListener(query)) {
      finish(null, afterLoad);
      return;
    }
    Loading l = new Loading(query, keyField, keyValue, column, index, afterLoad);
    current = l;
    table.setBusyText(stringManager.getString("ViewTableLoader-executing"));
    l.start();
  }

  private void finish(Loading l, Runnable afterLoad) {
    if (current == l) {
      current = null;
      table.setBusyText(null);
    }
    if (afterLoad != null) {
      afterLoad.run();
    }
  }

  private static String replaceParameters(String sqlText, ParameterList params) {
    // jak ParameterList.replaceParameters (niedostepne poza pakietem usedb): parametry niewiazane wstawiane tekstem
    for (int i = 0; i < params.parameterCount(); i++) {
      Parameter p = params.getParameter(i);
      if (!p.isBindable()) {
        sqlText = StringUtil.replaceString(sqlText, p.getParamName(), p.getString());
      }
    }
    return sqlText;
  }

  private class Loading extends Thread {

    private final Query query;
    private final Database database;
    private final String sqlText;
    private final String keyField;
    private final String keyValue;
    private final int column;
    private final int index;
    private final Runnable afterLoad;
    private volatile boolean cancelled = false;
    private volatile PreparedStatement statement;
    private int foundIndex = -1;
    private long lastProgress = 0;

    Loading(Query query, String keyField, String keyValue, int column, int index, Runnable afterLoad) {
      super("orbada-view-table-loader");
      setDaemon(true);
      this.query = query;
      this.database = query.getDatabase();
      ParameterList params = query.getParameterList();
      this.sqlText = query.getParamCheck() && params.parameterCount() > 0
        ? replaceParameters(query.getPreparedSqlText(), params) : query.getPreparedSqlText();
      this.keyField = keyField;
      this.keyValue = keyValue;
      this.column = column;
      this.index = index;
      this.afterLoad = afterLoad;
    }

    void cancel() {
      cancelled = true;
      PreparedStatement ps = statement;
      if (ps != null) {
        try {
          ps.cancel();
        } catch (Throwable ex) {
        }
      }
    }

    @Override
    public void run() {
      Throwable error = null;
      try {
        ResultSet rs = execute();
        if (rs != null && !cancelled) {
          attach(rs);
          fetchAll();
        }
      } catch (Throwable ex) {
        error = ex;
      }
      final Throwable e = cancelled ? null : error;
      java.awt.EventQueue.invokeLater(new Runnable() {
        public void run() {
          if (current != Loading.this) {
            return; // zastapione nowszym open() - ono zaznaczy wiersz i wywola swoje afterLoad
          }
          try {
            if (e != null) {
              ExceptionUtil.processException(e);
            } else if (query.isActive()) {
              if (foundIndex >= 0) {
                table.changeSelection(foundIndex, column);
              } else if (table.getRowCount() > 0) {
                table.changeSelection(Math.min(index, table.getRowCount() - 1), column);
              }
            }
          } finally {
            finish(Loading.this, afterLoad);
          }
        }
      });
    }

    /** Wykonanie zapytania - najdluzszy etap przy duzych slownikach (ORDER BY, zlaczenia widokow ALL_*) */
    private ResultSet execute() throws Exception {
      PreparedStatement ps = database.getConnection().prepareStatement(sqlText, query.getResultSetType(), query.getResultSetConcurrency());
      try {
        if (query.getParamCheck()) {
          query.getParameterList().bindParameters(ps);
        }
        String maxRows = database.getUserProperties().getProperty(Database.useDbParamStatementMaxRows);
        if (maxRows != null) {
          ps.setMaxRows(Integer.parseInt(maxRows));
        }
        String fetchSize = database.getUserProperties().getProperty(Database.useDbParamStatementFetchSize);
        ps.setFetchSize(fetchSize != null ? Integer.parseInt(fetchSize) : defaultFetchSize);
        statement = ps;
        if (cancelled) {
          ps.close();
          return null;
        }
        return ps.executeQuery();
      } catch (Exception ex) {
        statement = null;
        ps.close();
        throw ex;
      }
    }

    /** Podpiecie wyniku do query tabeli w watku GUI; pierwsza paczka rekordow czytana jest od razu */
    private void attach(final ResultSet rs) throws Exception {
      final Exception[] error = {null};
      java.awt.EventQueue.invokeAndWait(new Runnable() {
        public void run() {
          try {
            if (cancelled || current != Loading.this) {
              rs.getStatement().close();
              return;
            }
            query.setResultSet(rs);
            statement = null; // od teraz zamyka go Query.close()
          } catch (Exception ex) {
            error[0] = ex;
          }
        }
      });
      if (error[0] != null) {
        throw error[0];
      }
    }

    /**
     * Dociaganie kolejnych paczek; QueryTableModel sam dodaje wiersze po zdarzeniu FLUSHED.
     * ORBADA 1.3.2: fetchMore() zamiast getRecord() - getRecord() trzymal blokade bufora przez cale pobieranie
     * paczki z sieci, a watek GUI rysujacy tabele (getRecordCount/getRecord) czekal na nia (zawieszenia 20+ s).
     */
    private void fetchAll() throws Exception {
      int checked = 0;
      while (!cancelled && current == this && query.isActive() && !query.isFlushed()) {
        if (!query.fetchMore()) {
          break;
        }
        checked = findKey(checked);
        progress(false);
      }
      if (!cancelled && query.isActive()) {
        findKey(checked);
      }
    }

    private int findKey(int from) throws Exception {
      if (keyField == null || keyValue == null || foundIndex >= 0) {
        return from;
      }
      int count = query.getRecordCount();
      for (int i = from; i < count && !cancelled; i++) {
        CacheRecord rec = query.getRecord(i);
        if (rec == null) {
          break;
        }
        Variant value = rec.getField(keyField).getValue();
        if (!value.isNullValue() && keyValue.equals(value.getString())) {
          foundIndex = i;
          break;
        }
      }
      return count;
    }

    private void progress(boolean force) {
      long now = System.currentTimeMillis();
      if (force || now - lastProgress > 150) {
        lastProgress = now;
        final int count = query.getRecordCount();
        java.awt.EventQueue.invokeLater(new Runnable() {
          public void run() {
            if (current == Loading.this) {
              table.setBusyText(String.format(stringManager.getString("ViewTableLoader-fetching"), count));
            }
          }
        });
      }
    }

  }

}
