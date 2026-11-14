package com.metamorph.core.spec;

import java.nio.file.Path;

/** What the method knows about one finished rollout. Never carries a score. */
public record RunView(
    String label,
    String runId,
    Path runDir,
    boolean ok,
    boolean delivered,
    double costUsd,
    double wallS,
    String error) {

  public RunView(String label, String runId, Path runDir, boolean ok, boolean delivered) {
    this(label, runId, runDir, ok, delivered, 0.0, 0.0, "");
  }

  public RunView(
      String label, String runId, Path runDir, boolean ok, boolean delivered, String error) {
    this(label, runId, runDir, ok, delivered, 0.0, 0.0, error == null ? "" : error);
  }
}
