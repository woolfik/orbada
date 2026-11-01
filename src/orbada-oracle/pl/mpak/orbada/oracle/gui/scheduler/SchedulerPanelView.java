package pl.mpak.orbada.oracle.gui.scheduler;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Point;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JToolBar;
import pl.mpak.orbada.gui.IRootTabObjectInfo;
import pl.mpak.orbada.gui.util.SimpleSelectDialog;
import pl.mpak.orbada.oracle.OrbadaOraclePlugin;
import pl.mpak.orbada.oracle.Sql;
import pl.mpak.orbada.oracle.services.OracleDbInfoProvider;
import pl.mpak.orbada.plugins.IViewAccesibilities;
import pl.mpak.sky.gui.swing.SwingUtil;
import pl.mpak.usedb.core.Database;
import pl.mpak.usedb.core.Query;
import pl.mpak.usedb.util.QueryUtil;
import pl.mpak.util.ExceptionUtil;
import pl.mpak.util.StringManager;
import pl.mpak.util.StringManagerFactory;

/**
 * ORBADA Fuerteventura (F5): Oracle Scheduler (DBMS_SCHEDULER) - joby, programy, harmonogramy, chainy
 * (kroki, reguly), okna, grupy, klasy jobow i historia uruchomien w jednym widoku z zakladkami.
 */
public class SchedulerPanelView extends JPanel implements IRootTabObjectInfo {

  private final static StringManager stringManager = StringManagerFactory.getStringManager(OrbadaOraclePlugin.class);

  private IViewAccesibilities accesibilities;
  private final JTabbedPane tabs = new JTabbedPane();
  private final List<SchedulerObjectPanel> panels = new ArrayList<SchedulerObjectPanel>();
  private final JLabel labelSchema = new JLabel();
  private final JButton buttonSchema;
  private String tabTitle;
  private String currentSchemaName;

  public SchedulerPanelView(IViewAccesibilities accesibilities) {
    super(new BorderLayout());
    this.accesibilities = accesibilities;
    tabTitle = accesibilities.getTabTitle();
    currentSchemaName = OracleDbInfoProvider.getCurrentSchema(getDatabase());

    JToolBar toolBar = new JToolBar();
    toolBar.setFloatable(false);
    toolBar.add(new JButton(new AbstractAction(stringManager.getString("cmRefresh-text"),
        pl.mpak.sky.gui.swing.ImageManager.getImage("/pl/mpak/res/icons/refresh16.gif")) {
      public void actionPerformed(ActionEvent e) {
        refresh();
      }
    }));
    buttonSchema = new JButton(new AbstractAction(stringManager.getString("cmSelectSchema-text"),
        pl.mpak.sky.gui.swing.ImageManager.getImage("/pl/mpak/res/icons/users16.gif")) {
      public void actionPerformed(ActionEvent e) {
        selectSchema();
      }
    });
    toolBar.add(buttonSchema);
    toolBar.addSeparator();
    toolBar.add(labelSchema);
    JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    top.add(toolBar);
    add(top, BorderLayout.NORTH);

    for (SchedulerSpec spec : SchedulerSpecs.all()) {
      SchedulerObjectPanel p = new SchedulerObjectPanel(spec, getDatabase());
      p.setSchemaName(currentSchemaName);
      panels.add(p);
      tabs.addTab(stringManager.getString(spec.titleKey), p);
    }
    tabs.addChangeListener(e -> loadCurrentTab());
    add(tabs, BorderLayout.CENTER);
    updateSchemaLabel();
    addComponentListener(new java.awt.event.ComponentAdapter() {
      @Override
      public void componentShown(java.awt.event.ComponentEvent e) {
        loadCurrentTab();
      }
    });
    java.awt.EventQueue.invokeLater(new Runnable() {
      public void run() {
        loadCurrentTab();
      }
    });
  }

  private void loadCurrentTab() {
    int i = tabs.getSelectedIndex();
    if (i >= 0 && accesibilities != null) {
      panels.get(i).ensureLoaded();
    }
  }

  private void updateSchemaLabel() {
    labelSchema.setText(" " + String.format(stringManager.getString("Scheduler-schema"), currentSchemaName));
  }

  private void selectSchema() {
    Query query = getDatabase().createQuery();
    try {
      query.open(Sql.getSchemaList());
      Vector<String> vl = QueryUtil.staticData("{schema_name}", query);
      Point point = buttonSchema.getLocationOnScreen();
      point.y += buttonSchema.getHeight();
      SimpleSelectDialog.select((Window)SwingUtil.getWindowComponent(this), point.x, point.y, vl, vl.indexOf(currentSchemaName),
        new SimpleSelectDialog.CallBack() {
          public void selected(Object o) {
            setCurrentSchemaName(o.toString());
          }
        });
    } catch (Exception ex) {
      ExceptionUtil.processException(ex);
    } finally {
      query.close();
    }
  }

  private void setCurrentSchemaName(String schemaName) {
    if (schemaName.equals(currentSchemaName)) {
      return;
    }
    currentSchemaName = schemaName;
    accesibilities.setTabTitle(schemaName.equalsIgnoreCase(OracleDbInfoProvider.getCurrentSchema(getDatabase()))
      ? tabTitle : tabTitle + " (" + schemaName + ")");
    for (SchedulerObjectPanel p : panels) {
      p.setSchemaName(schemaName);
    }
    updateSchemaLabel();
    loadCurrentTab();
  }

  public void refresh() {
    int i = tabs.getSelectedIndex();
    if (i >= 0) {
      panels.get(i).refresh();
    }
  }

  public void refresh(String objectName) {
    refresh();
  }

  public boolean canClose() {
    return true;
  }

  public void close() throws IOException {
    for (SchedulerObjectPanel p : panels) {
      p.dispose();
    }
    accesibilities = null;
  }

  public Database getDatabase() {
    return accesibilities.getDatabase();
  }

}
