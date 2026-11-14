package com.metamorph.core.recursion;

import com.metamorph.core.ledger.Ledger;
import com.metamorph.core.spec.RunView;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ModeResult {
  public final String mode;
  public final String taskId;
  public final int k;
  public final List<Stage> stages = new ArrayList<>();
  public final Map<String, Map<String, String>> trees = new LinkedHashMap<>();
  public final Map<String, RunView> rollouts = new LinkedHashMap<>();
  public final List<String> finals = new ArrayList<>();
  public Ledger ledger;
  public Map<String, Object> similarity;
  public final List<String> errors = new ArrayList<>();

  public ModeResult(String mode, String taskId, int k) {
    this.mode = mode;
    this.taskId = taskId;
    this.k = k;
  }

  public boolean ok() {
    return !finals.isEmpty()
        && errors.isEmpty()
        && stages.stream().noneMatch(s -> s.executionFailed);
  }

  public List<String> fellBack() {
    return stages.stream().filter(s -> s.fellBack).map(s -> s.label).toList();
  }

  public Map<String, Object> toMap() {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("mode", mode);
    m.put("task_id", taskId);
    m.put("k", k);
    m.put("finals", finals);
    m.put("ok", ok());
    m.put("fell_back", fellBack());
    m.put("kept_base", stages.stream().filter(s -> s.keptBase).map(s -> s.label).toList());
    m.put("stages", stages.stream().map(Stage::toMap).toList());
    m.put("similarity", similarity);
    m.put("ledger", ledger == null ? null : ledger.toMap());
    m.put("errors", errors);
    return m;
  }
}
