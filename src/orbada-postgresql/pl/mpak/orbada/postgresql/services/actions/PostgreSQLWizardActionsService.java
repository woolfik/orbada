package pl.mpak.orbada.postgresql.services.actions;

import java.util.ArrayList;
import java.util.List;
import pl.mpak.orbada.plugins.ComponentAction;
import pl.mpak.orbada.plugins.providers.ComponentActionsProvider;
import pl.mpak.orbada.postgresql.OrbadaPostgreSQLPlugin;
import pl.mpak.orbada.postgresql.cm.PgWizardAction;
import pl.mpak.orbada.postgresql.cm.PgWizardAction.Context;
import pl.mpak.orbada.postgresql.cm.PgWizardAction.Factory;
import pl.mpak.orbada.postgresql.gui.wizards.AddColumnWizard;
import pl.mpak.orbada.postgresql.gui.wizards.AddEnumValueWizard;
import pl.mpak.orbada.postgresql.gui.wizards.AlterColumnWizard;
import pl.mpak.orbada.postgresql.gui.wizards.CallRoutineWizard;
import pl.mpak.orbada.postgresql.gui.wizards.CopyTableWizard;
import pl.mpak.orbada.postgresql.gui.wizards.CreateConstraintWizard;
import pl.mpak.orbada.postgresql.gui.wizards.CreateFkIndexesWizard;
import pl.mpak.orbada.postgresql.gui.wizards.CreateIndexWizard;
import pl.mpak.orbada.postgresql.gui.wizards.CreateRoutineWizard;
import pl.mpak.orbada.postgresql.gui.wizards.CreateSchemaWizard;
import pl.mpak.orbada.postgresql.gui.wizards.CreateTableWizard;
import pl.mpak.orbada.postgresql.gui.wizards.CreateTriggerWizard;
import pl.mpak.orbada.postgresql.gui.wizards.CreateTypeWizard;
import pl.mpak.orbada.postgresql.gui.wizards.CreateViewWizard;
import pl.mpak.orbada.postgresql.gui.wizards.DropObjectWizard;
import pl.mpak.orbada.postgresql.gui.wizards.EnableTriggerWizard;
import pl.mpak.orbada.postgresql.gui.wizards.GrantWizard;
import pl.mpak.orbada.postgresql.gui.wizards.LockTableWizard;
import pl.mpak.orbada.postgresql.gui.wizards.RefreshMViewWizard;
import pl.mpak.orbada.postgresql.gui.wizards.ReindexWizard;
import pl.mpak.orbada.postgresql.gui.wizards.RenameObjectWizard;
import pl.mpak.orbada.postgresql.gui.wizards.SequenceWizard;
import pl.mpak.orbada.postgresql.gui.wizards.SetParameterWizard;
import pl.mpak.orbada.postgresql.gui.wizards.SetSchemaWizard;
import pl.mpak.orbada.postgresql.gui.wizards.StatementWizard;
import pl.mpak.orbada.postgresql.gui.wizards.TruncateTableWizard;
import pl.mpak.orbada.universal.gui.wizards.SqlCodeWizardPanel;
import pl.mpak.usedb.core.Database;
import pl.mpak.util.StringManager;
import pl.mpak.util.StringManagerFactory;
import pl.mpak.util.StringUtil;

import static pl.mpak.orbada.postgresql.gui.wizards.PgWizard.ident;
import static pl.mpak.orbada.postgresql.gui.wizards.PgWizard.name;

/**
 * ORBADA 1.3.2: kreatory PostgreSQL w menu "Akcje" list obiektow i zakladek tabeli (odpowiednik akcji kreatorow
 * w panelach Oracle). Typ akcji = nazwa panelu + "-actions".
 */
public class PostgreSQLWizardActionsService extends ComponentActionsProvider {

  private final static StringManager stringManager = StringManagerFactory.getStringManager(OrbadaPostgreSQLPlugin.class);

  @Override
  public ComponentAction[] getForComponent(Database database, String actionType) {
    if (database == null || !OrbadaPostgreSQLPlugin.driverType.equals(database.getDriverType()) || actionType == null) {
      return null;
    }
    List<ComponentAction> a = new ArrayList<ComponentAction>();
    if ("postgresql-tables-actions".equals(actionType)) {
      tables(a);
    } else if ("postgresql-table-columns-actions".equals(actionType)) {
      tableColumns(a);
    } else if ("postgresql-table-constraints-actions".equals(actionType)) {
      tableConstraints(a);
    } else if ("postgresql-table-indexes-actions".equals(actionType)) {
      tableIndexes(a);
    } else if ("postgresql-views-actions".equals(actionType)) {
      views(a);
    } else if ("postgresql-functions-actions".equals(actionType)) {
      routines(a, CreateRoutineWizard.FUNCTION);
    } else if ("postgresql-trigger-functions-actions".equals(actionType)) {
      routines(a, CreateRoutineWizard.TRIGGER);
    } else if ("postgresql-procedures-actions".equals(actionType)) {
      routines(a, CreateRoutineWizard.PROCEDURE);
    } else if ("postgresql-triggers-actions".equals(actionType)) {
      triggers(a);
    } else if ("postgresql-sequences-actions".equals(actionType)) {
      sequences(a);
    } else if ("postgresql-types-actions".equals(actionType)) {
      types(a);
    } else if ("postgresql-schemas-actions".equals(actionType)) {
      schemas(a);
    }
    if (a.isEmpty()) {
      return null;
    }
    a.add(0, ComponentAction.Separator);
    return a.toArray(new ComponentAction[a.size()]);
  }

  private static PgWizardAction action(String key, String icon, boolean needsRow, Factory factory) {
    return new PgWizardAction(key, icon, needsRow, factory);
  }

  /** Wartosc pola zaznaczonego wiersza; brak - komunikat zamiast kreatora */
  private static String required(Context c, String field) throws Exception {
    String v = c.get(field);
    if (StringUtil.isEmpty(v)) {
      throw new Exception(stringManager.getString("wizard-select-object"));
    }
    return v;
  }

  /** Nazwa obiektu do GRANT: pole juz w SQL (full_object_name), nazwa w schemacie albo sam identyfikator (schemat) */
  private static final int AS_IS = 0;
  private static final int QUALIFY = 1;
  private static final int IDENT = 2;

  private static String grantName(Context c, String field, int mode) throws Exception {
    String v = required(c, field);
    return mode == QUALIFY ? name(c.schema, v) : mode == IDENT ? ident(v) : v;
  }

  private static void grants(List<ComponentAction> a, final String kind, final String[] privileges, final String nameField, final int mode) {
    a.add(action("wizard-grant", "grant_class.gif", true, new Factory() {
      public SqlCodeWizardPanel create(Database db, Context c) throws Exception {
        return new GrantWizard(db, false, kind(c, kind), grantName(c, nameField, mode), privileges);
      }
    }));
    a.add(action("wizard-revoke", "revoke_class.gif", true, new Factory() {
      public SqlCodeWizardPanel create(Database db, Context c) throws Exception {
        return new GrantWizard(db, true, kind(c, kind), grantName(c, nameField, mode), privileges);
      }
    }));
  }

  /** Domena ma GRANT ... ON DOMAIN */
  private static String kind(Context c, String kind) throws Exception {
    return "TYPE".equals(kind) && "DOMAIN".equals(c.get("type_type")) ? "DOMAIN" : kind;
  }

  // ---------------------------------------------------------------- tabele

  private void tables(List<ComponentAction> a) {
    a.add(action("wizard-create-table", "table.gif", false, new Factory() {
      public SqlCodeWizardPanel create(Database db, Context c) {
        return new CreateTableWizard(db, c.schema);
      }
    }));
    a.add(action("wizard-copy-table", "copy_table.gif", true, new Factory() {
      public SqlCodeWizardPanel create(Database db, Context c) throws Exception {
        return new CopyTableWizard(db, c.schema, required(c, "full_object_name"), required(c, "table_name"), false);
      }
    }));
    a.add(action("wizard-rename", "rename.gif", true, new Factory() {
      public SqlCodeWizardPanel create(Database db, Context c) throws Exception {
        return new RenameObjectWizard(db, "RenameTableWizard-title", "ALTER TABLE " + required(c, "full_object_name") + " RENAME", c.get("table_name"));
      }
    }));
    a.add(action("wizard-set-schema", "edit_schema.gif", true, new Factory() {
      public SqlCodeWizardPanel create(Database db, Context c) throws Exception {
        return new SetSchemaWizard(db, "ALTER TABLE " + required(c, "full_object_name"), c.get("full_object_name"), c.schema);
      }
    }));
    a.add(action("wizard-truncate", "fatcow/small/table_delete.png", true, new Factory() {
      public SqlCodeWizardPanel create(Database db, Context c) throws Exception {
        return new TruncateTableWizard(db, required(c, "full_object_name"));
      }
    }));
    a.add(action("wizard-lock-table", "lock16.gif", true, new Factory() {
      public SqlCodeWizardPanel create(Database db, Context c) throws Exception {
        return new LockTableWizard(db, required(c, "full_object_name"));
      }
    }));
    a.add(action("wizard-drop-table", "delete_square16.gif", true, new Factory() {
      public SqlCodeWizardPanel create(Database db, Context c) throws Exception {
        return new DropObjectWizard(db, "DropTableWizard-title", "DROP TABLE", required(c, "full_object_name"), "", true, false);
      }
    }));
    a.add(ComponentAction.Separator);
    a.add(action("wizard-add-column", "column.gif", true, new Factory() {
      public SqlCodeWizardPanel create(Database db, Context c) throws Exception {
        return new AddColumnWizard(db, c.schema, required(c, "table_name"));
      }
    }));
    a.add(action("wizard-create-constraint", "primary_key16.gif", true, new Factory() {
      public SqlCodeWizardPanel create(Database db, Context c) throws Exception {
        return new CreateConstraintWizard(db, c.schema, required(c, "table_name"));
      }
    }));
    a.add(action("wizard-create-index", "create_index16.gif", true, new Factory() {
      public SqlCodeWizardPanel create(Database db, Context c) throws Exception {
        return new CreateIndexWizard(db, c.schema, required(c, "table_name"));
      }
    }));
    a.add(action("wizard-create-fk-indexes", "foreign_key16.gif", true, new Factory() {
      public SqlCodeWizardPanel create(Database db, Context c) throws Exception {
        return new CreateFkIndexesWizard(db, c.schema, required(c, "table_name"));
      }
    }));
    a.add(action("wizard-reindex-table", "index.gif", true, new Factory() {
      public SqlCodeWizardPanel create(Database db, Context c) throws Exception {
        return new ReindexWizard(db, "TABLE", required(c, "full_object_name"));
      }
    }));
    a.add(action("wizard-create-trigger", "trigger.gif", true, new Factory() {
      public SqlCodeWizardPanel create(Database db, Context c) throws Exception {
        return new CreateTriggerWizard(db, c.schema, required(c, "table_name"));
      }
    }));
    a.add(action("wizard-enable-triggers", "enable_all.gif", true, new Factory() {
      public SqlCodeWizardPanel create(Database db, Context c) throws Exception {
        return new EnableTriggerWizard(db, required(c, "full_object_name"), null);
      }
    }));
    a.add(ComponentAction.Separator);
    grants(a, "TABLE", GrantWizard.TABLE, "full_object_name", AS_IS);
  }

  private void tableColumns(List<ComponentAction> a) {
    a.add(action("wizard-add-column", "column.gif", false, new Factory() {
      public SqlCodeWizardPanel create(Database db, Context c) {
        return new AddColumnWizard(db, c.schema, c.object);
      }
    }));
    a.add(action("wizard-alter-column", "edit16.gif", true, new Factory() {
      public SqlCodeWizardPanel create(Database db, Context c) throws Exception {
        return new AlterColumnWizard(db, c.schema, c.object, required(c, "column_name"));
      }
    }));
    a.add(action("wizard-rename", "rename.gif", true, new Factory() {
      public SqlCodeWizardPanel create(Database db, Context c) throws Exception {
        String column = required(c, "column_name");
        return new RenameObjectWizard(db, "RenameColumnWizard-title", "ALTER TABLE " + name(c.schema, c.object) + " RENAME COLUMN " + ident(column), column);
      }
    }));
    a.add(action("wizard-drop-column", "delete_square16.gif", true, new Factory() {
      public SqlCodeWizardPanel create(Database db, Context c) throws Exception {
        return new DropObjectWizard(db, "DropColumnWizard-title", "ALTER TABLE " + name(c.schema, c.object) + " DROP COLUMN", ident(required(c, "column_name")), "", true, false);
      }
    }));
  }

  private void tableConstraints(List<ComponentAction> a) {
    a.add(action("wizard-create-constraint", "primary_key16.gif", false, new Factory() {
      public SqlCodeWizardPanel create(Database db, Context c) {
        return new CreateConstraintWizard(db, c.schema, c.object);
      }
    }));
    a.add(action("wizard-create-fk-indexes", "foreign_key16.gif", false, new Factory() {
      public SqlCodeWizardPanel create(Database db, Context c) {
        return new CreateFkIndexesWizard(db, c.schema, c.object);
      }
    }));
    a.add(action("wizard-validate-constraint", "enabled.gif", true, new Factory() {
      public SqlCodeWizardPanel create(Database db, Context c) throws Exception {
        String constraint = required(c, "constraint_name");
        return new StatementWizard(db, "ValidateConstraintWizard-title", constraint,
          "ALTER TABLE " + name(c.schema, c.object) + " VALIDATE CONSTRAINT " + ident(constraint));
      }
    }));
    a.add(action("wizard-rename", "rename.gif", true, new Factory() {
      public SqlCodeWizardPanel create(Database db, Context c) throws Exception {
        String constraint = required(c, "constraint_name");
        return new RenameObjectWizard(db, "RenameConstraintWizard-title", "ALTER TABLE " + name(c.schema, c.object) + " RENAME CONSTRAINT " + ident(constraint), constraint);
      }
    }));
    a.add(action("wizard-drop-constraint", "delete_square16.gif", true, new Factory() {
      public SqlCodeWizardPanel create(Database db, Context c) throws Exception {
        return new DropObjectWizard(db, "DropConstraintWizard-title", "ALTER TABLE " + name(c.schema, c.object) + " DROP CONSTRAINT", ident(required(c, "constraint_name")), "", true, false);
      }
    }));
  }

  private void tableIndexes(List<ComponentAction> a) {
    a.add(action("wizard-create-index", "create_index16.gif", false, new Factory() {
      public SqlCodeWizardPanel create(Database db, Context c) {
        return new CreateIndexWizard(db, c.schema, c.object);
      }
    }));
    a.add(action("wizard-reindex", "index.gif", true, new Factory() {
      public SqlCodeWizardPanel create(Database db, Context c) throws Exception {
        return new ReindexWizard(db, "INDEX", name(c.schema, required(c, "index_name")));
      }
    }));
    a.add(action("wizard-rename", "rename.gif", true, new Factory() {
      public SqlCodeWizardPanel create(Database db, Context c) throws Exception {
        return new RenameObjectWizard(db, "RenameIndexWizard-title", "ALTER INDEX " + name(c.schema, required(c, "index_name")) + " RENAME", c.get("index_name"));
      }
    }));
    a.add(action("wizard-drop-index", "delete_square16.gif", true, new Factory() {
      public SqlCodeWizardPanel create(Database db, Context c) throws Exception {
        return new DropObjectWizard(db, "DropIndexWizard-title", "DROP INDEX", name(c.schema, required(c, "index_name")), "", true, true);
      }
    }));
  }

  // ---------------------------------------------------------------- widoki

  private static boolean materialized(Context c) throws Exception {
    return "Y".equals(c.get("materialized"));
  }

  private void views(List<ComponentAction> a) {
    a.add(action("wizard-create-view", "view.gif", false, new Factory() {
      public SqlCodeWizardPanel create(Database db, Context c) {
        return new CreateViewWizard(db, c.schema);
      }
    }));
    a.add(action("wizard-copy-view", "copy_table.gif", true, new Factory() {
      public SqlCodeWizardPanel create(Database db, Context c) throws Exception {
        return new CopyTableWizard(db, c.schema, required(c, "full_object_name"), required(c, "view_name"), true);
      }
    }));
    a.add(action("wizard-refresh-mview", "mview.gif", true, new Factory() {
      public SqlCodeWizardPanel create(Database db, Context c) throws Exception {
        if (!materialized(c)) {
          throw new Exception(stringManager.getString("wizard-not-mview"));
        }
        return new RefreshMViewWizard(db, required(c, "full_object_name"));
      }
    }));
    a.add(action("wizard-create-index", "create_index16.gif", true, new Factory() {
      public SqlCodeWizardPanel create(Database db, Context c) throws Exception {
        if (!materialized(c)) {
          throw new Exception(stringManager.getString("wizard-not-mview"));
        }
        return new CreateIndexWizard(db, c.schema, required(c, "view_name"));
      }
    }));
    a.add(action("wizard-create-view-trigger", "trigger.gif", true, new Factory() {
      public SqlCodeWizardPanel create(Database db, Context c) throws Exception {
        return new CreateTriggerWizard(db, c.schema, required(c, "view_name"));
      }
    }));
    a.add(action("wizard-rename", "rename.gif", true, new Factory() {
      public SqlCodeWizardPanel create(Database db, Context c) throws Exception {
        return new RenameObjectWizard(db, "RenameViewWizard-title",
          "ALTER " + (materialized(c) ? "MATERIALIZED VIEW " : "VIEW ") + required(c, "full_object_name") + " RENAME", c.get("view_name"));
      }
    }));
    a.add(action("wizard-set-schema", "edit_schema.gif", true, new Factory() {
      public SqlCodeWizardPanel create(Database db, Context c) throws Exception {
        return new SetSchemaWizard(db, "ALTER " + (materialized(c) ? "MATERIALIZED VIEW " : "VIEW ") + required(c, "full_object_name"), c.get("full_object_name"), c.schema);
      }
    }));
    a.add(action("wizard-drop-view", "delete_square16.gif", true, new Factory() {
      public SqlCodeWizardPanel create(Database db, Context c) throws Exception {
        return new DropObjectWizard(db, "DropViewWizard-title", materialized(c) ? "DROP MATERIALIZED VIEW" : "DROP VIEW", required(c, "full_object_name"), "", true, false);
      }
    }));
    a.add(ComponentAction.Separator);
    grants(a, "TABLE", GrantWizard.TABLE, "full_object_name", AS_IS);
  }

  // ---------------------------------------------------------------- funkcje i procedury

  private void routines(List<ComponentAction> a, final int kind) {
    final String keyword = kind == CreateRoutineWizard.PROCEDURE ? "PROCEDURE" : "FUNCTION";
    a.add(action(kind == CreateRoutineWizard.PROCEDURE ? "wizard-create-procedure" : kind == CreateRoutineWizard.TRIGGER ? "wizard-create-trigger-function" : "wizard-create-function",
        kind == CreateRoutineWizard.PROCEDURE ? "procedure.gif" : kind == CreateRoutineWizard.TRIGGER ? "trigger-function.gif" : "function.gif", false, new Factory() {
      public SqlCodeWizardPanel create(Database db, Context c) {
        return new CreateRoutineWizard(db, c.schema, kind);
      }
    }));
    if (kind != CreateRoutineWizard.TRIGGER) {
      a.add(action(kind == CreateRoutineWizard.PROCEDURE ? "wizard-call-procedure" : "wizard-call-function", "call.gif", true, new Factory() {
        public SqlCodeWizardPanel create(Database db, Context c) throws Exception {
          return new CallRoutineWizard(db, required(c, "full_object_name"));
        }
      }));
    }
    a.add(action("wizard-rename", "rename.gif", true, new Factory() {
      public SqlCodeWizardPanel create(Database db, Context c) throws Exception {
        return new RenameObjectWizard(db, "RenameRoutineWizard-title", "ALTER " + keyword + " " + required(c, "full_object_name") + " RENAME", c.get("function_name"));
      }
    }));
    a.add(action("wizard-set-schema", "edit_schema.gif", true, new Factory() {
      public SqlCodeWizardPanel create(Database db, Context c) throws Exception {
        return new SetSchemaWizard(db, "ALTER " + keyword + " " + required(c, "full_object_name"), c.get("full_object_name"), c.schema);
      }
    }));
    a.add(action(kind == CreateRoutineWizard.PROCEDURE ? "wizard-drop-procedure" : "wizard-drop-function", "delete_square16.gif", true, new Factory() {
      public SqlCodeWizardPanel create(Database db, Context c) throws Exception {
        return new DropObjectWizard(db, kind == CreateRoutineWizard.PROCEDURE ? "DropProcedureWizard-title" : "DropFunctionWizard-title",
          "DROP " + keyword, required(c, "full_object_name"), "", true, false);
      }
    }));
    a.add(ComponentAction.Separator);
    grants(a, keyword, GrantWizard.ROUTINE, "full_object_name", AS_IS);
  }

  // ---------------------------------------------------------------- wyzwalacze, sekwencje, typy, schematy

  private void triggers(List<ComponentAction> a) {
    a.add(action("wizard-create-trigger", "trigger.gif", false, new Factory() {
      public SqlCodeWizardPanel create(Database db, Context c) throws Exception {
        return new CreateTriggerWizard(db, c.schema, null);
      }
    }));
    a.add(action("wizard-enable-trigger", "enabled.gif", true, new Factory() {
      public SqlCodeWizardPanel create(Database db, Context c) throws Exception {
        return new EnableTriggerWizard(db, name(c.schema, required(c, "table_name")), required(c, "trigger_name"));
      }
    }));
    a.add(action("wizard-rename", "rename.gif", true, new Factory() {
      public SqlCodeWizardPanel create(Database db, Context c) throws Exception {
        String trigger = required(c, "trigger_name");
        return new RenameObjectWizard(db, "RenameTriggerWizard-title", "ALTER TRIGGER " + ident(trigger) + " ON " + name(c.schema, required(c, "table_name")) + " RENAME", trigger);
      }
    }));
    a.add(action("wizard-drop-trigger", "delete_square16.gif", true, new Factory() {
      public SqlCodeWizardPanel create(Database db, Context c) throws Exception {
        return new DropObjectWizard(db, "DropTriggerWizard-title", "DROP TRIGGER", ident(required(c, "trigger_name")), " ON " + name(c.schema, required(c, "table_name")), true, false);
      }
    }));
  }

  private void sequences(List<ComponentAction> a) {
    a.add(action("wizard-create-sequence", "sequence.gif", false, new Factory() {
      public SqlCodeWizardPanel create(Database db, Context c) {
        return new SequenceWizard(db, c.schema, null);
      }
    }));
    a.add(action("wizard-alter-sequence", "edit_sequence.gif", true, new Factory() {
      public SqlCodeWizardPanel create(Database db, Context c) throws Exception {
        return new SequenceWizard(db, c.schema, name(c.schema, required(c, "sequence_name")));
      }
    }));
    a.add(action("wizard-rename", "rename.gif", true, new Factory() {
      public SqlCodeWizardPanel create(Database db, Context c) throws Exception {
        return new RenameObjectWizard(db, "RenameSequenceWizard-title", "ALTER SEQUENCE " + name(c.schema, required(c, "sequence_name")) + " RENAME", c.get("sequence_name"));
      }
    }));
    a.add(action("wizard-set-schema", "edit_schema.gif", true, new Factory() {
      public SqlCodeWizardPanel create(Database db, Context c) throws Exception {
        String sequence = name(c.schema, required(c, "sequence_name"));
        return new SetSchemaWizard(db, "ALTER SEQUENCE " + sequence, sequence, c.schema);
      }
    }));
    a.add(action("wizard-drop-sequence", "delete_square16.gif", true, new Factory() {
      public SqlCodeWizardPanel create(Database db, Context c) throws Exception {
        return new DropObjectWizard(db, "DropSequenceWizard-title", "DROP SEQUENCE", name(c.schema, required(c, "sequence_name")), "", true, false);
      }
    }));
    a.add(ComponentAction.Separator);
    grants(a, "SEQUENCE", GrantWizard.SEQUENCE, "sequence_name", QUALIFY);
  }

  private void types(List<ComponentAction> a) {
    final String[] keys = {"wizard-create-composite-type", "wizard-create-enum-type", "wizard-create-domain", "wizard-create-range-type"};
    final String[] icons = {"type.gif", "type.gif", "domain.gif", "type.gif"};
    for (int i = 0; i < keys.length; i++) {
      final int kind = i;
      a.add(action(keys[i], icons[i], false, new Factory() {
        public SqlCodeWizardPanel create(Database db, Context c) {
          return new CreateTypeWizard(db, c.schema, kind);
        }
      }));
    }
    a.add(action("wizard-add-enum-value", "add8.gif", true, new Factory() {
      public SqlCodeWizardPanel create(Database db, Context c) throws Exception {
        if (!"ENUM".equals(c.get("type_type"))) {
          throw new Exception(stringManager.getString("wizard-not-enum"));
        }
        return new AddEnumValueWizard(db, required(c, "full_object_name"));
      }
    }));
    a.add(action("wizard-rename", "rename.gif", true, new Factory() {
      public SqlCodeWizardPanel create(Database db, Context c) throws Exception {
        return new RenameObjectWizard(db, "RenameTypeWizard-title", "ALTER " + kind(c, "TYPE") + " " + required(c, "full_object_name") + " RENAME", c.get("type_name"));
      }
    }));
    a.add(action("wizard-set-schema", "edit_schema.gif", true, new Factory() {
      public SqlCodeWizardPanel create(Database db, Context c) throws Exception {
        return new SetSchemaWizard(db, "ALTER " + kind(c, "TYPE") + " " + required(c, "full_object_name"), c.get("full_object_name"), c.schema);
      }
    }));
    a.add(action("wizard-drop-type", "delete_square16.gif", true, new Factory() {
      public SqlCodeWizardPanel create(Database db, Context c) throws Exception {
        return new DropObjectWizard(db, "DropTypeWizard-title", "DROP " + kind(c, "TYPE"), required(c, "full_object_name"), "", true, false);
      }
    }));
    a.add(ComponentAction.Separator);
    grants(a, "TYPE", GrantWizard.TYPE, "full_object_name", AS_IS);
  }

  private void schemas(List<ComponentAction> a) {
    a.add(action("wizard-create-schema", "new_schema.gif", false, new Factory() {
      public SqlCodeWizardPanel create(Database db, Context c) {
        return new CreateSchemaWizard(db);
      }
    }));
    a.add(action("wizard-search-path", "users16.gif", false, new Factory() {
      public SqlCodeWizardPanel create(Database db, Context c) {
        return new SetParameterWizard(db, "SearchPathWizard-title", "search_path", true);
      }
    }));
    a.add(action("wizard-rename", "rename.gif", true, new Factory() {
      public SqlCodeWizardPanel create(Database db, Context c) throws Exception {
        String schema = required(c, "schema_name");
        return new RenameObjectWizard(db, "RenameSchemaWizard-title", "ALTER SCHEMA " + ident(schema) + " RENAME", schema);
      }
    }));
    a.add(action("wizard-drop-schema", "delete_square16.gif", true, new Factory() {
      public SqlCodeWizardPanel create(Database db, Context c) throws Exception {
        return new DropObjectWizard(db, "DropSchemaWizard-title", "DROP SCHEMA", ident(required(c, "schema_name")), "", true, false);
      }
    }));
    a.add(ComponentAction.Separator);
    grants(a, "SCHEMA", GrantWizard.SCHEMA, "schema_name", IDENT);
  }

  @Override
  public String getDescription() {
    return stringManager.getString("PostgreSQLWizardActionsService-description");
  }

  @Override
  public String getGroupName() {
    return OrbadaPostgreSQLPlugin.driverType;
  }

}
