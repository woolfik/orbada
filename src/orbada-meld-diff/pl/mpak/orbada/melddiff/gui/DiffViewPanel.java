/*
 * DiffViewPanel.java
 *
 * Created on 28 listopad 2008, 11:56
 */

package pl.mpak.orbada.melddiff.gui;

import java.awt.Component;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.Method;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JTabbedPane;
import javax.swing.JToolBar;
import javax.swing.text.PlainDocument;
import org.jmeld.ui.BufferDiffPanel;
import org.jmeld.ui.FilePanel;
import org.jmeld.ui.JMeldPanel;
import org.jmeld.ui.action.MeldAction;
import org.jmeld.ui.util.ToolBarBuilder;
import org.jmeld.ui.util.ToolBarSeparator;
import org.jmeld.ui.util.WidgetFactory;
import pl.mpak.orbada.core.Application;
import pl.mpak.orbada.gui.PerspectivePanel;
import pl.mpak.orbada.melddiff.OrbadaMeldDiffPlugin;
import pl.mpak.orbada.melddiff.services.MeldSyntaxService;
import pl.mpak.orbada.plugins.IViewAccesibilities;
import pl.mpak.orbada.plugins.providers.ViewProvider;
import pl.mpak.sky.gui.mr.ModalResult;
import pl.mpak.sky.gui.swing.MessageBox;
import pl.mpak.util.ExceptionUtil;
import pl.mpak.util.StringManager;
import pl.mpak.util.StringManagerFactory;

/**
 *
 * @author  akaluza
 */
public class DiffViewPanel extends javax.swing.JPanel implements Closeable {

  private final StringManager stringManager = StringManagerFactory.getStringManager(OrbadaMeldDiffPlugin.class);

  /** Edytor zapytan SQL wtyczki uniwersalnej (SqlQueryView) - ma openText(String) */
  private final static String sqlQueryViewId = "orbada-universal-sqlquery-view";

  private IViewAccesibilities accesibilities;
  private boolean viewClosing = false;
  private JMeldPanel meldPanel;

  /** Creates new form DiffViewPanel */
  public DiffViewPanel(IViewAccesibilities accesibilities) {
    this.accesibilities = accesibilities;
    initComponents();
    init();
  }

  private void init() {
    meldPanel = new JMeldPanel();
    add(meldPanel);
  }

  /**
   * ORBADA 1.3.2: pasek przenoszenia tresci porownania (takze zmienionej w porownaniu, niezapisanej) do edytora SQL
   * bazy, z ktorej pochodzi strona (Ctrl+Enter kompiluje), albo z powrotem do edytora, z ktorego ja wzieto.
   */
  public void setSides(final MeldSyntaxService.Side left, final MeldSyntaxService.Side right) {
    Action[] actions = {
      action("DiffViewPanel-left-to-sql", "to-sql", "left", left, BufferDiffPanel.LEFT, true),
      action("DiffViewPanel-left-to-source", "to-source", "left", left, BufferDiffPanel.LEFT, false),
      null,
      action("DiffViewPanel-right-to-source", "to-source", "right", right, BufferDiffPanel.RIGHT, false),
      action("DiffViewPanel-right-to-sql", "to-sql", "right", right, BufferDiffPanel.RIGHT, true)};
    // przyciski na pasku JMeld (New, Save, Undo, Redo ...) - w tym samym stylu; bez paska JMeld wlasny pasek
    JToolBar meldBar = findToolBar(meldPanel);
    if (meldBar != null && addToMeldToolBar(meldBar, actions)) {
      return;
    }
    JToolBar bar = new JToolBar();
    bar.setFloatable(false);
    for (Action a : actions) {
      if (a == null) {
        bar.addSeparator();
      } else {
        JButton b = new JButton(a);
        b.setFocusable(false);
        bar.add(b);
      }
    }
    add(bar, java.awt.BorderLayout.NORTH);
    revalidate();
  }

  private static JToolBar findToolBar(Container c) {
    for (Component x : c.getComponents()) {
      if (x instanceof JToolBar) {
        return (JToolBar)x;
      }
      if (x instanceof Container) {
        JToolBar t = findToolBar((Container)x);
        if (t != null) {
          return t;
        }
      }
    }
    return null;
  }

  /**
   * Pasek JMeld ma FormLayout budowany przez ToolBarBuilder (kolumny po kolei) - przebudowa w tej samej kolejnosci,
   * nasze przyciski (WidgetFactory.getToolBarButton - jak New/Save) przed odstepem do Settings/Help/About.
   */
  private static boolean addToMeldToolBar(JToolBar bar, Action[] actions) {
    Component[] old = bar.getComponents();
    try {
      rebuildToolBar(bar, old, actions);
      return true;
    } catch (Throwable ex) {
      ExceptionUtil.processException(ex);
      try {
        // pasek JMeld jak przed zmiana (bez naszych przyciskow) - beda na osobnym pasku
        rebuildToolBar(bar, old, null);
      } catch (Throwable ex2) {
        ExceptionUtil.processException(ex2);
      }
      return false;
    }
  }

  private static void rebuildToolBar(JToolBar bar, Component[] old, Action[] actions) {
    int spring = old.length;
    for (int i = 0; i < old.length; i++) {
      if (old[i] instanceof Box.Filler) {
        spring = i;
        break;
      }
    }
    bar.removeAll();
    ToolBarBuilder builder = new ToolBarBuilder(bar);
    for (int i = 0; i <= old.length; i++) {
      if (i == spring && actions != null) {
        builder.addSeparator();
        for (Action a : actions) {
          if (a == null) {
            builder.addSeparator();
          } else {
            builder.addButton(WidgetFactory.getToolBarButton(a));
          }
        }
      }
      if (i == old.length) {
        break;
      }
      if (old[i] instanceof Box.Filler) {
        builder.addSpring();
      } else if (old[i] instanceof ToolBarSeparator) {
        builder.addSeparator();
      } else if (old[i] instanceof JComponent) {
        builder.addComponent((JComponent)old[i]);
      }
    }
    bar.revalidate();
    bar.repaint();
  }

  private Action action(String key, String icon, String sideName, final MeldSyntaxService.Side side, final int which, final boolean toSql) {
    Action a = new AbstractAction(stringManager.getString(key)) {
      @Override
      public void actionPerformed(ActionEvent e) {
        perform(side, which, toSql);
      }
    };
    a.putValue(Action.SMALL_ICON, OrbadaMeldDiffPlugin.icon(icon));
    // duza ikona przyciskow paska JMeld (WidgetFactory.getToolBarButton wymaga ImageIcon)
    a.putValue(MeldAction.LARGE_ICON_KEY, pl.mpak.sky.gui.swing.ImageManager.getImage("/pl/mpak/orbada/melddiff/res/" + icon + "-" + sideName + "-24.png"));
    a.putValue(Action.SHORT_DESCRIPTION, side.getTitle());
    a.setEnabled(toSql ? side.getPanel() != null : side.getArea() != null);
    return a;
  }

  private void perform(final MeldSyntaxService.Side side, final int which, final boolean toSql) {
    try {
      String text = sideText(which, side);
      if (toSql) {
        toSqlEditor(side, text);
      } else {
        toSourceEditor(side, text);
      }
    } catch (Exception ex) {
      ExceptionUtil.processException(ex);
      MessageBox.show(DiffViewPanel.this, stringManager.getString("error"), ex.getMessage() != null ? ex.getMessage() : ex.toString(), ModalResult.OK, MessageBox.ERROR);
    }
  }

  /** Biezaca tresc strony porownania (z edycjami w porownaniu); bez panelu JMeld - tekst wziety z edytora */
  private String sideText(int which, MeldSyntaxService.Side side) {
    BufferDiffPanel diff = findDiffPanel(meldPanel);
    if (diff != null) {
      try {
        // getFilePanel(int) jest w JMeld 2.0 pakietowa
        Method m = BufferDiffPanel.class.getDeclaredMethod("getFilePanel", int.class);
        m.setAccessible(true);
        FilePanel fp = (FilePanel)m.invoke(diff, which);
        if (fp != null && fp.getBufferDocument() != null) {
          PlainDocument doc = fp.getBufferDocument().getDocument();
          return doc.getText(0, doc.getLength());
        }
      } catch (Exception ex) {
        ExceptionUtil.processException(ex);
      }
    }
    return side.getText();
  }

  private static BufferDiffPanel findDiffPanel(Container c) {
    BufferDiffPanel found = null;
    for (Component x : c.getComponents()) {
      if (x instanceof BufferDiffPanel) {
        if (x.isShowing()) {
          return (BufferDiffPanel)x;
        }
        found = (BufferDiffPanel)x;
      } else if (x instanceof Container) {
        BufferDiffPanel f = findDiffPanel((Container)x);
        if (f != null && (found == null || f.isShowing())) {
          found = f;
        }
      }
    }
    return found;
  }

  /** Nowy edytor SQL w perspektywie (bazie) strony z trescia - Ctrl+Enter wykonuje/kompiluje */
  private void toSqlEditor(MeldSyntaxService.Side side, String text) throws Exception {
    PerspectivePanel panel = side.getPanel();
    if (panel == null || !panel.isDisplayable()) {
      throw new Exception(stringManager.getString("DiffViewPanel-connection-closed"));
    }
    ViewProvider provider = null;
    ViewProvider[] views = Application.get().getServiceArray(ViewProvider.class);
    if (views != null) {
      for (ViewProvider vp : views) {
        if (sqlQueryViewId.equals(vp.getViewId()) && vp.isForDatabase(panel.getDatabase())) {
          provider = vp.getClass().getDeclaredConstructor().newInstance();
          break;
        }
      }
    }
    if (provider == null) {
      throw new Exception(stringManager.getString("DiffViewPanel-no-sql-editor"));
    }
    Component view = panel.getPerspectiveAccesibilities().createView(provider);
    Component editor = findWithOpenText(view);
    if (editor == null) {
      throw new Exception(stringManager.getString("DiffViewPanel-no-sql-editor"));
    }
    editor.getClass().getMethod("openText", String.class).invoke(editor, text);
    reveal(editor);
  }

  private static Component findWithOpenText(Component c) {
    if (c == null) {
      return null;
    }
    try {
      c.getClass().getMethod("openText", String.class);
      return c;
    } catch (NoSuchMethodException ex) {
      // szukamy glebiej
    }
    if (c instanceof Container) {
      for (Component x : ((Container)c).getComponents()) {
        Component f = findWithOpenText(x);
        if (f != null) {
          return f;
        }
      }
    }
    return null;
  }

  /** Tresc z powrotem do edytora, z ktorego pochodzi strona (np. zrodlo pakietu - zapis jak zwykle) */
  private void toSourceEditor(MeldSyntaxService.Side side, String text) throws Exception {
    if (side.getArea() == null || !side.getArea().isDisplayable()) {
      throw new Exception(stringManager.getString("DiffViewPanel-editor-closed"));
    }
    side.getArea().setText(text);
    side.getArea().setChanged(true);
    reveal(side.getArea());
    side.getArea().getEditorArea().requestFocusInWindow();
  }

  /** Wybiera zakladki (perspektywa, widok, zakladka obiektu), w ktorych lezy komponent */
  private static void reveal(Component c) {
    for (Component x = c; x != null && x.getParent() != null; x = x.getParent()) {
      if (x.getParent() instanceof JTabbedPane) {
        ((JTabbedPane)x.getParent()).setSelectedComponent(x);
      }
    }
  }

  public void close() throws IOException {
    viewClosing = true;
    accesibilities = null;
  }

  public JMeldPanel getMeldPanel() {
    return meldPanel;
  }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
  // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
  private void initComponents() {

    setLayout(new java.awt.BorderLayout());
  }// </editor-fold>//GEN-END:initComponents


  // Variables declaration - do not modify//GEN-BEGIN:variables
  // End of variables declaration//GEN-END:variables

}
