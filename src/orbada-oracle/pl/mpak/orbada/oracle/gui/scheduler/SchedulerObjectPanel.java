package pl.mpak.orbada.oracle.gui.scheduler;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JToolBar;
import javax.swing.table.DefaultTableModel;
import pl.mpak.orbada.gui.comps.table.ViewTable;
import pl.mpak.orbada.gui.comps.table.ViewTableLoader;
import pl.mpak.orbada.oracle.OrbadaOraclePlugin;
import pl.mpak.sky.gui.mr.ModalResult;
import pl.mpak.sky.gui.swing.MessageBox;
import pl.mpak.usedb.core.CacheRecord;
import pl.mpak.usedb.core.Command;
import pl.mpak.usedb.core.Database;
import pl.mpak.usedb.core.Query;
import pl.mpak.usedb.gui.swing.QueryTableColumn;
import pl.mpak.usedb.gui.swing.QueryTableStatusBar;
import pl.mpak.usedb.util.SQLUtil;
import pl.mpak.util.ExceptionUtil;
import pl.mpak.util.StringManager;
import pl.mpak.util.StringManagerFactory;
import pl.mpak.util.variant.Variant;

/**
 * ORBADA Fuerteventura (F5): jedna zakladka schedulera - lista obiektow (ladowana w tle) i szczegoly
 * zaznaczonego obiektu w zakladkach; pasek akcji DBMS_SCHEDULER.
 */
class SchedulerObjectPanel extends JPanel {

  private final static StringManager stringManager = StringManagerFactory.getStringManager(OrbadaOraclePlugin.class);

  private final SchedulerSpec spec;
  private final Database database;
  private final ViewTable list = new ViewTable();
  private final ViewTableLoader listLoader;
  private final JTabbedPane detailTabs = new JTabbedPane();
  private final DefaultTableModel propertiesModel = new DefaultTableModel(new Object[] {
    stringManager.getString("Scheduler-property"), stringManager.getString("Scheduler-value")}, 0) {
    @Override
    public boolean isCellEditable(int row, int column) {
      return false;
    }
  };
  private final List<ViewTable> detailTables = new ArrayList<ViewTable>();
  private final List<ViewTableLoader> detailLoaders = new ArrayList<ViewTableLoader>();
  private final List<JButton> actionButtons = new ArrayList<JButton>();
  private final javax.swing.Timer selectionTimer;
  private String schemaName;
  private boolean loaded = false;
  private boolean refreshing = false;
  /** Obiekt, dla ktorego pokazano szczegoly (owner, nazwa) - zeby nie odswiezac ich bez potrzeby */
  private String detailsOwner;
  private String detailsName;

  SchedulerObjectPanel(SchedulerSpec spec, Database database) {
    super(new BorderLayout());
    this.spec = spec;
    this.database = database;
    list.getQuery().setDatabase(database);
    listLoader = new ViewTableLoader(list);
    for (SchedulerSpec.Column c : spec.columns) {
      list.addColumn(new QueryTableColumn(c.name, title(c.titleKey, c.name), c.width));
    }
    selectionTimer = new javax.swing.Timer(250, new java.awt.event.ActionListener() {
      public void actionPerformed(ActionEvent e) {
        refreshDetails(false);
      }
    });
    selectionTimer.setRepeats(false);
    list.getSelectionModel().addListSelectionListener(e -> {
      if (!e.getValueIsAdjusting() && !refreshing) {
        selectionTimer.restart();
      }
    });
    detailTabs.addChangeListener(e -> refreshDetails(true));

    JToolBar toolBar = new JToolBar();
    toolBar.setFloatable(false);
    if (SchedulerWizard.supports(spec.id)) {
      toolBar.add(new JButton(new AbstractAction(stringManager.getString("SchedWiz-new"),
          pl.mpak.sky.gui.swing.ImageManager.getImage("/pl/mpak/res/icons/new16.gif")) {
        public void actionPerformed(ActionEvent e) {
          create();
        }
      }));
      if (SchedulerWizard.supportsEdit(spec.id)) {
        // przycisk wlaczany z akcjami (zaznaczony obiekt)
        JButton edit = new JButton(new AbstractAction(stringManager.getString("SchedWiz-edit")) {
          public void actionPerformed(ActionEvent e) {
            edit();
          }
        });
        edit.setEnabled(false);
        actionButtons.add(edit);
        toolBar.add(edit);
      }
      toolBar.addSeparator();
    }
    for (final SchedulerSpec.Action a : spec.actions) {
      JButton b = new JButton(new AbstractAction(stringManager.getString(a.titleKey)) {
        public void actionPerformed(ActionEvent e) {
          runAction(a);
        }
      });
      b.setEnabled(false);
      actionButtons.add(b);
      toolBar.add(b);
    }

    JPanel listPanel = new JPanel(new BorderLayout());
    if (spec.actions.length > 0) {
      JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
      bar.add(toolBar);
      listPanel.add(bar, BorderLayout.NORTH);
    }
    listPanel.add(new JScrollPane(list), BorderLayout.CENTER);
    QueryTableStatusBar status = new QueryTableStatusBar();
    status.setShowFieldType(false);
    status.setShowFieldValue(false);
    status.setShowOpenTime(false);
    status.setTable(list);
    listPanel.add(status, BorderLayout.SOUTH);

    JTable properties = new JTable(propertiesModel);
    properties.getColumnModel().getColumn(0).setPreferredWidth(180);
    properties.getColumnModel().getColumn(1).setPreferredWidth(500);
    detailTabs.addTab(stringManager.getString("Scheduler-tab-properties"), new JScrollPane(properties));
    for (SchedulerSpec.Detail d : spec.details) {
      ViewTable t = new ViewTable();
      t.getQuery().setDatabase(database);
      detailTables.add(t);
      detailLoaders.add(new ViewTableLoader(t));
      detailTabs.addTab(stringManager.getString(d.titleKey), new JScrollPane(t));
    }

    JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, listPanel, detailTabs);
    split.setResizeWeight(0.55);
    split.setContinuousLayout(true);
    split.setBorder(null);
    add(split, BorderLayout.CENTER);
  }

  private static String title(String key, String fallback) {
    try {
      String s = stringManager.getString(key);
      return s == null || s.length() == 0 || s.equals(key) ? fallback.toUpperCase() : s;
    } catch (Exception ex) {
      return fallback.toUpperCase();
    }
  }

  SchedulerSpec getSpec() {
    return spec;
  }

  /** Zmiana schematu - lista zostanie pobrana ponownie przy najblizszym pokazaniu */
  void setSchemaName(String schemaName) {
    if (spec.perSchema && !equal(schemaName, this.schemaName)) {
      loaded = false;
    }
    this.schemaName = schemaName;
  }

  /** Pobiera liste, jesli jeszcze nie byla pobrana (zakladka pokazana pierwszy raz albo po zmianie schematu) */
  void ensureLoaded() {
    if (!loaded) {
      refresh();
    }
  }

  void refresh() {
    refresh(selectedValue(spec.keyColumn));
  }

  /** Kreator nowego obiektu; po utworzeniu lista odswiezona z zaznaczeniem nowego obiektu */
  private void create() {
    SchedulerWizard wizard = SchedulerWizard.create(spec.id, database, schemaName);
    if (wizard != null && wizard.show(this)) {
      refresh(wizard.getObjectName());
    }
  }

  /** Kreator wypelniony zaznaczonym obiektem; po zapisaniu lista i szczegoly odswiezone */
  private void edit() {
    String owner = selectedValue("OWNER");
    String name = selectedValue(spec.keyColumn);
    if (name == null) {
      return;
    }
    SchedulerWizard wizard;
    try {
      wizard = SchedulerWizard.edit(spec.id, database, owner, name);
    } catch (IllegalArgumentException ex) {
      MessageBox.show(this, stringManager.getString("SchedWiz-edit"), ex.getMessage(), ModalResult.OK, MessageBox.WARNING);
      return;
    }
    if (wizard != null && wizard.show(this)) {
      detailsOwner = null;
      detailsName = null;
      refresh(name);
    }
  }

  /** @param key wartosc kolumny klucza wiersza do zaznaczenia po pobraniu listy */
  private void refresh(String key) {
    loaded = true;
    refreshing = true;
    try {
      int column = Math.max(0, list.getSelectedColumn());
      int index = Math.max(0, list.getSelectedRow());
      Query q = list.getQuery();
      q.close();
      q.setSqlText(spec.listSql());
      if (spec.perSchema) {
        q.paramByName("SCHEMA_NAME").setString(schemaName);
      }
      listLoader.open(spec.keyColumn, key, column, index, new Runnable() {
        public void run() {
          refreshing = false;
          refreshDetails(false);
        }
      });
    } catch (Exception ex) {
      refreshing = false;
      ExceptionUtil.processException(ex);
    }
  }

  void dispose() {
    selectionTimer.stop();
    listLoader.dispose();
    for (ViewTableLoader l : detailLoaders) {
      l.dispose();
    }
    list.getQuery().close();
    for (ViewTable t : detailTables) {
      t.getQuery().close();
    }
  }

  private String selectedValue(String column) {
    int row = list.getSelectedRow();
    if (row < 0 || !list.getQuery().isActive()) {
      return null;
    }
    try {
      CacheRecord rec = list.getQuery().getRecord(row);
      if (rec == null) {
        return null;
      }
      Variant v = rec.getField(column).getValue();
      return v.isNullValue() ? null : v.getString();
    } catch (Exception ex) {
      ExceptionUtil.processException(ex);
      return null;
    }
  }

  /**
   * Szczegoly zaznaczonego obiektu: wlasciwosci (jeden wiersz z widoku) i biezaca zakladka szczegolow.
   * @param tabChanged true - zmiana zakladki szczegolow (wlasciwosci nie trzeba czytac ponownie)
   */
  private void refreshDetails(boolean tabChanged) {
    String owner = selectedValue("OWNER");
    String name = selectedValue(spec.keyColumn);
    boolean objectChanged = !equal(owner, detailsOwner) || !equal(name, detailsName);
    for (JButton b : actionButtons) {
      b.setEnabled(name != null);
    }
    if (objectChanged) {
      detailsOwner = owner;
      detailsName = name;
      fillProperties(owner, name);
      for (ViewTable t : detailTables) {
        t.getQuery().close();
      }
    }
    int tab = detailTabs.getSelectedIndex() - 1;
    if (tab >= 0 && tab < detailTables.size() && (objectChanged || tabChanged)) {
      ViewTable t = detailTables.get(tab);
      if (name == null) {
        detailLoaders.get(tab).dispose();
        t.getQuery().close();
      } else if (objectChanged || !t.getQuery().isActive() && !detailLoaders.get(tab).isLoading()) {
        openDetail(tab, owner, name);
      }
    }
  }

  private void openDetail(int tab, String owner, String name) {
    try {
      Query q = detailTables.get(tab).getQuery();
      q.close();
      q.setSqlText(spec.details[tab].sql);
      setParam(q, "OWNER", owner);
      setParam(q, "NAME", name);
      detailLoaders.get(tab).open(null, null, 0, 0, null);
    } catch (Exception ex) {
      ExceptionUtil.processException(ex);
    }
  }

  /** Nie kazde zapytanie szczegolow uzywa :OWNER (np. historia okien) - ustawiamy tylko istniejace parametry */
  private static void setParam(Query q, String param, String value) throws Exception {
    if (q.getParameterList().findParamByName(param) != null) {
      q.paramByName(param).setString(value);
    }
  }

  private void fillProperties(String owner, String name) {
    propertiesModel.setRowCount(0);
    if (name == null) {
      return;
    }
    Query q = database.createQuery();
    try {
      q.setSqlText(spec.detailsSql());
      q.paramByName("OWNER").setString(owner);
      q.paramByName("NAME").setString(name);
      q.open();
      if (!q.isEmpty()) {
        for (int i = 0; i < q.getFieldCount(); i++) {
          Variant v = q.getField(i).getValue();
          propertiesModel.addRow(new Object[] {q.getField(i).getFieldName(), v.isNullValue() ? "" : v.getString()});
        }
      }
    } catch (Exception ex) {
      ExceptionUtil.processException(ex);
    } finally {
      q.close();
    }
  }

  private void runAction(SchedulerSpec.Action action) {
    String owner = selectedValue("OWNER");
    String name = selectedValue(spec.keyColumn);
    if (name == null) {
      return;
    }
    // OWNER.NAZWA, w cudzyslowach tylko gdy trzeba - DBMS_SCHEDULER przyjmuje obie postacie
    String qualified = SQLUtil.createSqlName(owner, name);
    String actionTitle = stringManager.getString(action.titleKey);
    if (action.confirm && MessageBox.show(this, actionTitle,
        String.format(stringManager.getString("Scheduler-confirm"), actionTitle, qualified), ModalResult.YESNO) != ModalResult.YES) {
      return;
    }
    Command command = new Command(database);
    try {
      command.setSqlText(action.plsql);
      command.paramByName("NAME").setString(qualified);
      command.execute();
      refresh();
    } catch (Exception ex) {
      MessageBox.show(this, actionTitle, ex.getMessage(), ModalResult.OK, MessageBox.ERROR);
    }
  }

  private static boolean equal(String a, String b) {
    return a == null ? b == null : a.equals(b);
  }

}
