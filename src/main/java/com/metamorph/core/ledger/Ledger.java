package com.metamorph.core.ledger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Budget accounting: R = 4k rollouts and P = 3k improvement stages.
 *
 * <p>Base executions count toward each mode. Retries are separate invocation counts; selection and
 * reflection are recorded outside this ledger.
 */
public final class Ledger {
  public record Entry(String kind, String label, double usd, boolean cached, int attempts) {
    public Entry(String kind, String label, double usd, boolean cached) {
      this(kind, label, usd, cached, 1);
    }
  }

  private final String mode;
  private final String taskId;
  private final int k;
  private final List<Entry> entries = new ArrayList<>();

  public Ledger(String mode, String taskId, int k) {
    this.mode = mode;
    this.taskId = taskId;
    this.k = k;
  }

  public static int expectedRollouts(int k) {
    return 4 * k;
  }

  public static int expectedSessions(int k) {
    return 3 * k;
  }

  public void rollout(String label, double usd, boolean cached) {
    entries.add(new Entry("rollout", label, usd, cached));
  }

  public void rollout(String label) {
    rollout(label, 0.0, false);
  }

  public void session(String label, double usd, boolean cached, int attempts) {
    entries.add(new Entry("session", label, usd, cached, attempts));
  }

  public void session(String label) {
    session(label, 0.0, false, 1);
  }

  public int R() {
    return (int) entries.stream().filter(e -> "rollout".equals(e.kind())).count();
  }

  public int P() {
    return (int) entries.stream().filter(e -> "session".equals(e.kind())).count();
  }

  public double usd() {
    return round6(entries.stream().mapToDouble(Entry::usd).sum());
  }

  public double rolloutUsd() {
    return round6(
        entries.stream().filter(e -> "rollout".equals(e.kind())).mapToDouble(Entry::usd).sum());
  }

  public double sessionUsd() {
    return round6(
        entries.stream().filter(e -> "session".equals(e.kind())).mapToDouble(Entry::usd).sum());
  }

  public List<String> labels(String kind) {
    return entries.stream().filter(e -> kind.equals(e.kind())).map(Entry::label).toList();
  }

  public List<Entry> entries() {
    return List.copyOf(entries);
  }

  public String mode() {
    return mode;
  }

  public String taskId() {
    return taskId;
  }

  public int k() {
    return k;
  }

  /**
   * Raise unless the recorded entries show exactly R=4k and P=3k with no duplicate label.
   * Duplicates are checked before the counts, because a duplicate can satisfy them.
   */
  public void assertIdentity() {
    int wantR = expectedRollouts(k);
    int wantP = expectedSessions(k);
    List<String> rl = labels("rollout");
    List<String> sl = labels("session");
    TreeSet<String> dupes = new TreeSet<>();
    for (String label : rl) {
      if (rl.stream().filter(label::equals).count() > 1) {
        dupes.add(label);
      }
    }
    for (String label : sl) {
      if (sl.stream().filter(label::equals).count() > 1) {
        dupes.add(label);
      }
    }
    if (dupes.isEmpty() && R() == wantR && P() == wantP) {
      return;
    }
    StringBuilder detail = new StringBuilder();
    detail
        .append(mode)
        .append('-')
        .append(k)
        .append(' ')
        .append(taskId)
        .append(": R=")
        .append(R())
        .append(" (want ")
        .append(wantR)
        .append("), P=")
        .append(P())
        .append(" (want ")
        .append(wantP)
        .append(")\n  rollouts: ")
        .append(rl.isEmpty() ? "(none)" : String.join(", ", rl))
        .append("\n  sessions: ")
        .append(sl.isEmpty() ? "(none)" : String.join(", ", sl));
    if (!dupes.isEmpty()) {
      detail
          .append("\n  DUPLICATE labels: ")
          .append(dupes)
          .append(
              " -- a repeated label means two stages wrote the same run id and one silently"
                  + " overwrote the other, so a candidate the ledger counts was never actually"
                  + " produced");
    }
    throw new BudgetError(detail.toString());
  }

  public Map<String, Object> toMap() {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("mode", mode);
    m.put("task_id", taskId);
    m.put("k", k);
    m.put("R", R());
    m.put("expected_R", expectedRollouts(k));
    m.put("P", P());
    m.put(
        "session_attempts",
        entries.stream()
            .filter(e -> "session".equals(e.kind()))
            .mapToInt(Entry::attempts)
            .sum());
    m.put("expected_P", expectedSessions(k));
    m.put("usd", usd());
    m.put("rollout_usd", rolloutUsd());
    m.put("session_usd", sessionUsd());
    m.put("rollouts", labels("rollout"));
    m.put("sessions", labels("session"));
    return m;
  }

  private static double round6(double v) {
    return Math.round(v * 1_000_000.0) / 1_000_000.0;
  }
}
