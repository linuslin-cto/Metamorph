package com.metamorph.core.recursion;

import java.util.LinkedHashMap;
import java.util.Map;

/** An improvement stage, including retries, its harness, and decision notes. */
public final class Stage {
  public final String label;
  public final String dim;
  public Map<String, String> tree;
  public boolean keptBase;
  public boolean fellBack;
  public String error = "";
  public double usd;
  public int attempts;
  public boolean cached;
  public String notes = "";
  public boolean executionFailed;

  public Stage(String label, String dim, Map<String, String> tree) {
    this.label = label;
    this.dim = dim;
    this.tree = new LinkedHashMap<>(tree);
  }

  public Map<String, Object> toMap() {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("label", label);
    m.put("dim", dim);
    m.put("fell_back", fellBack);
    m.put("kept_base", keptBase);
    m.put("execution_failed", executionFailed);
    m.put("attempts", attempts);
    m.put("usd", usd);
    m.put("cached", cached);
    m.put("notes", notes);
    m.put("error", error == null ? "" : error.substring(0, Math.min(400, error.length())));
    return m;
  }
}
