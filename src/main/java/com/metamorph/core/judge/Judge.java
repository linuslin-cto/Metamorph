package com.metamorph.core.judge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.metamorph.core.spec.BenchmarkAdapter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;

/**
 * Measurement of the run that selection chose, after it chose it.
 *
 * <p>A judge failure is null/ungraded, never a zero.
 */
public final class Judge {
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final Set<String> DERIVED = Set.of("frac", "graded");

  private Judge() {}

  /** A score needs a numerator and a positive denominator; zero passes is valid. */
  public static boolean hasGrade(Integer nPassed, Integer nCriteria) {
    return nPassed != null && nCriteria != null && nCriteria > 0;
  }

  public static Scored judgeOne(
      BenchmarkAdapter adapter,
      String taskId,
      String mode,
      int k,
      String label,
      String runId,
      Path runDir,
      boolean chosenIsBase,
      String selectedAt) {
    Scored s = new Scored(taskId, mode, k, label, runId, chosenIsBase, selectedAt);
    try {
      Map<String, Object> got = adapter.score(runDir, taskId);
      if (got == null || got.isEmpty()) {
        s.error = "judge returned nothing";
        return s;
      }
      s.raw = new LinkedHashMap<>(got);
      s.nPassed = asInt(got.get("n_passed"));
      s.nCriteria = asInt(got.get("n_criteria"));
      Object ap = got.get("all_pass");
      s.allPass =
          ap != null
              ? Boolean.parseBoolean(String.valueOf(ap))
              : s.graded() && s.nPassed != null && s.nPassed.equals(s.nCriteria);
    } catch (Exception exc) {
      s.error = exc.getClass().getSimpleName() + ": " + exc.getMessage();
    }
    return s;
  }

  /** Read a cached {@code scores.json} payload the same way LAB baseline grading does. */
  public static Map<String, Object> scoreFromCache(Path runDir) throws Exception {
    Path scores = runDir.resolve("scores.json");
    if (!java.nio.file.Files.isRegularFile(scores)) {
      return null;
    }
    @SuppressWarnings("unchecked")
    Map<String, Object> raw =
        new com.fasterxml.jackson.databind.ObjectMapper()
            .readValue(java.nio.file.Files.readString(scores), Map.class);
    return raw;
  }

  public static List<Scored> judgeSelections(
      BenchmarkAdapter adapter, List<Map<String, Object>> selections, int parallel)
      throws Exception {
    List<Scored> out = new ArrayList<>();
    if (selections == null || selections.isEmpty()) {
      return out;
    }
    ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, parallel));
    try {
      List<Future<Scored>> futs = new ArrayList<>();
      for (Map<String, Object> sel : selections) {
        if (!Boolean.TRUE.equals(sel.get("ok"))
            || sel.get("chosen_label") == null
            || sel.get("chosen_run_dir") == null) {
          out.add(
              new Scored(
                  String.valueOf(sel.getOrDefault("task_id", "")),
                  modeOf(sel),
                  ((Number) sel.getOrDefault("k", 0)).intValue(),
                  String.valueOf(sel.getOrDefault("chosen_label", "")),
                  String.valueOf(sel.getOrDefault("chosen_run_id", "")),
                  Boolean.TRUE.equals(sel.get("chosen_is_base")),
                  String.valueOf(sel.getOrDefault("selected_at", "")),
                  "selection did not complete successfully"));
          continue;
        }
        futs.add(
            pool.submit(
                () ->
                    judgeOne(
                        adapter,
                        String.valueOf(sel.get("task_id")),
                        modeOf(sel),
                        ((Number) sel.get("k")).intValue(),
                        String.valueOf(sel.get("chosen_label")),
                        String.valueOf(sel.getOrDefault("chosen_run_id", "")),
                        Path.of(String.valueOf(sel.get("chosen_run_dir"))),
                        Boolean.TRUE.equals(sel.get("chosen_is_base")),
                        String.valueOf(sel.getOrDefault("selected_at", "")))));
      }
      for (Future<Scored> f : futs) {
        out.add(f.get());
      }
    } finally {
      pool.shutdownNow();
    }
    return out;
  }

  private static String modeOf(Map<String, Object> sel) {
    if (sel.containsKey("mode")) {
      return String.valueOf(sel.get("mode"));
    }
    return String.valueOf(sel.getOrDefault("arm", ""));
  }

  private static Integer asInt(Object v) {
    if (v == null) {
      return null;
    }
    if (v instanceof Number n) {
      return n.intValue();
    }
    try {
      return Integer.parseInt(String.valueOf(v));
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /** Every requested mode's selection record from every finished task summary. */
  @SuppressWarnings("unchecked")
  public static List<Map<String, Object>> collectSelections(Path nsDir, List<String> modes)
      throws Exception {
    List<Map<String, Object>> out = new ArrayList<>();
    if (nsDir == null || !Files.isDirectory(nsDir)) {
      return out;
    }
    try (Stream<Path> paths = Files.list(nsDir)) {
      List<Path> summaries =
          paths
              .map(p -> p.resolve("summary.json"))
              .filter(Files::isRegularFile)
              .sorted()
              .toList();
      for (Path summaryPath : summaries) {
        Map<String, Object> summary = JSON.readValue(Files.readString(summaryPath), Map.class);
        Map<String, Object> sels =
            summary.get("selections") instanceof Map<?, ?> m
                ? (Map<String, Object>) m
                : Map.of();
        for (String mode : modes) {
          Object sel = sels.get(mode);
          if (sel instanceof Map<?, ?> raw) {
            out.add(selectionRecord((Map<String, Object>) raw));
          }
        }
      }
    }
    return out;
  }

  static Map<String, Object> selectionRecord(Map<String, Object> sel) {
    if (sel.containsKey("mode") || !sel.containsKey("arm")) {
      return new LinkedHashMap<>(sel);
    }
    Map<String, Object> copy = new LinkedHashMap<>(sel);
    copy.put("mode", copy.remove("arm"));
    return copy;
  }

  /** Rebuild {@link Scored} rows from a judged.json written by {@link #write}. */
  @SuppressWarnings("unchecked")
  public static List<Scored> loadRows(Path path) throws Exception {
    if (path == null || !Files.isRegularFile(path)) {
      return List.of();
    }
    List<Map<String, Object>> raw =
        JSON.readValue(Files.readString(path, StandardCharsets.UTF_8), List.class);
    List<Scored> rows = new ArrayList<>();
    for (Map<String, Object> row : raw) {
      Map<String, Object> cleaned = new LinkedHashMap<>();
      for (var e : row.entrySet()) {
        if (!DERIVED.contains(e.getKey())) {
          cleaned.put(e.getKey(), e.getValue());
        }
      }
      if (!cleaned.containsKey("mode") && cleaned.containsKey("arm")) {
        cleaned.put("mode", cleaned.remove("arm"));
      }
      rows.add(fromMap(cleaned));
    }
    return rows;
  }

  /**
   * Per (task, mode, k): a changed chosen run or re-selected task replaces; for the same
   * selection the graded row wins, so a failed re-judge never erases an existing score.
   */
  public static List<Scored> merge(List<Scored> oldRows, List<Scored> newRows) {
    Map<String, Scored> byKey = new LinkedHashMap<>();
    for (Scored r : oldRows == null ? List.<Scored>of() : oldRows) {
      byKey.put(key(r), r);
    }
    for (Scored r : newRows == null ? List.<Scored>of() : newRows) {
      String k = key(r);
      Scored prev = byKey.get(k);
      if (prev != null
          && eq(prev.runId, r.runId)
          && eq(prev.selectedAt, r.selectedAt)
          && prev.graded()
          && !r.graded()
          && (r.error == null || !r.error.startsWith("selection"))) {
        continue;
      }
      byKey.put(k, r);
    }
    return byKey.values().stream()
        .sorted(
            Comparator.comparing((Scored s) -> s.taskId)
                .thenComparing(s -> s.mode)
                .thenComparingInt(s -> s.k))
        .toList();
  }

  public static Path write(List<Scored> rows, Path path) throws Exception {
    Files.createDirectories(path.getParent());
    List<Map<String, Object>> payload = rows.stream().map(Scored::toMap).toList();
    Files.writeString(
        path,
        JSON.writerWithDefaultPrettyPrinter().writeValueAsString(payload),
        StandardCharsets.UTF_8);
    return path;
  }

  @SuppressWarnings("unchecked")
  static Scored fromMap(Map<String, Object> row) {
    Scored s =
        new Scored(
            str(row.get("task_id")),
            str(row.getOrDefault("mode", "")),
            row.get("k") instanceof Number n ? n.intValue() : 0,
            str(row.getOrDefault("label", "")),
            str(row.getOrDefault("run_id", "")),
            Boolean.TRUE.equals(row.get("chosen_is_base")),
            str(row.getOrDefault("selected_at", "")),
            str(row.getOrDefault("error", "")));
    s.nPassed = asInt(row.get("n_passed"));
    s.nCriteria = asInt(row.get("n_criteria"));
    Object ap = row.get("all_pass");
    if (ap != null) {
      s.allPass = Boolean.parseBoolean(String.valueOf(ap));
    } else if (row.get("score") instanceof Map<?, ?> score) {
      s.nPassed = asInt(score.get("n_passed"));
      s.nCriteria = asInt(score.get("n_criteria"));
      Object sap = score.get("all_pass");
      s.allPass = sap != null && Boolean.parseBoolean(String.valueOf(sap));
    }
    if (row.get("raw") instanceof Map<?, ?> raw) {
      s.raw = new LinkedHashMap<>((Map<String, Object>) raw);
    }
    return s;
  }

  private static String key(Scored r) {
    return r.taskId + "|" + r.mode + "|" + r.k;
  }

  private static boolean eq(String a, String b) {
    return String.valueOf(a == null ? "" : a).equals(String.valueOf(b == null ? "" : b));
  }

  private static String str(Object v) {
    return v == null ? "" : String.valueOf(v);
  }
}
