package pl.mpak.orbada.gui.schemas;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import pl.mpak.orbada.Consts;
import pl.mpak.orbada.util.TnsNames;
import pl.mpak.util.StringManager;
import pl.mpak.util.StringManagerFactory;

/**
 * ORBADA Fuerteventura (F1): wybor aliasu z tnsnames.ora dla polaczenia Oracle.
 */
public class TnsAliasDialog extends JDialog {

  private final static StringManager stringManager = StringManagerFactory.getStringManager(Consts.class);

  /** Wynik: wybrany alias i katalog z tnsnames.ora */
  public static class Result {
    public final String alias;
    public final File tnsAdmin;
    Result(String alias, File tnsAdmin) {
      this.alias = alias;
      this.tnsAdmin = tnsAdmin;
    }
  }

  private final static Pattern hostPattern = Pattern.compile("(?i)\\(\\s*HOST\\s*=\\s*([^)\\s]+)");
  private final static Pattern portPattern = Pattern.compile("(?i)\\(\\s*PORT\\s*=\\s*([^)\\s]+)");
  private final static Pattern servicePattern = Pattern.compile("(?i)\\(\\s*(SERVICE_NAME|SID)\\s*=\\s*([^)\\s]+)");

  private final JComboBox<String> comboDir = new JComboBox<String>();
  private final JTextField textFilter = new JTextField();
  private final AliasModel model = new AliasModel();
  private final JTable table = new JTable(model);
  private final JTextArea textDescription = new JTextArea(4, 60);
  private final JLabel labelStatus = new JLabel(" ");
  private Result result;

  private TnsAliasDialog(Window owner, Properties connectionProperties, String currentAlias) {
    super(owner, stringManager.getString("TnsAliasDialog-title"), ModalityType.APPLICATION_MODAL);
    buildUi();
    comboDir.setEditable(true);
    for (File dir : TnsNames.candidateDirs(connectionProperties)) {
      comboDir.addItem(dir.getAbsolutePath());
    }
    loadAliases();
    selectAlias(currentAlias);
    pack();
    setLocationRelativeTo(owner);
  }

  /** Pokazuje okno; zwraca wybrany alias albo null po anulowaniu */
  public static Result show(Window owner, Properties connectionProperties, String currentAlias) {
    TnsAliasDialog dialog = new TnsAliasDialog(owner, connectionProperties, currentAlias);
    dialog.setVisible(true);
    return dialog.result;
  }

  private void buildUi() {
    JPanel top = new JPanel(new GridBagLayout());
    GridBagConstraints c = new GridBagConstraints();
    c.insets = new Insets(4, 4, 4, 4);
    c.anchor = GridBagConstraints.WEST;
    top.add(new JLabel(stringManager.getString("TnsAliasDialog-directory")), c);
    c.gridx = 1;
    c.weightx = 1;
    c.fill = GridBagConstraints.HORIZONTAL;
    top.add(comboDir, c);
    c.gridx = 2;
    c.weightx = 0;
    c.fill = GridBagConstraints.NONE;
    JButton buttonBrowse = new JButton(new AbstractAction("...") {
      public void actionPerformed(ActionEvent e) {
        browse();
      }
    });
    buttonBrowse.setToolTipText(stringManager.getString("TnsAliasDialog-browse-hint"));
    top.add(buttonBrowse, c);
    c.gridx = 0;
    c.gridy = 1;
    top.add(new JLabel(stringManager.getString("TnsAliasDialog-filter")), c);
    c.gridx = 1;
    c.gridwidth = 2;
    c.weightx = 1;
    c.fill = GridBagConstraints.HORIZONTAL;
    top.add(textFilter, c);

    comboDir.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if ("comboBoxChanged".equals(e.getActionCommand()) || "comboBoxEdited".equals(e.getActionCommand())) {
          loadAliases();
        }
      }
    });
    textFilter.getDocument().addDocumentListener(new DocumentListener() {
      public void insertUpdate(DocumentEvent e) { model.setFilter(textFilter.getText()); }
      public void removeUpdate(DocumentEvent e) { model.setFilter(textFilter.getText()); }
      public void changedUpdate(DocumentEvent e) { model.setFilter(textFilter.getText()); }
    });

    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    table.setAutoCreateRowSorter(true);
    table.getColumnModel().getColumn(0).setPreferredWidth(180);
    table.getColumnModel().getColumn(1).setPreferredWidth(200);
    table.getColumnModel().getColumn(2).setPreferredWidth(60);
    table.getColumnModel().getColumn(3).setPreferredWidth(160);
    table.getSelectionModel().addListSelectionListener(e -> {
      Row row = selectedRow();
      textDescription.setText(row == null ? "" : row.description);
      textDescription.setCaretPosition(0);
    });
    table.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2) {
          ok();
        }
      }
    });
    JScrollPane scrollTable = new JScrollPane(table);
    scrollTable.setPreferredSize(new Dimension(640, 280));
    textDescription.setEditable(false);
    textDescription.setLineWrap(true);
    textDescription.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 11));

    JPanel center = new JPanel(new BorderLayout(4, 4));
    center.add(scrollTable, BorderLayout.CENTER);
    center.add(new JScrollPane(textDescription), BorderLayout.SOUTH);

    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    JButton buttonOk = new JButton(new AbstractAction(stringManager.getString("TnsAliasDialog-ok")) {
      public void actionPerformed(ActionEvent e) {
        ok();
      }
    });
    JButton buttonCancel = new JButton(new AbstractAction(stringManager.getString("TnsAliasDialog-cancel")) {
      public void actionPerformed(ActionEvent e) {
        dispose();
      }
    });
    buttons.add(buttonOk);
    buttons.add(buttonCancel);
    JPanel bottom = new JPanel(new BorderLayout());
    bottom.add(labelStatus, BorderLayout.CENTER);
    bottom.add(buttons, BorderLayout.EAST);

    JPanel root = new JPanel(new BorderLayout(4, 4));
    root.setBorder(javax.swing.BorderFactory.createEmptyBorder(6, 6, 6, 6));
    root.add(top, BorderLayout.NORTH);
    root.add(center, BorderLayout.CENTER);
    root.add(bottom, BorderLayout.SOUTH);
    setContentPane(root);
    getRootPane().setDefaultButton(buttonOk);
    getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel");
    getRootPane().getActionMap().put("cancel", buttonCancel.getAction());
  }

  private File currentDir() {
    Object o = comboDir.getEditor().getItem();
    String s = o == null ? "" : o.toString().trim();
    return s.length() == 0 ? null : new File(s);
  }

  private void browse() {
    JFileChooser chooser = new JFileChooser(currentDir());
    chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
    chooser.setDialogTitle(stringManager.getString("TnsAliasDialog-browse-hint"));
    if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
      comboDir.setSelectedItem(chooser.getSelectedFile().getAbsolutePath());
    }
  }

  private void loadAliases() {
    List<Row> rows = new ArrayList<Row>();
    File dir = currentDir();
    if (dir == null) {
      labelStatus.setText(stringManager.getString("TnsAliasDialog-no-directory"));
    } else {
      File file = new File(dir, TnsNames.fileName);
      if (!file.isFile()) {
        labelStatus.setText(String.format(stringManager.getString("TnsAliasDialog-no-file"), file.getAbsolutePath()));
      } else {
        try {
          for (Map.Entry<String, String> e : TnsNames.parse(file).entrySet()) {
            rows.add(new Row(e.getKey(), e.getValue()));
          }
          labelStatus.setText(String.format(stringManager.getString("TnsAliasDialog-loaded"), rows.size(), file.getAbsolutePath()));
        } catch (Exception ex) {
          labelStatus.setText(ex.getMessage());
        }
      }
    }
    model.setRows(rows);
  }

  private void selectAlias(String alias) {
    if (alias == null) {
      return;
    }
    for (int i = 0; i < table.getRowCount(); i++) {
      if (alias.equalsIgnoreCase(model.visible.get(table.convertRowIndexToModel(i)).alias)) {
        table.setRowSelectionInterval(i, i);
        table.scrollRectToVisible(table.getCellRect(i, 0, true));
        return;
      }
    }
  }

  private Row selectedRow() {
    int view = table.getSelectedRow();
    return view < 0 ? null : model.visible.get(table.convertRowIndexToModel(view));
  }

  private void ok() {
    Row row = selectedRow();
    if (row == null) {
      return;
    }
    result = new Result(row.alias, currentDir());
    dispose();
  }

  private static String find(Pattern p, String s, int group) {
    Matcher m = p.matcher(s);
    return m.find() ? m.group(group) : "";
  }

  private static class Row {
    final String alias;
    final String description;
    final String host;
    final String port;
    final String service;
    Row(String alias, String description) {
      this.alias = alias;
      this.description = description;
      this.host = find(hostPattern, description, 1);
      this.port = find(portPattern, description, 1);
      this.service = find(servicePattern, description, 2);
    }
  }

  private static class AliasModel extends AbstractTableModel {
    private final String[] columns = {
      stringManager.getString("TnsAliasDialog-alias"), stringManager.getString("TnsAliasDialog-host"),
      stringManager.getString("TnsAliasDialog-port"), stringManager.getString("TnsAliasDialog-service")};
    List<Row> rows = new ArrayList<Row>();
    List<Row> visible = new ArrayList<Row>();
    private String filter = "";

    void setRows(List<Row> rows) {
      this.rows = rows;
      refilter();
    }

    void setFilter(String filter) {
      this.filter = filter == null ? "" : filter.trim().toUpperCase();
      refilter();
    }

    private void refilter() {
      visible = new ArrayList<Row>();
      for (Row r : rows) {
        if (filter.length() == 0 || r.alias.toUpperCase().contains(filter) || r.host.toUpperCase().contains(filter)
          || r.service.toUpperCase().contains(filter)) {
          visible.add(r);
        }
      }
      fireTableDataChanged();
    }

    public int getRowCount() {
      return visible.size();
    }

    public int getColumnCount() {
      return columns.length;
    }

    @Override
    public String getColumnName(int column) {
      return columns[column];
    }

    public Object getValueAt(int row, int column) {
      Row r = visible.get(row);
      switch (column) {
        case 0: return r.alias;
        case 1: return r.host;
        case 2: return r.port;
        default: return r.service;
      }
    }
  }

}
