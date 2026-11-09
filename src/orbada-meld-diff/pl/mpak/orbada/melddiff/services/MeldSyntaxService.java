/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package pl.mpak.orbada.melddiff.services;

import java.io.FileOutputStream;
import java.io.IOException;
import javax.swing.JMenu;
import pl.mpak.orbada.gui.PerspectivePanel;
import pl.mpak.orbada.gui.comps.AbsOrbadaSyntaxTextArea;
import pl.mpak.orbada.melddiff.OrbadaMeldDiffPlugin;
import pl.mpak.orbada.melddiff.cm.LeftContentAction;
import pl.mpak.orbada.melddiff.cm.RightContentAction;
import pl.mpak.orbada.plugins.providers.OrbadaSyntaxTextAreaProvider;
import pl.mpak.sky.gui.swing.syntax.SyntaxTextArea;
import pl.mpak.util.ExceptionUtil;
import pl.mpak.util.StringManager;
import pl.mpak.util.StringManagerFactory;
import pl.mpak.util.StringUtil;

/**
 *
 * @author akaluza
 */
public class MeldSyntaxService extends OrbadaSyntaxTextAreaProvider {

  private StringManager stringManager = StringManagerFactory.getStringManager(OrbadaMeldDiffPlugin.class);

  private JMenu menu;

  /**
   * ORBADA 1.3.2: strona porownania z pochodzeniem - edytor, z ktorego wzieto tekst, i jego perspektywa (baza),
   * zeby z widoku roznic mozna bylo otworzyc tresc w edytorze SQL tej bazy albo wstawic ja z powrotem do edytora.
   */
  public static class Side {
    private final String text;
    // slabe referencje: strona ustawiona bez drugiej nie moze trzymac zamknietego edytora/perspektywy
    private final java.lang.ref.WeakReference<SyntaxTextArea> area;
    private final java.lang.ref.WeakReference<PerspectivePanel> panel;

    Side(String text, SyntaxTextArea area, PerspectivePanel panel) {
      this.text = text;
      this.area = new java.lang.ref.WeakReference<SyntaxTextArea>(area);
      this.panel = new java.lang.ref.WeakReference<PerspectivePanel>(panel);
    }

    public String getText() {
      return text;
    }

    public SyntaxTextArea getArea() {
      return area.get();
    }

    public PerspectivePanel getPanel() {
      return panel.get();
    }

    /** Opis do podpowiedzi: polaczenie i obiekt edytora, np. "orbada@baza: PACKAGE BODY HR.PKG" */
    public String getTitle() {
      StringBuilder sb = new StringBuilder();
      PerspectivePanel panel = getPanel();
      SyntaxTextArea area = getArea();
      if (panel != null && panel.getDatabase() != null) {
        sb.append(StringUtil.nvl(panel.getDatabase().getPublicName(), panel.getDatabase().getUrl()));
      }
      if (area instanceof AbsOrbadaSyntaxTextArea && !StringUtil.isEmpty(((AbsOrbadaSyntaxTextArea)area).getObjectName())) {
        AbsOrbadaSyntaxTextArea a = (AbsOrbadaSyntaxTextArea)area;
        sb.append(sb.length() > 0 ? ": " : "").append(StringUtil.nvl(a.getObjectType(), "")).append(" ")
          .append(StringUtil.isEmpty(a.getSchemaName()) ? "" : a.getSchemaName() + ".").append(a.getObjectName());
      }
      return sb.toString();
    }
  }

  private static Side left;
  private static Side right;

  public String getDescription() {
    return stringManager.getString("MeldSyntaxService-description");
  }

  public String getGroupName() {
    return OrbadaMeldDiffPlugin.pluginGroupName;
  }

  private static void checkFilledContents(PerspectivePanel panel) {
    if (left != null && right != null) {
      try {
        // osobne pliki dla kazdego porownania (kolejne porownanie nie nadpisuje otwartego); kodowanie domyslne JVM,
        // bo takim JMeld je czyta
        java.io.File leftFile = java.io.File.createTempFile("ojmeld-left-", ".txt");
        java.io.File rightFile = java.io.File.createTempFile("ojmeld-right-", ".txt");
        leftFile.deleteOnExit();
        rightFile.deleteOnExit();
        FileOutputStream fos = new FileOutputStream(leftFile);
        fos.write(left.getText().getBytes());
        fos.close();
        fos = new FileOutputStream(rightFile);
        fos.write(right.getText().getBytes());
        fos.close();
        panel.getPerspectiveAccesibilities().createView(new DiffViewService(leftFile.getAbsolutePath(), rightFile.getAbsolutePath(), left, right));
        left = null;
        right = null;
      } catch (IOException ex) {
        ExceptionUtil.processException(ex);
      }
    }
  }

  public static void setLeftContent(String text, PerspectivePanel panel) {
    setLeftContent(text, null, panel);
  }

  public static void setRightContent(String text, PerspectivePanel panel) {
    setRightContent(text, null, panel);
  }

  public static void setLeftContent(String text, SyntaxTextArea area, PerspectivePanel panel) {
    left = new Side(text, area, panel);
    checkFilledContents(panel);
  }

  public static void setRightContent(String text, SyntaxTextArea area, PerspectivePanel panel) {
    right = new Side(text, area, panel);
    checkFilledContents(panel);
  }

  @Override
  public void setSyntaxTextArea(SyntaxTextArea syntaxTextArea) {
    super.setSyntaxTextArea(syntaxTextArea);
    if (syntaxTextArea instanceof SyntaxTextArea) {
      menu = new JMenu(stringManager.getString("MeldSyntaxService-menu-diff-content"));
      menu.setIcon(OrbadaMeldDiffPlugin.icon("compare"));
      menu.add(new LeftContentAction(syntaxTextArea));
      menu.add(new RightContentAction(syntaxTextArea));
      syntaxTextArea.getEditorArea().getComponentPopupMenu().add(menu);
    }
  }

}
