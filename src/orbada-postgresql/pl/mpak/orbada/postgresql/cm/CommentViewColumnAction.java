/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package pl.mpak.orbada.postgresql.cm;

import pl.mpak.orbada.gui.ITabObjectInfo;
import pl.mpak.orbada.gui.comps.table.ViewTable;
import pl.mpak.orbada.postgresql.OrbadaPostgreSQLPlugin;
import pl.mpak.orbada.postgresql.gui.wizards.CommentUniversalWizard;
import pl.mpak.orbada.universal.cm.UniversalPropTabAction;
import pl.mpak.orbada.universal.gui.wizards.SqlCodeWizardDialog;
import pl.mpak.util.StringManager;
import pl.mpak.util.StringManagerFactory;

/**
 *
 * @author akaluza
 */
public class CommentViewColumnAction extends UniversalPropTabAction {

  private final StringManager stringManager = StringManagerFactory.getStringManager(OrbadaPostgreSQLPlugin.class);

  public CommentViewColumnAction() {
    super();
    setText(stringManager.getString("comment-edit"));
    setSmallIcon(pl.mpak.sky.gui.swing.ImageManager.getImage("/pl/mpak/res/icons/comment_edit.gif"));
    setActionCommandKey("CommentViewColumnAction");
  }

  @Override
  public boolean isToolButton() {
    return true;
  }

  @Override
  protected boolean doAction(ViewTable vt, ITabObjectInfo ip) throws Exception {
    return SqlCodeWizardDialog.show(
      new CommentUniversalWizard(
        vt.getQuery().getDatabase(),
        "column-dd", "COLUMN", 
        vt.getQuery().fieldByName("full_object_name").getString(),
        vt.getQuery().fieldByName("description").getString()
      ), true) != null;
  }

}
