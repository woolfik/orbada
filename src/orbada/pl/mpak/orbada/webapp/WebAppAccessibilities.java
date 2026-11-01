/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package pl.mpak.orbada.webapp;

import java.awt.Desktop;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.net.URI;
import java.net.URLEncoder;
import pl.mpak.orbada.Consts;
import pl.mpak.orbada.plugins.IWebAppAccessibilities;
import pl.mpak.util.ExceptionUtil;
import pl.mpak.util.StringUtil;

/**
 *
 * @author akaluza
 */
public class WebAppAccessibilities implements IWebAppAccessibilities {

  @Override
  public String requestPost(String severity, String title, String os, String db, String jdbc, String java, String path, String callstack, String content, String workaround) {
    if (os == null) {
      os = System.getProperty("os.name") + ", " + System.getProperty("os.version");
    }
    if (java == null) {
      java = System.getProperty("java.vendor") + ", " + System.getProperty("java.version");
    }
    // GitHub Issues (zgloszenia sa publiczne - bez identyfikatora uzytkownika Orbady): tytul i tresc w adresie
    // nowego zgloszenia, tresc takze w schowku - zbyt dluga (stos wywolan) nie miesci sie w adresie
    String body = issueBody(os, db, jdbc, java, path, callstack, content, workaround);
    Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
    StringSelection data = new StringSelection(body);
    clipboard.setContents(data, data);
    if (SUGGESTION.equals(severity) || PROBLEM.equals(severity) || ERROR.equals(severity)) {
      try {
        Desktop.getDesktop().browse(new URI(issueUrl(severity, title, body)));
      } catch (Exception ex) {
        ExceptionUtil.processException(ex);
      }
    }
    return body;
  }

  /** Najwiekszy adres nowego zgloszenia (GitHub i przegladarki obcinaja dluzsze) */
  private final static int MAX_URL = 7000;
  /** Najdluzszy tytul w adresie (pelny tytul jest w tresci w schowku tylko, gdy uzytkownik go tam wpisal) */
  private final static int MAX_TITLE = 200;

  private static String issueUrl(String severity, String title, String body) throws Exception {
    // etykiety ustawi GitHub tylko osobom z uprawnieniami w repo - dla pozostalych parametr jest pomijany
    String base = Consts.orbadaNewIssueUrl +"?labels=" +(SUGGESTION.equals(severity) ? "enhancement" : "bug")
      +"&title=" +encode(StringUtil.isEmpty(title) ? "" : title.length() > MAX_TITLE ? title.substring(0, MAX_TITLE) : title);
    String url = base +"&body=" +encode(body);
    if (url.length() <= MAX_URL) {
      return url;
    }
    return base +"&body=" +encode("(Tre\u015b\u0107 zg\u0142oszenia jest w schowku - wklej j\u0105 tutaj, Ctrl+V.)");
  }

  private static String issueBody(String os, String db, String jdbc, String java, String path, String callstack, String content, String workaround) {
    StringBuilder sb = new StringBuilder();
    sb.append(StringUtil.isEmpty(content) ? "" : content.trim()).append("\n\n");
    sb.append("**ORBADA:** ").append(Consts.orbadaVersion).append("\n");
    sb.append("**System:** ").append(StringUtil.isEmpty(os) ? "N/A" : os).append("\n");
    sb.append("**Java:** ").append(StringUtil.isEmpty(java) ? "N/A" : java).append("\n");
    if (!StringUtil.isEmpty(db)) {
      sb.append("**Baza danych:** ").append(db).append("\n");
    }
    if (!StringUtil.isEmpty(jdbc)) {
      sb.append("**JDBC:** ").append(jdbc).append("\n");
    }
    if (!StringUtil.isEmpty(path)) {
      sb.append("**Miejsce:** ").append(path).append("\n");
    }
    if (!StringUtil.isEmpty(workaround)) {
      sb.append("\n### Obej\u015bcie\n").append(workaround.trim()).append("\n");
    }
    if (!StringUtil.isEmpty(callstack)) {
      sb.append("\n### Stos wywo\u0142a\u0144\n```\n").append(callstack.trim()).append("\n```\n");
    }
    return sb.toString();
  }

  private static String encode(String s) throws Exception {
    return URLEncoder.encode(s, "UTF-8").replace("+", "%20");
  }

}
