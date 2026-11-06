package pl.mpak.orbada.oracle.gui.scheduler;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import pl.mpak.orbada.gui.comps.OrbadaSyntaxTextArea;
import pl.mpak.orbada.oracle.gui.scheduler.SchedulerWizard.Page;
import pl.mpak.sky.gui.mr.ModalResult;
import pl.mpak.sky.gui.swing.MessageBox;

/**
 * ORBADA Fuerteventura (F5): okno kreatora schedulera - lista krokow z lewej, strona z prawej,
 * Wstecz / Dalej, na koncu kod PL/SQL (mozna go poprawic) i "Utworz".
 */
class SchedulerWizardDialog extends JDialog {

  private final SchedulerWizard wizard;
  private final List<Page> pages = new ArrayList<Page>();
  private final CardLayout cards = new CardLayout();
  private final JPanel center = new JPanel(cards);
  private final JList<String> steps;
  private final JLabel title = new JLabel();
  private final JLabel error = new JLabel(" ");
  private final JButton buttonBack;
  private final JButton buttonNext;
  private final OrbadaSyntaxTextArea code = new OrbadaSyntaxTextArea();
  private int current = -1;
  private boolean created = false;
  private boolean selecting = false;

  SchedulerWizardDialog(Component parent, SchedulerWizard wizard) {
    super(owner(parent), ModalityType.APPLICATION_MODAL);
    this.wizard = wizard;
    setTitle(wizard.getTitle());
    setDefaultCloseOperation(DISPOSE_ON_CLOSE);

    pages.addAll(wizard.getPages());
    pages.add(createCodePage());
    String[] names = new String[pages.size()];
    for (int i = 0; i < pages.size(); i++) {
      names[i] = (i + 1) + ". " + SchedulerWizard.text(pages.get(i).titleKey);
      center.add(pages.get(i).component, String.valueOf(i));
    }

    steps = new JList<String>(names);
    steps.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    steps.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
    steps.setCellRenderer(new DefaultListCellRenderer() {
      @Override
      public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        super.getListCellRendererComponent(list, value, index, isSelected, false);
        setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        return this;
      }
    });
    steps.addListSelectionListener(e -> {
      if (!e.getValueIsAdjusting() && !selecting && steps.getSelectedIndex() >= 0) {
        go(steps.getSelectedIndex());
      }
    });
    JScrollPane stepsScroll = new JScrollPane(steps);
    stepsScroll.setPreferredSize(new Dimension(190, 100));

    title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize2D() + 3f));
    title.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));
    error.setForeground(new Color(0xB00020));
    error.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));

    buttonBack = new JButton(new AbstractAction(SchedulerWizard.text("SchedWiz-back")) {
      public void actionPerformed(ActionEvent e) {
        go(current - 1);
      }
    });
    buttonNext = new JButton(new AbstractAction(SchedulerWizard.text("SchedWiz-next")) {
      public void actionPerformed(ActionEvent e) {
        if (current == pages.size() - 1) {
          execute();
        } else {
          go(current + 1);
        }
      }
    });
    JButton buttonCancel = new JButton(new AbstractAction(SchedulerWizard.text("SchedWiz-cancel")) {
      public void actionPerformed(ActionEvent e) {
        dispose();
      }
    });
    getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel");
    getRootPane().getActionMap().put("cancel", buttonCancel.getAction());

    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    buttons.add(buttonBack);
    buttons.add(buttonNext);
    buttons.add(buttonCancel);
    JPanel south = new JPanel(new BorderLayout());
    south.add(error, BorderLayout.CENTER);
    south.add(buttons, BorderLayout.EAST);
    south.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY));

    JPanel right = new JPanel(new BorderLayout());
    right.add(title, BorderLayout.NORTH);
    right.add(center, BorderLayout.CENTER);

    getContentPane().setLayout(new BorderLayout());
    getContentPane().add(stepsScroll, BorderLayout.WEST);
    getContentPane().add(right, BorderLayout.CENTER);
    getContentPane().add(south, BorderLayout.SOUTH);
    setSize(960, 620);
    setLocationRelativeTo(owner(parent));
    go(0);
  }

  /** Okno nadrzedne: sam parent, gdy to okno (np. glowne okno z menu Kreatory), inaczej okno zawierajace parent */
  private static Window owner(Component parent) {
    if (parent == null) {
      return null;
    }
    return parent instanceof Window ? (Window)parent : SwingUtilities.getWindowAncestor(parent);
  }

  @Override
  public void dispose() {
    wizard.dispose();
    super.dispose();
  }

  boolean isCreated() {
    return created;
  }

  private Page createCodePage() {
    code.setDatabase(wizard.getDatabase());
    JButton copy = new JButton(new AbstractAction(SchedulerWizard.text("SchedWiz-copy")) {
      public void actionPerformed(ActionEvent e) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(code.getText()), null);
      }
    });
    JPanel panel = new SchedulerWizard.Form()
      .hint(wizard.isEdit() ? "SchedWiz-code-hint-edit" : "SchedWiz-code-hint")
      .full(code, true);
    JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    bar.add(copy);
    ((SchedulerWizard.Form)panel).full(bar, false);
    return new Page("SchedWiz-page-code", panel) {
      @Override
      void shown() {
        code.setText(wizard.buildPlsql());
      }
    };
  }

  /** Przejscie na strone: do przodu tylko przez strony bez bledow */
  private void go(int index) {
    if (index < 0 || index >= pages.size()) {
      return;
    }
    if (current >= 0 && index > current) {
      for (int i = current; i < index; i++) {
        String msg = pages.get(i).validate();
        if (msg != null) {
          show(i);
          error.setText(msg);
          return;
        }
      }
    }
    show(index);
  }

  private void show(int index) {
    current = index;
    error.setText(" ");
    selecting = true;
    try {
      steps.setSelectedIndex(index);
    } finally {
      selecting = false;
    }
    Page p = pages.get(index);
    title.setText(SchedulerWizard.text(p.titleKey));
    p.shown();
    cards.show(center, String.valueOf(index));
    buttonBack.setEnabled(index > 0);
    buttonNext.setText(SchedulerWizard.text(index < pages.size() - 1 ? "SchedWiz-next" : wizard.isEdit() ? "SchedWiz-save" : "SchedWiz-create"));
  }

  /** Wykonuje kod ze strony PL/SQL zwyklym JDBC - bez parsowania parametrow ":nazwa" (moga byc w akcji joba) */
  private void execute() {
    String sql = code.getText().trim();
    if (sql.endsWith("/")) {
      sql = sql.substring(0, sql.length() - 1).trim();
    }
    try (Statement st = wizard.getDatabase().getConnection().createStatement()) {
      st.execute(sql);
      created = true;
      dispose();
    } catch (SQLException ex) {
      MessageBox.show((Window)this, getTitle(), ex.getMessage(), ModalResult.OK, MessageBox.ERROR);
    }
  }

}
