package pl.mpak.orbada.postgresql.gui.functions;

import java.awt.Component;
import pl.mpak.orbada.gui.OrbadaTabbedPane;
import pl.mpak.orbada.plugins.IViewAccesibilities;

/**
 * ORBADA 1.3.2: zakladki procedury - zrodlo (CREATE PROCEDURE), argumenty, uprawnienia, szczegoly, zaleznosci.
 */
public class ProcedureTabbedPane extends OrbadaTabbedPane {

  public ProcedureTabbedPane(IViewAccesibilities accesibilities) {
    super("PROCEDURE",
      new Component[] {
        new FunctionSourceTab(accesibilities, "PROCEDURE"),
        new FunctionArgumentListTab(accesibilities),
        new FunctionPrivilegeListTab(accesibilities),
        new FunctionDetailsTab(accesibilities),
        new FunctionDependsTab(accesibilities)
    });
  }

}
