package com.metamorph.core.judge;

import java.util.LinkedHashMap;
import java.util.Map;

/** One judged selection. {@link #graded()} means the judge succeeded, not that the run did well. */
public final class Scored {
  public final String taskId;
  public final String mode;
  public final int k;
  public final String label;
  public final String runId;
  public final boolean chosenIsBase;
  public final String selectedAt;
  public Integer nPassed;
  public Integer nCriteria;
  public boolean allPass;
  public Map<String, Object> raw = new LinkedHashMap<>();
  public String error = "";

  public Scored(
      String taskId,
      String mode,
      int k,
      String label,
      String runId,
      boolean chosenIsBase,
      String selectedAt) {
    this.taskId = taskId;
    this.mode = mode;
    this.k = k;
    this.label = label;
    this.runId = runId;
    this.chosenIsBase = chosenIsBase;
    this.selectedAt = selectedAt == null ? "" : selectedAt;
  }

  public Scored(
      String taskId,
      String mode,
      int k,
      String label,
      String runId,
      boolean chosenIsBase,
      String selectedAt,
      String error) {
    this(taskId, mode, k, label, runId, chosenIsBase, selectedAt);
    this.error = error == null ? "" : error;
  }

  public boolean graded() {
    return Judge.hasGrade(nPassed, nCriteria);
  }

  public Double frac() {
    return graded() ? (nPassed.doubleValue() / nCriteria.doubleValue()) : null;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("task_id", taskId);
    m.put("mode", mode);
    m.put("k", k);
    m.put("label", label);
    m.put("run_id", runId);
    m.put("chosen_is_base", chosenIsBase);
    m.put("selected_at", selectedAt);
    m.put("n_passed", nPassed);
    m.put("n_criteria", nCriteria);
    m.put("all_pass", allPass);
    m.put("graded", graded());
    m.put("frac", frac());
    m.put("score", graded() ? Map.of("n_passed", nPassed, "n_criteria", nCriteria, "all_pass", allPass) : null);
    m.put("raw", raw);
    m.put("error", error);
    return m;
  }
}
