package pl.mpak.orbada.oracle.services;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import pl.mpak.orbada.oracle.OrbadaOraclePlugin;
import pl.mpak.orbada.oracle.datasync.DataSyncDialog;
import pl.mpak.orbada.universal.gui.SqlQueryPanelView;
import pl.mpak.orbada.universal.providers.IUniversalViewAccessibilities;
import pl.mpak.orbada.universal.providers.UniversalActionProvider;
import pl.mpak.sky.gui.mr.ModalResult;
import pl.mpak.sky.gui.swing.MessageBox;
import pl.mpak.sky.gui.swing.syntax.SyntaxEditor;
import pl.mpak.usedb.core.Database;
import pl.mpak.util.StringManager;
import pl.mpak.util.StringManagerFactory;

/**
 * ORBADA 1.3.8: "Wyrownaj dane (MERGE)..." w edytorze SQL Oracle (menu Zapytanie i menu pod prawym przyciskiem):
 * z zapytania pod kursorem (select * from tabela@dblink where ...) skrypt PL/SQL kopiujacy wiersze do tej samej
 * tabeli w biezacej bazie - okno DataSyncDialog, wynik w nowej zawartosci edytora.
 */
public class OracleDataSyncProvider extends UniversalActionProvider {

  private final StringManager stringManager = StringManagerFactory.getStringManager(OrbadaOraclePlugin.class);

  private boolean inPopup;
  private boolean listenerAdded;

  public boolean isForDatabase(Database database) {
    if (database == null || !OrbadaOraclePlugin.oracleDriverType.equals(database.getDriverType())) {
      return false;
    }
    setText(stringManager.getString("OracleDataSyncProvider-text"));
    setSmallIcon(pl.mpak.sky.gui.swing.ImageManager.getImage("/pl/mpak/res/icons/data_move.gif"));
    setActionCommandKey("OracleDataSyncProvider");
    if (!listenerAdded) {
      addActionListener(createActionListener());
      listenerAdded = true;
    }
    return true;
  }

  @Override
  public void setUniversalViewAccessibilities(IUniversalViewAccessibilities accessibilities) {
    super.setUniversalViewAccessibilities(accessibilities);
    // dodatkowo w menu pod prawym przyciskiem edytora (jedna instancja dostawcy na edytor)
    SyntaxEditor editor = accessibilities.getSyntaxEditor();
    if (!inPopup && editor != null && editor.getComponentPopupMenu() != null) {
      JPopupMenu popup = editor.getComponentPopupMenu();
      popup.addSeparator();
      popup.add(this);
      inPopup = true;
    }
  }

  public boolean addToolButton() {
    return false;
  }

  public boolean addMenuItem() {
    return true;
  }

  public boolean addToEditor() {
    return false;
  }

  public String getDescription() {
    return stringManager.getString("OracleDataSyncProvider-description");
  }

  private ActionListener createActionListener() {
    return new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final SyntaxEditor editor = accessibilities.getSyntaxEditor();
        String sql = editor.getCurrentText();
        if (sql == null || !sql.trim().toLowerCase().startsWith("select") && !sql.trim().toLowerCase().startsWith("with")) {
          MessageBox.show(editor, getText(), stringManager.getString("datasync-no-query"), ModalResult.OK, MessageBox.INFORMATION);
          return;
        }
        Database database = accessibilities.getViewAccesibilities().getDatabase();
        new DataSyncDialog(database, sql, new DataSyncDialog.Target() {
          public void open(String script) {
            Component panel = SwingUtilities.getAncestorOfClass(SqlQueryPanelView.class, editor);
            if (panel instanceof SqlQueryPanelView) {
              ((SqlQueryPanelView)panel).openText(script);
            } else {
              editor.replaceSelection(script);
            }
          }
        }).setVisible(true);
      }
    };
  }

  public String getGroupName() {
    return OrbadaOraclePlugin.oracleDriverType;
  }

}
