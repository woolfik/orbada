package pl.mpak.orbada.oracle.debug;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * ORBADA 1.3.6: debugger PL/SQL na DBMS_DEBUG - podlaczenie do sesji innej aplikacji.
 *
 * <p>Sesja aplikacji (np. program w Delphi przez OCI) wykonuje DBMS_DEBUG.INITIALIZE + DEBUG_ON i pokazuje
 * identyfikator; debugger robi ATTACH_SESSION na osobnym polaczeniu. Kazde kolejne wywolanie PL/SQL w sesji aplikacji
 * czeka na debugger: debugger stawia breakpointy i puszcza wykonanie do breakpointu/wyjatku, potem krokuje, czyta
 * i zmienia zmienne.</p>
 *
 * <p>Wszystkie wywolania DBMS_DEBUG ida z jednego watku roboczego (jedno polaczenie). W stanie oczekiwania na
 * wywolanie aplikacji watek robi SYNCHRONIZE z krotkim timeoutem (SET_TIMEOUT po stronie debuggera), zeby
 * polecenia (odlaczenie, breakpointy) nie czekaly. Na breakpoincie watek co kilka sekund odpytuje sesje aplikacji -
 * dzieki temu aplikacja z krotkim timeoutem i NODEBUG_ON_TIMEOUT nie rusza sama, a po zniknieciu debuggera
 * (zamkniecie Orbady, utrata polaczenia) po timeoucie wykonuje sie dalej bez debugowania.</p>
 *
 * <p>Zdarzenia listenera sa wolane z watku roboczego.</p>
 */
public class PlSqlDebugger {

  public enum State {
    /** Odlaczony */
    DETACHED,
    /** Podlaczony, sesja aplikacji nic nie wykonuje */
    WAITING,
    /** Sesja aplikacji wykonuje kod (do breakpointu) */
    RUNNING,
    /** Wykonanie zatrzymane (breakpoint, krok, wyjatek) */
    PAUSED
  }

  public interface Listener {
    /** Zmiana stanu; dla PAUSED stack zawiera stos wywolan (ostatni element - biezaca ramka) */
    void stateChanged(State state, RuntimeInfo info, List<Frame> stack);
    void message(String text, boolean error);
  }

  public interface Callback<T> {
    void done(T result);
  }

  /** Stan wykonania z DBMS_DEBUG.RUNTIME_INFO */
  public static class RuntimeInfo {
    public int line;
    public String owner;
    public String name;
    public int namespace;
    public int reason;
    public boolean terminated;
    public int depth;
    public int breakpoint;
    public int oer;
  }

  /** Ramka stosu z DBMS_DEBUG.PRINT_BACKTRACE; index - numer ramki dla GET_VALUE (1 - dol stosu) */
  public static class Frame {
    public int index;
    public String owner;
    public String name;
    public int namespace;
    public int line;
    public String entryPoint;
    public int libunitType;

    public boolean hasSource() {
      return owner != null && name != null && namespace > 0;
    }

    @Override
    public String toString() {
      if (!hasSource()) {
        return "#" + index + " (anonymous block)";
      }
      return "#" + index + " " + owner + "." + name + (entryPoint != null && !entryPoint.equals(name) ? "." + entryPoint : "") + " : " + line;
    }
  }

  /** Wartosc zmiennej; code: 0 - wartosc, VALUE_NULL - NULL, inne - kod bledu DBMS_DEBUG */
  public static class Value {
    public final String name;
    public final int code;
    public final String value;

    public Value(String name, int code, String value) {
      this.name = name;
      this.code = code;
      this.value = value;
    }
  }

  /** Breakpoint ustawiany w sesji aplikacji, gdy ta jest zatrzymana */
  public static class Breakpoint {
    public final String owner;
    public final String name;
    public final int namespace;
    public final int line;
    /** Numer w DBMS_DEBUG, -1 - jeszcze nie ustawiony */
    volatile int number = -1;
    /** Kod bledu SET_BREAKPOINT, 0 - ok */
    volatile int error = 0;

    public Breakpoint(String owner, String name, int namespace, int line) {
      this.owner = owner;
      this.name = name;
      this.namespace = namespace;
      this.line = line;
    }

    public boolean isSet() {
      return number >= 0;
    }

    public int getError() {
      return error;
    }

    public boolean same(String owner, String name, int namespace, int line) {
      return this.owner.equals(owner) && this.name.equals(name) && this.namespace == namespace && this.line == line;
    }
  }

  /** DBMS_DEBUG.NAMESPACE_* */
  public static final int NAMESPACE_PKGSPEC_OR_TOPLEVEL = 1;
  public static final int NAMESPACE_PKG_BODY = 2;
  public static final int NAMESPACE_TRIGGER = 3;

  /** Odpytywanie zatrzymanej sesji aplikacji (ms) */
  public static final int KEEPALIVE_MS = 2000;
  /** Timeout SYNCHRONIZE/CONTINUE po stronie debuggera (s) */
  public static final int DEBUGGER_TIMEOUT = 2;

  private static final String INFO = "dbms_debug.info_getstackdepth + dbms_debug.info_getbreakpoint + dbms_debug.info_getlineinfo + dbms_debug.info_getoerinfo";

  /** Stale DBMS_DEBUG odczytane z bazy przy podlaczeniu */
  public static int VALUE_NULL = 32;
  public static int ERROR_NO_SUCH_OBJECT = 3;
  public static int ERROR_NO_DEBUG_INFO = 2;
  public static int ERROR_BOGUS_FRAME = 1;
  public static int ERROR_INDEXED_TABLE = 18;
  public static int ERROR_NULLCOLLECTION = 40;
  public static int ERROR_TIMEOUT = 31;
  private int reasonStarting = 2;
  private int reasonException = 11;

  private final Connection connection;
  private final Listener listener;
  private final LinkedBlockingQueue<Runnable> commands = new LinkedBlockingQueue<Runnable>();
  private final List<Breakpoint> breakpoints = Collections.synchronizedList(new ArrayList<Breakpoint>());
  private final List<Breakpoint> removed = Collections.synchronizedList(new ArrayList<Breakpoint>());
  private volatile State state = State.DETACHED;
  private volatile boolean breakOnException = false;
  private volatile boolean stop = false;
  private volatile RuntimeInfo info;
  private int lastError = 0;
  private volatile List<Frame> stack = new ArrayList<Frame>();
  private volatile Thread worker;

  public PlSqlDebugger(Connection connection, Listener listener) {
    this.connection = connection;
    this.listener = listener;
  }

  /**
   * Podlaczenie do sesji aplikacji (wolac poza EDT) i start watku roboczego
   * @param debugSessionId identyfikator z DBMS_DEBUG.INITIALIZE w sesji aplikacji
   */
  public void attach(String debugSessionId) throws SQLException {
    if (worker != null) {
      throw new IllegalStateException("already attached");
    }
    readConstants();
    try (CallableStatement c = connection.prepareCall("declare t binary_integer; begin t := dbms_debug.set_timeout(?); dbms_debug.attach_session(?); end;")) {
      c.setInt(1, DEBUGGER_TIMEOUT);
      c.setString(2, debugSessionId);
      c.execute();
    }
    stop = false;
    setState(State.WAITING);
    worker = new Thread(new Runnable() {
      public void run() {
        loop();
      }
    }, "orbada-plsql-debugger");
    worker.setDaemon(true);
    worker.start();
  }

  public State getState() {
    return state;
  }

  public boolean isAttached() {
    return state != State.DETACHED;
  }

  public void setBreakOnException(boolean breakOnException) {
    this.breakOnException = breakOnException;
  }

  public boolean isBreakOnException() {
    return breakOnException;
  }

  public List<Breakpoint> getBreakpoints() {
    synchronized (breakpoints) {
      return new ArrayList<Breakpoint>(breakpoints);
    }
  }

  public Breakpoint findBreakpoint(String owner, String name, int namespace, int line) {
    synchronized (breakpoints) {
      for (Breakpoint b : breakpoints) {
        if (b.same(owner, name, namespace, line)) {
          return b;
        }
      }
    }
    return null;
  }

  /** Dodaje breakpoint; w sesji aplikacji zostanie ustawiony od razu (zatrzymana) albo na poczatku kolejnego wywolania */
  public Breakpoint addBreakpoint(String owner, String name, int namespace, int line) {
    Breakpoint b = findBreakpoint(owner, name, namespace, line);
    if (b != null) {
      return b;
    }
    b = new Breakpoint(owner, name, namespace, line);
    breakpoints.add(b);
    if (state == State.PAUSED) {
      post(new Runnable() {
        public void run() {
          applyBreakpoints();
        }
      });
    }
    return b;
  }

  public void removeBreakpoint(Breakpoint b) {
    if (breakpoints.remove(b) && b.isSet()) {
      removed.add(b);
      if (state == State.PAUSED) {
        post(new Runnable() {
          public void run() {
            applyBreakpoints();
          }
        });
      }
    }
  }

  // ---------------------------------------------------------------- sterowanie (tylko w stanie PAUSED)

  public void resume() {
    run(0);
  }

  public void stepOver() {
    run(-1);
  }

  public void stepInto() {
    run(-2);
  }

  public void stepOut() {
    run(-3);
  }

  /** Przerywa wykonanie wywolania w sesji aplikacji (aplikacja dostaje ORA-06543) */
  public void abort() {
    run(-4);
  }

  private void run(final int what) {
    if (state != State.PAUSED) {
      return;
    }
    post(new Runnable() {
      public void run() {
        if (state != State.PAUSED) {
          return;
        }
        String flags;
        switch (what) {
          case -1: flags = "dbms_debug.break_next_line"; break;
          case -2: flags = "dbms_debug.break_any_call"; break;
          case -3: flags = "dbms_debug.break_any_return"; break;
          case -4: flags = "dbms_debug.abort_execution"; break;
          default: flags = "0";
        }
        if (what != -4 && breakOnException) {
          flags += " + dbms_debug.break_exception";
        }
        doContinue(flags);
      }
    });
  }

  /** Odczyt wartosci zmiennych z ramki (frame - Frame.index, 0 - biezaca) */
  public void getValues(final int frame, final List<String> names, final Callback<List<Value>> callback) {
    post(new Runnable() {
      public void run() {
        List<Value> result = new ArrayList<Value>();
        if (state == State.PAUSED) {
          for (String name : names) {
            result.add(value(frame, name));
          }
        }
        callback.done(result);
      }
    });
  }

  /**
   * Zmiana wartosci zmiennej: "name := expression;"
   * @param expression wyrazenie PL/SQL, np. 42, 'tekst', null, sysdate
   */
  public void setValue(final int frame, final String name, final String expression, final Callback<String> callback) {
    post(new Runnable() {
      public void run() {
        String error = null;
        if (state != State.PAUSED) {
          error = "not paused";
        } else {
          try (CallableStatement c = connection.prepareCall("begin ? := dbms_debug.set_value(?, ?); end;")) {
            c.registerOutParameter(1, Types.INTEGER);
            c.setInt(2, frame);
            c.setString(3, name + " := " + expression + ";");
            c.execute();
            if (c.getInt(1) != 0) {
              error = "DBMS_DEBUG.SET_VALUE " + c.getInt(1);
            }
          } catch (SQLException ex) {
            error = ex.getMessage();
          }
        }
        callback.done(error);
      }
    });
  }

  /**
   * Odlaczenie. Zatrzymana sesja aplikacji jest puszczana dalej (bez breakpointow); przy kolejnym wywolaniu
   * aplikacja poczeka na debugger do swojego timeoutu - dlatego lepiej najpierw wylaczyc debugowanie w aplikacji.
   */
  public void detach() {
    if (worker == null) {
      return;
    }
    post(new Runnable() {
      public void run() {
        doDetach();
      }
    });
  }

  /** Czeka na zakonczenie watku roboczego (po detach) */
  public void join(long millis) throws InterruptedException {
    Thread w = worker;
    if (w != null) {
      w.join(millis);
    }
  }

  // ---------------------------------------------------------------- watek roboczy

  private void post(Runnable command) {
    commands.add(command);
  }

  private void loop() {
    try {
      while (!stop) {
        Runnable command;
        if (state == State.PAUSED) {
          command = commands.poll(KEEPALIVE_MS, TimeUnit.MILLISECONDS);
          if (command == null) {
            keepAlive();
            continue;
          }
        } else {
          command = commands.poll();
        }
        if (command != null) {
          command.run();
          continue;
        }
        // WAITING / RUNNING - czekamy na sesje aplikacji krotko, zeby obsluzyc polecenia
        RuntimeInfo ri = call("dbms_debug.synchronize(ri, " + INFO + ")");
        if (ri != null) {
          stopped(ri);
        }
      }
    } catch (InterruptedException ex) {
      // koniec
    } catch (Throwable ex) {
      listener.message(ex.getMessage(), true);
      setState(State.DETACHED);
    } finally {
      worker = null;
    }
  }

  /** Wynik SYNCHRONIZE/CONTINUE */
  private void stopped(RuntimeInfo ri) {
    if (ri.terminated) {
      info = null;
      stack = new ArrayList<Frame>();
      setState(State.WAITING);
      return;
    }
    if (ri.reason == reasonStarting) {
      // poczatek wywolania w sesji aplikacji - breakpointy i dalej do breakpointu/wyjatku
      applyBreakpoints();
      doContinue(breakOnException ? "dbms_debug.break_exception" : "0");
      return;
    }
    info = ri;
    stack = backtrace();
    setState(State.PAUSED);
  }

  private void doContinue(String flags) {
    setState(State.RUNNING);
    RuntimeInfo ri = call("dbms_debug.continue(ri, " + flags + ", " + INFO + ")");
    if (ri != null) {
      stopped(ri);
    }
    // null - timeout: sesja aplikacji dalej wykonuje kod, petla czeka SYNCHRONIZE
  }

  private void doDetach() {
    try {
      if (state == State.PAUSED || state == State.RUNNING) {
        synchronized (breakpoints) {
          removed.addAll(breakpoints);
          breakpoints.clear();
        }
        if (state == State.PAUSED) {
          applyBreakpoints();
          // puszczamy wywolanie; jesli trwa dluzej niz timeout debuggera, to i tak pojdzie dalej samo
          call("dbms_debug.continue(ri, 0, " + INFO + ")");
        }
      }
      try (CallableStatement c = connection.prepareCall("begin dbms_debug.detach_session; end;")) {
        c.execute();
      }
    } catch (SQLException ex) {
      listener.message(ex.getMessage(), true);
    } finally {
      stop = true;
      info = null;
      stack = new ArrayList<Frame>();
      setState(State.DETACHED);
    }
  }

  private void keepAlive() {
    try (CallableStatement c = connection.prepareCall("declare ri dbms_debug.runtime_info; begin ? := dbms_debug.get_runtime_info(dbms_debug.info_getlineinfo, ri); end;")) {
      c.registerOutParameter(1, Types.INTEGER);
      c.execute();
      if (c.getInt(1) != 0) {
        // sesja aplikacji juz nie czeka (timeout, rozlaczenie)
        listener.message("DBMS_DEBUG.GET_RUNTIME_INFO " + c.getInt(1), true);
        info = null;
        stack = new ArrayList<Frame>();
        setState(State.WAITING);
      }
    } catch (SQLException ex) {
      listener.message(ex.getMessage(), true);
      info = null;
      stack = new ArrayList<Frame>();
      if (connectionLost(ex)) {
        stop = true;
        setState(State.DETACHED);
      } else {
        setState(State.WAITING);
      }
    }
  }

  private void applyBreakpoints() {
    List<Breakpoint> toRemove;
    synchronized (removed) {
      toRemove = new ArrayList<Breakpoint>(removed);
      removed.clear();
    }
    for (Breakpoint b : toRemove) {
      if (b.number >= 0) {
        try (CallableStatement c = connection.prepareCall("begin ? := dbms_debug.delete_breakpoint(?); end;")) {
          c.registerOutParameter(1, Types.INTEGER);
          c.setInt(2, b.number);
          c.execute();
          if (c.getInt(1) != 0) {
            listener.message(b.owner + "." + b.name + " : " + b.line + " - DBMS_DEBUG.DELETE_BREAKPOINT " + c.getInt(1), true);
          }
        } catch (SQLException ex) {
          listener.message(ex.getMessage(), true);
        }
        b.number = -1;
      }
    }
    for (Breakpoint b : getBreakpoints()) {
      if (b.number >= 0) {
        continue;
      }
      try (CallableStatement c = connection.prepareCall(
        "declare pi dbms_debug.program_info; bp binary_integer; begin pi.namespace := ?; pi.name := ?; pi.owner := ?; " +
        "? := dbms_debug.set_breakpoint(pi, ?, bp); ? := bp; end;")) {
        c.setInt(1, b.namespace);
        c.setString(2, b.name);
        c.setString(3, b.owner);
        c.registerOutParameter(4, Types.INTEGER);
        c.setInt(5, b.line);
        c.registerOutParameter(6, Types.INTEGER);
        c.execute();
        b.error = c.getInt(4);
        if (b.error == 0) {
          b.number = c.getInt(6);
        } else {
          listener.message(b.owner + "." + b.name + " : " + b.line + " - DBMS_DEBUG.SET_BREAKPOINT " + b.error, true);
        }
      } catch (SQLException ex) {
        b.error = -1;
        listener.message(ex.getMessage(), true);
      }
    }
  }

  /**
   * SYNCHRONIZE/CONTINUE
   * @return null - timeout (sesja aplikacji nie zatrzymala sie w czasie DEBUGGER_TIMEOUT)
   */
  private RuntimeInfo call(String call) {
    try (CallableStatement s = connection.prepareCall("declare ri dbms_debug.runtime_info; r binary_integer; begin r := " + call + "; " +
      "? := r; ? := ri.line#; ? := ri.program.owner; ? := ri.program.name; ? := ri.program.namespace; " +
      "? := ri.reason; ? := ri.terminated; ? := ri.stackdepth; ? := ri.breakpoint; ? := ri.oer; end;")) {
      for (int i = 1; i <= 10; i++) {
        s.registerOutParameter(i, i == 3 || i == 4 ? Types.VARCHAR : Types.INTEGER);
      }
      s.execute();
      int r = s.getInt(1);
      if (r == ERROR_TIMEOUT) {
        return null;
      }
      if (r != 0) {
        // np. sesja aplikacji rozlaczona - bez zasypywania dziennika i bez petli na pelnych obrotach
        if (r != lastError) {
          listener.message(call.substring(0, call.indexOf('(')).toUpperCase() + " " + r, true);
        }
        lastError = r;
        try {
          Thread.sleep(500);
        } catch (InterruptedException ex) {
          stop = true;
        }
        return null;
      }
      lastError = 0;
      RuntimeInfo ri = new RuntimeInfo();
      ri.line = s.getInt(2);
      ri.owner = s.getString(3);
      ri.name = s.getString(4);
      ri.namespace = s.getInt(5);
      ri.reason = s.getInt(6);
      ri.terminated = s.getInt(7) != 0;
      ri.depth = s.getInt(8);
      ri.breakpoint = s.getInt(9);
      ri.oer = s.getInt(10);
      return ri;
    } catch (SQLException ex) {
      listener.message(ex.getMessage(), true);
      if (connectionLost(ex)) {
        stop = true;
        setState(State.DETACHED);
      }
      return null;
    }
  }

  private boolean connectionLost(SQLException ex) {
    try {
      return connection.isClosed() || !connection.isValid(2);
    } catch (SQLException e) {
      return true;
    }
  }

  private List<Frame> backtrace() {
    List<Frame> result = new ArrayList<Frame>();
    // kazda ramka w osobnej linii, pola rozdzielone znakiem 0x1F; od gory stosu i z zapasem do limitu VARCHAR2 -
    // przy bardzo glebokiej rekurencji gubimy najwyzej najstarsze ramki
    try (CallableStatement s = connection.prepareCall("declare bt dbms_debug.backtrace_table; l varchar2(32767); f varchar2(4000); sep varchar2(1) := chr(31); " +
      "begin dbms_debug.print_backtrace(bt); for i in reverse 1 .. bt.count loop " +
      "f := substr(i || sep || bt(i).owner || sep || bt(i).name || sep || bt(i).namespace || sep || bt(i).line# || sep || " +
      "bt(i).entrypointname || sep || bt(i).libunittype || chr(10), 1, 4000); exit when nvl(length(l), 0) + length(f) > 32000; " +
      "l := l || f; end loop; ? := l; end;")) {
      s.registerOutParameter(1, Types.VARCHAR);
      s.execute();
      String text = s.getString(1);
      if (text != null) {
        for (String line : text.split("\n")) {
          String[] f = line.split("\u001f", -1);
          if (f.length < 7) {
            continue;
          }
          Frame frame = new Frame();
          frame.index = toInt(f[0]);
          frame.owner = empty(f[1]);
          frame.name = empty(f[2]);
          frame.namespace = toInt(f[3]);
          frame.line = toInt(f[4]);
          frame.entryPoint = empty(f[5]);
          frame.libunitType = toInt(f[6]);
          result.add(0, frame);
        }
      }
    } catch (SQLException ex) {
      listener.message(ex.getMessage(), true);
    }
    return result;
  }

  private Value value(int frame, String name) {
    try (CallableStatement c = connection.prepareCall("declare v varchar2(32767); begin ? := dbms_debug.get_value(?, ?, v, null); ? := v; end;")) {
      c.registerOutParameter(1, Types.INTEGER);
      c.setString(2, name);
      c.setInt(3, frame);
      c.registerOutParameter(4, Types.VARCHAR);
      c.execute();
      int r = c.getInt(1);
      if (r == ERROR_NO_SUCH_OBJECT && name.indexOf('.') > 0) {
        // zmienna pakietu: [wlasciciel.]pakiet.zmienna
        Value pv = packageValue(name);
        if (pv != null) {
          return pv;
        }
      }
      return new Value(name, r, r == 0 ? c.getString(4) : null);
    } catch (SQLException ex) {
      return new Value(name, -1, ex.getMessage());
    }
  }

  private Value packageValue(String name) throws SQLException {
    String[] p = name.split("\\.");
    if (p.length < 2 || p.length > 3) {
      return null;
    }
    String owner = p.length == 3 ? p[0].toUpperCase() : (info != null ? info.owner : null);
    String pkg = p[p.length - 2].toUpperCase();
    String var = p[p.length - 1];
    if (owner == null) {
      return null;
    }
    for (int ns : new int[] {NAMESPACE_PKG_BODY, NAMESPACE_PKGSPEC_OR_TOPLEVEL}) {
      try (CallableStatement c = connection.prepareCall("declare pi dbms_debug.program_info; v varchar2(32767); begin " +
        "pi.namespace := ?; pi.name := ?; pi.owner := ?; ? := dbms_debug.get_value(?, pi, v, null); ? := v; end;")) {
        c.setInt(1, ns);
        c.setString(2, pkg);
        c.setString(3, owner);
        c.registerOutParameter(4, Types.INTEGER);
        c.setString(5, var);
        c.registerOutParameter(6, Types.VARCHAR);
        c.execute();
        int r = c.getInt(4);
        if (r != ERROR_NO_SUCH_OBJECT) {
          return new Value(name, r, r == 0 ? c.getString(6) : null);
        }
      }
    }
    return null;
  }

  private void readConstants() throws SQLException {
    try (CallableStatement c = connection.prepareCall("begin ? := dbms_debug.error_nullvalue; ? := dbms_debug.error_no_such_object; " +
      "? := dbms_debug.error_no_debug_info; ? := dbms_debug.error_bogus_frame; ? := dbms_debug.error_indexed_table; " +
      "? := dbms_debug.error_nullcollection; ? := dbms_debug.error_timeout; ? := dbms_debug.reason_interpreter_starting; " +
      "? := dbms_debug.reason_exception; end;")) {
      for (int i = 1; i <= 9; i++) {
        c.registerOutParameter(i, Types.INTEGER);
      }
      c.execute();
      VALUE_NULL = c.getInt(1);
      ERROR_NO_SUCH_OBJECT = c.getInt(2);
      ERROR_NO_DEBUG_INFO = c.getInt(3);
      ERROR_BOGUS_FRAME = c.getInt(4);
      ERROR_INDEXED_TABLE = c.getInt(5);
      ERROR_NULLCOLLECTION = c.getInt(6);
      ERROR_TIMEOUT = c.getInt(7);
      reasonStarting = c.getInt(8);
      reasonException = c.getInt(9);
    }
  }

  public boolean isException(RuntimeInfo ri) {
    return ri != null && ri.reason == reasonException;
  }

  public RuntimeInfo getInfo() {
    return info;
  }

  public List<Frame> getStack() {
    return stack;
  }

  private void setState(State state) {
    this.state = state;
    fireState();
  }

  private void fireState() {
    listener.stateChanged(state, info, stack);
  }

  private static int toInt(String s) {
    try {
      return Integer.parseInt(s.trim());
    } catch (NumberFormatException ex) {
      return 0;
    }
  }

  private static String empty(String s) {
    return s == null || s.length() == 0 ? null : s;
  }

  /** Nazwy uzywane w podgladzie (DBMS_DEBUG.NAMESPACE_*) -> typy obiektow w ALL_SOURCE */
  public static String[] sourceTypes(int namespace) {
    switch (namespace) {
      case NAMESPACE_PKG_BODY: return new String[] {"PACKAGE BODY", "TYPE BODY"};
      case NAMESPACE_TRIGGER: return new String[] {"TRIGGER"};
      default: return new String[] {"PROCEDURE", "FUNCTION", "PACKAGE", "TYPE"};
    }
  }

  /** Typ obiektu (ALL_SOURCE.TYPE) -> DBMS_DEBUG.NAMESPACE_* */
  public static int namespace(String type) {
    if (type == null) {
      return 0;
    }
    if (type.endsWith("BODY")) {
      return NAMESPACE_PKG_BODY;
    }
    if ("TRIGGER".equals(type)) {
      return NAMESPACE_TRIGGER;
    }
    return NAMESPACE_PKGSPEC_OR_TOPLEVEL;
  }

  /** Zmienne jako mapa nazwa -> tekst (do testow) */
  public static Map<String, String> toMap(List<Value> values) {
    Map<String, String> m = new LinkedHashMap<String, String>();
    for (Value v : values) {
      m.put(v.name, v.code == 0 ? v.value : v.code == VALUE_NULL ? "NULL" : "#" + v.code);
    }
    return m;
  }
}
