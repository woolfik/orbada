package pl.mpak.orbada.oracle.datasync;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import pl.mpak.orbada.core.Application;
import pl.mpak.orbada.gui.comps.OrbadaSyntaxTextArea;
import pl.mpak.orbada.oracle.OrbadaOraclePlugin;
import pl.mpak.orbada.plugins.ISettings;
import pl.mpak.sky.gui.swing.SwingUtil;
import pl.mpak.sky.gui.swing.comp.TextField;
import pl.mpak.usedb.core.Database;
import pl.mpak.util.StringManager;
import pl.mpak.util.StringManagerFactory;

/**
 * ORBADA 1.3.8: okno "Wyrownaj dane" - tabela docelowa, klucz dopasowania, opcje i podglad skryptu PL/SQL
 * (DataSyncGenerator). Struktura wczytywana w tle (zapytanie przez dblink moze trwac), skrypt do nowej zawartosci
 * edytora SQL albo do schowka.
 */
public class DataSyncDialog extends JDialog {

  private final static StringManager stringManager = StringManagerFactory.getStringManager(OrbadaOraclePlugin.class);

  /** Tekst i18n; z argumentami przez MessageFormat */
  static String msg(String key, Object... args) {
    return args.length == 0 ? stringManager.getString(key) : stringManager.getString(key, args);
  }

  /** Odbiorca gotowego skryptu (nowa zawartosc edytora SQL) */
  public interface Target {
    void open(String script);
  }

  private final Database database;
  private final String query;
  private final Target target;
  private DataSyncGenerator.Model model;
  private boolean filling;
  private int loadNo;

  private final JLabel source = new JLabel();
  private final TextField table = new TextField();
  private final JButton load = new JButton(msg("datasync-load"));
  private final DefaultListModel<String> keyModel = new DefaultListModel<String>();
  private final JList<String> key = new JList<String>(keyModel);
  private final JCheckBox update = new JCheckBox(msg("datasync-update"), true);
  private final JCheckBox parents = new JCheckBox(msg("datasync-parents"), true);
  private final OrbadaSyntaxTextArea code = new OrbadaSyntaxTextArea();
  private final JLabel status = new JLabel(" ");
  private final JButton open = new JButton(msg("datasync-open"));
  private final JButton copy = new JButton(msg("datasync-copy"));
  private final JButton close = new JButton(msg("datasync-close"));
  private final ISettings settings;

  public DataSyncDialog(Database database, String query, Target target) {
    super(SwingUtil.getRootFrame(), msg("datasync-title"), true);
    this.database = database;
    this.query = DataSyncGenerator.cleanQuery(query);
    this.target = target;
    settings = Application.get().getSettings("oracle-data-sync-dialog");
    init();
    DataSyncGenerator.Source src = DataSyncGenerator.parseSource(this.query);
    if (src != null) {
      source.setText((src.owner == null ? "" : src.owner + ".") + src.table + (src.link == null ? "" : "@" + src.link));
      table.setText((src.owner == null ? "" : DataSyncGenerator.id(src.owner) + ".") + DataSyncGenerator.id(src.table));
    } else {
      source.setText(msg("datasync-no-source"));
    }
    load();
  }

  private void init() {
    JPanel form = new JPanel(new GridBagLayout());
    form.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));
    row(form, 0, "datasync-source", source);
    JPanel tablePanel = new JPanel(new BorderLayout(4, 0));
    tablePanel.add(table, BorderLayout.CENTER);
    tablePanel.add(load, BorderLayout.EAST);
    row(form, 1, "datasync-target", tablePanel);
    key.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    key.setVisibleRowCount(5);
    key.setToolTipText(msg("datasync-key-hint"));
    row(form, 2, "datasync-key", new JScrollPane(key));
    JPanel checks = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    checks.add(update);
    checks.add(parents);
    row(form, 3, null, checks);

    code.setDatabase(database);
    JPanel bottom = new JPanel(new BorderLayout());
    bottom.setBorder(BorderFactory.createEmptyBorder(4, 8, 8, 8));
    bottom.add(status, BorderLayout.CENTER);
    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
    buttons.add(open);
    buttons.add(copy);
    buttons.add(close);
    bottom.add(buttons, BorderLayout.EAST);

    JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, form, code);
    split.setResizeWeight(0);
    split.setBorder(null);
    getContentPane().setLayout(new BorderLayout());
    getContentPane().add(split, BorderLayout.CENTER);
    getContentPane().add(bottom, BorderLayout.SOUTH);

    ActionListener regen = new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        generate();
      }
    };
    update.addActionListener(regen);
    parents.addActionListener(regen);
    key.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        if (!e.getValueIsAdjusting() && !filling) {
          generate();
        }
      }
    });
    ActionListener reload = new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        load();
      }
    };
    load.addActionListener(reload);
    table.addActionListener(reload);
    open.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        target.open(code.getText());
        dispose();
      }
    });
    copy.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(code.getText()), null);
        status.setText(msg("datasync-copied"));
      }
    });
    close.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        dispose();
      }
    });
    getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close");
    getRootPane().getActionMap().put("close", new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        dispose();
      }
    });
    setSize(settings.getValue("width", 900L).intValue(), settings.getValue("height", 700L).intValue());
    setLocationRelativeTo(getOwner());
    enableScript(false);
  }

  private static void row(JPanel form, int y, String labelKey, JComponent field) {
    GridBagConstraints gc = new GridBagConstraints();
    gc.gridy = y;
    gc.insets = new Insets(2, 2, 2, 2);
    if (labelKey != null) {
      gc.gridx = 0;
      gc.anchor = GridBagConstraints.NORTHEAST;
      form.add(new JLabel(msg(labelKey) + ":"), gc);
    }
    gc.gridx = 1;
    gc.weightx = 1;
    gc.fill = GridBagConstraints.HORIZONTAL;
    gc.anchor = GridBagConstraints.WEST;
    form.add(field, gc);
  }

  private void enableScript(boolean enabled) {
    open.setEnabled(enabled);
    copy.setEnabled(enabled);
    key.setEnabled(enabled);
    update.setEnabled(enabled);
    parents.setEnabled(enabled);
  }

  /** Struktura tabel w tle; wynik starszego wczytania (zmieniona tabela) pomijany */
  private void load() {
    final int no = ++loadNo;
    final String name = table.getText();
    enableScript(false);
    load.setEnabled(false);
    status.setText(msg("datasync-loading"));
    Thread thread = new Thread(new Runnable() {
      public void run() {
        DataSyncGenerator.Model m = null;
        String error = null;
        try {
          m = DataSyncGenerator.load(database.getConnection(), query, name);
        } catch (Throwable ex) {
          error = ex.getMessage() == null ? ex.toString() : ex.getMessage();
        }
        final DataSyncGenerator.Model result = m;
        final String message = error;
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            if (no != loadNo || !isDisplayable()) {
              return;
            }
            load.setEnabled(true);
            model = result;
            if (result == null) {
              status.setText(message);
              code.setText("");
              return;
            }
            filling = true;
            try {
              keyModel.clear();
              List<String> avail = result.keyCols();
              for (String c : avail) {
                keyModel.addElement(c);
              }
              List<String> def = result.defaultKey();
              for (String c : def) {
                int i = avail.indexOf(c);
                key.addSelectionInterval(i, i);
              }
            } finally {
              filling = false;
            }
            enableScript(true);
            generate();
          }
        });
      }
    }, "orbada-data-sync");
    thread.setDaemon(true);
    thread.start();
  }

  private void generate() {
    if (model == null) {
      return;
    }
    DataSyncGenerator.Options o = new DataSyncGenerator.Options();
    o.key = new ArrayList<String>(key.getSelectedValuesList());
    o.update = update.isSelected();
    o.syncParents = parents.isSelected();
    code.setText(DataSyncGenerator.script(model, o));
    code.getEditorArea().setCaretPosition(0);
    status.setText(o.key.isEmpty() ? msg("datasync-nokey-warning") : msg("datasync-ready", model.tables.size()));
  }

  @Override
  public void dispose() {
    loadNo++;
    settings.setValue("width", (long)getWidth());
    settings.setValue("height", (long)getHeight());
    settings.store();
    super.dispose();
  }

}
