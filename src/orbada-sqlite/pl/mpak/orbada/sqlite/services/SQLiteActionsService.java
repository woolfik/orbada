package pl.mpak.orbada.sqlite.services;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import pl.mpak.orbada.gui.IRootTabObjectInfo;
import pl.mpak.orbada.gui.ITabObjectInfo;
import pl.mpak.orbada.gui.comps.table.ViewTable;
import pl.mpak.orbada.plugins.ComponentAction;
import pl.mpak.orbada.plugins.providers.ComponentActionsProvider;
import pl.mpak.orbada.sqlite.OrbadaSQLitePlugin;
import pl.mpak.orbada.sqlite.SQLiteSql;
import pl.mpak.orbada.sqlite.gui.ext.SQLiteWizard;
import pl.mpak.orbada.sqlite.gui.ext.SQLiteWizards;
import pl.mpak.orbada.universal.gui.tabbed.UniversalPropTab;
import pl.mpak.orbada.universal.gui.wizards.SqlCodeWizardDialog;
import pl.mpak.sky.gui.mr.ModalResult;
import pl.mpak.sky.gui.swing.MessageBox;
import pl.mpak.sky.gui.swing.SwingUtil;
import pl.mpak.usedb.core.Database;
import pl.mpak.usedb.core.Query;
import pl.mpak.util.StringManager;
import pl.mpak.util.StringManagerFactory;
import pl.mpak.util.StringUtil;

/**
 * ORBADA 1.3.8: akcje SQLite w widokach (tabele, widoki, wyzwalacze, indeksy, obiekty, AUTOINCREMENT, pragmy, bazy)
 * i zakladkach tabeli (kolumny, indeksy, wyzwalacze). Kazda zmiana przez kreator z zakladka SQL (SQLiteWizards),
 * sprawdzenia (integrity_check, foreign_key_check) pokazuja wynik w oknie.
 */
public class SQLiteActionsService extends ComponentActionsProvider {

  private final static StringManager stringManager = StringManagerFactory.getStringManager(OrbadaSQLitePlugin.class);

  /** Kontekst akcji: baza, obiekt (z listy albo obiekt zakladki), element zakladki (kolumna, indeks) i wiersz */
  public static class Ctx {
    public Database database;
    public String db = "main";
    public String object;
    public String item;
    public Query row;
  }

  /** Wywolanie akcji; true - odswiezyc widok */
  public interface Handler {
    boolean run(Ctx ctx) throws Exception;
  }

  static class Action extends ComponentAction {

    private final Handler handler;
    private final boolean needsObject;

    Action(String textKey, String icon, boolean needsObject, Handler handler) {
      super();
      this.handler = handler;
      this.needsObject = needsObject;
      setText(stringManager.getString(textKey));
      if (icon != null) {
        setSmallIcon(pl.mpak.sky.gui.swing.ImageManager.getImage("/pl/mpak/res/icons/" + icon));
      }
      setActionCommandKey("SQLiteAction-" + textKey);
      addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          perform();
        }
      });
    }

    private void perform() {
      Component parent = getComponent();
      try {
        Ctx ctx = context(getDatabase(), parent);
        if (needsObject && StringUtil.isEmpty(ctx.object)) {
          MessageBox.show(stringManager.getString("error"), stringManager.getString("sqlite-select-object"), ModalResult.OK);
          return;
        }
        if (handler.run(ctx)) {
          refresh(parent);
        }
      } catch (Exception ex) {
        MessageBox.show(stringManager.getString("error"), ex.getMessage(), ModalResult.OK);
      }
    }
  }

  /** Kontekst z komponentu akcji: zakladka (UniversalPropTab) albo lista widoku (ViewTable z kolumnami name, database) */
  public static Ctx context(Database database, Component parent) throws Exception {
    Ctx ctx = new Ctx();
    ctx.database = database;
    UniversalPropTab tab = parent == null ? null : (UniversalPropTab)SwingUtil.getOwnerComponent(UniversalPropTab.class, parent);
    if (parent instanceof ViewTable) {
      ViewTable vt = (ViewTable)parent;
      if (vt.getSelectedRow() >= 0 && vt.getQuery().isActive() && !vt.getQuery().isEmpty()) {
        vt.getQuery().getRecord(vt.getSelectedRow());
        ctx.row = vt.getQuery();
      }
    }
    if (tab != null) {
      ctx.object = tab.getCurrentObjectName();
      if (!StringUtil.isEmpty(tab.getCurrentSchemaName())) {
        ctx.db = tab.getCurrentSchemaName();
      }
      if (ctx.row != null && ctx.row.findFieldByName("name") != null) {
        ctx.item = ctx.row.fieldByName("name").getString();
      }
    } else if (ctx.row != null) {
      if (ctx.row.findFieldByName("name") != null) {
        ctx.object = ctx.row.fieldByName("name").getString();
      }
      if (ctx.row.findFieldByName("database") != null && !StringUtil.isEmpty(ctx.row.fieldByName("database").getString())) {
        ctx.db = ctx.row.fieldByName("database").getString();
      }
    }
    return ctx;
  }

  private static void refresh(Component parent) {
    final IRootTabObjectInfo root = parent == null ? null : (IRootTabObjectInfo)SwingUtil.getOwnerComponent(IRootTabObjectInfo.class, parent);
    final ITabObjectInfo tab = parent == null ? null : (ITabObjectInfo)SwingUtil.getOwnerComponent(ITabObjectInfo.class, parent);
    java.awt.EventQueue.invokeLater(new Runnable() {
      public void run() {
        if (root != null) {
          root.refresh();
        }
        if (tab != null) {
          tab.refresh();
        }
      }
    });
  }

  // ---------------------------------------------------------------- fabryki

  interface WizardFactory {
    SQLiteWizard create(Ctx ctx) throws Exception;
  }

  static Handler wizard(final WizardFactory factory) {
    return new Handler() {
      public boolean run(Ctx ctx) throws Exception {
        SQLiteWizard w = factory.create(ctx);
        return w != null && SqlCodeWizardDialog.show(w, true) != null;
      }
    };
  }

  /** Polecenie z potwierdzeniem; %s w poleceniach i pytaniu - nazwa obiektu (w SQL z prefiksem bazy) */
  static Handler statement(final String titleKey, final String questionKey, final boolean outside, final String... sql) {
    return wizard(new WizardFactory() {
      public SQLiteWizard create(Ctx ctx) {
        String[] s = new String[sql.length];
        for (int i = 0; i < sql.length; i++) {
          s[i] = sql[i].replace("%db", SQLiteSql.ident(ctx.db)).replace("%n", SQLiteSql.name(ctx.db, ctx.object == null ? "" : ctx.object))
            .replace("%i", SQLiteSql.name(ctx.db, ctx.item == null ? "" : ctx.item)).replace("%l", SQLiteSql.lit(ctx.object));
        }
        return new SQLiteWizards.Statement(ctx.database, ctx.db, titleKey, stringManager.getString(questionKey, new Object[] {ctx.object, ctx.db, ctx.item}), outside, s);
      }
    });
  }

  /** Zapytanie sprawdzajace - wynik (do 200 wierszy) w oknie; brak wierszy albo "ok" - komunikat o poprawnosci */
  static Handler check(final String titleKey, final String sql) {
    return new Handler() {
      public boolean run(Ctx ctx) throws Exception {
        String s = sql.replace("%db", SQLiteSql.ident(ctx.db)).replace("%l", SQLiteSql.lit(ctx.object)).replace("%dl", SQLiteSql.lit(ctx.db));
        StringBuilder sb = new StringBuilder();
        Query q = ctx.database.createQuery();
        int n = 0;
        try {
          q.setParamCheck(false);
          q.setSqlText(s);
          q.open();
          while (!q.eof() && n < 200) {
            StringBuilder line = new StringBuilder();
            for (int i = 0; i < q.getFieldCount(); i++) {
              line.append(i > 0 ? " | " : "").append(q.getField(i).getString());
            }
            if (!"ok".equalsIgnoreCase(line.toString())) {
              sb.append(line).append("\n");
            }
            n++;
            q.next();
          }
        } finally {
          q.close();
        }
        String msg = sb.length() == 0 ? stringManager.getString("sqlite-check-ok") : s + "\n\n" + sb;
        MessageBox.show(SwingUtil.getRootFrame(), stringManager.getString(titleKey), msg, ModalResult.OK, sb.length() == 0 ? MessageBox.INFORMATION : MessageBox.WARNING);
        return false;
      }
    };
  }

  // ---------------------------------------------------------------- akcje

  private static ComponentAction createTable() {
    return new Action("sqlite-action-create-table", "add_table16.gif", false, wizard(new WizardFactory() {
      public SQLiteWizard create(Ctx c) { return new SQLiteWizards.CreateTable(c.database, c.db); }
    }));
  }

  private static ComponentAction copyTable() {
    return new Action("sqlite-action-copy-table", "copy_table.gif", true, wizard(new WizardFactory() {
      public SQLiteWizard create(Ctx c) { return new SQLiteWizards.CopyTable(c.database, c.db, c.object); }
    }));
  }

  private static ComponentAction renameTable() {
    return new Action("sqlite-action-rename-table", "rename.gif", true, wizard(new WizardFactory() {
      public SQLiteWizard create(Ctx c) { return new SQLiteWizards.RenameTable(c.database, c.db, c.object); }
    }));
  }

  private static ComponentAction addColumn() {
    return new Action("sqlite-action-add-column", "add12.gif", true, wizard(new WizardFactory() {
      public SQLiteWizard create(Ctx c) { return new SQLiteWizards.AddColumn(c.database, c.db, c.object); }
    }));
  }

  private static ComponentAction renameColumn() {
    return new Action("sqlite-action-rename-column", "rename.gif", true, wizard(new WizardFactory() {
      public SQLiteWizard create(Ctx c) { return new SQLiteWizards.RenameColumn(c.database, c.db, c.object, c.item); }
    }));
  }

  private static ComponentAction dropColumn() {
    return new Action("sqlite-action-drop-column", "trash.gif", true, wizard(new WizardFactory() {
      public SQLiteWizard create(Ctx c) { return new SQLiteWizards.DropColumn(c.database, c.db, c.object, c.item); }
    }));
  }

  private static ComponentAction createIndex(final boolean fromIndexList) {
    return new Action("sqlite-action-create-index", "create_index16.gif", false, wizard(new WizardFactory() {
      public SQLiteWizard create(Ctx c) {
        String table = c.object;
        if (fromIndexList) {
          table = c.row != null && c.row.findFieldByName("tbl_name") != null ? field(c.row, "tbl_name") : null;
        }
        return new SQLiteWizards.CreateIndex(c.database, c.db, table);
      }
    }));
  }

  private static ComponentAction createTrigger(final boolean view, final boolean fromTriggerList) {
    return new Action(view ? "sqlite-action-create-view-trigger" : "sqlite-action-create-trigger", "trigger.gif", false, wizard(new WizardFactory() {
      public SQLiteWizard create(Ctx c) {
        String table = c.object;
        if (fromTriggerList) {
          table = c.row != null && c.row.findFieldByName("tbl_name") != null ? field(c.row, "tbl_name") : null;
        }
        return new SQLiteWizards.CreateTrigger(c.database, c.db, table, view);
      }
    }));
  }

  private static ComponentAction createView(final boolean fromTable) {
    return new Action(fromTable ? "sqlite-action-create-view-from-table" : "sqlite-action-create-view", "view.gif", fromTable, wizard(new WizardFactory() {
      public SQLiteWizard create(Ctx c) { return new SQLiteWizards.CreateView(c.database, c.db, fromTable ? c.object : null); }
    }));
  }

  static String field(Query row, String name) {
    try {
      return row.fieldByName(name).getString();
    } catch (Exception ex) {
      return null;
    }
  }

  /** Indeks z zakladki (element) albo z listy indeksow (obiekt) */
  private static ComponentAction dropIndex(final boolean tab) {
    return new Action("sqlite-action-drop-index", "trash.gif", true, new Handler() {
      public boolean run(Ctx c) throws Exception {
        String idx = tab ? c.item : c.object;
        if (StringUtil.isEmpty(idx)) {
          MessageBox.show(stringManager.getString("error"), stringManager.getString("sqlite-select-object"), ModalResult.OK);
          return false;
        }
        if (SQLiteSql.source(c.database, c.db, idx) == null) {
          MessageBox.show(stringManager.getString("error"), stringManager.getString("sqlite-auto-index"), ModalResult.OK);
          return false;
        }
        return SqlCodeWizardDialog.show(new SQLiteWizards.Statement(c.database, c.db, "sqlite-action-drop-index",
          stringManager.getString("sqlite-drop-index-q", new Object[] {idx}), false, "DROP INDEX " + SQLiteSql.name(c.db, idx)), true) != null;
      }
    });
  }

  private static ComponentAction reindex(final boolean tab) {
    return new Action("sqlite-action-reindex", "repair.gif", true, new Handler() {
      public boolean run(Ctx c) throws Exception {
        String o = tab && !StringUtil.isEmpty(c.item) ? c.item : c.object;
        return SqlCodeWizardDialog.show(new SQLiteWizards.Statement(c.database, c.db, "sqlite-action-reindex",
          stringManager.getString("sqlite-reindex-q", new Object[] {o}), false, "REINDEX " + SQLiteSql.name(c.db, o)), true) != null;
      }
    });
  }

  /** Usuniecie dowolnego obiektu z listy obiektow (typ z kolumny type) */
  private static ComponentAction dropObject() {
    return new Action("sqlite-action-drop-object", "trash.gif", true, new Handler() {
      public boolean run(Ctx c) throws Exception {
        String type = c.row == null ? null : field(c.row, "type");
        if (type == null || !type.matches("table|view|index|trigger")) {
          return false;
        }
        if (SQLiteSql.source(c.database, c.db, c.object) == null || c.object.startsWith("sqlite_")) {
          MessageBox.show(stringManager.getString("error"), stringManager.getString("sqlite-auto-index"), ModalResult.OK);
          return false;
        }
        return SqlCodeWizardDialog.show(new SQLiteWizards.Statement(c.database, c.db, "sqlite-action-drop-object",
          stringManager.getString("sqlite-drop-object-q", new Object[] {type.toUpperCase(), c.object}), false,
          "DROP " + type.toUpperCase() + " " + SQLiteSql.name(c.db, c.object)), true) != null;
      }
    });
  }

  @Override
  public ComponentAction[] getForComponent(Database database, String actionType) {
    if (database == null || !OrbadaSQLitePlugin.driverType.equals(database.getDriverType()) || actionType == null) {
      return null;
    }
    List<ComponentAction> a = new ArrayList<ComponentAction>();
    if ("sqlite-tables-actions".equals(actionType)) {
      a.add(createTable());
      a.add(copyTable());
      a.add(renameTable());
      a.add(addColumn());
      a.add(createIndex(false));
      a.add(createTrigger(false, false));
      a.add(createView(true));
      a.add(ComponentAction.Separator);
      a.add(new Action("sqlite-action-analyze", "analyze.gif", true, statement("sqlite-action-analyze", "sqlite-analyze-q", false, "ANALYZE %n")));
      a.add(reindex(false));
      a.add(new Action("sqlite-action-integrity-table", "check.gif", true, check("sqlite-action-integrity-table", "PRAGMA %db.integrity_check(%l)")));
      a.add(new Action("sqlite-action-fk-check-table", "foreign_key16.gif", true, check("sqlite-action-fk-check-table", "PRAGMA %db.foreign_key_check(%l)")));
      a.add(ComponentAction.Separator);
      a.add(new Action("sqlite-action-delete-rows", "delete_square16.gif", true, statement("sqlite-action-delete-rows", "sqlite-delete-rows-q", false, "DELETE FROM %n")));
    } else if ("sqlite-table-columns-actions".equals(actionType)) {
      a.add(addColumn());
      a.add(renameColumn());
      a.add(dropColumn());
    } else if ("sqlite-table-indexes-actions".equals(actionType)) {
      a.add(createIndex(false));
      a.add(dropIndex(true));
      a.add(reindex(true));
    } else if ("sqlite-table-triggers-actions".equals(actionType)) {
      a.add(createTrigger(false, false));
    } else if ("sqlite-views-actions".equals(actionType)) {
      a.add(createView(false));
      a.add(createTrigger(true, false));
    } else if ("sqlite-view-triggers-actions".equals(actionType)) {
      a.add(createTrigger(true, false));
    } else if ("sqlite-triggers-actions".equals(actionType)) {
      a.add(createTrigger(false, true));
    } else if ("sqlite-indexes-actions".equals(actionType)) {
      a.add(createIndex(true));
      a.add(dropIndex(false));
      a.add(reindex(false));
      a.add(new Action("sqlite-action-analyze", "analyze.gif", true, statement("sqlite-action-analyze", "sqlite-analyze-q", false, "ANALYZE %n")));
    } else if ("sqlite-objects-actions".equals(actionType)) {
      a.add(dropObject());
    } else if ("sqlite-sequences-actions".equals(actionType)) {
      a.add(new Action("sqlite-action-set-sequence", "edit_sequence.gif", true, wizard(new WizardFactory() {
        public SQLiteWizard create(Ctx c) { return new SQLiteWizards.SetSequence(c.database, c.db, c.object); }
      })));
      a.add(new Action("sqlite-action-reset-sequence", "trash.gif", true, statement("sqlite-action-reset-sequence", "sqlite-reset-sequence-q", false,
        "DELETE FROM %db.sqlite_sequence WHERE name = %l")));
    } else if ("sqlite-pragmas-actions".equals(actionType)) {
      a.add(new Action("sqlite-action-set-pragma", "edit16.gif", false, wizard(new WizardFactory() {
        public SQLiteWizard create(Ctx c) { return new SQLiteWizards.SetPragma(c.database, c.db, c.object); }
      })));
    } else if ("sqlite-databases-actions".equals(actionType)) {
      a.add(new Action("sqlite-action-backup", "save_as16.gif", true, wizard(new WizardFactory() {
        public SQLiteWizard create(Ctx c) { return new SQLiteWizards.Backup(c.database, c.object); }
      })));
      a.add(new Action("sqlite-action-vacuum", "cleaning.gif", true, dbStatement("sqlite-action-vacuum", "sqlite-vacuum-q", true, "VACUUM %db")));
      a.add(new Action("sqlite-action-analyze-db", "analyze.gif", true, dbStatement("sqlite-action-analyze-db", "sqlite-analyze-db-q", false, "ANALYZE %db")));
      a.add(new Action("sqlite-action-optimize", "optimize.gif", true, dbStatement("sqlite-action-optimize", "sqlite-optimize-q", false, "PRAGMA %db.optimize")));
      a.add(new Action("sqlite-action-checkpoint", "commit.gif", true, dbStatement("sqlite-action-checkpoint", "sqlite-checkpoint-q", true, "PRAGMA %db.wal_checkpoint(TRUNCATE)")));
      a.add(ComponentAction.Separator);
      a.add(new Action("sqlite-action-quick-check", "check.gif", true, dbCheck("sqlite-action-quick-check", "PRAGMA %db.quick_check")));
      a.add(new Action("sqlite-action-fk-check", "foreign_key16.gif", true, dbCheck("sqlite-action-fk-check", "PRAGMA %db.foreign_key_check")));
    }
    if (a.isEmpty()) {
      return null;
    }
    return a.toArray(new ComponentAction[a.size()]);
  }

  /** Akcja widoku baz: obiektem jest nazwa bazy (kolumna name), nie tabela */
  private static Handler dbStatement(final String titleKey, final String questionKey, final boolean outside, final String sql) {
    final Handler h = statement(titleKey, questionKey, outside, sql);
    return new Handler() {
      public boolean run(Ctx c) throws Exception {
        c.db = c.object;
        return h.run(c);
      }
    };
  }

  private static Handler dbCheck(final String titleKey, final String sql) {
    final Handler h = check(titleKey, sql);
    return new Handler() {
      public boolean run(Ctx c) throws Exception {
        c.db = c.object;
        return h.run(c);
      }
    };
  }

  @Override
  public String getDescription() {
    return stringManager.getString("SQLiteActionsService-description");
  }

  @Override
  public String getGroupName() {
    return OrbadaSQLitePlugin.driverType;
  }

}
