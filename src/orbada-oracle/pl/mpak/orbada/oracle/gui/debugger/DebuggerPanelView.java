package pl.mpak.orbada.oracle.gui.debugger;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.Closeable;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import pl.mpak.orbada.db.ConnectionFactory;
import pl.mpak.orbada.gui.comps.OrbadaSyntaxTextArea;
import pl.mpak.orbada.oracle.OrbadaOraclePlugin;
import pl.mpak.orbada.oracle.debug.PlSqlDebugger;
import pl.mpak.orbada.oracle.debug.PlSqlDebugger.Breakpoint;
import pl.mpak.orbada.oracle.debug.PlSqlDebugger.Frame;
import pl.mpak.orbada.oracle.debug.PlSqlDebugger.RuntimeInfo;
import pl.mpak.orbada.oracle.debug.PlSqlDebugger.State;
import pl.mpak.orbada.oracle.debug.PlSqlDebugger.Value;
import pl.mpak.orbada.oracle.debug.PlSqlVariables;
import pl.mpak.orbada.oracle.syntax.parser.OraclePlSqlStructureParser;
import pl.mpak.orbada.plugins.IViewAccesibilities;
import pl.mpak.sky.gui.mr.ModalResult;
import pl.mpak.sky.gui.swing.MessageBox;
import pl.mpak.sky.gui.swing.syntax.GutterEvent;
import pl.mpak.sky.gui.swing.syntax.GutterListener;
import pl.mpak.sky.gui.swing.syntax.SyntaxDocument.LineMark;
import pl.mpak.usedb.core.Command;
import pl.mpak.usedb.core.Database;
import pl.mpak.usedb.core.Query;
import pl.mpak.util.StringManager;
import pl.mpak.util.StringManagerFactory;

/**
 * ORBADA 1.3.6: widok "Debugger PL/SQL" - podlaczenie do sesji innej aplikacji przez DBMS_DEBUG (identyfikator z
 * DBMS_DEBUG.INITIALIZE pokazany w aplikacji), breakpointy na marginesie zrodla, kroki, stos wywolan, zmienne
 * (z mozliwoscia zmiany), obserwowane wyrazenia, zatrzymanie na wyjatku i kompilacja obiektu z DEBUG.
 *
 * <p>Debugger dziala na osobnym polaczeniu (kopia biezacego); zrodla i kompilacja ida przez polaczenie widoku.</p>
 */
public class DebuggerPanelView extends JPanel implements Closeable, PlSqlDebugger.Listener {

  private static final long serialVersionUID = 1L;

  private final StringManager stringManager = StringManagerFactory.getStringManager(OrbadaOraclePlugin.class);

  private static final Color CURRENT_LINE_COLOR = new Color(255, 240, 150);
  private static final Color BREAKPOINT_COLOR = new Color(255, 215, 215);

  private IViewAccesibilities accesibilities;
  private volatile Database debugDatabase;
  private volatile PlSqlDebugger debugger;
  private State state = State.DETACHED;

  /** Breakpointy widoku (przed podlaczeniem i po odlaczeniu tez) */
  private final List<BreakpointDef> breakpoints = new ArrayList<BreakpointDef>();
  private final List<String> watches = new ArrayList<String>();
  private final Map<String, SourceInfo> sources = new HashMap<String, SourceInfo>();

  /** Zrodlo widoczne w edytorze */
  private SourceInfo shown;
  /** Ramka, ktorej dotyczy podglad zmiennych */
  private Frame frame;
  private RuntimeInfo info;
  private List<Frame> stack = new ArrayList<Frame>();
  private int loadNo = 0;

  private JTextField textId;
  private JButton buttonAttach;
  private JButton buttonDetach;
  private JButton buttonResume;
  private JButton buttonStepOver;
  private JButton buttonStepInto;
  private JButton buttonStepOut;
  private JButton buttonAbort;
  private JButton buttonToggle;
  private JButton buttonCompile;
  private JCheckBox checkException;
  private JLabel labelStatus;
  private JLabel labelObject;
  private OrbadaSyntaxTextArea syntaxSource;
  private ValuesModel variablesModel;
  private ValuesModel watchesModel;
  private DefaultListModel<Frame> stackModel;
  private JList<Frame> listStack;
  private BreakpointsModel breakpointsModel;
  private JTable tableBreakpoints;
  private JTable tableWatches;
  private JTextArea textLog;

  /** Breakpoint ustawiony w widoku; bp - odpowiednik w debuggerze (gdy podlaczony) */
  private static class BreakpointDef {
    final String owner;
    final String name;
    final int namespace;
    final int line;
    Breakpoint bp;

    BreakpointDef(String owner, String name, int namespace, int line) {
      this.owner = owner;
      this.name = name;
      this.namespace = namespace;
      this.line = line;
    }

    boolean in(SourceInfo s) {
      return s != null && owner.equals(s.owner) && name.equals(s.name) && namespace == s.namespace;
    }
  }

  private static class SourceInfo {
    String owner;
    String name;
    int namespace;
    String type;
    String text;
    boolean debug;

    String key() {
      return key(owner, name, namespace);
    }

    static String key(String owner, String name, int namespace) {
      return owner + "." + name + "#" + namespace;
    }
  }

  public DebuggerPanelView(IViewAccesibilities accesibilities) {
    this.accesibilities = accesibilities;
    initComponents();
    updateState();
  }

  public Database getDatabase() {
    return accesibilities.getDatabase();
  }

  // ================================================================= GUI

  private ImageIcon icon(String name) {
    return pl.mpak.sky.gui.swing.ImageManager.getImage("/pl/mpak/res/icons/" + name);
  }

  private JButton button(JToolBar bar, String textKey, String iconName, boolean showText, final Runnable run) {
    JButton b = new JButton(new AbstractAction(showText ? stringManager.getString(textKey) : null, icon(iconName)) {
      private static final long serialVersionUID = 1L;
      public void actionPerformed(ActionEvent e) {
        run.run();
      }
    });
    b.setToolTipText(stringManager.getString(textKey));
    b.setFocusable(false);
    bar.add(b);
    return b;
  }

  private void key(KeyStroke ks, final JButton button) {
    String name = "debugger-" + ks.toString();
    getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(ks, name);
    getActionMap().put(name, new AbstractAction() {
      private static final long serialVersionUID = 1L;
      public void actionPerformed(ActionEvent e) {
        if (button.isEnabled()) {
          button.doClick();
        }
      }
    });
  }

  private void initComponents() {
    setLayout(new BorderLayout());

    JToolBar bar = new JToolBar();
    bar.setFloatable(false);
    bar.setRollover(true);
    bar.add(new JLabel(" " + stringManager.getString("debugger-session-id") + " "));
    textId = new JTextField(28);
    textId.setMaximumSize(new Dimension(320, textId.getPreferredSize().height));
    textId.setToolTipText(stringManager.getString("debugger-attach-hint"));
    bar.add(textId);
    buttonAttach = button(bar, "debugger-attach", "connect16.gif", true, new Runnable() {
      public void run() {
        attach();
      }
    });
    buttonDetach = button(bar, "debugger-detach", "fatcow/small/disconnect.png", true, new Runnable() {
      public void run() {
        detach();
      }
    });
    bar.addSeparator();
    buttonResume = button(bar, "debugger-resume", "debug_resume.gif", false, new Runnable() {
      public void run() {
        debugger.resume();
      }
    });
    buttonStepOver = button(bar, "debugger-step-over", "debug_step_over.gif", false, new Runnable() {
      public void run() {
        debugger.stepOver();
      }
    });
    buttonStepInto = button(bar, "debugger-step-into", "debug_step_into.gif", false, new Runnable() {
      public void run() {
        debugger.stepInto();
      }
    });
    buttonStepOut = button(bar, "debugger-step-out", "debug_step_return.gif", false, new Runnable() {
      public void run() {
        debugger.stepOut();
      }
    });
    buttonAbort = button(bar, "debugger-abort", "debug_terminate.gif", false, new Runnable() {
      public void run() {
        debugger.abort();
      }
    });
    checkException = new JCheckBox(stringManager.getString("debugger-break-exception"));
    checkException.setFocusable(false);
    checkException.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (debugger != null) {
          debugger.setBreakOnException(checkException.isSelected());
        }
      }
    });
    bar.add(checkException);
    bar.addSeparator();
    buttonToggle = button(bar, "debugger-toggle-bp", "red_bdot.gif", false, new Runnable() {
      public void run() {
        toggleBreakpointAtCaret();
      }
    });
    button(bar, "debugger-open", "db_info16.gif", false, new Runnable() {
      public void run() {
        openObject();
      }
    });
    buttonCompile = button(bar, "debugger-compile", "debug.gif", false, new Runnable() {
      public void run() {
        compileDebug();
      }
    });
    button(bar, "debugger-app-code", "edit16.gif", false, new Runnable() {
      public void run() {
        showAppCode();
      }
    });

    key(KeyStroke.getKeyStroke(KeyEvent.VK_F9, 0), buttonResume);
    key(KeyStroke.getKeyStroke(KeyEvent.VK_F8, 0), buttonStepOver);
    key(KeyStroke.getKeyStroke(KeyEvent.VK_F7, 0), buttonStepInto);
    key(KeyStroke.getKeyStroke(KeyEvent.VK_F8, InputEvent.SHIFT_DOWN_MASK), buttonStepOut);
    key(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), buttonToggle);

    labelStatus = new JLabel(" ");
    labelStatus.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
    labelStatus.setFont(labelStatus.getFont().deriveFont(Font.BOLD));
    JPanel north = new JPanel(new BorderLayout());
    north.add(bar, BorderLayout.NORTH);
    north.add(labelStatus, BorderLayout.SOUTH);
    add(north, BorderLayout.NORTH);

    // zrodlo
    syntaxSource = new OrbadaSyntaxTextArea();
    syntaxSource.setDatabase(getDatabase());
    syntaxSource.setStructureParser(new OraclePlSqlStructureParser());
    syntaxSource.setEditable(false);
    syntaxSource.addGutterListener(new GutterListener() {
      public void markLineSelected(GutterEvent e) {
        toggleBreakpoint(e.getLine());
      }
    });
    labelObject = new JLabel(" ");
    labelObject.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
    JPanel sourcePanel = new JPanel(new BorderLayout());
    sourcePanel.add(labelObject, BorderLayout.NORTH);
    sourcePanel.add(syntaxSource, BorderLayout.CENTER);

    // zakladki
    JTabbedPane tabs = new JTabbedPane();
    variablesModel = new ValuesModel(false);
    JTable tableVariables = valuesTable(variablesModel);
    tabs.addTab(stringManager.getString("debugger-tab-variables"), new JScrollPane(tableVariables));

    watchesModel = new ValuesModel(true);
    tableWatches = valuesTable(watchesModel);
    JPanel watchesPanel = new JPanel(new BorderLayout());
    JPanel watchButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 2));
    watchButtons.add(new JButton(new AbstractAction(stringManager.getString("debugger-watch-add"), icon("new16.gif")) {
      private static final long serialVersionUID = 1L;
      public void actionPerformed(ActionEvent e) {
        addWatch(null);
      }
    }));
    watchButtons.add(new JButton(new AbstractAction(stringManager.getString("debugger-remove"), icon("stop16.gif")) {
      private static final long serialVersionUID = 1L;
      public void actionPerformed(ActionEvent e) {
        int r = tableWatches.getSelectedRow();
        if (r >= 0 && r < watches.size()) {
          watches.remove(r);
          refreshValues();
        }
      }
    }));
    watchesPanel.add(watchButtons, BorderLayout.NORTH);
    watchesPanel.add(new JScrollPane(tableWatches), BorderLayout.CENTER);
    tabs.addTab(stringManager.getString("debugger-tab-watches"), watchesPanel);
    tableVariables.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        JTable t = (JTable)e.getSource();
        int r = t.rowAtPoint(e.getPoint());
        if (e.getClickCount() == 2 && t.columnAtPoint(e.getPoint()) == 0 && r >= 0) {
          addWatch(variablesModel.names.get(r));
        }
      }
    });

    stackModel = new DefaultListModel<Frame>();
    listStack = new JList<Frame>(stackModel);
    listStack.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    listStack.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        if (!e.getValueIsAdjusting() && listStack.getSelectedValue() != null && listStack.getSelectedValue() != frame) {
          showFrame(listStack.getSelectedValue());
        }
      }
    });
    tabs.addTab(stringManager.getString("debugger-tab-stack"), new JScrollPane(listStack));

    breakpointsModel = new BreakpointsModel();
    tableBreakpoints = new JTable(breakpointsModel);
    tableBreakpoints.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    tableBreakpoints.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        int r = tableBreakpoints.getSelectedRow();
        if (e.getClickCount() == 2 && r >= 0 && r < breakpoints.size()) {
          BreakpointDef d = breakpoints.get(r);
          showSource(d.owner, d.name, d.namespace, d.line);
        }
      }
    });
    JPanel bpPanel = new JPanel(new BorderLayout());
    JPanel bpButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 2));
    bpButtons.add(new JButton(new AbstractAction(stringManager.getString("debugger-remove"), icon("stop16.gif")) {
      private static final long serialVersionUID = 1L;
      public void actionPerformed(ActionEvent e) {
        int r = tableBreakpoints.getSelectedRow();
        if (r >= 0 && r < breakpoints.size()) {
          removeBreakpoint(breakpoints.get(r));
        }
      }
    }));
    bpPanel.add(bpButtons, BorderLayout.NORTH);
    bpPanel.add(new JScrollPane(tableBreakpoints), BorderLayout.CENTER);
    tabs.addTab(stringManager.getString("debugger-tab-breakpoints"), bpPanel);

    textLog = new JTextArea();
    textLog.setEditable(false);
    textLog.setFont(new Font(Font.MONOSPACED, Font.PLAIN, textLog.getFont().getSize()));
    tabs.addTab(stringManager.getString("debugger-tab-log"), new JScrollPane(textLog));

    // poczatkowy podzial z preferowanych rozmiarow: zrodlo wieksze niz zakladki
    sourcePanel.setPreferredSize(new Dimension(600, 420));
    tabs.setPreferredSize(new Dimension(600, 180));
    JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, sourcePanel, tabs);
    split.setResizeWeight(0.7);
    split.setOneTouchExpandable(true);
    add(split, BorderLayout.CENTER);
  }

  private JTable valuesTable(ValuesModel model) {
    JTable t = new JTable(model);
    t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    t.getColumnModel().getColumn(0).setPreferredWidth(160);
    t.getColumnModel().getColumn(1).setPreferredWidth(400);
    t.setToolTipText(stringManager.getString("debugger-value-edit-hint"));
    return t;
  }

  // ================================================================= podlaczenie

  private void attach() {
    String id = textId.getText().trim();
    if (id.length() > 1 && (id.startsWith("'") && id.endsWith("'") || id.startsWith("\"") && id.endsWith("\""))) {
      id = id.substring(1, id.length() - 1).trim();
    }
    if (id.length() == 0 || debugger != null) {
      textId.requestFocusInWindow();
      return;
    }
    final String sessionId = id;
    buttonAttach.setEnabled(false);
    new Thread(new Runnable() {
      public void run() {
        Database db = null;
        try {
          db = createDebugDatabase();
          final PlSqlDebugger d = new PlSqlDebugger(db.getConnection(), DebuggerPanelView.this);
          d.setBreakOnException(checkException.isSelected());
          synchronized (breakpoints) {
            for (BreakpointDef b : breakpoints) {
              b.bp = d.addBreakpoint(b.owner, b.name, b.namespace, b.line);
            }
          }
          debugDatabase = db;
          debugger = d;
          d.attach(sessionId);
          message(stringManager.getString("debugger-log-attached", sessionId), false);
        } catch (final Exception ex) {
          debugger = null;
          debugDatabase = null;
          closeDatabase(db);
          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              updateState();
              MessageBox.show(DebuggerPanelView.this, stringManager.getString("error"), ex.getMessage(), ModalResult.OK, MessageBox.ERROR);
            }
          });
        }
      }
    }, "orbada-debugger-attach").start();
  }

  /** Osobne polaczenie debuggera - kopia polaczenia widoku */
  protected Database createDebugDatabase() throws Exception {
    return new ConnectionFactory(getDatabase()).createDatabase();
  }

  private void detach() {
    if (debugger != null) {
      buttonDetach.setEnabled(false);
      debugger.detach();
    }
  }

  private static void closeDatabase(final Database db) {
    if (db != null) {
      try {
        db.close();
      } catch (Throwable ex) {
        // zamykanie
      }
    }
  }

  public void close() throws IOException {
    final PlSqlDebugger d = debugger;
    final Database db = debugDatabase;
    debugger = null;
    debugDatabase = null;
    if (d != null) {
      new Thread(new Runnable() {
        public void run() {
          d.detach();
          try {
            d.join(10000);
          } catch (InterruptedException ex) {
            // koniec
          }
          closeDatabase(db);
        }
      }, "orbada-debugger-close").start();
    } else {
      closeDatabase(db);
    }
    syntaxSource.setDatabase(null);
  }

  // ================================================================= zdarzenia debuggera (watek roboczy)

  public void stateChanged(final State state, final RuntimeInfo info, final List<Frame> stack) {
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        onState(state, info, stack);
      }
    });
  }

  public void message(final String text, final boolean error) {
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        textLog.append(new SimpleDateFormat("HH:mm:ss").format(new Date()) + (error ? " ! " : "   ") + text + "\n");
        // dlugie sesje - dziennik ograniczony do ostatnich ~200 tys. znakow
        int over = textLog.getDocument().getLength() - 200000;
        if (over > 0) {
          textLog.replaceRange("", 0, over);
        }
        textLog.setCaretPosition(textLog.getDocument().getLength());
      }
    });
  }

  private void onState(State newState, RuntimeInfo newInfo, List<Frame> newStack) {
    State old = state;
    state = newState;
    if (newState == State.DETACHED && debugger != null) {
      final PlSqlDebugger d = debugger;
      final Database db = debugDatabase;
      debugger = null;
      debugDatabase = null;
      synchronized (breakpoints) {
        for (BreakpointDef b : breakpoints) {
          b.bp = null;
        }
      }
      message(stringManager.getString("debugger-log-detached"), false);
      new Thread(new Runnable() {
        public void run() {
          try {
            d.join(10000);
          } catch (InterruptedException ex) {
            // koniec
          }
          closeDatabase(db);
        }
      }, "orbada-debugger-close").start();
    }
    if (newState == State.PAUSED) {
      info = newInfo;
      stack = newStack != null ? newStack : new ArrayList<Frame>();
      stackModel.clear();
      for (int i = stack.size() - 1; i >= 0; i--) {
        stackModel.addElement(stack.get(i));
      }
      Frame top = stack.isEmpty() ? null : stack.get(stack.size() - 1);
      // frame ustawiony przed zaznaczeniem - listener listy nie laduje ramki drugi raz
      frame = top;
      if (top != null) {
        listStack.setSelectedIndex(0);
        showFrame(top);
      }
      if (debugger != null && debugger.isException(info)) {
        message(stringManager.getString("debugger-reason-exception", String.format("%05d", info.oer)), true);
      }
    } else if (newState == State.WAITING || newState == State.DETACHED) {
      if (old == State.RUNNING || old == State.PAUSED) {
        message(stringManager.getString("debugger-call-finished"), false);
      }
      clearPause();
    }
    updateState();
  }

  private void clearPause() {
    info = null;
    frame = null;
    stack = new ArrayList<Frame>();
    stackModel.clear();
    variablesModel.set(new ArrayList<String>(), null);
    watchesModel.set(new ArrayList<String>(watches), null);
    updateMarks();
  }

  private void updateState() {
    boolean attached = debugger != null && state != State.DETACHED;
    boolean paused = attached && state == State.PAUSED;
    textId.setEnabled(!attached);
    buttonAttach.setEnabled(debugger == null);
    buttonDetach.setEnabled(attached);
    buttonResume.setEnabled(paused);
    buttonStepOver.setEnabled(paused);
    buttonStepInto.setEnabled(paused);
    buttonStepOut.setEnabled(paused);
    buttonAbort.setEnabled(paused);
    // kompilacja zatrzymanego/wykonywanego kodu zawislaby na blokadzie biblioteki
    buttonCompile.setEnabled(shown != null && (state == State.DETACHED || state == State.WAITING));
    String text;
    switch (attached ? state : State.DETACHED) {
      case WAITING: text = stringManager.getString("debugger-state-waiting"); break;
      case RUNNING: text = stringManager.getString("debugger-state-running"); break;
      case PAUSED:
        Frame top = stack.isEmpty() ? null : stack.get(stack.size() - 1);
        String where = top != null && top.hasSource() ? top.owner + "." + top.name + (top.entryPoint != null && !top.entryPoint.equals(top.name) ? "." + top.entryPoint : "") : "?";
        String reason;
        if (debugger != null && debugger.isException(info)) {
          reason = stringManager.getString("debugger-reason-exception", String.format("%05d", info.oer));
        } else if (info != null && info.breakpoint > 0) {
          reason = stringManager.getString("debugger-reason-breakpoint");
        } else {
          reason = stringManager.getString("debugger-reason-step");
        }
        text = stringManager.getString("debugger-state-paused", new Object[] {where, Integer.toString(info != null ? info.line : 0)}) + " (" + reason + ")";
        break;
      default: text = stringManager.getString("debugger-state-detached");
    }
    labelStatus.setText(text);
    labelStatus.setForeground(paused ? new Color(0xB0, 0x30, 0x00) : attached ? new Color(0x00, 0x70, 0x00) : Color.GRAY);
    breakpointsModel.fireTableDataChanged();
  }

  // ================================================================= ramka, zrodlo, zmienne

  private void showFrame(final Frame f) {
    frame = f;
    if (!f.hasSource()) {
      shown = null;
      syntaxSource.setText("");
      labelObject.setText(stringManager.getString("debugger-no-source"));
      variablesModel.set(new ArrayList<String>(), null);
      refreshValues();
      updateState();
      return;
    }
    showSource(f.owner, f.name, f.namespace, f.line);
  }

  /** Pokazuje zrodlo (z bazy w tle, potem z pamieci) i przewija do linii */
  private void showSource(final String owner, final String name, final int namespace, final int line) {
    SourceInfo cached = sources.get(SourceInfo.key(owner, name, namespace));
    if (cached != null) {
      setSource(cached, line);
      return;
    }
    final int no = ++loadNo;
    new Thread(new Runnable() {
      public void run() {
        final SourceInfo s = loadSource(owner, name, namespace);
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            if (no != loadNo) {
              return;
            }
            if (s == null) {
              MessageBox.show(DebuggerPanelView.this, stringManager.getString("debugger-open"),
                stringManager.getString("debugger-open-not-found", owner + "." + name), ModalResult.OK, MessageBox.WARNING);
              return;
            }
            sources.put(s.key(), s);
            setSource(s, line);
          }
        });
      }
    }, "orbada-debugger-source").start();
  }

  private SourceInfo loadSource(String owner, String name, int namespace) {
    try {
      Query q = getDatabase().createQuery();
      try {
        StringBuilder types = new StringBuilder();
        for (String t : PlSqlDebugger.sourceTypes(namespace)) {
          types.append(types.length() > 0 ? ", " : "").append("'").append(t).append("'");
        }
        q.setSqlText("select s.type, s.text, (select max(o.plsql_debug) from all_plsql_object_settings o\n" +
          "          where o.owner = s.owner and o.name = s.name and o.type = s.type) plsql_debug\n" +
          "  from all_source s\n" +
          " where s.owner = :owner and s.name = :name and s.type in (" + types + ")\n" +
          " order by s.type, s.line");
        q.paramByName("owner").setString(owner);
        q.paramByName("name").setString(name);
        q.open();
        if (q.eof()) {
          return null;
        }
        SourceInfo s = new SourceInfo();
        s.owner = owner;
        s.name = name;
        s.namespace = namespace;
        s.type = q.fieldByName("type").getString();
        s.debug = "TRUE".equals(q.fieldByName("plsql_debug").getString());
        StringBuilder text = new StringBuilder();
        while (!q.eof() && s.type.equals(q.fieldByName("type").getString())) {
          String l = q.fieldByName("text").getString();
          text.append(l == null ? "\n" : l);
          if (l != null && !l.endsWith("\n")) {
            text.append('\n');
          }
          q.next();
        }
        s.text = text.toString();
        return s;
      } finally {
        q.close();
      }
    } catch (Exception ex) {
      message(ex.getMessage(), true);
      return null;
    }
  }

  private void setSource(SourceInfo s, int line) {
    if (shown != s) {
      shown = s;
      syntaxSource.setText(s.text);
    }
    labelObject.setText(s.owner + "." + s.name + " (" + s.type + ")" + (s.debug ? "" : "  -  " + stringManager.getString("debugger-no-debug-info")));
    labelObject.setForeground(s.debug ? javax.swing.UIManager.getColor("Label.foreground") : new Color(0xB0, 0x30, 0x00));
    updateMarks();
    if (line > 0) {
      try {
        syntaxSource.getEditorArea().setCaretPosition(syntaxSource.getEditorArea().getLineStartOffset(line - 1), true);
      } catch (Exception ex) {
        // linia poza zrodlem
      }
    }
    if (frame != null && frame.hasSource() && s.owner.equals(frame.owner) && s.name.equals(frame.name) && s.namespace == frame.namespace) {
      List<String> names = new ArrayList<String>(PlSqlVariables.find(s.text, frame.entryPoint, frame.line));
      if (s.namespace == PlSqlDebugger.NAMESPACE_PKG_BODY && "PACKAGE BODY".equals(s.type)) {
        for (String g : PlSqlVariables.packageGlobals(s.text)) {
          names.add(s.owner + "." + s.name + "." + g);
        }
      }
      variablesModel.set(names, null);
      refreshValues();
    }
    updateState();
  }

  /** Odczyt zmiennych i obserwowanych dla wybranej ramki */
  private void refreshValues() {
    watchesModel.set(new ArrayList<String>(watches), null);
    final PlSqlDebugger d = debugger;
    final Frame f = frame;
    if (d == null || f == null || state != State.PAUSED) {
      return;
    }
    final List<String> vars = new ArrayList<String>(variablesModel.names);
    final List<String> watched = new ArrayList<String>(watches);
    List<String> all = new ArrayList<String>(vars);
    all.addAll(watched);
    d.getValues(f.index, all, new PlSqlDebugger.Callback<List<Value>>() {
      public void done(final List<Value> result) {
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            if (f != frame || result.size() != vars.size() + watched.size()) {
              return;
            }
            // zmienne poza zasiegiem (np. petla FOR jeszcze nie wykonana) pomijamy
            List<String> vn = new ArrayList<String>();
            List<Value> vv = new ArrayList<Value>();
            for (int i = 0; i < vars.size(); i++) {
              if (result.get(i).code != PlSqlDebugger.ERROR_NO_SUCH_OBJECT) {
                vn.add(vars.get(i));
                vv.add(result.get(i));
              }
            }
            variablesModel.set(vn, vv);
            watchesModel.set(watched, new ArrayList<Value>(result.subList(vars.size(), result.size())));
          }
        });
      }
    });
  }

  private void addWatch(String name) {
    if (name == null) {
      name = JOptionPane.showInputDialog(this, stringManager.getString("debugger-watch-add-q"), stringManager.getString("debugger-tab-watches"), JOptionPane.QUESTION_MESSAGE);
    }
    if (name != null && name.trim().length() > 0 && !watches.contains(name.trim())) {
      watches.add(name.trim());
      refreshValues();
    }
  }

  private String display(Value v) {
    if (v == null) {
      return "";
    }
    if (v.code == 0) {
      return v.value;
    }
    if (v.code == PlSqlDebugger.VALUE_NULL) {
      return "NULL";
    }
    if (v.code == PlSqlDebugger.ERROR_INDEXED_TABLE) {
      return stringManager.getString("debugger-value-collection");
    }
    if (v.code == PlSqlDebugger.ERROR_NULLCOLLECTION) {
      return stringManager.getString("debugger-value-nullcollection");
    }
    if (v.code == PlSqlDebugger.ERROR_NO_DEBUG_INFO) {
      return stringManager.getString("debugger-value-nodebug");
    }
    if (v.code == PlSqlDebugger.ERROR_NO_SUCH_OBJECT) {
      return stringManager.getString("debugger-value-unknown");
    }
    return stringManager.getString("debugger-value-error", v.code < 0 ? v.value : Integer.toString(v.code));
  }

  private void setValue(String name, String expression) {
    final PlSqlDebugger d = debugger;
    if (d == null || frame == null || state != State.PAUSED || expression == null) {
      return;
    }
    d.setValue(frame.index, name, expression, new PlSqlDebugger.Callback<String>() {
      public void done(final String error) {
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            if (error != null) {
              MessageBox.show(DebuggerPanelView.this, stringManager.getString("error"), stringManager.getString("debugger-set-value-error", error), ModalResult.OK, MessageBox.ERROR);
            }
            refreshValues();
          }
        });
      }
    });
  }

  private class ValuesModel extends AbstractTableModel {
    private static final long serialVersionUID = 1L;
    final boolean watch;
    List<String> names = new ArrayList<String>();
    List<Value> values;

    ValuesModel(boolean watch) {
      this.watch = watch;
    }

    void set(List<String> names, List<Value> values) {
      this.names = names;
      this.values = values;
      fireTableDataChanged();
    }

    public int getRowCount() {
      return names.size();
    }

    public int getColumnCount() {
      return 2;
    }

    @Override
    public String getColumnName(int column) {
      return stringManager.getString(column == 0 ? "debugger-col-name" : "debugger-col-value");
    }

    public Object getValueAt(int row, int column) {
      if (column == 0) {
        return names.get(row);
      }
      return values != null && row < values.size() ? display(values.get(row)) : "";
    }

    @Override
    public boolean isCellEditable(int row, int column) {
      return column == 1 && state == State.PAUSED && values != null && row < values.size()
        && (values.get(row).code == 0 || values.get(row).code == PlSqlDebugger.VALUE_NULL);
    }

    @Override
    public void setValueAt(Object value, int row, int column) {
      String old = (String)getValueAt(row, column);
      if (value != null && !value.toString().equals(old)) {
        setValue(names.get(row), value.toString());
      }
    }
  }

  // ================================================================= breakpointy

  private void toggleBreakpointAtCaret() {
    if (shown == null) {
      return;
    }
    int line = syntaxSource.getEditorArea().getLineOfOffset(syntaxSource.getEditorArea().getCaretPosition()) + 1;
    toggleBreakpoint(line);
  }

  private void toggleBreakpoint(int line) {
    if (shown == null || line < 1) {
      return;
    }
    synchronized (breakpoints) {
      for (BreakpointDef b : breakpoints) {
        if (b.in(shown) && b.line == line) {
          removeBreakpoint(b);
          return;
        }
      }
      BreakpointDef b = new BreakpointDef(shown.owner, shown.name, shown.namespace, line);
      breakpoints.add(b);
      if (debugger != null) {
        b.bp = debugger.addBreakpoint(b.owner, b.name, b.namespace, b.line);
      }
    }
    updateMarks();
    breakpointsModel.fireTableDataChanged();
  }

  private void removeBreakpoint(BreakpointDef b) {
    synchronized (breakpoints) {
      breakpoints.remove(b);
      if (debugger != null && b.bp != null) {
        debugger.removeBreakpoint(b.bp);
      }
    }
    updateMarks();
    breakpointsModel.fireTableDataChanged();
  }

  private void updateMarks() {
    syntaxSource.clearLineMarks();
    if (shown != null) {
      int current = -1;
      if (frame != null && frame.hasSource() && shown.owner.equals(frame.owner) && shown.name.equals(frame.name) && shown.namespace == frame.namespace) {
        current = frame.line;
      }
      ImageIcon bpIcon = icon("red_bdot.gif");
      if (current > 0) {
        syntaxSource.setLineMark(new LineMark(current, CURRENT_LINE_COLOR, icon("line_point.gif"), null));
      }
      synchronized (breakpoints) {
        for (BreakpointDef b : breakpoints) {
          if (b.in(shown) && b.line != current) {
            syntaxSource.setLineMark(new LineMark(b.line, BREAKPOINT_COLOR, bpIcon, stringManager.getString("debugger-tab-breakpoints")));
          }
        }
      }
    }
    syntaxSource.getEditorArea().repaint();
    syntaxSource.getGutterPanel().repaint();
  }

  private class BreakpointsModel extends AbstractTableModel {
    private static final long serialVersionUID = 1L;

    public int getRowCount() {
      return breakpoints.size();
    }

    public int getColumnCount() {
      return 3;
    }

    @Override
    public String getColumnName(int column) {
      return stringManager.getString(column == 0 ? "debugger-col-object" : column == 1 ? "debugger-col-line" : "debugger-col-state");
    }

    public Object getValueAt(int row, int column) {
      if (row >= breakpoints.size()) {
        return "";
      }
      BreakpointDef b = breakpoints.get(row);
      switch (column) {
        case 0: return b.owner + "." + b.name + (b.namespace == PlSqlDebugger.NAMESPACE_PKG_BODY ? " (body)" : "");
        case 1: return Integer.valueOf(b.line);
        default:
          if (b.bp == null) {
            return "";
          }
          if (b.bp.isSet()) {
            return stringManager.getString("debugger-bp-set");
          }
          if (b.bp.getError() != 0) {
            return stringManager.getString("debugger-bp-error", Integer.toString(b.bp.getError()));
          }
          return stringManager.getString("debugger-bp-pending");
      }
    }
  }

  // ================================================================= obiekty

  private void openObject() {
    String text = JOptionPane.showInputDialog(this, stringManager.getString("debugger-open-q"), stringManager.getString("debugger-open"), JOptionPane.QUESTION_MESSAGE);
    if (text == null || text.trim().length() == 0) {
      return;
    }
    text = text.trim();
    String owner = null;
    String name = text;
    int dot = text.indexOf('.');
    if (dot > 0) {
      owner = ident(text.substring(0, dot));
      name = text.substring(dot + 1);
    }
    name = ident(name);
    try {
      Query q = getDatabase().createQuery();
      try {
        q.setSqlText("select owner, type from all_source\n" +
          " where name = :name and (:owner is null or owner = :owner2) and line = 1\n" +
          "   and type in ('PACKAGE BODY', 'TYPE BODY', 'PROCEDURE', 'FUNCTION', 'TRIGGER', 'PACKAGE', 'TYPE')\n" +
          " order by decode(owner, user, 0, 1), decode(type, 'PACKAGE BODY', 0, 'TYPE BODY', 1, 'PACKAGE', 3, 'TYPE', 3, 2), owner");
        q.paramByName("name").setString(name);
        q.paramByName("owner").setString(owner);
        q.paramByName("owner2").setString(owner);
        q.open();
        if (q.eof()) {
          MessageBox.show(this, stringManager.getString("debugger-open"), stringManager.getString("debugger-open-not-found", text), ModalResult.OK, MessageBox.WARNING);
          return;
        }
        String o = q.fieldByName("owner").getString();
        String type = q.fieldByName("type").getString();
        frame = null;
        showSource(o, name, PlSqlDebugger.namespace(type), 1);
      } finally {
        q.close();
      }
    } catch (Exception ex) {
      MessageBox.show(this, stringManager.getString("error"), ex.getMessage(), ModalResult.OK, MessageBox.ERROR);
    }
  }

  /** Nazwa wpisana przez uzytkownika: "x" - jak jest, inaczej wielkimi literami */
  private static String ident(String s) {
    s = s.trim();
    if (s.length() > 1 && s.startsWith("\"") && s.endsWith("\"")) {
      return s.substring(1, s.length() - 1);
    }
    return s.toUpperCase(java.util.Locale.ROOT);
  }

  private static String quote(String s) {
    return "\"" + s + "\"";
  }

  /** ALTER ... COMPILE DEBUG dla widocznego obiektu */
  static String compileSql(String owner, String name, String type) {
    String obj = quote(owner) + "." + quote(name);
    if ("PACKAGE BODY".equals(type)) {
      return "ALTER PACKAGE " + obj + " COMPILE DEBUG BODY";
    }
    if ("TYPE BODY".equals(type)) {
      return "ALTER TYPE " + obj + " COMPILE DEBUG BODY";
    }
    return "ALTER " + type + " " + obj + " COMPILE DEBUG";
  }

  private void compileDebug() {
    final SourceInfo s = shown;
    if (s == null) {
      return;
    }
    final String sql = compileSql(s.owner, s.name, s.type);
    if (MessageBox.show(this, stringManager.getString("debugger-compile"), stringManager.getString("debugger-compile-q", sql), ModalResult.YESNO) != ModalResult.YES) {
      return;
    }
    buttonCompile.setEnabled(false);
    new Thread(new Runnable() {
      public void run() {
        String error = null;
        boolean invalid = false;
        try {
          Command command = getDatabase().createCommand();
          command.setParamCheck(false);
          command.setSqlText(sql);
          command.execute();
          Query q = getDatabase().createQuery();
          try {
            q.setSqlText("select status from all_objects where owner = :owner and object_name = :name and object_type = :type");
            q.paramByName("owner").setString(s.owner);
            q.paramByName("name").setString(s.name);
            q.paramByName("type").setString(s.type);
            q.open();
            invalid = !q.eof() && !"VALID".equals(q.fieldByName("status").getString());
          } finally {
            q.close();
          }
          message(sql, false);
        } catch (Exception ex) {
          error = ex.getMessage();
        }
        final String err = error;
        final boolean inv = invalid;
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            sources.remove(s.key());
            if (shown == s) {
              shown = null;
              showSource(s.owner, s.name, s.namespace, syntaxSource.getEditorArea().getLineOfOffset(syntaxSource.getEditorArea().getCaretPosition()) + 1);
            }
            updateState();
            if (err != null) {
              MessageBox.show(DebuggerPanelView.this, stringManager.getString("error"), err, ModalResult.OK, MessageBox.ERROR);
            } else if (inv) {
              MessageBox.show(DebuggerPanelView.this, stringManager.getString("debugger-compile"), stringManager.getString("debugger-compile-invalid"), ModalResult.OK, MessageBox.WARNING);
            }
          }
        });
      }
    }, "orbada-debugger-compile").start();
  }

  /** Kod do wywolania w aplikacji (np. Delphi przez OCI) i uprawnienia */
  static String appCode(StringManager sm, String debuggerUser) {
    return
      "-- " + sm.getString("debugger-app-code-on") + "\n" +
      "declare\n" +
      "  t binary_integer;\n" +
      "begin\n" +
      "  :debug_id := dbms_debug.initialize(diagnostics => 0);\n" +
      "  -- " + sm.getString("debugger-app-code-timeout") + "\n" +
      "  dbms_debug.set_timeout_behaviour(dbms_debug.nodebug_on_timeout);\n" +
      "  t := dbms_debug.set_timeout(30);\n" +
      "  dbms_debug.debug_on;\n" +
      "end;\n" +
      "\n" +
      "-- " + sm.getString("debugger-app-code-off") + "\n" +
      "begin\n" +
      "  dbms_debug.debug_off;\n" +
      "end;\n" +
      "\n" +
      "-- " + sm.getString("debugger-app-code-grants") + "\n" +
      "grant debug connect session to <APP_USER>;\n" +
      "grant debug connect session, debug any procedure to " + (debuggerUser != null ? debuggerUser.toUpperCase(java.util.Locale.ROOT) : "<ORBADA_USER>") + ";\n" +
      "-- " + sm.getString("debugger-app-code-grants-object") + "\n" +
      "-- grant debug on <OWNER>.<PACKAGE> to " + (debuggerUser != null ? debuggerUser.toUpperCase(java.util.Locale.ROOT) : "<ORBADA_USER>") + ";\n";
  }

  private void showAppCode() {
    JTextArea text = new JTextArea(appCode(stringManager, getDatabase().getUserName()));
    text.setEditable(false);
    text.setFont(new Font(Font.MONOSPACED, Font.PLAIN, text.getFont().getSize()));
    text.setCaretPosition(0);
    JScrollPane scroll = new JScrollPane(text);
    scroll.setPreferredSize(new Dimension(640, 420));
    JPanel panel = new JPanel(new BorderLayout(0, 6));
    JLabel info = new JLabel("<html>" + stringManager.getString("debugger-app-code-info") + "</html>");
    info.setPreferredSize(new Dimension(640, 70));
    panel.add(info, BorderLayout.NORTH);
    panel.add(scroll, BorderLayout.CENTER);
    JOptionPane.showMessageDialog(this, panel, stringManager.getString("debugger-app-code"), JOptionPane.PLAIN_MESSAGE);
  }
}
