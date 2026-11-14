package com.metamorph.core.spec;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Implement these and the whole method works. Nothing here is method logic. */
public interface BenchmarkAdapter {
  BenchSpec spec();

  SubstrateLike substrate();

  List<String> tasks(String split);

  default List<String> tasks() {
    return tasks("all");
  }

  String domainOf(String taskId);

  String taskPrompt(String taskId);

  Path taskFiles(String taskId);

  Path execute(String taskId, Path harnessDir, String runId, Integer timeout);

  default Path execute(String taskId, Path harnessDir, String runId) {
    return execute(taskId, harnessDir, runId, null);
  }

  boolean delivered(Path runDir);

  String trajectory(Path runDir, int obsTrunc);

  Map<String, String> deliverableFiles(Path runDir);

  String deliverableText(Path runDir);

  Path baseHarness();

  void validateTree(Map<String, String> tree);

  default Map<String, Object> score(Path runDir, String taskId) {
    return null;
  }

  default BenchPrompts promptsFor(String taskId) {
    return spec().prompts();
  }
}
