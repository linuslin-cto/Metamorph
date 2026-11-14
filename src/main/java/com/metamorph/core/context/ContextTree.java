package com.metamorph.core.context;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** A built evidence tree, plus what actually landed in it. */
public final class ContextTree {
  private final Path root;
  private final String taskId;
  private final List<String> rollouts;
  private final List<String> deliverables;
  private final int harnessFiles;
  private final boolean library;
  private final List<String> pool;
  private final List<String> failed;
  private final String deliverablesMode;
  private final int sourceFiles;
  private final int decisions;

  public ContextTree(
      Path root,
      String taskId,
      List<String> rollouts,
      List<String> deliverables,
      int harnessFiles,
      boolean library,
      List<String> pool,
      List<String> failed,
      String deliverablesMode,
      int sourceFiles,
      int decisions) {
    this.root = root;
    this.taskId = taskId;
    this.rollouts = List.copyOf(rollouts);
    this.deliverables = List.copyOf(deliverables);
    this.harnessFiles = harnessFiles;
    this.library = library;
    this.pool = List.copyOf(pool);
    this.failed = failed == null ? List.of() : List.copyOf(failed);
    this.deliverablesMode = deliverablesMode;
    this.sourceFiles = sourceFiles;
    this.decisions = decisions;
  }

  public Path root() {
    return root;
  }

  public String taskId() {
    return taskId;
  }

  public List<String> rollouts() {
    return rollouts;
  }

  public int sourceFiles() {
    return sourceFiles;
  }

  public int harnessFiles() {
    return harnessFiles;
  }

  public String layout() {
    List<String> lines = new ArrayList<>();
    lines.add("context/task.md                  the task as the agent received it");
    if (sourceFiles > 0) {
      lines.add("context/source/                 task source materials");
    }
    if (decisions > 0) {
      lines.add("context/decisions/              earlier edits and composition decisions");
    }
    if (!rollouts.isEmpty()) {
      lines.add("context/rollouts/               FULL trajectories");
    }
    if (!deliverables.isEmpty()) {
      lines.add("context/deliverables/<label>/    rollout artifacts");
    }
    if (harnessFiles > 0) {
      lines.add("context/harness/                the harness you are editing (" + harnessFiles + ")");
    }
    if (!pool.isEmpty()) {
      lines.add("context/pool/                   " + pool.size() + " candidates");
    }
    if (library) {
      lines.add("context/library/                earlier tasks in this area");
    }
    return String.join("\n", lines);
  }
}
