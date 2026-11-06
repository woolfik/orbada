package pl.mpak.orbada.postgresql.services;

import javax.swing.JMenu;
import javax.swing.JToolBar;
import pl.mpak.orbada.Consts;
import pl.mpak.orbada.plugins.providers.PerspectiveProvider;
import pl.mpak.orbada.plugins.queue.PluginMessage;
import pl.mpak.orbada.postgresql.OrbadaPostgreSQLPlugin;
import pl.mpak.orbada.postgresql.cm.PgWizardAction;
import pl.mpak.orbada.postgresql.gui.wizards.CreateIndexWizard;
import pl.mpak.orbada.postgresql.gui.wizards.CreateRoutineWizard;
import pl.mpak.orbada.postgresql.gui.wizards.CreateSchemaWizard;
import pl.mpak.orbada.postgresql.gui.wizards.CreateTableWizard;
import pl.mpak.orbada.postgresql.gui.wizards.CreateTriggerWizard;
import pl.mpak.orbada.postgresql.gui.wizards.CreateTypeWizard;
import pl.mpak.orbada.postgresql.gui.wizards.CreateViewWizard;
import pl.mpak.orbada.postgresql.gui.wizards.SequenceWizard;
import pl.mpak.orbada.postgresql.gui.wizards.SetParameterWizard;
import pl.mpak.orbada.universal.gui.wizards.SqlCodeWizardPanel;
import pl.mpak.sky.gui.swing.comp.ToolButton;
import pl.mpak.usedb.core.Database;
import pl.mpak.util.id.VersionID;
import pl.mpak.util.StringManager;
import pl.mpak.util.StringManagerFactory;

/**
 *
 * @author akaluza
 */
public class PostgreSQLPerspectiveProvider extends PerspectiveProvider {
  
  private final StringManager stringManager = StringManagerFactory.getStringManager(OrbadaPostgreSQLPlugin.class);

  @Override
  public boolean isForDatabase(Database database) {
    if (database == null) {
      return false;
    }
    return OrbadaPostgreSQLPlugin.driverType.equals(database.getDriverType());
  }

  @Override
  public String getDescription() {
    return stringManager.getString("PostgreSQLPerspectiveProvider-description");
  }

  @Override
  public String getGroupName() {
    return OrbadaPostgreSQLPlugin.driverType;
  }
  
  @Override
  public void initialize() {
//    final AttachDatabaseAction attachDatabaseAction = new AttachDatabaseAction(accesibilities.getDatabase());
//    final DatabaseVacuumAction databaseVacuumAction = new DatabaseVacuumAction(accesibilities.getDatabase());

    final JMenu postgresql = new JMenu(OrbadaPostgreSQLPlugin.driverType);
    final JMenu wizards = new JMenu(stringManager.getString("wizards"));
    final JToolBar toolBar = new JToolBar(OrbadaPostgreSQLPlugin.driverType);
    final Database database = accesibilities.getDatabase();
    java.awt.EventQueue.invokeLater(new Runnable() {
      @Override
      public void run() {
        // ORBADA 1.3.2: kreatory i parametry sesji jak w menu Oracle
        wizards.add(create(database, "wizard-create-table", "table.gif", new PgWizardAction.Factory() {
          public SqlCodeWizardPanel create(Database db, PgWizardAction.Context c) {
            return new CreateTableWizard(db, c.schema);
          }
        }));
        wizards.add(create(database, "wizard-create-view", "view.gif", new PgWizardAction.Factory() {
          public SqlCodeWizardPanel create(Database db, PgWizardAction.Context c) {
            return new CreateViewWizard(db, c.schema);
          }
        }));
        wizards.add(create(database, "wizard-create-index", "create_index16.gif", new PgWizardAction.Factory() {
          public SqlCodeWizardPanel create(Database db, PgWizardAction.Context c) {
            return new CreateIndexWizard(db, c.schema, null);
          }
        }));
        wizards.add(create(database, "wizard-create-trigger", "trigger.gif", new PgWizardAction.Factory() {
          public SqlCodeWizardPanel create(Database db, PgWizardAction.Context c) {
            return new CreateTriggerWizard(db, c.schema, null);
          }
        }));
        wizards.addSeparator();
        wizards.add(create(database, "wizard-create-sequence", "sequence.gif", new PgWizardAction.Factory() {
          public SqlCodeWizardPanel create(Database db, PgWizardAction.Context c) {
            return new SequenceWizard(db, c.schema, null);
          }
        }));
        String[] typeKeys = {"wizard-create-composite-type", "wizard-create-enum-type", "wizard-create-domain", "wizard-create-range-type"};
        for (int i = 0; i < typeKeys.length; i++) {
          final int kind = i;
          wizards.add(create(database, typeKeys[i], i == CreateTypeWizard.DOMAIN ? "domain.gif" : "type.gif", new PgWizardAction.Factory() {
            public SqlCodeWizardPanel create(Database db, PgWizardAction.Context c) {
              return new CreateTypeWizard(db, c.schema, kind);
            }
          }));
        }
        wizards.addSeparator();
        wizards.add(create(database, "wizard-create-function", "function.gif", routine(CreateRoutineWizard.FUNCTION)));
        if (isVersion11(database)) {
          wizards.add(create(database, "wizard-create-procedure", "procedure.gif", routine(CreateRoutineWizard.PROCEDURE)));
        }
        wizards.add(create(database, "wizard-create-trigger-function", "trigger-function.gif", routine(CreateRoutineWizard.TRIGGER)));
        wizards.addSeparator();
        wizards.add(create(database, "wizard-create-schema", "new_schema.gif", new PgWizardAction.Factory() {
          public SqlCodeWizardPanel create(Database db, PgWizardAction.Context c) {
            return new CreateSchemaWizard(db);
          }
        }));

        postgresql.add(wizards);
        postgresql.addSeparator();
        postgresql.add(parameter(database, "SearchPathWizard-title", "users16.gif", "search_path", true));
        postgresql.add(parameter(database, "IsolationLevelWizard-title", null, "default_transaction_isolation", false,
          "read committed", "repeatable read", "serializable", "read uncommitted"));
        postgresql.add(parameter(database, "StatementTimeoutWizard-title", null, "statement_timeout", true, "0", "30s", "5min", "1h"));
        postgresql.add(parameter(database, "LockTimeoutWizard-title", null, "lock_timeout", true, "0", "5s", "30s", "5min"));
        postgresql.add(parameter(database, "IdleInTransactionTimeoutWizard-title", null, "idle_in_transaction_session_timeout", true, "0", "10min", "1h"));
        postgresql.add(parameter(database, "WorkMemWizard-title", null, "work_mem", true, "4MB", "64MB", "256MB", "1GB"));
        postgresql.add(parameter(database, "ClientMinMessagesWizard-title", null, "client_min_messages", false,
          "debug5", "debug4", "debug3", "debug2", "debug1", "log", "notice", "warning", "error"));
        postgresql.add(parameter(database, "TimeZoneWizard-title", null, "TimeZone", true, "UTC", "Europe/Warsaw", "localtime"));
        postgresql.add(parameter(database, "PlpgsqlWarningsWizard-title", null, "plpgsql.extra_warnings", true,
          "none", "all", "shadowed_variables", "strict_multi_assignment", "too_many_rows"));
        postgresql.add(parameter(database, "PlpgsqlErrorsWizard-title", null, "plpgsql.extra_errors", true,
          "none", "all", "shadowed_variables", "strict_multi_assignment", "too_many_rows"));

        toolBar.add(new ToolButton(create(database, "wizard-create-table", "table.gif", new PgWizardAction.Factory() {
          public SqlCodeWizardPanel create(Database db, PgWizardAction.Context c) {
            return new CreateTableWizard(db, c.schema);
          }
        })));
        toolBar.add(new ToolButton(create(database, "wizard-create-sequence", "sequence.gif", new PgWizardAction.Factory() {
          public SqlCodeWizardPanel create(Database db, PgWizardAction.Context c) {
            return new SequenceWizard(db, c.schema, null);
          }
        })));
        toolBar.add(new ToolButton(create(database, "wizard-create-function", "function.gif", routine(CreateRoutineWizard.FUNCTION))));
        if (isVersion11(database)) {
          toolBar.add(new ToolButton(create(database, "wizard-create-procedure", "procedure.gif", routine(CreateRoutineWizard.PROCEDURE))));
        }
        toolBar.add(new ToolButton(create(database, "wizard-create-trigger", "trigger.gif", new PgWizardAction.Factory() {
          public SqlCodeWizardPanel create(Database db, PgWizardAction.Context c) {
            return new CreateTriggerWizard(db, c.schema, null);
          }
        })));
        toolBar.addSeparator();
        toolBar.add(new ToolButton(parameter(database, "SearchPathWizard-title", "users16.gif", "search_path", true)));
      }
    });
    accesibilities.addMenu(postgresql);
    accesibilities.addToolBar(toolBar);
  }

  private static PgWizardAction create(Database database, String key, String icon, PgWizardAction.Factory factory) {
    PgWizardAction action = new PgWizardAction(database, key, icon, factory);
    action.setTooltip(action.getText());
    return action;
  }

  private static PgWizardAction.Factory routine(final int kind) {
    return new PgWizardAction.Factory() {
      public SqlCodeWizardPanel create(Database db, PgWizardAction.Context c) {
        return new CreateRoutineWizard(db, c.schema, kind);
      }
    };
  }

  private static PgWizardAction parameter(Database database, final String titleKey, String icon, final String parameter, final boolean editable, final String... values) {
    return create(database, titleKey, icon, new PgWizardAction.Factory() {
      public SqlCodeWizardPanel create(Database db, PgWizardAction.Context c) {
        return new SetParameterWizard(db, titleKey, parameter, editable, values);
      }
    });
  }

  private static boolean isVersion11(Database database) {
    String version = PostgreSQLDbInfoProvider.instance.getVersion(database);
    return version != null && new VersionID(version).compare(11, 0) >= 0;
  }

  @Override
  public void processMessage(PluginMessage message) {
    if (getAccesibilities() != null &&
        getAccesibilities().getDatabase() != null &&
        getAccesibilities().getDatabase().getUniqueID().equals(message.getDestinationId()) &&
        message.isMessageId(Consts.globalMessageFreezeObject)) {
//      final IDatabaseObject object = (IDatabaseObject)message.getObject();
      java.awt.EventQueue.invokeLater(new Runnable() {
        @Override
        public void run() {
//          FreezeViewService service = new FreezeFactory().createInstance(object.getObjectType(), StringUtil.nvl(object.getSchemaName(), object.getCatalogName()), object.getObjectName());
//          IPerspectiveAccesibilities accessibilities = application.getActivePerspective();
//          if (service != null && accessibilities != null) {
//            accessibilities.createView(service);
//          }
        }
      });
      message.setServed(true);
    }
  }
  
}
