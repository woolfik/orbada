package pl.mpak.orbada.oracle.debug;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ORBADA 1.3.6: nazwy zmiennych widocznych w miejscu zatrzymania debuggera.
 *
 * <p>DBMS_DEBUG nie podaje listy zmiennych lokalnych - zbieramy je ze zrodla (ALL_SOURCE): parametry i deklaracje
 * podprogramu (bez zagniezdzonych procedur/funkcji, kursorow, typow i wyjatkow) oraz zmienne petli FOR przed biezaca
 * linia. Heurystyka - wartosci i tak czyta GET_VALUE, nazwy spoza zasiegu zwracaja "brak obiektu" i sa pomijane.</p>
 */
public final class PlSqlVariables {

  private PlSqlVariables() {
  }

  private static final Pattern TOKEN = Pattern.compile("\"[^\"]+\"|[A-Za-z_][A-Za-z0-9_$#]*|[();,]");

  private static final Set<String> SKIP_DECL = set("CURSOR", "TYPE", "SUBTYPE", "PRAGMA", "PROCEDURE", "FUNCTION");

  private static final Set<String> PARAM_MODES = set("IN", "OUT", "NOCOPY");

  private static Set<String> set(String... s) {
    Set<String> r = new LinkedHashSet<String>();
    for (String x : s) {
      r.add(x);
    }
    return r;
  }

  private static class Token {
    final String text;
    final String upper;
    final int start;

    Token(String text, int start) {
      this.text = text;
      this.upper = text.startsWith("\"") ? text.substring(1, text.length() - 1) : text.toUpperCase();
      this.start = start;
    }

    boolean is(String word) {
      return upper.equals(word) && !text.startsWith("\"");
    }
  }

  /**
   * Zmienne podprogramu zawierajacego linie
   * @param source zrodlo (linie jak w ALL_SOURCE)
   * @param entryPoint nazwa podprogramu z backtrace (moze byc null)
   * @param line linia zatrzymania (od 1)
   */
  public static List<String> find(String source, String entryPoint, int line) {
    String clean = clean(source);
    int offset = lineOffset(clean, line);
    List<Token> tokens = tokens(clean);
    Set<String> result = new LinkedHashSet<String>();
    int header = -1;
    for (int i = 0; i < tokens.size() - 1 && tokens.get(i).start < offset; i++) {
      Token t = tokens.get(i);
      if ((t.is("PROCEDURE") || t.is("FUNCTION")) && (entryPoint == null || tokens.get(i + 1).upper.equals(entryPoint.toUpperCase()))) {
        // pomijamy deklaracje zapowiadajace (konczace sie ';' przed IS/AS)
        int is = findIsAs(tokens, i + 2);
        if (is >= 0) {
          header = i;
        }
      }
    }
    int declStart;
    if (header >= 0) {
      int i = header + 2;
      if (i < tokens.size() && tokens.get(i).text.equals("(")) {
        i = params(tokens, i, result);
      }
      declStart = findIsAs(tokens, i) + 1;
    } else {
      // wyzwalacz / blok: deklaracje po DECLARE
      declStart = -1;
      for (int i = 0; i < tokens.size() && tokens.get(i).start < offset; i++) {
        if (tokens.get(i).is("DECLARE")) {
          declStart = i + 1;
          break;
        }
      }
      if (declStart < 0) {
        return new ArrayList<String>(result);
      }
    }
    int begin = declarations(tokens, declStart, result);
    // zmienne petli FOR i .. / FOR r IN (select ...) przed biezaca linia
    for (int i = Math.max(begin, 0); i < tokens.size() - 2 && tokens.get(i).start < offset; i++) {
      if (tokens.get(i).is("FOR") && tokens.get(i + 2).is("IN")) {
        result.add(tokens.get(i + 1).upper);
      }
    }
    return new ArrayList<String>(result);
  }

  /** Zmienne globalne pakietu (deklaracje w ciele/specyfikacji poza podprogramami) */
  public static List<String> packageGlobals(String source) {
    List<Token> tokens = tokens(clean(source));
    Set<String> result = new LinkedHashSet<String>();
    int start = findIsAs(tokens, 0);
    if (start >= 0) {
      declarations(tokens, start + 1, result);
    }
    return new ArrayList<String>(result);
  }

  /** Lista parametrow od '(' - zwraca indeks za ')' */
  private static int params(List<Token> tokens, int i, Set<String> result) {
    int depth = 0;
    boolean expectName = true;
    for (; i < tokens.size(); i++) {
      String t = tokens.get(i).text;
      if (t.equals("(")) {
        depth++;
        if (depth == 1) {
          expectName = true;
        }
      } else if (t.equals(")")) {
        depth--;
        if (depth == 0) {
          return i + 1;
        }
      } else if (t.equals(",") && depth == 1) {
        expectName = true;
      } else if (depth == 1 && expectName && !PARAM_MODES.contains(tokens.get(i).upper)) {
        result.add(tokens.get(i).upper);
        expectName = false;
      }
    }
    return i;
  }

  /** IS/AS naglowka podprogramu od indeksu; -1 gdy wczesniej ';' (deklaracja zapowiadajaca) */
  private static int findIsAs(List<Token> tokens, int from) {
    int depth = 0;
    for (int i = from; i < tokens.size(); i++) {
      Token t = tokens.get(i);
      if (t.text.equals("(")) {
        depth++;
      } else if (t.text.equals(")")) {
        depth--;
      } else if (depth == 0) {
        if (t.is("IS") || t.is("AS")) {
          return i;
        }
        if (t.text.equals(";")) {
          return -1;
        }
      }
    }
    return -1;
  }

  /**
   * Deklaracje od indeksu do BEGIN (lub END) na tym samym poziomie; zagniezdzone podprogramy pomijane
   * @return indeks BEGIN albo -1
   */
  private static int declarations(List<Token> tokens, int i, Set<String> result) {
    boolean statementStart = true;
    while (i >= 0 && i < tokens.size()) {
      Token t = tokens.get(i);
      if (t.is("BEGIN")) {
        return i;
      }
      if (t.is("END") && statementStart) {
        return -1;
      }
      if (statementStart && (t.is("PROCEDURE") || t.is("FUNCTION"))) {
        i = skipSubprogram(tokens, i);
        statementStart = true;
        continue;
      }
      if (statementStart) {
        boolean exception = i + 1 < tokens.size() && tokens.get(i + 1).is("EXCEPTION");
        if (!SKIP_DECL.contains(t.upper) && !exception && !t.text.equals(";") && !t.text.equals("(")) {
          result.add(t.upper);
        }
        statementStart = false;
      }
      if (t.text.equals(";")) {
        statementStart = true;
      }
      i++;
    }
    return -1;
  }

  /** Pomija zagniezdzony podprogram (deklaracja lub definicja) - zwraca indeks za nim */
  private static int skipSubprogram(List<Token> tokens, int i) {
    int is = findIsAs(tokens, i + 2);
    if (is < 0) {
      // deklaracja zapowiadajaca - do ';'
      while (i < tokens.size() && !tokens.get(i).text.equals(";")) {
        i++;
      }
      return i + 1;
    }
    Set<String> nested = new LinkedHashSet<String>();
    int begin = declarations(tokens, is + 1, nested);
    if (begin < 0) {
      return tokens.size();
    }
    return skipBlock(tokens, begin);
  }

  /** Od BEGIN do pasujacego END ...; - zwraca indeks za ';' */
  private static int skipBlock(List<Token> tokens, int i) {
    int depth = 0;
    for (; i < tokens.size(); i++) {
      Token t = tokens.get(i);
      if (t.is("BEGIN") || t.is("CASE")) {
        depth++;
      } else if (t.is("END")) {
        Token n = i + 1 < tokens.size() ? tokens.get(i + 1) : null;
        if (n != null && (n.is("IF") || n.is("LOOP"))) {
          i++;
          continue;
        }
        if (n != null && n.is("CASE")) {
          i++;
        }
        depth--;
        if (depth == 0) {
          while (i < tokens.size() && !tokens.get(i).text.equals(";")) {
            i++;
          }
          return i + 1;
        }
      }
    }
    return i;
  }

  private static List<Token> tokens(String clean) {
    List<Token> result = new ArrayList<Token>();
    Matcher m = TOKEN.matcher(clean);
    while (m.find()) {
      result.add(new Token(m.group(), m.start()));
    }
    return result;
  }

  private static int lineOffset(String text, int line) {
    int offset = 0;
    for (int l = 1; l < line && offset >= 0; l++) {
      offset = text.indexOf('\n', offset);
      if (offset < 0) {
        return text.length();
      }
      offset++;
    }
    return offset;
  }

  /** Komentarze i literaly zamienione na spacje (z zachowaniem konca linii i pozycji) */
  static String clean(String s) {
    char[] c = s.toCharArray();
    int i = 0;
    while (i < c.length) {
      if (c[i] == '-' && i + 1 < c.length && c[i + 1] == '-') {
        while (i < c.length && c[i] != '\n') {
          c[i++] = ' ';
        }
      } else if (c[i] == '/' && i + 1 < c.length && c[i + 1] == '*') {
        c[i++] = ' ';
        c[i++] = ' ';
        while (i < c.length && !(c[i] == '*' && i + 1 < c.length && c[i + 1] == '/')) {
          if (c[i] != '\n') {
            c[i] = ' ';
          }
          i++;
        }
        if (i < c.length) {
          c[i++] = ' ';
          c[i++] = ' ';
        }
      } else if (c[i] == '\'') {
        c[i++] = ' ';
        while (i < c.length) {
          if (c[i] == '\'') {
            if (i + 1 < c.length && c[i + 1] == '\'') {
              c[i++] = ' ';
              c[i++] = ' ';
              continue;
            }
            c[i++] = ' ';
            break;
          }
          if (c[i] != '\n') {
            c[i] = ' ';
          }
          i++;
        }
      } else {
        i++;
      }
    }
    return new String(c);
  }
}
