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
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;

/**
 * ORBADA Fuerteventura (F5): tabela edytowalna dla kreatorow schedulera (argumenty, kroki i reguly chaina)
 * z przyciskami Dodaj / Usun / W gore / W dol.
 */
class SchedulerTablePanel extends JPanel {

  private final boolean[] editable;
  private final DefaultTableModel model;
  final JTable table;
  private final JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));

  /**
   * @param titleKeys klucze naglowkow kolumn
   * @param editable ktore kolumny mozna edytowac
   * @param withButtons true - przyciski dodawania, usuwania i przesuwania wierszy
   */
  SchedulerTablePanel(String[] titleKeys, boolean[] editable, boolean withButtons) {
    super(new BorderLayout(0, 4));
    if (editable.length != titleKeys.length) {
      throw new IllegalArgumentException("editable: " + editable.length + ", kolumn: " + titleKeys.length);
    }
    this.editable = editable;
    String[] titles = new String[titleKeys.length];
    for (int i = 0; i < titleKeys.length; i++) {
      titles[i] = SchedulerWizard.text(titleKeys[i]);
    }
    model = new DefaultTableModel(titles, 0) {
      @Override
      public boolean isCellEditable(int row, int column) {
        return SchedulerTablePanel.this.editable[column];
      }
    };
    table = new JTable(model);
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
    table.setRowHeight(Math.max(table.getRowHeight(), 20));
    add(new JScrollPane(table), BorderLayout.CENTER);
    if (withButtons) {
      addButton("SchedWiz-row-add", () -> {
        Object[] row = new Object[titles.length];
        for (int i = 0; i < row.length; i++) {
          row[i] = "";
        }
        addRow(row);
        int r = model.getRowCount() - 1;
        table.setRowSelectionInterval(r, r);
        table.editCellAt(r, firstEditable());
      });
      addButton("SchedWiz-row-remove", () -> {
        int r = table.getSelectedRow();
        if (r >= 0) {
          stopEditing();
          model.removeRow(r);
          if (model.getRowCount() > 0) {
            r = Math.min(r, model.getRowCount() - 1);
            table.setRowSelectionInterval(r, r);
          }
        }
      });
      addButton("SchedWiz-row-up", () -> move(-1));
      addButton("SchedWiz-row-down", () -> move(1));
    }
  }

  /** Dodatkowy przycisk pod tabela */
  JButton addButton(String key, Runnable action) {
    JButton b = new JButton(new AbstractAction(SchedulerWizard.text(key)) {
      public void actionPerformed(ActionEvent e) {
        action.run();
      }
    });
    buttons.add(b);
    if (buttons.getParent() == null) {
      add(buttons, BorderLayout.SOUTH);
    }
    return b;
  }

  private int firstEditable() {
    for (int i = 0; i < editable.length; i++) {
      if (editable[i]) {
        return i;
      }
    }
    return 0;
  }

  private void move(int delta) {
    int r = table.getSelectedRow();
    int to = r + delta;
    if (r < 0 || to < 0 || to >= model.getRowCount()) {
      return;
    }
    stopEditing();
    model.moveRow(r, r, to);
    table.setRowSelectionInterval(to, to);
  }

  void stopEditing() {
    if (table.isEditing()) {
      table.getCellEditor().stopCellEditing();
    }
  }

  void addRow(Object[] row) {
    model.addRow(row);
  }

  void clear() {
    stopEditing();
    model.setRowCount(0);
  }

  int getRowCount() {
    return model.getRowCount();
  }

  /** Wartosc komorki jako tekst bez spacji na brzegach ("" dla pustej) */
  String get(int row, int column) {
    Object o = model.getValueAt(row, column);
    return o == null ? "" : o.toString().trim();
  }

  List<String[]> getRows() {
    stopEditing();
    List<String[]> rows = new ArrayList<String[]>();
    for (int r = 0; r < model.getRowCount(); r++) {
      String[] row = new String[model.getColumnCount()];
      for (int c = 0; c < row.length; c++) {
        row[c] = get(r, c);
      }
      rows.add(row);
    }
    return rows;
  }

}
