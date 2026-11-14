package com.metamorph.core.schedule;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TaskResult {
  public String taskId;
  public String domain;
  public int k;
  public Integer seed;
  public String status = "running";
  public String inputs;
  public Map<String, Object> modes = new LinkedHashMap<>();
  public Map<String, Object> selections = new LinkedHashMap<>();
  public List<String> base = new ArrayList<>();
  public List<String> errors = new ArrayList<>();

  public Map<String, Object> toMap() {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("task_id", taskId);
    m.put("domain", domain);
    m.put("k", k);
    m.put("seed", seed);
    m.put("status", status);
    m.put("inputs", inputs);
    m.put("base", base);
    m.put("modes", modes);
    m.put("selections", selections);
    m.put("errors", errors);
    return m;
  }
}
