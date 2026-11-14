package com.metamorph.core.proposer;

import java.nio.file.Path;

/** One session: what came back, what it cost, and where the audit trail is. */
public record Reply(
    String label,
    String text,
    boolean ok,
    double costUsd,
    double wallS,
    int attempts,
    boolean cached,
    String channel,
    String error,
    Path audit,
    Path workspace) {

  public Reply(String label, String text, boolean ok, int attempts, Path workspace) {
    this(label, text, ok, 0.0, 0.0, attempts, false, "", "", null, workspace);
  }
}
