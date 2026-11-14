package com.metamorph.benchmarks.jb;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Offline JobBench rubric failure contract: a worker/transport failure is quarantined and stays
 * ungraded — never accepted as a real zero. Mirrors the consumer side of {@code
 * test_jobbench_worker_failure.py} without the pinned upstream judge module.
 */
public final class JobBenchRubric {
  public static final String FAILURE_MARKER = "Judge raised an exception:";
  private static final ObjectMapper JSON = new ObjectMapper();

  private JobBenchRubric() {}

  /** Shape produced when a rubric worker dies mid-flight. */
  public static Map<String, Object> failedRubricResult(int index, Map<String, Object> rubric, String detail) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("index", index);
    out.put("rubric", rubric == null ? Map.of() : new LinkedHashMap<>(rubric));
    out.put("rubric_passed", null);
    out.put("criteria_results", List.of());
    out.put("failed", true);
    out.put(
        "error",
        FAILURE_MARKER + " " + (detail == null || detail.isBlank() ? "offline worker failure" : detail));
    return out;
  }

  public static Map<String, Object> modelResult(boolean passed) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("rubric_passed", passed);
    out.put("criteria_results", List.of(Map.of("passed", passed)));
    out.put("overall_reasoning", "local response fixture");
    out.put("failed", false);
    return out;
  }

  public static boolean isWorkerFailure(Map<String, Object> result) {
    if (result == null) {
      return true;
    }
    if (Boolean.TRUE.equals(result.get("failed"))) {
      return true;
    }
    Object err = result.get("error");
    return err != null && String.valueOf(err).startsWith(FAILURE_MARKER);
  }

  /**
   * Aggregate rubric rows into a cacheable score. Any worker failure → {@code null} (ungraded). A
   * valid all-false row remains a graded zero.
   */
  public static Map<String, Object> scoreFromResults(List<Map<String, Object>> results) {
    if (results == null || results.isEmpty()) {
      return null;
    }
    for (Map<String, Object> r : results) {
      if (isWorkerFailure(r)) {
        return null;
      }
      if (r.get("rubric_passed") == null) {
        return null;
      }
    }
    int n = results.size();
    int passed = 0;
    for (Map<String, Object> r : results) {
      if (Boolean.TRUE.equals(r.get("rubric_passed"))) {
        passed++;
      }
    }
    Map<String, Object> score = new LinkedHashMap<>();
    score.put("n_passed", passed);
    score.put("n_criteria", n);
    score.put("all_pass", passed == n && n > 0);
    score.put("metric", "rubric");
    return score;
  }

  /** Move a details report aside when the judge rejects the failure payload. */
  public static Path quarantineDetails(Path details) throws Exception {
    Path failed = details.resolveSibling(details.getFileName().toString() + ".judge_failed");
    if (Files.isRegularFile(details)) {
      Files.move(details, failed, StandardCopyOption.REPLACE_EXISTING);
    }
    return failed;
  }

  public static Path writeDetails(Path details, String label, String judge, List<Map<String, Object>> results)
      throws Exception {
    Files.createDirectories(details.getParent());
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("label", label);
    payload.put("judge", judge);
    payload.put("results", results == null ? List.of() : new ArrayList<>(results));
    boolean anyFail = results != null && results.stream().anyMatch(JobBenchRubric::isWorkerFailure);
    payload.put("has_worker_failure", anyFail);
    Files.writeString(
        details, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(payload), StandardCharsets.UTF_8);
    return details;
  }
}
