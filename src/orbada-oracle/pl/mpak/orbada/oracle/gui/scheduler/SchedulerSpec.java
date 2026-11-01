package pl.mpak.orbada.oracle.gui.scheduler;

/**
 * ORBADA Fuerteventura (F5): opis jednej zakladki schedulera - widok slownika, kolumny listy,
 * zakladki szczegolow i akcje DBMS_SCHEDULER. Same dane, bez Swinga.
 */
class SchedulerSpec {

  /** Kolumna listy: nazwa kolumny w zapytaniu, klucz tekstu naglowka, szerokosc */
  static class Column {
    final String name;
    final String titleKey;
    final int width;
    Column(String name, String titleKey, int width) {
      this.name = name;
      this.titleKey = titleKey;
      this.width = width;
    }
  }

  /** Zakladka szczegolow: zapytanie z parametrami :OWNER i :NAME (kolumny tworzone automatycznie) */
  static class Detail {
    final String titleKey;
    final String sql;
    Detail(String titleKey, String sql) {
      this.titleKey = titleKey;
      this.sql = sql;
    }
  }

  /** Akcja na zaznaczonym obiekcie: blok PL/SQL z parametrem :NAME (owner.nazwa) */
  static class Action {
    final String titleKey;
    final String plsql;
    final boolean confirm;
    Action(String titleKey, String plsql, boolean confirm) {
      this.titleKey = titleKey;
      this.plsql = plsql;
      this.confirm = confirm;
    }
  }

  final String id;
  final String titleKey;
  final String view;
  final String keyColumn;
  /** false dla obiektow SYS (okna, grupy, klasy jobow) - lista bez filtra schematu */
  final boolean perSchema;
  final String orderBy;
  final Column[] columns;
  final Detail[] details;
  final Action[] actions;

  SchedulerSpec(String id, String titleKey, String view, String keyColumn, boolean perSchema, String orderBy,
                Column[] columns, Detail[] details, Action[] actions) {
    this.id = id;
    this.titleKey = titleKey;
    this.view = view;
    this.keyColumn = keyColumn;
    this.perSchema = perSchema;
    this.orderBy = orderBy;
    this.columns = columns;
    this.details = details;
    this.actions = actions;
  }

  String listSql() {
    StringBuilder sb = new StringBuilder("select owner");
    for (Column c : columns) {
      sb.append(", ").append(c.name);
    }
    sb.append("\n  from ").append(view);
    if (perSchema) {
      sb.append("\n where owner = :SCHEMA_NAME");
    }
    sb.append("\n order by ").append(orderBy != null ? orderBy : "owner, " + keyColumn);
    return sb.toString();
  }

  String detailsSql() {
    return "select * from " + view + " where owner = :OWNER and " + keyColumn + " = :NAME";
  }

}
