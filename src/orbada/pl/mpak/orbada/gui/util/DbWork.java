package pl.mpak.orbada.gui.util;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import pl.mpak.orbada.Consts;
import pl.mpak.util.ExceptionUtil;
import pl.mpak.util.StringManager;
import pl.mpak.util.StringManagerFactory;

/**
 * ORBADA 1.3.2: operacja na bazie (np. kompilacja pakietu) wykonywana poza watkiem GUI.
 * <p>Wolane z watku GUI czeka najwyzej {@link #SHOW_DELAY_MS} ms; gdy operacja trwa dluzej, pokazuje modalne okno
 * "Wykonywanie..." z przyciskiem "Przerwij" (np. Statement.cancel()) i dalej obsluguje zdarzenia - okno programu
 * sie odrysowuje, zamiast zawiesic sie do konca operacji (kompilacja pakietu uzywanego przez inna sesje potrafi
 * czekac minutami). Wynik albo wyjatek operacji wraca do wywolujacego jak przy wywolaniu bezposrednim.
 * Poza watkiem GUI operacja wykonuje sie wprost.
 */
public final class DbWork {

  private final static StringManager stringManager = StringManagerFactory.getStringManager(Consts.class);

  public final static long SHOW_DELAY_MS = 400;

  public interface Work<T> {
    T run() throws Exception;
  }

  private DbWork() {
  }

  /**
   * @param owner komponent, nad ktorym pokazac okno (moze byc null)
   * @param message tekst okna, np. "Kompilowanie..."; null - tekst domyslny
   * @param work operacja
   * @param cancel przerwanie operacji (wolane poza watkiem GUI), null - bez przycisku Przerwij
   */
  public static <T> T run(Component owner, String message, final Work<T> work, final Runnable cancel) throws Exception {
    if (!SwingUtilities.isEventDispatchThread()) {
      return work.run();
    }
    final Object[] result = {null};
    final Throwable[] error = {null};
    final JDialog dialog = createDialog(owner, message, cancel);
    Thread thread = new Thread("orbada-db-work") {
      @Override
      public void run() {
        try {
          result[0] = work.run();
        } catch (Throwable ex) {
          error[0] = ex;
        } finally {
          // zamkniecie okna przez kolejke zdarzen - takze gdy jeszcze nie zostalo pokazane (modalna petla je obsluzy)
          SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
              dialog.dispose();
            }
          });
        }
      }
    };
    thread.setDaemon(true);
    thread.start();
    thread.join(SHOW_DELAY_MS);
    if (thread.isAlive()) {
      dialog.setVisible(true);
    }
    // setVisible wraca po dispose(); join gwarantuje widocznosc wyniku (happens-before)
    thread.join();
    if (error[0] instanceof Exception) {
      throw (Exception)error[0];
    }
    if (error[0] instanceof Error) {
      throw (Error)error[0];
    }
    @SuppressWarnings("unchecked")
    T value = (T)result[0];
    return value;
  }

  /**
   * Odswiezenie informacji o obiektach bazy (np. listy tabel do pola wyboru w kreatorze) poza watkiem GUI -
   * jak DbObjectIdentified.saveRefresh(): bledy tylko do dziennika.
   */
  public static void refresh(final pl.mpak.orbada.plugins.dbinfo.DbObjectIdentified object) {
    try {
      run(null, stringManager.getString("DbWork-loading"), new Work<Object>() {
        public Object run() throws Exception {
          object.refresh();
          return null;
        }
      }, null);
    } catch (Exception ex) {
      ExceptionUtil.processException(ex);
    }
  }

  private static JDialog createDialog(Component owner, String message, final Runnable cancel) {
    Window window = owner == null ? null : (owner instanceof Window ? (Window)owner : SwingUtilities.getWindowAncestor(owner));
    final JDialog dialog = new JDialog(window, stringManager.getString("DbWork-title"), Dialog.ModalityType.APPLICATION_MODAL);
    dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
    JPanel panel = new JPanel(new BorderLayout(8, 8));
    panel.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
    panel.add(new JLabel(message != null ? message : stringManager.getString("DbWork-executing")), BorderLayout.CENTER);
    if (cancel != null) {
      final JButton button = new JButton(stringManager.getString("DbWork-cancel"));
      button.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          button.setEnabled(false);
          button.setText(stringManager.getString("DbWork-cancelling"));
          // przerwanie poza watkiem GUI - sterownik moze chwile czekac na serwer
          Thread t = new Thread("orbada-db-work-cancel") {
            @Override
            public void run() {
              try {
                cancel.run();
              } catch (Throwable ex) {
                ExceptionUtil.processException(ex);
              }
            }
          };
          t.setDaemon(true);
          t.start();
        }
      });
      JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
      buttons.add(button);
      panel.add(buttons, BorderLayout.SOUTH);
    }
    dialog.setContentPane(panel);
    dialog.pack();
    dialog.setMinimumSize(new java.awt.Dimension(300, dialog.getHeight()));
    dialog.setLocationRelativeTo(window);
    return dialog;
  }

}
