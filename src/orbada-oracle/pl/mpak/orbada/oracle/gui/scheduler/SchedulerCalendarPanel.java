package pl.mpak.orbada.oracle.gui.scheduler;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.sql.CallableStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TreeSet;
import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import pl.mpak.usedb.core.Database;

/**
 * ORBADA Fuerteventura (F5): budowanie wyrazenia kalendarza DBMS_SCHEDULER (FREQ=...;BYDAY=...) z pol
 * formularza, daty poczatku i konca oraz podglad najblizszych uruchomien liczony przez sama baze
 * (DBMS_SCHEDULER.EVALUATE_CALENDAR_STRING) - ten sam wynik, ktory pozniej da scheduler.
 */
class SchedulerCalendarPanel extends JPanel {

  /** Indeks w liscie czestotliwosci; ostatnia pozycja - wlasne wyrazenie */
  private final static String[] freqs = {"MINUTELY", "HOURLY", "DAILY", "WEEKLY", "MONTHLY", "YEARLY", null};
  private final static int HOURLY = 1;
  private final static int DAILY = 2;
  private final static int MONTHLY = 4;
  private final static int YEARLY = 5;
  private final static int CUSTOM = 6;
  private final static String[] days = {"MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"};
  private final static int PREVIEW_COUNT = 10;

  private final Database database;
  private final JComboBox<String> comboFreq = new JComboBox<String>();
  private final JSpinner spinInterval = new JSpinner(new SpinnerNumberModel(1, 1, 999, 1));
  private final JLabel labelUnit = new JLabel();
  private final JCheckBox[] checkDays = new JCheckBox[7];
  private final JCheckBox[] checkMonths = new JCheckBox[12];
  private final JLabel labelTimes = new JLabel();
  private final JTextField textTimes = new JTextField();
  private final JTextField textMonthDays = new JTextField();
  private final JTextField textExpression = new JTextField();
  private final JTextField textStart = new JTextField();
  private final JTextField textEnd = new JTextField();
  private final JTextArea preview = new JTextArea(PREVIEW_COUNT, 24);
  private final JLabel previewError = new JLabel(" ");
  private final javax.swing.Timer previewTimer;
  private boolean updating = false;
  /** Wyrazenie, start i koniec, dla ktorych pokazano podglad - bez ponownego pytania bazy */
  private String previewKey;
  /** Numer ostatniego zadania podgladu - starsze wyniki sa pomijane */
  private int previewSeq = 0;
  private boolean disposed = false;

  SchedulerCalendarPanel(Database database) {
    super(new BorderLayout());
    this.database = database;
    for (int i = 0; i < freqs.length; i++) {
      comboFreq.addItem(SchedulerWizard.text("SchedWiz-cal-freq-" + (freqs[i] == null ? "custom" : freqs[i].toLowerCase())));
    }
    comboFreq.setSelectedIndex(DAILY);
    previewTimer = new javax.swing.Timer(500, e -> updatePreview());
    previewTimer.setRepeats(false);

    JPanel every = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
    every.add(new JLabel(SchedulerWizard.text("SchedWiz-cal-every")));
    every.add(spinInterval);
    every.add(labelUnit);
    JPanel dayPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
    for (int i = 0; i < 7; i++) {
      checkDays[i] = new JCheckBox(SchedulerWizard.text("SchedWiz-cal-day-" + days[i].toLowerCase()));
      dayPanel.add(checkDays[i]);
    }
    JPanel monthPanel = new JPanel(new java.awt.GridLayout(2, 6, 2, 0));
    for (int i = 0; i < 12; i++) {
      checkMonths[i] = new JCheckBox(SchedulerWizard.text("SchedWiz-cal-month-" + (i + 1)));
      monthPanel.add(checkMonths[i]);
    }
    textStart.setColumns(14);
    textEnd.setColumns(14);
    JPanel startPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    startPanel.add(textStart);
    startPanel.add(new JLabel(SchedulerWizard.text("SchedWiz-cal-start-hint")));
    JPanel endPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    endPanel.add(textEnd);
    endPanel.add(new JLabel(SchedulerWizard.text("SchedWiz-cal-end-hint")));

    SchedulerWizard.Form form = new SchedulerWizard.Form();
    form.row("SchedWiz-cal-freq", comboFreq)
      .row("SchedWiz-cal-interval", every)
      .row("SchedWiz-cal-days", dayPanel)
      .row("SchedWiz-cal-monthdays", textMonthDays)
      .row("SchedWiz-cal-months", monthPanel);
    form.row(labelTimes, textTimes)
      .row("SchedWiz-cal-expression", textExpression)
      .row("SchedWiz-cal-start", startPanel)
      .row("SchedWiz-cal-end", endPanel);
    form.full(new JLabel("<html>" + SchedulerWizard.text("SchedWiz-cal-tz", SchedulerWizard.timeZone()) + "</html>"), false).end();
    textExpression.setToolTipText(SchedulerWizard.text("SchedWiz-cal-expression-tip"));
    textMonthDays.setToolTipText(SchedulerWizard.text("SchedWiz-cal-monthdays-tip"));

    preview.setEditable(false);
    preview.setFont(new Font(Font.MONOSPACED, Font.PLAIN, preview.getFont().getSize()));
    preview.setBackground(new Color(0xF7F7F2));
    previewError.setForeground(new Color(0xB00020));
    JPanel previewPanel = new JPanel(new BorderLayout());
    previewPanel.setBorder(BorderFactory.createCompoundBorder(
      BorderFactory.createTitledBorder(SchedulerWizard.text("SchedWiz-cal-preview", PREVIEW_COUNT)),
      BorderFactory.createEmptyBorder(2, 4, 2, 4)));
    previewPanel.add(new JScrollPane(preview), BorderLayout.CENTER);
    previewPanel.add(previewError, BorderLayout.SOUTH);

    add(form, BorderLayout.CENTER);
    add(previewPanel, BorderLayout.EAST);

    comboFreq.addActionListener(e -> changed());
    spinInterval.addChangeListener(e -> changed());
    for (JCheckBox c : checkDays) {
      c.addActionListener(e -> changed());
    }
    for (JCheckBox c : checkMonths) {
      c.addActionListener(e -> changed());
    }
    DocumentListener dl = new DocumentListener() {
      public void insertUpdate(DocumentEvent e) {
        changed();
      }
      public void removeUpdate(DocumentEvent e) {
        changed();
      }
      public void changedUpdate(DocumentEvent e) {
        changed();
      }
    };
    textTimes.getDocument().addDocumentListener(dl);
    textMonthDays.getDocument().addDocumentListener(dl);
    textExpression.getDocument().addDocumentListener(dl);
    textStart.getDocument().addDocumentListener(dl);
    textEnd.getDocument().addDocumentListener(dl);
    changed();
  }

  /** Pola dostepne dla wybranej czestotliwosci i wyrazenie zbudowane od nowa */
  private void changed() {
    if (updating) {
      return;
    }
    updating = true;
    try {
      int f = comboFreq.getSelectedIndex();
      boolean custom = f == CUSTOM;
      spinInterval.setEnabled(!custom);
      labelUnit.setText(custom ? "" : SchedulerWizard.text("SchedWiz-cal-unit-" + freqs[f].toLowerCase()));
      for (JCheckBox c : checkDays) {
        c.setEnabled(!custom);
      }
      for (JCheckBox c : checkMonths) {
        c.setEnabled(!custom);
      }
      textMonthDays.setEnabled(f == MONTHLY || f == YEARLY);
      textTimes.setEnabled(!custom && f >= HOURLY);
      labelTimes.setText(SchedulerWizard.text(f == HOURLY ? "SchedWiz-cal-minutes" : "SchedWiz-cal-times"));
      textTimes.setToolTipText(SchedulerWizard.text(f == HOURLY ? "SchedWiz-cal-minutes-tip" : "SchedWiz-cal-times-tip"));
      textExpression.setEditable(custom);
      if (!custom) {
        String expr;
        try {
          expr = buildExpression();
        } catch (IllegalArgumentException ex) {
          expr = "";
        }
        textExpression.setText(expr);
      }
    } finally {
      updating = false;
    }
    previewTimer.restart();
  }

  /** Wyrazenie z pol formularza; IllegalArgumentException z komunikatem przy blednych wartosciach */
  private String buildExpression() {
    int f = comboFreq.getSelectedIndex();
    StringBuilder sb = new StringBuilder("FREQ=").append(freqs[f]);
    int interval = ((Number)spinInterval.getValue()).intValue();
    if (interval > 1) {
      sb.append(";INTERVAL=").append(interval);
    }
    StringBuilder months = new StringBuilder();
    for (int i = 0; i < 12; i++) {
      if (checkMonths[i].isSelected()) {
        months.append(months.length() > 0 ? "," : "").append(i + 1);
      }
    }
    if (months.length() > 0) {
      sb.append(";BYMONTH=").append(months);
    }
    if (textMonthDays.isEnabled() && textMonthDays.getText().trim().length() > 0) {
      sb.append(";BYMONTHDAY=").append(monthDays(textMonthDays.getText()));
    }
    StringBuilder byDay = new StringBuilder();
    for (int i = 0; i < 7; i++) {
      if (checkDays[i].isSelected()) {
        byDay.append(byDay.length() > 0 ? "," : "").append(days[i]);
      }
    }
    if (byDay.length() > 0) {
      sb.append(";BYDAY=").append(byDay);
    }
    String t = textTimes.getText().trim();
    if (f == HOURLY && t.length() > 0) {
      sb.append(";BYMINUTE=").append(numbers(t, 0, 59, "SchedWiz-cal-err-minutes")).append(";BYSECOND=0");
    } else if (f > HOURLY && t.length() > 0) {
      sb.append(times(t)).append(";BYSECOND=0");
    } else if (f == 0) {
      sb.append(";BYSECOND=0");
    }
    return sb.toString();
  }

  /** "8:00, 16:30" -> ;BYHOUR=8,16;BYMINUTE=0,30 - tylko gdy kazda godzina ma te same minuty (iloczyn list) */
  private static String times(String text) {
    TreeSet<Integer> hours = new TreeSet<Integer>();
    TreeSet<Integer> minutes = new TreeSet<Integer>();
    TreeSet<String> pairs = new TreeSet<String>();
    for (String s : text.split("[,; ]+")) {
      if (s.length() == 0) {
        continue;
      }
      String[] hm = s.split(":");
      int h;
      int m;
      try {
        h = Integer.parseInt(hm[0]);
        m = hm.length > 1 ? Integer.parseInt(hm[1]) : 0;
      } catch (NumberFormatException ex) {
        throw new IllegalArgumentException(SchedulerWizard.text("SchedWiz-cal-err-times", s));
      }
      if (hm.length > 2 || h < 0 || h > 23 || m < 0 || m > 59) {
        throw new IllegalArgumentException(SchedulerWizard.text("SchedWiz-cal-err-times", s));
      }
      hours.add(h);
      minutes.add(m);
      pairs.add(h + ":" + m);
    }
    if (hours.isEmpty()) {
      return "";
    }
    if (hours.size() * minutes.size() != pairs.size()) {
      throw new IllegalArgumentException(SchedulerWizard.text("SchedWiz-cal-err-times-mixed"));
    }
    return ";BYHOUR=" + join(hours) + ";BYMINUTE=" + join(minutes);
  }

  private static String monthDays(String text) {
    TreeSet<Integer> set = new TreeSet<Integer>();
    for (String s : text.trim().split("[,; ]+")) {
      int d;
      try {
        d = Integer.parseInt(s);
      } catch (NumberFormatException ex) {
        throw new IllegalArgumentException(SchedulerWizard.text("SchedWiz-cal-err-monthdays", s));
      }
      if (d == 0 || d < -31 || d > 31) {
        throw new IllegalArgumentException(SchedulerWizard.text("SchedWiz-cal-err-monthdays", s));
      }
      set.add(d);
    }
    return join(set);
  }

  private static String numbers(String text, int min, int max, String errorKey) {
    TreeSet<Integer> set = new TreeSet<Integer>();
    for (String s : text.split("[,; ]+")) {
      int n;
      try {
        n = Integer.parseInt(s);
      } catch (NumberFormatException ex) {
        throw new IllegalArgumentException(SchedulerWizard.text(errorKey, s));
      }
      if (n < min || n > max) {
        throw new IllegalArgumentException(SchedulerWizard.text(errorKey, s));
      }
      set.add(n);
    }
    return join(set);
  }

  private static String join(Iterable<Integer> values) {
    StringBuilder sb = new StringBuilder();
    for (Integer v : values) {
      sb.append(sb.length() > 0 ? "," : "").append(v);
    }
    return sb.toString();
  }

  /** @return komunikat bledu albo null */
  String validateInput() {
    if (comboFreq.getSelectedIndex() != CUSTOM) {
      try {
        buildExpression();
      } catch (IllegalArgumentException ex) {
        return ex.getMessage();
      }
    }
    if (getExpression().length() == 0) {
      return SchedulerWizard.text("SchedWiz-err-required", SchedulerWizard.text("SchedWiz-cal-expression"));
    }
    String msg = checkDate(textStart, "SchedWiz-cal-start");
    if (msg == null) {
      msg = checkDate(textEnd, "SchedWiz-cal-end");
    }
    return msg;
  }

  /** @return komunikat bledu daty "RRRR-MM-DD GG:MM" albo null (puste pole jest poprawne) */
  static String checkDate(JTextField field, String labelKey) {
    String s = field.getText().trim();
    if (s.length() == 0) {
      return null;
    }
    SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm");
    df.setLenient(false);
    try {
      if (!s.matches("\\d{4}-\\d{2}-\\d{2} \\d{1,2}:\\d{2}")) {
        throw new ParseException(s, 0);
      }
      df.parse(s);
      return null;
    } catch (ParseException ex) {
      return SchedulerWizard.text("SchedWiz-err-date", SchedulerWizard.text(labelKey));
    }
  }

  String getExpression() {
    return textExpression.getText().trim();
  }

  /**
   * Edycja: kalendarz ze slownika. Wyrazenie rozkladane na pola, gdy pola zbuduja dokladnie to samo
   * (np. zapisane przez kreator); inne (BYSETPOS, BYDAY=-1FRI, wyrazenie PL/SQL itp.) - jako wlasne wyrazenie.
   */
  void setValues(String expression, String start, String end) {
    expression = expression == null ? "" : expression.trim();
    updating = true;
    try {
      textStart.setText(start == null ? "" : start);
      textEnd.setText(end == null ? "" : end);
      boolean parsed = parseExpression(expression);
      if (parsed) {
        String built;
        try {
          built = buildExpression();
        } catch (IllegalArgumentException ex) {
          built = null;
        }
        parsed = built != null && normalize(built).equals(normalize(expression));
      }
      if (!parsed) {
        comboFreq.setSelectedIndex(CUSTOM);
        textExpression.setText(expression);
      }
    } finally {
      updating = false;
    }
    changed();
  }

  private static String normalize(String expression) {
    return expression.replaceAll("\\s+", "").replaceAll(";+$", "").toUpperCase(Locale.ROOT);
  }

  /** Pola formularza z wyrazenia; false - wyrazenie ma elementy, ktorych formularz nie zna */
  private boolean parseExpression(String expression) {
    int freq = -1;
    int interval = 1;
    String byMonth = null;
    String byMonthDay = null;
    String byDay = null;
    String byHour = null;
    String byMinute = null;
    for (String part : expression.split(";")) {
      part = part.trim();
      if (part.length() == 0) {
        continue;
      }
      int eq = part.indexOf('=');
      if (eq < 0) {
        return false;
      }
      String key = part.substring(0, eq).trim().toUpperCase(Locale.ROOT);
      String value = part.substring(eq + 1).trim().toUpperCase(Locale.ROOT);
      if ("FREQ".equals(key)) {
        for (int i = 0; i < CUSTOM; i++) {
          if (freqs[i].equals(value)) {
            freq = i;
            break;
          }
        }
      } else if ("INTERVAL".equals(key)) {
        try {
          interval = Integer.parseInt(value);
        } catch (NumberFormatException ex) {
          return false;
        }
      } else if ("BYMONTH".equals(key)) {
        byMonth = value;
      } else if ("BYMONTHDAY".equals(key)) {
        byMonthDay = value;
      } else if ("BYDAY".equals(key)) {
        byDay = value;
      } else if ("BYHOUR".equals(key)) {
        byHour = value;
      } else if ("BYMINUTE".equals(key)) {
        byMinute = value;
      } else if (!("BYSECOND".equals(key) && "0".equals(value))) {
        return false;
      }
    }
    if (freq < 0 || interval < 1 || interval > 999) {
      return false;
    }
    comboFreq.setSelectedIndex(freq);
    // changed() jest wstrzymane - dostepnosc pola, od ktorej zalezy buildExpression(), ustawiana tutaj
    textMonthDays.setEnabled(freq == MONTHLY || freq == YEARLY);
    spinInterval.setValue(interval);
    for (int i = 0; i < 12; i++) {
      checkMonths[i].setSelected(false);
    }
    for (int i = 0; i < 7; i++) {
      checkDays[i].setSelected(false);
    }
    try {
      if (byMonth != null) {
        for (String m : byMonth.split(",")) {
          checkMonths[Integer.parseInt(m.trim()) - 1].setSelected(true);
        }
      }
    } catch (RuntimeException ex) {
      return false;
    }
    if (byDay != null) {
      for (String d : byDay.split(",")) {
        int i = java.util.Arrays.asList(days).indexOf(d.trim());
        if (i < 0) {
          return false;
        }
        checkDays[i].setSelected(true);
      }
    }
    textMonthDays.setText(byMonthDay == null ? "" : byMonthDay.replace(",", ", "));
    if (freq == HOURLY) {
      if (byHour != null) {
        return false;
      }
      textTimes.setText(byMinute == null ? "" : byMinute.replace(",", ", "));
    } else if (freq > HOURLY) {
      if (byHour == null && byMinute == null) {
        textTimes.setText("");
      } else if (byHour == null || byMinute == null) {
        return false;
      } else {
        // iloczyn godzin i minut - tak jak buduje go times()
        StringBuilder sb = new StringBuilder();
        for (String h : byHour.split(",")) {
          for (String m : byMinute.split(",")) {
            try {
              sb.append(sb.length() > 0 ? ", " : "").append(String.format("%d:%02d", Integer.parseInt(h.trim()), Integer.parseInt(m.trim())));
            } catch (NumberFormatException ex) {
              return false;
            }
          }
        }
        textTimes.setText(sb.toString());
      }
    } else if (byHour != null || byMinute != null) {
      return false;
    }
    return true;
  }

  /** Poczatek "RRRR-MM-DD GG:MM" albo pusty (teraz) */
  String getStart() {
    return textStart.getText().trim();
  }

  /** Koniec "RRRR-MM-DD GG:MM" albo pusty (bez konca) */
  String getEnd() {
    return textEnd.getText().trim();
  }

  void dispose() {
    disposed = true;
    previewTimer.stop();
  }

  /** Najblizsze uruchomienia wedlug bazy; bledy wyrazenia (ORA-27412 itp.) pokazywane pod lista */
  private void updatePreview() {
    if (!isShowing()) {
      return;
    }
    String msg = validateInput();
    String expr = getExpression();
    String key = expr + "|" + getStart() + "|" + getEnd();
    if (msg != null) {
      previewKey = null;
      preview.setText("");
      showError(msg);
      return;
    }
    if (key.equals(previewKey)) {
      return;
    }
    previewKey = key;
    String sql = "declare\n"
      + "  st varchar2(80) := ?; en varchar2(80) := ?;\n"
      + "  s timestamp with time zone; e timestamp with time zone; aft timestamp with time zone; nx timestamp with time zone;\n"
      + "  r varchar2(4000);\n"
      + "begin\n"
      + "  if st is null then s := systimestamp at time zone ?; aft := s;\n"
      + "  else s := to_timestamp_tz(st, 'YYYY-MM-DD HH24:MI TZR'); aft := greatest(s - interval '1' second, systimestamp); end if;\n"
      + "  if en is not null then e := to_timestamp_tz(en, 'YYYY-MM-DD HH24:MI TZR'); end if;\n"
      + "  for i in 1 .. ? loop\n"
      + "    dbms_scheduler.evaluate_calendar_string(?, s, aft, nx);\n"
      + "    exit when nx is null or nx > e;\n"
      + "    r := r || to_char(nx, 'YYYY-MM-DD HH24:MI:SS  Dy', 'NLS_DATE_LANGUAGE=' || ?) || chr(10);\n"
      + "    aft := nx;\n"
      + "  end loop;\n"
      + "  ? := r;\n"
      + "end;";
    String tz = SchedulerWizard.timeZone();
    String start = getStart().length() == 0 ? null : getStart() + " " + tz;
    String end = getEnd().length() == 0 ? null : getEnd() + " " + tz;
    String language = "pl".equals(Locale.getDefault().getLanguage()) ? "POLISH" : "AMERICAN";
    final int seq = ++previewSeq;
    // baza w osobnym watku - wolne polaczenie nie blokuje okna; wynik tylko dla ostatniego zadania
    Thread t = new Thread(() -> {
      String result;
      String error = null;
      try (CallableStatement cs = database.getConnection().prepareCall(sql)) {
        cs.setString(1, start);
        cs.setString(2, end);
        cs.setString(3, tz);
        cs.setInt(4, PREVIEW_COUNT);
        cs.setString(5, expr);
        cs.setString(6, language);
        cs.registerOutParameter(7, Types.VARCHAR);
        cs.execute();
        result = cs.getString(7);
      } catch (SQLException ex) {
        result = null;
        error = String.valueOf(ex.getMessage()).split("\n")[0];
      }
      final String r = result;
      final String err = error;
      SwingUtilities.invokeLater(() -> {
        if (seq != previewSeq || disposed) {
          return;
        }
        if (err != null) {
          preview.setText("");
          showError(err);
        } else {
          preview.setText(r == null ? SchedulerWizard.text("SchedWiz-cal-no-runs") : r);
          preview.setCaretPosition(0);
          previewError.setText(" ");
        }
      });
    }, "orbada-scheduler-preview");
    t.setDaemon(true);
    t.start();
  }

  /** Komunikat pod podgladem zawijany do szerokosci listy (dlugie ORA- nie poszerzaja panelu) */
  private void showError(String msg) {
    previewError.setText("<html><body style='width:" + Math.max(120, preview.getPreferredSize().width - 10) + "px'>"
      + msg.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;") + "</body></html>");
  }

  /** Strona z kalendarzem pokazana - podglad liczony od nowa (czas biegnie) */
  void shown() {
    previewKey = null;
    previewTimer.restart();
  }

}
