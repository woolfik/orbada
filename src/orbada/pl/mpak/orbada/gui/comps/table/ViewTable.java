/*
 * ViewTable.java
 *
 * Created on 2007-10-23, 20:51:54
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package pl.mpak.orbada.gui.comps.table;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.EventObject;
import java.util.StringTokenizer;
import javax.swing.table.TableColumn;
import pl.mpak.orbada.Consts;
import pl.mpak.orbada.core.Application;
import pl.mpak.orbada.plugins.ISettings;
import pl.mpak.orbada.util.Utils;
import pl.mpak.usedb.core.DefaultQueryListener;
import pl.mpak.usedb.core.Query;
import pl.mpak.usedb.core.QueryListener;
import pl.mpak.usedb.gui.swing.QueryTable;
import pl.mpak.usedb.gui.swing.QueryTableColumn;
import pl.mpak.usedb.gui.swing.QueryTableColumnModel;

/**
 *
 * @author akaluza
 */
public class ViewTable extends QueryTable {
  
  private final static pl.mpak.util.StringManager stringManager = pl.mpak.util.StringManagerFactory.getStringManager(Consts.class);

  private QueryListener queryListener;
  private ArrayList<OryginalColumnProps> orygColumnList;
  
  public ViewTable() {
    super();
    java.awt.EventQueue.invokeLater(new Runnable() {
      public void run() {
        setComponentPopupMenu(new ViewTablePopupMenu(ViewTable.this));
      }
    });
    new SearchOnQueryTable(this);
    setRowHeight(18);
    // nakladka "pobieranie..." ma byc widoczna takze przy pustej liscie
    setFillsViewportHeight(true);
    addKeyListener(new java.awt.event.KeyAdapter() {
      @Override
      public void keyPressed(java.awt.event.KeyEvent e) {
        if (busyText != null && e.getKeyCode() == java.awt.event.KeyEvent.VK_ESCAPE) {
          e.consume();
          fireCancelLoad();
        }
      }
    });
    addMouseListener(new java.awt.event.MouseAdapter() {
      @Override
      public void mousePressed(java.awt.event.MouseEvent e) {
        if (busyText != null && busyBounds != null && busyBounds.contains(e.getPoint())) {
          e.consume();
          fireCancelLoad();
        }
      }
    });
  }

  // ORBADA Fuerteventura: nakladka postepu dla ViewTableLoader

  private String busyText;
  private java.awt.Rectangle busyBounds;
  private javax.swing.Timer busyTimer;
  private int busyAngle = 0;
  private Runnable cancelLoadListener;

  void setCancelLoadListener(Runnable listener) {
    cancelLoadListener = listener;
  }

  private void fireCancelLoad() {
    if (cancelLoadListener != null) {
      cancelLoadListener.run();
    }
  }

  /** Tekst nakladki postepu; null chowa nakladke. Tylko w watku GUI. */
  public void setBusyText(String text) {
    busyText = text;
    if (text != null && busyTimer == null) {
      busyTimer = new javax.swing.Timer(80, new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent e) {
          busyAngle = (busyAngle + 30) % 360;
          if (busyBounds != null) {
            repaint(busyBounds);
          }
        }
      });
      busyTimer.start();
    } else if (text == null && busyTimer != null) {
      busyTimer.stop();
      busyTimer = null;
    }
    repaint();
  }

  public String getBusyText() {
    return busyText;
  }

  @Override
  protected void paintComponent(java.awt.Graphics g) {
    super.paintComponent(g);
    if (busyText == null) {
      busyBounds = null;
      return;
    }
    java.awt.Graphics2D g2 = (java.awt.Graphics2D)g.create();
    try {
      g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      java.awt.Font font = getFont().deriveFont(java.awt.Font.BOLD);
      java.awt.Font hintFont = getFont().deriveFont(java.awt.Font.PLAIN, getFont().getSize2D() - 1f);
      java.awt.FontMetrics fm = g2.getFontMetrics(font);
      java.awt.FontMetrics hfm = g2.getFontMetrics(hintFont);
      String hint = stringManager.getString("ViewTableLoader-cancel-hint");
      int spinner = 22;
      int pad = 12;
      int textWidth = Math.max(fm.stringWidth(busyText), hfm.stringWidth(hint));
      int w = pad + spinner + pad + textWidth + pad;
      int h = Math.max(spinner, fm.getHeight() + hfm.getHeight()) + 2 * pad;
      java.awt.Rectangle visible = getVisibleRect();
      int x = visible.x + Math.max(4, (visible.width - w) / 2);
      int y = visible.y + Math.max(4, Math.min(visible.height / 3, visible.height - h - 4));
      busyBounds = new java.awt.Rectangle(x, y, w + 1, h + 1);
      g2.setColor(new java.awt.Color(30, 40, 50, 215));
      g2.fillRoundRect(x, y, w, h, 14, 14);
      // obracajacy sie luk w kolorze piasku Fuerteventury
      int sx = x + pad;
      int sy = y + (h - spinner) / 2;
      g2.setStroke(new java.awt.BasicStroke(3f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
      g2.setColor(new java.awt.Color(255, 255, 255, 60));
      g2.drawOval(sx, sy, spinner, spinner);
      g2.setColor(new java.awt.Color(240, 170, 60));
      g2.drawArc(sx, sy, spinner, spinner, -busyAngle, 100);
      int tx = sx + spinner + pad;
      int ty = y + (h - fm.getHeight() - hfm.getHeight()) / 2;
      g2.setColor(java.awt.Color.WHITE);
      g2.setFont(font);
      g2.drawString(busyText, tx, ty + fm.getAscent());
      g2.setColor(new java.awt.Color(200, 210, 220));
      g2.setFont(hintFont);
      g2.drawString(hint, tx, ty + fm.getHeight() + hfm.getAscent());
    } finally {
      g2.dispose();
    }
  }

  @Override
  public void setQuery(Query query) {
    if (getQuery() != null) {
      getQuery().removeQueryListener(getQueryListener());
    }
    super.setQuery(query);
    if (getQuery() != null) {
      getQuery().addQueryListener(getQueryListener());
    }
  }
  
  private QueryListener getQueryListener() {
    if (queryListener == null) {
      queryListener = new DefaultQueryListener() {
        @Override
        public void afterOpen(EventObject e) {
          restoreColumnProps();
        }
        @Override
        public void beforeClose(EventObject e) {
          storeColumnProps();
        }
      };
    }
    return queryListener;
  }
  
  public void restoreOryginalColumnProps() {
    if (getColumnModel() instanceof QueryTableColumnModel && orygColumnList.size() > 0) {
      QueryTableColumnModel model = (QueryTableColumnModel)getColumnModel();
      model.clearColumns();
      for (OryginalColumnProps ocp : orygColumnList) {
        ocp.column.setPreferredWidth(ocp.width);
        model.addColumn(ocp.column);
      }
    }
  }
  
  private void restoreColumnProps() {
    if (Application.get() != null && Application.get().getSettings().getValue(Consts.autoSaveColumnWidths, true) && getColumnModel() instanceof QueryTableColumnModel) {
      ISettings settings = Application.get().getSettings(getQuery().getDatabase().getUserProperties().getProperty("schemaId"), "orbada-view-table-columns-props-" +Utils.getUniqueCompId(this));
      QueryTableColumnModel model = (QueryTableColumnModel)getColumnModel();
      if (orygColumnList == null) {
        orygColumnList = new ArrayList<OryginalColumnProps>();
        for (int i=0; i<model.getColumnCount(); i++) {
          TableColumn tc = model.getColumn(i);
          orygColumnList.add(new OryginalColumnProps(tc, tc.getWidth()));
        }
      }
      
      TableColumn tcs[] = new TableColumn[model.getColumnCount()];
      for (int i=0; i<model.getColumnCount(); i++) {
        tcs[i] = model.getColumn(i);
      }
      for (TableColumn tc : tcs) {
        if (tc instanceof QueryTableColumn) {
          QueryTableColumn qtc = (QueryTableColumn)tc;
          String fieldName = qtc.getFieldName();
          if (fieldName.length() > 200) {
            fieldName = fieldName.substring(0, 199);
          }
          int oinx = -1;
          for (int i=0; i<model.getColumnCount(); i++) {
            if (model.getColumn(i) == tc) {
              oinx = i;
              break;
            }
          }
          StringTokenizer st = new StringTokenizer(settings.getValue(fieldName, qtc.getWidth() +"," +oinx), ",");
          //System.out.println(fieldName +":" +qtc.getWidth() +"," +oinx +":" +settings.getValue(fieldName, qtc.getWidth() +"," +oinx));
          if (st.hasMoreTokens()) {
            int width = Integer.parseInt(st.nextToken());
            if (width != qtc.getWidth()) {
              qtc.setPreferredWidth((int)width);
            }
            if (st.hasMoreTokens()) {
              int index = Integer.parseInt(st.nextToken());
              if (index != oinx) {
                try {
                  model.moveColumn(oinx, index);
                }
                catch (IllegalArgumentException ex) {
                }
              }
            }
          }
        }
      }
    }
  }

  private void storeColumnProps() {
    if (Application.get() != null && Application.get().getSettings().getValue(Consts.autoSaveColumnWidths, true) && getColumnModel() instanceof QueryTableColumnModel) {
      ISettings settings = Application.get().getSettings(getQuery().getDatabase().getUserProperties().getProperty("schemaId"), "orbada-view-table-columns-props-" +Utils.getUniqueCompId(this));
      for (int i=0; i<getColumnCount(); i++) {
        Object o = getColumnModel().getColumn(i);
        if (o instanceof QueryTableColumn) {
          QueryTableColumn qtc = (QueryTableColumn)o;
          String fieldName = qtc.getFieldName();
          if (fieldName.length() > 200) {
            fieldName = fieldName.substring(0, 199);
          }
          settings.setValue(fieldName, qtc.getWidth() +"," +i);
        }
      }
      settings.store();
    }
  }
  
  private static class OryginalColumnProps {
    TableColumn column;
    int width;
    public OryginalColumnProps(TableColumn column, int width) {
      this.column = column;
      this.width = width;
    }
  }
  
}
