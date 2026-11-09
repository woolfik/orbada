package pl.mpak.orbada.melddiff.services;

import java.awt.Component;
import javax.swing.Icon;
import pl.mpak.orbada.melddiff.OrbadaMeldDiffPlugin;
import pl.mpak.orbada.melddiff.gui.DiffViewPanel;
import pl.mpak.orbada.plugins.IViewAccesibilities;
import pl.mpak.orbada.plugins.providers.ViewProvider;
import pl.mpak.usedb.core.Database;
import pl.mpak.util.StringManager;
import pl.mpak.util.StringManagerFactory;

/**
 *
 * @author akaluza
 */
public class DiffViewService extends ViewProvider {

  private StringManager stringManager = StringManagerFactory.getStringManager(OrbadaMeldDiffPlugin.class);

  private DiffViewPanel viewPanel;
  private String leftFileName;
  private String rightFileName;
  
  public DiffViewService() {
    super();
  }
  
  public DiffViewService(String leftFileName, String rightFileName) {
    super();
    this.leftFileName = leftFileName;
    this.rightFileName = rightFileName;
  }

  /** ORBADA 1.3.2: porownanie zawartosci dwoch edytorow - z przyciskami powrotu tresci do edytorow/bazy */
  public DiffViewService(String leftFileName, String rightFileName, MeldSyntaxService.Side left, MeldSyntaxService.Side right) {
    this(leftFileName, rightFileName);
    this.left = left;
    this.right = right;
  }

  private MeldSyntaxService.Side left;
  private MeldSyntaxService.Side right;

  public Component createView(IViewAccesibilities accesibilities) {
    viewPanel = new DiffViewPanel(accesibilities);
    if (leftFileName != null && rightFileName != null) {
      viewPanel.getMeldPanel().openComparison(leftFileName, rightFileName);
      if (left != null && right != null) {
        viewPanel.setSides(left, right);
      }
    }
    return viewPanel;
  }
  
  public String getPublicName() {
    return stringManager.getString("DiffViewService-public-name");
  }
  
  public String getViewId() {
    return null;
  }
  
  public Icon getIcon() {
    return OrbadaMeldDiffPlugin.icon("compare");
  }

  public boolean isForDatabase(Database database) {
    return true;
  }

  public String getDescription() {
    return stringManager.getString("DiffViewService-description");
  }

  public String getGroupName() {
    return OrbadaMeldDiffPlugin.pluginGroupName;
  }
  
}
