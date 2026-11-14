package com.metamorph.core.schedule;

import java.nio.file.Path;

/** Every name for one (benchmark, substrate, mode, k, seed, tag) setting. */
public record Namespace(
    String benchmark, String substrate, String mode, int k, Integer seed, String tag) {

  public Namespace(String benchmark, String substrate, String mode, int k) {
    this(benchmark, substrate, mode, k, null, "");
  }

  @Override
  public String toString() {
    return Schedule.ns(benchmark, substrate, mode, k, seed, tag == null ? "" : tag);
  }

  public String runId(String taskId, String label) {
    return this + "/" + taskId + "/" + label;
  }

  public Path taskDir(Path root, String taskId) {
    return root.resolve(toString()).resolve(taskId.replace("/", "__"));
  }
}
