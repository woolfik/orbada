package pl.mpak.orbada.core;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Enumeration;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.json.JSONObject;
import pl.mpak.orbada.Consts;

/**
 * ORBADA 1.3.2 (ROADMAP F7): automatyczna aktualizacja z GitHub Release. Paczka (downloadUrl z changes.json)
 * pobierana do update/, sprawdzana suma SHA-256 (sha256 z changes.json), rozpakowywana do update/new;
 * pliki podmienia pl.mpak.startup.Updater z nowego startup.jar uruchomiony jako osobny proces, kiedy Orbada
 * sie zamknie (dzialajaca Orbada blokuje swoje JAR-y). Profil ~/.orbada bez zmian.
 */
public class AutoUpdate {

  /** Postep pobierania: bajty pobrane i calkowite (-1 - nieznane) */
  public interface Progress {
    void progress(long done, long total);
  }

  private final static String UPDATER_CLASS = "pl.mpak.startup.Updater";
  /** Blokada (i jej plik) trzymana do konca dzialania JVM - Updater czeka na jej zwolnienie */
  private static RandomAccessFile runningFile;
  private static FileLock runningLock;

  private AutoUpdate() {
  }

  /** Katalog instalacji: tam, skad zaladowano Orbada.jar; awaryjnie biezacy (startup.jar uruchamia Orbade z niego) */
  public static File installDir() {
    try {
      File jar = new File(AutoUpdate.class.getProtectionDomain().getCodeSource().getLocation().toURI());
      if (jar.isFile() && jar.getName().equalsIgnoreCase("Orbada.jar")) {
        return jar.getParentFile();
      }
    } catch (Exception ex) {
      // np. brak CodeSource - nizej katalog biezacy
    }
    return new File("").getAbsoluteFile();
  }

  private static File updateDir() {
    return new File(installDir(), "update");
  }

  private static File newDir() {
    return new File(updateDir(), "new");
  }

  private static File readyFile() {
    return new File(updateDir(), "ready");
  }

  /**
   * Czy wersje z changes.json mozna zainstalowac automatycznie: paczka i suma w opisie wersji, Orbada
   * uruchomiona z katalogu instalacji (startup.jar, Orbada.jar) i katalog zapisywalny (np. nie Program Files).
   */
  public static boolean isAvailable(JSONObject version) {
    if (version == null || !version.has("downloadUrl") || !version.has("sha256")) {
      return false;
    }
    File install = installDir();
    if (!new File(install, "startup.jar").isFile() || !new File(install, "Orbada.jar").isFile()) {
      return false;
    }
    try {
      File probe = File.createTempFile("orbada", ".tmp", install);
      if (!probe.delete()) {
        // chwilowo trzymany (antywirus) - usuniety najpozniej przy zamknieciu
        probe.deleteOnExit();
      }
      return true;
    } catch (IOException ex) {
      return false;
    }
  }

  /**
   * Pobiera i rozpakowuje wersje (wolac poza watkiem GUI); po powodzeniu aktualizacja czeka w update/new.
   * @throws IOException z komunikatem (blad sieci, zla suma, uszkodzona paczka)
   */
  public static synchronized void prepare(JSONObject version, Progress progress) throws IOException {
    checkNotScheduled();
    String url = version.optString("downloadUrl", "");
    String sha256 = version.optString("sha256", "").trim().toLowerCase(Locale.ROOT);
    if (!url.startsWith("https://")) {
      throw new IOException("Adres paczki musi byc https: " + url);
    }
    if (!sha256.matches("[0-9a-f]{64}")) {
      throw new IOException("Niepoprawna suma SHA-256 w opisie wersji: " + sha256);
    }
    File dir = updateDir();
    if (!dir.isDirectory() && !dir.mkdirs()) {
      throw new IOException("Nie mozna utworzyc katalogu " + dir);
    }
    // druga Orbada z tej instalacji nie moze w tym czasie pisac do update/new
    try (RandomAccessFile raf = new RandomAccessFile(new File(dir, "prepare.lock"), "rw");
         FileLock lock = raf.getChannel().tryLock()) {
      if (lock == null) {
        throw new IOException("Aktualizacja jest pobierana w innym oknie Orbady");
      }
      readyFile().delete();
      File zip = new File(dir, "orbada-update.zip");
      download(url, sha256, zip, progress);
      try {
        unpack(zip);
      } catch (IOException ex) {
        // nieudana paczka nie zostaje na dysku (ani do instalacji przy nastepnym starcie)
        deleteTree(newDir());
        zip.delete();
        throw ex;
      }
      Files.write(readyFile().toPath(), version.optString("version", "?").getBytes(StandardCharsets.UTF_8));
    }
  }

  private static void unpack(File zip) throws IOException {
    deleteTree(newDir());
    extract(zip, newDir());
    if (!new File(newDir(), "startup.jar").isFile() || !new File(newDir(), "Orbada.jar").isFile()) {
      throw new IOException("Paczka nie zawiera Orbady (startup.jar, Orbada.jar)");
    }
    // pliki podmienia Updater nowej wersji - paczka sprzed 1.3.2 go nie ma (wtedy tylko strona pobierania)
    try (ZipFile startup = new ZipFile(new File(newDir(), "startup.jar"))) {
      if (startup.getEntry(UPDATER_CLASS.replace('.', '/') + ".class") == null) {
        throw new IOException("Ta wersja nie obsluguje automatycznej instalacji - pobierz ja ze strony wydan");
      }
    }
    zip.delete();
  }

  private static void download(String address, String sha256, File target, Progress progress) throws IOException {
    File part = new File(target.getPath() + ".part");
    MessageDigest md;
    try {
      md = MessageDigest.getInstance("SHA-256");
    } catch (Exception ex) {
      throw new IOException(ex);
    }
    HttpURLConnection connection = (HttpURLConnection)new URL(address).openConnection();
    // GitHub przekierowuje pobranie z releases na serwer plikow (https -> https - HttpURLConnection podaza sam)
    connection.setInstanceFollowRedirects(true);
    connection.setConnectTimeout(15000);
    connection.setReadTimeout(120000);
    connection.setRequestProperty("User-Agent", "Orbada/" + Consts.orbadaVersion);
    try {
      if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
        throw new IOException("HTTP " + connection.getResponseCode() + " " + address);
      }
      long total = connection.getContentLengthLong();
      long done = 0;
      byte[] buf = new byte[65536];
      try (InputStream in = connection.getInputStream(); OutputStream out = new FileOutputStream(part)) {
        int n;
        while ((n = in.read(buf)) > 0) {
          out.write(buf, 0, n);
          md.update(buf, 0, n);
          done += n;
          if (progress != null) {
            progress.progress(done, total);
          }
        }
      }
    } catch (IOException ex) {
      // zerwane pobranie - bez niepelnego pliku na dysku
      part.delete();
      throw ex;
    } finally {
      connection.disconnect();
    }
    StringBuilder hex = new StringBuilder();
    for (byte b : md.digest()) {
      hex.append(String.format("%02x", b));
    }
    if (!hex.toString().equals(sha256)) {
      part.delete();
      throw new IOException("Suma SHA-256 pobranej paczki nie zgadza sie z opisem wersji - paczka usunieta");
    }
    Files.move(part.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
  }

  /**
   * Rozpakowuje paczke bez wspolnego katalogu glownego (orbada/); separatory "/" i "\" (zip z Compress-Archive).
   * Wpisy wychodzace poza katalog docelowy (../, sciezki bezwzgledne) przerywaja rozpakowanie.
   */
  static void extract(File zipFile, File dest) throws IOException {
    String root = null;
    try (ZipFile zip = new ZipFile(zipFile)) {
      for (Enumeration<? extends ZipEntry> e = zip.entries(); e.hasMoreElements();) {
        String name = e.nextElement().getName().replace('\\', '/');
        String first = name.indexOf('/') > 0 ? name.substring(0, name.indexOf('/') + 1) : "";
        root = root == null ? first : root.equals(first) ? root : "";
      }
      if (root == null) {
        throw new IOException("Pusta paczka");
      }
      String destPath = dest.getCanonicalPath() + File.separator;
      for (Enumeration<? extends ZipEntry> e = zip.entries(); e.hasMoreElements();) {
        ZipEntry entry = e.nextElement();
        String name = entry.getName().replace('\\', '/').substring(root.length());
        if (name.length() == 0) {
          continue;
        }
        File target = new File(dest, name);
        if (!target.getCanonicalPath().startsWith(destPath)) {
          throw new IOException("Niedozwolona sciezka w paczce: " + entry.getName());
        }
        if (entry.isDirectory() || name.endsWith("/")) {
          target.mkdirs();
          continue;
        }
        target.getParentFile().mkdirs();
        try (InputStream in = zip.getInputStream(entry)) {
          Files.copy(in, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
      }
    }
  }

  /** Jeden Updater na sesje: uruchomiony czeka na zamkniecie Orbady i korzysta z update/new */
  private static void checkNotScheduled() throws IOException {
    if (runningLock != null) {
      throw new IOException("Aktualizacja czeka juz na zamkniecie Orbady - zamknij program, aby ja zainstalowac");
    }
  }

  /** Usuwa katalog z zawartoscia; dowiazanie usuwane jako samo dowiazanie */
  private static void deleteTree(File f) {
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

  /** Wersja pobrana i gotowa do instalacji albo null */
  public static String pendingVersion() {
    File ready = readyFile();
    if (!ready.isFile() || !new File(newDir(), "startup.jar").isFile()) {
      return null;
    }
    try {
      return new String(Files.readAllBytes(ready.toPath()), StandardCharsets.UTF_8).trim();
    } catch (IOException ex) {
      return null;
    }
  }

  /**
   * Uruchamia Updater z nowego startup.jar (kopia w update/updater.jar - plik w instalacji zostanie zastapiony);
   * Updater czeka na zamkniecie Orbady. Wywolujacy zamyka Orbade (od razu albo pozniej - przy restart=false).
   */
  public static synchronized void launchUpdater(boolean restart) throws IOException {
    checkNotScheduled();
    File dir = updateDir();
    File lock = new File(dir, "orbada.lock");
    // plik i kanal w polach statycznych do konca JVM - blokade zwalnia dopiero zamkniecie procesu; zajeta -
    // inna Orbada z tej instalacji juz zaplanowala aktualizacje
    RandomAccessFile raf = new RandomAccessFile(lock, "rw");
    FileLock l = raf.getChannel().tryLock();
    if (l == null) {
      raf.close();
      throw new IOException("Aktualizacja jest juz w toku (zablokowany " + lock + ")");
    }
    try {
      // updater.jar dopiero po blokadzie - inny czekajacy Updater moglby go trzymac otwartego
      File updater = new File(dir, "updater.jar");
      Files.copy(new File(newDir(), "startup.jar").toPath(), updater.toPath(), StandardCopyOption.REPLACE_EXISTING);
      boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
      File java = new File(System.getProperty("java.home"), "bin" + File.separator + (windows ? "javaw.exe" : "java"));
      ProcessBuilder pb = new ProcessBuilder(java.getPath(), "-cp", updater.getPath(), UPDATER_CLASS,
        installDir().getPath(), newDir().getPath(), lock.getPath(), String.valueOf(restart));
      pb.directory(dir);
      pb.redirectErrorStream(true);
      pb.redirectOutput(new File(dir, "updater.out"));
      pb.start();
    } catch (IOException ex) {
      // Updater nie wystartowal - blokada zwolniona, mozna sprobowac ponownie
      l.release();
      raf.close();
      throw ex;
    }
    runningFile = raf;
    runningLock = l;
    readyFile().delete();
  }

}
