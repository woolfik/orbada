package pl.mpak.orbada.util;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * ORBADA Fuerteventura (F1): pliki tnsnames.ora - wyszukiwanie katalogu i lista aliasow.
 * <p>Obsluguje komentarze (#), kilka aliasow dla jednego opisu (A, B = (...)), dowolnie zagniezdzone
 * nawiasy i dolaczanie plikow (IFILE = sciezka). Nie wymaga klienta Oracle.
 */
public final class TnsNames {

  /** Wlasnosc polaczenia ojdbc wskazujaca katalog z tnsnames.ora */
  public final static String tnsAdminProperty = "oracle.net.tns_admin";
  public final static String fileName = "tnsnames.ora";

  /** URL ojdbc "thin" z samym aliasem TNS (bez host:port, //, opisu w nawiasach) */
  private final static Pattern aliasUrl = Pattern.compile("(?i)jdbc:oracle:thin:@[A-Za-z0-9_.$#-]+");
  private final static int maxIncludeDepth = 8;
  /** Wielkosc liter w sciezkach nie ma znaczenia tylko na Windows */
  private final static boolean windows = System.getProperty("os.name", "").toLowerCase().startsWith("windows");

  private TnsNames() {
  }

  /**
   * Katalogi, w ktorych szukamy tnsnames.ora, w kolejnosci: wlasnosc polaczenia oracle.net.tns_admin,
   * wlasnosc systemowa, zmienna TNS_ADMIN, ORACLE_HOME\network\admin. Bez duplikatow; tylko istniejace.
   */
  public static List<File> candidateDirs(Properties connectionProperties) {
    List<String> paths = new ArrayList<String>();
    if (connectionProperties != null) {
      paths.add(connectionProperties.getProperty(tnsAdminProperty)); // puste wartosci odrzucane nizej
    }
    paths.add(System.getProperty(tnsAdminProperty));
    paths.add(System.getenv("TNS_ADMIN"));
    String oracleHome = System.getenv("ORACLE_HOME");
    if (oracleHome != null) {
      paths.add(new File(new File(oracleHome, "network"), "admin").getPath());
    }
    List<File> result = new ArrayList<File>();
    Set<String> seen = new HashSet<String>();
    for (String p : paths) {
      if (p == null || p.trim().length() == 0) {
        continue;
      }
      File dir = new File(p.trim());
      if (dir.isDirectory() && seen.add(windows ? dir.getAbsolutePath().toLowerCase() : dir.getAbsolutePath())) {
        result.add(dir);
      }
    }
    return result;
  }

  /** Pierwszy katalog z kandydatow, ktory zawiera tnsnames.ora, albo null */
  public static File findTnsAdmin(Properties connectionProperties) {
    for (File dir : candidateDirs(connectionProperties)) {
      if (new File(dir, fileName).isFile()) {
        return dir;
      }
    }
    return null;
  }

  /**
   * Gdy URL to sam alias TNS, a polaczenie nie wskazuje katalogu tnsnames.ora, ustawia
   * oracle.net.tns_admin na pierwszy znaleziony katalog (ojdbc sam go nie szuka w ORACLE_HOME).
   * @return ustawiony katalog albo null, gdy nic nie zmieniono
   */
  public static File applyDefaultTnsAdmin(String url, Properties connectionProperties) {
    if (url == null || connectionProperties == null || !aliasUrl.matcher(url.trim()).matches()) {
      return null;
    }
    if (!isBlank(connectionProperties.getProperty(tnsAdminProperty)) || !isBlank(System.getProperty(tnsAdminProperty))) {
      return null;
    }
    File dir = findTnsAdmin(connectionProperties);
    if (dir != null) {
      connectionProperties.setProperty(tnsAdminProperty, dir.getAbsolutePath());
    }
    return dir;
  }

  /**
   * Aliasy z pliku (z dolaczonymi plikami IFILE). Klucz: alias tak jak w pliku, wartosc: opis polaczenia
   * w jednej linii. Przy powtorzonym aliasie obowiazuje ostatni wpis - tak rozwiazuje go ojdbc (sprawdzone na
   * ojdbc11 23.26), ktorym Orbada sie laczy. Bledy plikow dolaczanych
   * sa pomijane, blad pliku glownego - wyjatek.
   */
  public static Map<String, String> parse(File file) throws IOException {
    Map<String, String> result = new LinkedHashMap<String, String>();
    parse(file, result, new HashSet<String>(), 0);
    return result;
  }

  private static void parse(File file, Map<String, String> result, Set<String> visited, int depth) throws IOException {
    if (!visited.add(windows ? file.getCanonicalPath().toLowerCase() : file.getCanonicalPath())) {
      return;
    }
    // nazwy i opisy sa ASCII; kodowanie systemu jest wazne tylko dla sciezek IFILE z polskimi znakami
    String text = new String(Files.readAllBytes(file.toPath()), nativeCharset());
    String content = stripComments(text);
    int i = 0;
    int n = content.length();
    while (i < n) {
      // nazwy przed "=" na poziomie 0
      int eq = content.indexOf('=', i);
      if (eq < 0) {
        break;
      }
      String names = content.substring(i, eq).trim();
      int j = eq + 1;
      while (j < n && Character.isWhitespace(content.charAt(j))) {
        j++;
      }
      String value;
      if (j < n && content.charAt(j) == '(') {
        int end = matchingParen(content, j);
        if (end < 0) {
          break; // niedomkniety nawias - reszta pliku nieczytelna
        }
        value = content.substring(j, end + 1);
        i = end + 1;
      } else {
        // wartosc bez nawiasow (IFILE = sciezka) - do konca linii
        int eol = content.indexOf('\n', j);
        if (eol < 0) {
          eol = n;
        }
        value = content.substring(j, eol).trim();
        i = eol;
      }
      if (names.equalsIgnoreCase("IFILE")) {
        if (depth < maxIncludeDepth) {
          File inc = new File(unquote(value));
          if (!inc.isAbsolute()) {
            inc = new File(file.getParentFile(), inc.getPath());
          }
          try {
            parse(inc, result, visited, depth + 1);
          } catch (IOException ex) {
            // brak dolaczanego pliku nie przekresla reszty listy
          }
        }
        continue;
      }
      String description = value.replaceAll("\\s+", " ");
      for (String name : names.split(",")) {
        name = name.trim();
        if (name.length() > 0) {
          // jak ojdbc (thin): przy powtorzonym aliasie (bez wzgledu na wielkosc liter) obowiazuje ostatni wpis;
          // miejsce na liscie zostaje z pierwszego wystapienia
          String existing = findKeyIgnoreCase(result, name);
          result.put(existing != null ? existing : name, description);
        }
      }
    }
  }

  private static String stripComments(String text) {
    StringBuilder sb = new StringBuilder(text.length());
    for (String line : text.split("\r\n|\r|\n", -1)) {
      int hash = line.indexOf('#');
      sb.append(hash >= 0 ? line.substring(0, hash) : line).append('\n');
    }
    return sb.toString();
  }

  private static int matchingParen(String s, int open) {
    int level = 0;
    for (int k = open; k < s.length(); k++) {
      char c = s.charAt(k);
      if (c == '(') {
        level++;
      } else if (c == ')') {
        level--;
        if (level == 0) {
          return k;
        }
      }
    }
    return -1;
  }

  private static String unquote(String s) {
    s = s.trim();
    if (s.length() >= 2 && (s.startsWith("\"") && s.endsWith("\"") || s.startsWith("'") && s.endsWith("'"))) {
      return s.substring(1, s.length() - 1);
    }
    return s;
  }

  private static boolean isBlank(String s) {
    return s == null || s.trim().length() == 0;
  }

  /** Kodowanie systemu (Windows: Cp1250) - sciezki IFILE moga miec polskie znaki; Java 18+ domyslnie ma UTF-8 */
  private static Charset nativeCharset() {
    String name = System.getProperty("native.encoding", System.getProperty("sun.jnu.encoding"));
    try {
      if (name != null && Charset.isSupported(name)) {
        return Charset.forName(name);
      }
    } catch (Exception ex) {
      // nieznana nazwa - nizej domyslne
    }
    return Charset.defaultCharset();
  }

  private static String findKeyIgnoreCase(Map<String, String> map, String key) {
    for (String k : map.keySet()) {
      if (k.equalsIgnoreCase(key)) {
        return k;
      }
    }
    return null;
  }

}
