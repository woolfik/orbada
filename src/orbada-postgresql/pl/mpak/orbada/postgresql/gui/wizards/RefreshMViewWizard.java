package pl.mpak.orbada.postgresql.gui.wizards;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import javax.swing.JCheckBox;
import pl.mpak.usedb.core.Database;

/**
 * ORBADA 1.3.2: odswiezenie widoku zmaterializowanego (REFRESH MATERIALIZED VIEW [CONCURRENTLY] ... [WITH NO DATA]) -
 * odpowiednik kreatorow odswiezania MView Oracle.
 */
public class RefreshMViewWizard extends PgWizard {

  private final String view;
  private final JCheckBox checkConcurrently;
  private final JCheckBox checkNoData;

  public RefreshMViewWizard(Database database, String view) {
    super(database, null);
    this.view = view;
    info("object-name", view);
    checkConcurrently = check("concurrently");
    note("refresh-concurrently-hint");
    checkNoData = check("with-no-data");
    checkConcurrently.setEnabled(atLeast(9, 4));
    checkConcurrently.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        checkNoData.setEnabled(!checkConcurrently.isSelected());
      }
    });
  }

  @Override
  protected void build(List<String> sql) {
    sql.add("REFRESH MATERIALIZED VIEW " + (checkConcurrently.isSelected() ? "CONCURRENTLY " : "") + view +
      (checkNoData.isSelected() && !checkConcurrently.isSelected() ? " WITH NO DATA" : ""));
  }

}
