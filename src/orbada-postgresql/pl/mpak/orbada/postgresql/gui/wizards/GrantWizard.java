package pl.mpak.orbada.postgresql.gui.wizards;

import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import pl.mpak.usedb.core.Database;

/**
 * ORBADA 1.3.2: nadanie (GRANT) albo odebranie (REVOKE) uprawnien do obiektu dla roli.
 * objectKind: TABLE (takze widoki), SEQUENCE, FUNCTION, PROCEDURE, SCHEMA, TYPE.
 */
public class GrantWizard extends PgWizard {

  public static final String[] TABLE = {"SELECT", "INSERT", "UPDATE", "DELETE", "TRUNCATE", "REFERENCES", "TRIGGER"};
  public static final String[] SEQUENCE = {"USAGE", "SELECT", "UPDATE"};
  public static final String[] ROUTINE = {"EXECUTE"};
  public static final String[] SCHEMA = {"USAGE", "CREATE"};
  public static final String[] TYPE = {"USAGE"};

  private final boolean revoke;
  private final String objectKind;
  private final String objectName;
  private final JComboBox<String> comboRole;
  private final JCheckBox checkAll;
  private final JCheckBox[] checkPrivileges;
  private final JCheckBox checkOption;
  private final JCheckBox checkCascade;

  public GrantWizard(Database database, boolean revoke, String objectKind, String objectName, String[] privileges) {
    super(database, null);
    this.revoke = revoke;
    this.objectKind = objectKind;
    this.objectName = objectName;
    this.titleKey = revoke ? "RevokeWizard-title" : "GrantWizard-title";
    if ("TABLE".equals(objectKind) && atLeast(17, 0)) {
      String[] p = new String[privileges.length + 1];
      System.arraycopy(privileges, 0, p, 0, privileges.length);
      p[privileges.length] = "MAINTAIN";
      privileges = p;
    }
    info("object-name", objectName);
    comboRole = combo("role-name", true, roles());
    checkAll = check("all-privileges");
    JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
    checkPrivileges = new JCheckBox[privileges.length];
    for (int i = 0; i < privileges.length; i++) {
      checkPrivileges[i] = new JCheckBox(privileges[i]);
      panel.add(checkPrivileges[i]);
    }
    checkPrivileges[0].setSelected(true);
    row("privileges", panel);
    checkAll.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        for (JCheckBox c : checkPrivileges) {
          c.setEnabled(!checkAll.isSelected());
        }
      }
    });
    checkOption = check(revoke ? "revoke-grant-option" : "with-grant-option");
    checkCascade = revoke ? check("drop-cascade") : null;
  }

  private String role() {
    String role = value(comboRole);
    return "PUBLIC".equalsIgnoreCase(role) ? "PUBLIC" : ident(role);
  }

  @Override
  protected void build(List<String> sql) {
    StringBuilder privileges = new StringBuilder();
    if (checkAll.isSelected()) {
      privileges.append("ALL PRIVILEGES");
    } else {
      for (JCheckBox c : checkPrivileges) {
        if (c.isSelected()) {
          privileges.append(privileges.length() > 0 ? ", " : "").append(c.getText());
        }
      }
    }
    if (revoke) {
      sql.add("REVOKE " + (checkOption.isSelected() ? "GRANT OPTION FOR " : "") + privileges +
        " ON " + objectKind + " " + objectName + " FROM " + role() + (checkCascade.isSelected() ? " CASCADE" : ""));
    } else {
      sql.add("GRANT " + privileges + " ON " + objectKind + " " + objectName + " TO " + role() +
        (checkOption.isSelected() ? " WITH GRANT OPTION" : ""));
    }
  }

}
