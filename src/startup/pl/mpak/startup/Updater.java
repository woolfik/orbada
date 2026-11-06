package pl.mpak.startup;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * ORBADA 1.3.2 (ROADMAP F7): podmiana plikow instalacji na nowa wersje, uruchamiana przez Orbade jako osobny
 * proces z kopii startup.jar nowej wersji (update/updater.jar) - dzialajaca Orbada blokuje swoje JAR-y.
 *
 * Protokol (nie zmieniac - starsza Orbada uruchamia Updater z nowej wersji):
 *   java -cp update/updater.jar pl.mpak.startup.Updater KATALOG_INSTALACJI KATALOG_NOWEJ_WERSJI PLIK_BLOKADY RESTART
 * Czeka, az Orbada zwolni blokade pliku PLIK_BLOKADY (zamkniecie programu), kopiuje zastepowane pliki do
 * update/backup, kopiuje nowe pliki; przy bledzie przywraca kopie. RESTART=true - na koncu uruchamia Orbade.
 * Przebieg w update/update.log.
 */
public class Updater {

  private static PrintWriter log;
  /** Po bledzie kopiowania poprzednia wersja przywrocona w calosci - mozna ja uruchomic */
  private static boolean restored = false;

  public static void main(String[] args) {
    if (args.length < 4) {
      System.err.println("Updater INSTALL_DIR NEW_DIR LOCK_FILE RESTART");
      System.exit(2);
    }
    File install = new File(args[0]).getAbsoluteFile();
    File source = new File(args[1]).getAbsoluteFile();
    File lockFile = new File(args[2]).getAbsoluteFile();
    boolean restart = Boolean.parseBoolean(args[3]);
    File updateDir = source.getParentFile();
    int code = 0;
    try {
      log = new PrintWriter(new FileOutputStream(new File(updateDir, "update.log"), true), true);
      log("Aktualizacja " + install + " z " + source);
      waitForOrbada(lockFile);
      apply(install, source, new File(updateDir, "backup"));
      deleteTree(source);
      log("Aktualizacja zakonczona");
    } catch (Throwable ex) {
      code = 1;
      log("BLAD: " + ex);
      if (log != null) {
        ex.printStackTrace(log);
      }
    }
    // po bledzie tylko gdy przywrocono poprzednia wersje - inaczej pliki moga byc niespojne
    if (restart && (code == 0 || restored)) {
      try {
        start(install);
      } catch (IOException ex) {
        log("Nie udalo sie uruchomic Orbady: " + ex);
      }
    }
    if (log != null) {
      log.close();
    }
    System.exit(code);
  }

  private static void log(String s) {
    String line = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + " " + s;
    System.out.println(line);
    if (log != null) {
      log.println(line);
    }
  }

  /** Orbada trzyma blokade pliku do konca dzialania JVM - zwolnienie oznacza zamkniecie (takze awaryjne) */
  private static void waitForOrbada(File lockFile) throws IOException, InterruptedException {
    log("Czekam na zamkniecie Orbady");
    try (RandomAccessFile raf = new RandomAccessFile(lockFile, "rw")) {
      while (true) {
        FileLock lock = raf.getChannel().tryLock();
        if (lock != null) {
          lock.release();
          break;
        }
        Thread.sleep(1000);
      }
    }
    // system zwalnia pliki JVM chwile po zakonczeniu procesu (Windows, antywirus)
    Thread.sleep(2000);
  }

  /** Kopiuje pliki nowej wersji; zastepowane najpierw do backup, przy bledzie przywraca stan sprzed zmian */
  static void apply(File install, File source, File backup) throws IOException {
    deleteTree(backup);
    List<String> files = new ArrayList<String>();
    list(source, "", files);
    if (!files.contains("startup.jar") || !files.contains("Orbada.jar")) {
      throw new IOException("Niepelna paczka (brak startup.jar albo Orbada.jar): " + source);
    }
    List<String> replaced = new ArrayList<String>();
    List<String> created = new ArrayList<String>();
    try {
      for (String rel : files) {
        File target = new File(install, rel);
        if (target.exists()) {
          copy(target, new File(backup, rel));
          replaced.add(rel);
        } else {
          created.add(rel);
        }
        copy(new File(source, rel), target);
      }
      log("Skopiowano " + files.size() + " plikow (zastapione: " + replaced.size() + ", nowe: " + created.size() + ")");
    } catch (IOException ex) {
      log("Blad kopiowania - przywracam poprzednia wersje: " + ex);
      for (String rel : replaced) {
        copy(new File(backup, rel), new File(install, rel));
      }
      for (String rel : created) {
        new File(install, rel).delete();
      }
      restored = true;
      throw ex;
    }
  }

  /** Kopia z kilkoma probami - plik moze byc chwilowo otwarty (antywirus, indeksowanie) */
  private static void copy(File from, File to) throws IOException {
    to.getParentFile().mkdirs();
    IOException last = null;
    for (int i = 0; i < 10; i++) {
      try {
        Files.copy(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
        return;
      } catch (IOException ex) {
        last = ex;
        try {
          Thread.sleep(500);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          break;
        }
      }
    }
    throw last;
  }

  private static void list(File dir, String prefix, List<String> result) {
    File[] files = dir.listFiles();
    if (files == null) {
      return;
    }
    for (File f : files) {
      if (f.isDirectory()) {
        list(f, prefix + f.getName() + "/", result);
      } else {
        result.add(prefix + f.getName());
      }
    }
  }

  static void deleteTree(File f) {
    if (Files.isSymbolicLink(f.toPath())) {
      f.delete();
      return;
    }
    File[] children = f.listFiles();
    if (children != null) {
      for (File c : children) {
        deleteTree(c);
      }
    }
    f.delete();
  }

  /** Uruchamia Orbade tak jak skrot: orbada.bat (Windows) albo orbada.sh */
  private static void start(File install) throws IOException {
    ProcessBuilder pb;
    if (System.getProperty("os.name", "").toLowerCase().startsWith("windows")) {
      pb = new ProcessBuilder("cmd", "/c", "start", "\"\"", "/d", install.getPath(), "orbada.bat");
    } else {
      pb = new ProcessBuilder("sh", new File(install, "orbada.sh").getPath());
    }
    pb.directory(install);
    pb.start();
    log("Uruchomiono Orbade");
  }

}
