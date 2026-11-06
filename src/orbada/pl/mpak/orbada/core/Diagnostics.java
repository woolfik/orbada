package pl.mpak.orbada.core;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import javax.management.ObjectName;
import pl.mpak.orbada.Consts;
import pl.mpak.usedb.core.Database;
import pl.mpak.usedb.core.DatabaseManager;
import pl.mpak.usedb.core.DatabaseManagerEvent;
import pl.mpak.usedb.core.DatabaseManagerListener;

/**
 * ORBADA 1.3.2: tryb diagnostyczny (-Dorbada.diag=katalog, skrypt orbada-diag.bat) - zawieszenia i brak pamieci
 * przy wielu polaczeniach. Osobny watek zapisuje do pliku w katalogu:
 * <ul>
 * <li>co minute: sterta, pule pamieci, GC, watki, liczba polaczen, wielkosc kolejek timerow,</li>
 * <li>polaczenia i rozlaczenia (tylko typ sterownika - bez nazw, adresow i SQL),</li>
 * <li>zawieszenie watku Swing (EDT) dluzsze niz 3 s: pelny zrzut watkow z blokadami (powtarzany co 30 s),</li>
 * <li>zakleszczenia watkow (findDeadlockedThreads),</li>
 * <li>histogram klas: co 30 min i gdy po GC stara generacja przekracza 85% (nie czesciej niz co 5 min).</li>
 * </ul>
 * Bez parametru orbada.diag nic nie jest uruchamiane.
 */
public final class Diagnostics implements Runnable {

  private static final long STALL_MS = 3000;
  private static final long STALL_REPEAT_MS = 30000;
  private static final long STATS_MS = 60000;
  private static final long HISTOGRAM_MS = 30 * 60000;
  private static final long HISTOGRAM_MIN_MS = 5 * 60000;
  private static final double OLD_GEN_THRESHOLD = 0.85;
  private static final int HISTOGRAM_LINES = 60;
  /** Limit pliku - dalej tylko statystyki co minute (bez zrzutow) */
  private static final long MAX_DUMP_BYTES = 200L * 1024 * 1024;

  private static Diagnostics instance;

  private final File file;
  private final PrintWriter out;
  private final SimpleDateFormat time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
  private final ThreadMXBean threads = ManagementFactory.getThreadMXBean();
  private volatile long probeSent;
  private volatile long probeDone;
  private long stallStart;
  private long lastStallDump;
  private long lastStats;
  private long lastHistogram;
  private boolean deadlockLogged;

  private Diagnostics(File file) throws Exception {
    this.file = file;
    this.out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file, true), "UTF-8"), true);
  }

  /** Uruchamia diagnostyke, jesli podano -Dorbada.diag (katalog albo "1" - katalog diag obok konfiguracji) */
  public static synchronized void startIfRequested(String configPath) {
    String dir = System.getProperty("orbada.diag");
    if (dir == null || instance != null) {
      return;
    }
    try {
      File parent = configPath != null ? new File(configPath).getParentFile() : null;
      if (parent == null) {
        parent = new File(System.getProperty("user.home"), ".orbada");
      }
      File d = "1".equals(dir) || dir.trim().length() == 0 ? new File(parent, "diag") : new File(dir);
      d.mkdirs();
      instance = new Diagnostics(new File(d, "orbada-diag-" + new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()) + ".log"));
      instance.header();
      DatabaseManager.getManager().addDatabaseManagerListener(new DatabaseManagerListener() {
        @Override
        public void databaseAdded(DatabaseManagerEvent e) {
          instance.connection("POLACZENIE +", e);
        }
        @Override
        public void databaseRemoved(DatabaseManagerEvent e) {
          instance.connection("POLACZENIE -", e);
        }
      });
      Thread t = new Thread(instance, "orbada-diag");
      t.setDaemon(true);
      t.start();
    } catch (Throwable ex) {
      System.err.println("Diagnostyka niedostepna: " + ex);
    }
  }

  private synchronized void log(String s) {
    out.println(time.format(new Date()) + " " + s);
  }

  private void header() {
    Runtime rt = Runtime.getRuntime();
    log("START diagnostyki ORBADA " + Consts.orbadaVersion);
    log("Java " + System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ", " + System.getProperty("java.vm.name") + ")" +
      ", OS " + System.getProperty("os.name") + " " + System.getProperty("os.version") + " " + System.getProperty("os.arch") +
      ", procesory " + rt.availableProcessors() + ", max sterta " + mb(rt.maxMemory()));
    log("Opcje JVM: " + ManagementFactory.getRuntimeMXBean().getInputArguments());
    log("Plik: " + file.getAbsolutePath());
  }

  private void connection(String what, DatabaseManagerEvent e) {
    Database db = e.getDatabase();
    log(what + " typ " + (db != null ? db.getDriverType() : "?") + ", polaczen razem " + databaseCount());
  }

  private static int databaseCount() {
    try {
      return DatabaseManager.getManager().getDatabaseCount();
    } catch (Throwable ex) {
      return -1;
    }
  }

  @Override
  public void run() {
    lastStats = System.currentTimeMillis() - STATS_MS;
    lastHistogram = System.currentTimeMillis();
    int tick = 0;
    while (true) {
      try {
        Thread.sleep(1000);
        long now = System.currentTimeMillis();
        checkEdt(now);
        if (now - lastStats >= STATS_MS) {
          lastStats = now;
          stats();
        }
        if (++tick % 10 == 0) {
          checkDeadlock();
        }
        checkHistogram(now);
      } catch (InterruptedException ex) {
        return;
      } catch (Throwable ex) {
        log("BLAD diagnostyki: " + ex);
      }
    }
  }

  /** Probka EDT: invokeLater ustawia probeDone; brak odpowiedzi przez STALL_MS = zawieszenie */
  private void checkEdt(long now) {
    if (probeSent == 0 || probeDone >= probeSent) {
      if (stallStart != 0) {
        log("EDT ODWIESZONY po " + (now - stallStart) / 1000.0 + " s");
        stallStart = 0;
      }
      probeSent = now;
      java.awt.EventQueue.invokeLater(new Runnable() {
        @Override
        public void run() {
          probeDone = System.currentTimeMillis();
        }
      });
      return;
    }
    long waiting = now - probeSent;
    if (waiting >= STALL_MS) {
      if (stallStart == 0) {
        stallStart = probeSent;
        log("EDT ZAWIESZONY od " + waiting / 1000.0 + " s, polaczen " + databaseCount() + ", " + heapLine());
        threadDump("zawieszenie EDT");
        lastStallDump = now;
      } else if (now - lastStallDump >= STALL_REPEAT_MS && file.length() <= MAX_DUMP_BYTES) {
        log("EDT nadal zawieszony od " + waiting / 1000.0 + " s, " + heapLine());
        threadDump("zawieszenie EDT (ponownie)");
        lastStallDump = now;
      }
    }
  }

  private void checkDeadlock() {
    long[] ids = threads.isSynchronizerUsageSupported() ? threads.findDeadlockedThreads() : threads.findMonitorDeadlockedThreads();
    if (ids != null && ids.length > 0) {
      if (!deadlockLogged) {
        deadlockLogged = true;
        log("ZAKLESZCZENIE watkow: " + ids.length);
        threadDump("zakleszczenie");
      }
    } else {
      deadlockLogged = false;
    }
  }

  private void checkHistogram(long now) {
    boolean high = false;
    for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
      if (pool.getType() == MemoryType.HEAP && isOldGen(pool.getName())) {
        MemoryUsage after = pool.getCollectionUsage();
        MemoryUsage usage = pool.getUsage();
        long max = usage != null && usage.getMax() > 0 ? usage.getMax() : Runtime.getRuntime().maxMemory();
        if (after != null && after.getUsed() > OLD_GEN_THRESHOLD * max) {
          high = true;
        }
      }
    }
    if ((high && now - lastHistogram >= HISTOGRAM_MIN_MS) || now - lastHistogram >= HISTOGRAM_MS) {
      lastHistogram = now;
      histogram(high ? "stara generacja po GC > 85%" : "okresowy");
    }
  }

  private static boolean isOldGen(String name) {
    String n = name.toLowerCase();
    return n.contains("old") || n.contains("tenured");
  }

  private static String mb(long bytes) {
    return bytes < 0 ? "?" : (bytes / (1024 * 1024)) + " MB";
  }

  private static String heapLine() {
    MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
    return "sterta " + mb(heap.getUsed()) + " / " + mb(heap.getCommitted()) + " (max " + mb(heap.getMax()) + ")";
  }

  private void stats() {
    StringBuilder sb = new StringBuilder("STAT ").append(heapLine());
    MemoryUsage nonHeap = ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage();
    sb.append(", poza sterta ").append(mb(nonHeap.getUsed()));
    for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
      if (pool.getType() == MemoryType.HEAP) {
        MemoryUsage after = pool.getCollectionUsage();
        MemoryUsage usage = pool.getUsage();
        sb.append(", [").append(pool.getName()).append(" ").append(usage != null ? mb(usage.getUsed()) : "?")
          .append(after != null ? ", po GC " + mb(after.getUsed()) : "").append("]");
      }
    }
    for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
      sb.append(", GC ").append(gc.getName()).append(" ").append(gc.getCollectionCount()).append("x/").append(gc.getCollectionTime()).append(" ms");
    }
    sb.append(", watki ").append(threads.getThreadCount()).append(", polaczen ").append(databaseCount());
    sb.append(", kolejki timerow ").append(timerQueues());
    log(sb.toString());
  }

  /** Liczba timerow w kolejkach (pole czytane bez blokady - zablokowana kolejka nie moze zatrzymac diagnostyki) */
  private static String timerQueues() {
    StringBuilder sb = new StringBuilder("{");
    try {
      Map<String, pl.mpak.util.timer.TimerQueue> map = pl.mpak.util.timer.TimerManager.getLock();
      Field f = pl.mpak.util.timer.TimerQueue.class.getDeclaredField("timerArray");
      f.setAccessible(true);
      for (Object name : new ArrayList<Object>(map.keySet())) {
        pl.mpak.util.timer.TimerQueue q = map.get(name);
        Object list = q != null ? f.get(q) : null;
        sb.append(sb.length() > 1 ? ", " : "").append(name).append("=").append(list instanceof List ? ((List<?>)list).size() : -1)
          .append(q != null ? "/" + q.getState() : "");
      }
    } catch (Throwable ex) {
      sb.append(ex.getClass().getSimpleName());
    }
    return sb.append("}").toString();
  }

  private void histogram(String reason) {
    if (file.length() > MAX_DUMP_BYTES) {
      return;
    }
    try {
      Object result = ManagementFactory.getPlatformMBeanServer().invoke(new ObjectName("com.sun.management:type=DiagnosticCommand"),
        "gcClassHistogram", new Object[] {new String[0]}, new String[] {String[].class.getName()});
      String[] lines = String.valueOf(result).split("\n");
      StringBuilder sb = new StringBuilder("HISTOGRAM klas (" + reason + "), " + heapLine() + "\n");
      int shown = Math.min(lines.length, HISTOGRAM_LINES + 3);
      for (int i = 0; i < shown; i++) {
        sb.append("  ").append(lines[i]).append("\n");
      }
      // ostatnia linia: Total
      if (lines.length > shown) {
        sb.append("  ").append(lines[lines.length - 1]).append("\n");
      }
      log(sb.toString());
    } catch (Throwable ex) {
      log("HISTOGRAM niedostepny: " + ex);
    }
  }

  /** Nazwa watku bez adresow polaczen (jdbc:..., uzytkownik@host) i skrocona */
  private static String safeName(String name) {
    if (name == null) {
      return null;
    }
    String s = name.replaceAll("jdbc:\\S*", "jdbc:***").replaceAll("\\S+@\\S+", "***@***");
    return s.length() > 80 ? s.substring(0, 80) + "..." : s;
  }

  /** Pelny zrzut watkow (ThreadInfo.toString obcina stos do 8 ramek) z blokadami */
  private void threadDump(String reason) {
    if (file.length() > MAX_DUMP_BYTES) {
      log("ZRZUT pominiety - plik przekroczyl " + mb(MAX_DUMP_BYTES));
      return;
    }
    StringBuilder sb = new StringBuilder("ZRZUT WATKOW (" + reason + ")\n");
    for (ThreadInfo ti : threads.dumpAllThreads(threads.isObjectMonitorUsageSupported(), threads.isSynchronizerUsageSupported())) {
      if (ti == null) {
        continue;
      }
      sb.append("\"").append(safeName(ti.getThreadName())).append("\" id=").append(ti.getThreadId()).append(" ").append(ti.getThreadState());
      if (ti.getLockName() != null) {
        sb.append(" czeka na ").append(ti.getLockName());
      }
      if (ti.getLockOwnerName() != null) {
        sb.append(" trzymana przez \"").append(safeName(ti.getLockOwnerName())).append("\" id=").append(ti.getLockOwnerId());
      }
      sb.append("\n");
      StackTraceElement[] stack = ti.getStackTrace();
      MonitorInfo[] monitors = ti.getLockedMonitors();
      for (int i = 0; i < stack.length; i++) {
        sb.append("    at ").append(stack[i]).append("\n");
        for (MonitorInfo mi : monitors) {
          if (mi.getLockedStackDepth() == i) {
            sb.append("      - trzyma ").append(mi).append("\n");
          }
        }
      }
      for (LockInfo li : ti.getLockedSynchronizers()) {
        sb.append("      - trzyma synchronizator ").append(li).append("\n");
      }
      sb.append("\n");
    }
    log(sb.toString());
  }

}
