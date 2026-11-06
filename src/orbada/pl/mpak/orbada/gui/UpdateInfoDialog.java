/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/*
 * UpdateInfoDialog.java
 *
 * Created on 2011-02-03, 15:10:25
 */

package pl.mpak.orbada.gui;

import java.awt.Desktop;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.event.WindowEvent;
import javax.swing.JOptionPane;
import pl.mpak.orbada.core.AutoUpdate;
import pl.mpak.sky.gui.mr.ModalResult;
import pl.mpak.sky.gui.swing.MessageBox;
import java.net.URL;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.event.HyperlinkEvent;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import pl.mpak.orbada.Consts;
import pl.mpak.orbada.core.Application;
import pl.mpak.sky.gui.swing.SwingUtil;
import pl.mpak.util.ExceptionUtil;
import pl.mpak.util.StringManager;
import pl.mpak.util.StringManagerFactory;
import pl.mpak.util.StringUtil;
import pl.mpak.util.id.VersionID;

/**
 *
 * @author akaluza
 */
public class UpdateInfoDialog extends javax.swing.JDialog {

  private final static StringManager stringManager = StringManagerFactory.getStringManager(Consts.class);

  private JSONObject updateInfo;

  /** Creates new form UpdateInfoDialog */
  public UpdateInfoDialog(JSONObject updateInfo) {
    super(SwingUtil.getRootFrame());
    this.updateInfo = updateInfo;
    initComponents();
    init();
  }

  public static void showDialog() {
    final JSONObject updateInfo = Application.get().getMainFrame().getUpdateInfo();
    if (updateInfo != null) {
      java.awt.EventQueue.invokeLater(new Runnable() {
        @Override
        public void run() {
          UpdateInfoDialog dialog = new UpdateInfoDialog(updateInfo);
          dialog.setVisible(true);
        }
      });
    }
  }

  private void init() {
//    try {
//      URL url = new URL("http://sflogo.sourceforge.net/sflogo.php?group_id=361699&amp;type=10");
//      labelHosted.setIcon(new ImageIcon(url));
//    } catch (MalformedURLException ex) {
//    }
    textInfo.setFont(new Font(Font.MONOSPACED, Font.PLAIN, textInfo.getFont().getSize()));
    produceHtml();

    getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(cmClose.getShortCut(), "cmCancel");
    getRootPane().getActionMap().put("cmCancel", cmClose);
    SwingUtil.setButtonSizesTheSame(new AbstractButton[] {buttonDownload, buttonCancel});
    SwingUtil.centerWithinScreen(this);
  }

  /** Pobranie i rozpakowanie w tle (postep pod lista zmian), potem pytanie o instalacje */
  private void install(final JSONObject lastVersion) {
    cmDownload.setEnabled(false);
    labelDownload.setText(stringManager.getString("update-downloading"));
    Thread t = new Thread(new Runnable() {
      private long shownMb = -1;
      @Override
      public void run() {
        try {
          AutoUpdate.prepare(lastVersion, new AutoUpdate.Progress() {
            @Override
            public void progress(final long done, final long total) {
              final long mb = done / 1048576;
              if (mb != shownMb) {
                shownMb = mb;
                EventQueue.invokeLater(new Runnable() {
                  @Override
                  public void run() {
                    labelDownload.setText(String.format(stringManager.getString("update-progress"), mb,
                      total > 0 ? String.valueOf(total / 1048576) : "?"));
                  }
                });
              }
            }
          });
          EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
              labelDownload.setText(" ");
              dispose();
              askInstall(lastVersion.optString("version"));
            }
          });
        } catch (final Exception ex) {
          EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
              labelDownload.setText(" ");
              cmDownload.setEnabled(true);
              MessageBox.show(UpdateInfoDialog.this, getTitle(), ex.getMessage(), ModalResult.OK, MessageBox.ERROR);
            }
          });
        }
      }
    }, "orbada-update-download");
    t.setDaemon(true);
    t.start();
  }

  /**
   * Aktualizacja pobrana: TAK - Orbada zamyka sie, pliki sa podmieniane i Orbada startuje ponownie;
   * NIE - podmiana po zamknieciu Orbady (bez ponownego uruchomienia).
   */
  static void askInstall(String version) {
    int r = MessageBox.show(SwingUtil.getRootFrame(), stringManager.getString("cmDownload-text"),
      String.format(stringManager.getString("update-ready-q"), version), ModalResult.YESNO, MessageBox.QUESTION);
    boolean now = r == ModalResult.YES;
    try {
      AutoUpdate.launchUpdater(now);
    } catch (Exception ex) {
      MessageBox.show(SwingUtil.getRootFrame(), stringManager.getString("cmDownload-text"), ex.getMessage(), ModalResult.OK, MessageBox.ERROR);
      return;
    }
    if (now) {
      java.awt.Frame frame = Application.get().getMainFrame();
      frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING));
    }
  }

  /** Przy starcie: aktualizacja pobrana wczesniej, a nie zainstalowana - pytanie o instalacje */
  public static void offerPending() {
    String version = AutoUpdate.pendingVersion();
    if (version != null) {
      askInstall(version);
    }
  }

  private void produceHtml() {
    try {
      JSONObject lastVersion = updateInfo.getJSONObject("lastVersion");
      if (lastVersion != null) {
        VersionID ver = new VersionID(lastVersion.getString("version"));
        cmDownload.setEnabled(ver.getBuild() > Consts.orbadaVersion.getBuild());
      }
      StringBuffer sb = new StringBuffer();
      sb.append("<html>");
      sb.append("<head>");
      sb.append("<style>");
      sb.append("  body { font-family: Tahoma, Arial, serif; font-size: 10px; padding: 5px; }");
      sb.append("</style>");
      sb.append("</head>");
      sb.append("<body>");

      JSONArray versions = updateInfo.getJSONArray("versions");
      for (int v=0; v<versions.length(); v++) {
        if (v > 0) {
          sb.append("<hr>");
        }
        JSONObject ver = versions.getJSONObject(v);
        sb.append("<h2>").
          append(stringManager.getString("version-dd")).append(" ").append(ver.getString("version")).
          append(", ").append(stringManager.getString("released-dd")).append(" ").append(ver.getString("released")).
          append("</h2>");
        JSONArray details = ver.getJSONArray("details");
        String type = null;
        for (int d=0; d<details.length(); d++) {
          JSONObject det = details.getJSONObject(d);
          if (!StringUtil.equals(type, det.getString("type"))) {
            type = det.getString("type");
            if (d > 0) {
              sb.append("</table>");
            }
            sb.append("<p>").append(type).append("</p>");
            sb.append("<table>");
          }

          sb.append("<tr>");
          String title = det.getString("title");
          String request = "&nbsp;";
          if (title.startsWith("#") && title.indexOf('-') > 0) {
            request = title.substring(0, title.indexOf('-') -1).trim();
            title = title.substring(title.indexOf('-') +1);
          }
          sb.append("<td>").append(request).append("</td>");
          sb.append("<td>").append(title).append("</td>");
          sb.append("</tr>");
        }
        if (type != null) {
          sb.append("</table>");
        }
      }

      sb.append("</body>");
      sb.append("</html>");
      textInfo.setText(sb.toString());
    } catch (JSONException ex) {
      textInfo.setText(ex.getMessage());
    }
  }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
  // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
  private void initComponents() {

    cmClose = new pl.mpak.sky.gui.swing.Action();
    cmDownload = new pl.mpak.sky.gui.swing.Action();
    buttonCancel = new javax.swing.JButton();
    buttonDownload = new javax.swing.JButton();
    labelDownload = new javax.swing.JLabel();
    jScrollPane1 = new javax.swing.JScrollPane();
    textInfo = new pl.mpak.sky.gui.swing.comp.HtmlEditorPane();

    cmClose.setActionCommandKey("cmCancel");
    cmClose.setShortCut(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0));
    cmClose.setText(stringManager.getString("cmClose-text")); // NOI18N
    cmClose.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        cmCloseActionPerformed(evt);
      }
    });

    cmDownload.setText(stringManager.getString("cmDownload-text")); // NOI18N
    cmDownload.setTooltip(stringManager.getString("cmDownload-hint")); // NOI18N
    cmDownload.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        cmDownloadActionPerformed(evt);
      }
    });

    setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
    setTitle(stringManager.getString("UpdateInfoDialog-title")); // NOI18N
    setModal(true);

    buttonCancel.setAction(cmClose);
    buttonCancel.setMargin(new java.awt.Insets(2, 2, 2, 2));
    buttonCancel.setPreferredSize(new java.awt.Dimension(85, 25));

    buttonDownload.setAction(cmDownload);
    buttonDownload.setMargin(new java.awt.Insets(2, 2, 2, 2));
    buttonDownload.setPreferredSize(new java.awt.Dimension(85, 25));

    labelDownload.setText(" ");

    textInfo.addHyperlinkListener(new javax.swing.event.HyperlinkListener() {
      public void hyperlinkUpdate(javax.swing.event.HyperlinkEvent evt) {
        textInfoHyperlinkUpdate(evt);
      }
    });
    jScrollPane1.setViewportView(textInfo);

    javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
    getContentPane().setLayout(layout);
    layout.setHorizontalGroup(
      layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(layout.createSequentialGroup()
        .addContainerGap()
        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
          .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 601, Short.MAX_VALUE)
          .addGroup(layout.createSequentialGroup()
            .addComponent(buttonDownload, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(labelDownload)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 424, Short.MAX_VALUE)
            .addComponent(buttonCancel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
        .addContainerGap())
    );
    layout.setVerticalGroup(
      layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
        .addContainerGap()
        .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 399, Short.MAX_VALUE)
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
          .addComponent(buttonCancel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
          .addComponent(buttonDownload, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
          .addComponent(labelDownload))
        .addContainerGap())
    );

    pack();
  }// </editor-fold>//GEN-END:initComponents

    private void cmCloseActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cmCloseActionPerformed
      dispose();
}//GEN-LAST:event_cmCloseActionPerformed

    private void cmDownloadActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cmDownloadActionPerformed
      try {
        if (updateInfo.has("lastVersion")) {
          JSONObject lastVersion = updateInfo.getJSONObject("lastVersion");
          if (AutoUpdate.isAvailable(lastVersion)) {
            // ORBADA 1.3.2 (F7): instalacja z GitHub Release albo, jak dawniej, strona pobierania
            Object[] options = {
              stringManager.getString("update-install-auto"),
              stringManager.getString("update-open-page"),
              stringManager.getString("update-cancel")};
            int choice = JOptionPane.showOptionDialog(this,
              String.format(stringManager.getString("update-install-q"), lastVersion.optString("version")),
              getTitle(), JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
            if (choice == 0) {
              install(lastVersion);
              return;
            }
            if (choice != 1) {
              return;
            }
          }
          if (lastVersion.has("downloadPage")) {
            try {
              Desktop.getDesktop().browse(new URL((String)lastVersion.get("downloadPage")).toURI());
            } catch (Exception ex) {
              ExceptionUtil.processException(ex);
            }
          }
        }
      } catch (JSONException ex) {
        ExceptionUtil.processException(ex);
      }

    }//GEN-LAST:event_cmDownloadActionPerformed

    private void textInfoHyperlinkUpdate(javax.swing.event.HyperlinkEvent evt) {//GEN-FIRST:event_textInfoHyperlinkUpdate
      if (evt.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
        try {
          Desktop.getDesktop().browse(evt.getURL().toURI());
        }
        catch (Exception ex) {
          ExceptionUtil.processException(ex);
        }
      }
    }//GEN-LAST:event_textInfoHyperlinkUpdate

  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JButton buttonCancel;
  private javax.swing.JButton buttonDownload;
  private pl.mpak.sky.gui.swing.Action cmClose;
  private pl.mpak.sky.gui.swing.Action cmDownload;
  private javax.swing.JScrollPane jScrollPane1;
  private javax.swing.JLabel labelDownload;
  private pl.mpak.sky.gui.swing.comp.HtmlEditorPane textInfo;
  // End of variables declaration//GEN-END:variables

}
