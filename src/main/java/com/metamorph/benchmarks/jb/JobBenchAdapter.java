package com.metamorph.benchmarks.jb;

import com.metamorph.core.judge.Judge;
import com.metamorph.core.spec.BenchPrompts;
import com.metamorph.core.spec.BenchSpec;
import com.metamorph.core.spec.BenchmarkAdapter;
import com.metamorph.core.spec.ChannelSpec;
import com.metamorph.core.spec.SubstrateLike;
import com.metamorph.core.tree.HarnessTree;
import com.metamorph.substrate.OpencodeSubstrate;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JobBench adapter skeleton: isolation helpers + base harness. Full OpenCode rollout
 * execute path lands later when bun/opencode pins are present.
 */
public final class JobBenchAdapter implements BenchmarkAdapter {
  private final Path root;
  private final Path tempBase;
  private final OpencodeSubstrate substrate = new OpencodeSubstrate();
  private final BenchSpec spec;

  public JobBenchAdapter(Path root) throws Exception {
    this(root, root.resolve("hevo_ws"));
  }

  public JobBenchAdapter(Path root, Path tempBase) throws Exception {
    this.root = root;
    this.tempBase = tempBase;
    ChannelSpec ch = new ChannelSpec("opencode", "jb/stub");
    this.spec =
        BenchSpec.builder(
                "jb",
                root.resolve("runs"),
                new BenchPrompts("complete the office / web / code task from TASK_INSTRUCTIONS.txt"))
            .installMode("workspace_config")
            .channels(Map.of("proposer", ch, "selector", ch))
            .domainWord("task family")
            .build();
    Files.createDirectories(tempBase);
    materializeBase();
  }

  public Path tempBase() {
    return tempBase;
  }

  private void materializeBase() throws Exception {
    Path base = baseHarness();
    if (Files.isDirectory(base)) {
      return;
    }
    Map<String, String> tree = new LinkedHashMap<>();
    for (String name : substrate.surface().alwaysOn()) {
      tree.put(name, "");
    }
    tree.put(
        "systemprompt.md",
        "Only write final deliverables under output/. Stay inside the staged workspace.\n");
    HarnessTree.writeTree(substrate.surface(), tree, base, false);
  }

  /** Build the scrubbed env a rollout subprocess should inherit. */
  public Map<String, String> rolloutEnv(Path workspace, Map<String, String> baseEnv)
      throws Exception {
    String path = baseEnv.getOrDefault("PATH", System.getenv("PATH") == null ? "" : System.getenv("PATH"));
    Map<String, String> env = new LinkedHashMap<>(baseEnv);
    env.putAll(JobBenchIsolation.isolationEnv(workspace, path));
    env.put("OPENCODE_PERMISSION", JobBenchIsolation.permission(workspace, tempBase));
    return env;
  }

  /**
   * Stage a rollout workspace under {@link #tempBase()} and return it. Bun/OpenCode execute still
   * requires the pinned runtime; this prepares the tree the runner would launch into.
   */
  public Path stageRollout(String taskId, String label, Path renderDir) throws Exception {
    Path taskDir = taskFiles(taskId).resolve("task_folder");
    if (!Files.isDirectory(taskDir)) {
      taskDir = taskFiles(taskId);
    }
    String safe = taskId.replace('/', '_').replace('\\', '_');
    Path workspace = tempBase.resolve(safe + "_" + label + "_" + ProcessHandle.current().pid());
    JobBenchStaging.stage(taskDir, renderDir, workspace);
    return workspace;
  }

  public void resetRollout(String taskId, Path workspace) throws Exception {
    Path taskDir = taskFiles(taskId).resolve("task_folder");
    if (!Files.isDirectory(taskDir)) {
      taskDir = taskFiles(taskId);
    }
    JobBenchStaging.resetTask(taskDir, workspace);
  }

  @Override
  public BenchSpec spec() {
    return spec;
  }

  @Override
  public SubstrateLike substrate() {
    return substrate;
  }

  @Override
  public List<String> tasks(String split) {
    Path tasks = root.resolve("tasks");
    if (!Files.isDirectory(tasks)) {
      return List.of();
    }
    try (var stream = Files.list(tasks)) {
      List<String> out = new ArrayList<>();
      stream
          .filter(Files::isDirectory)
          .map(p -> p.getFileName().toString())
          .filter(n -> !n.startsWith("."))
          .sorted()
          .forEach(out::add);
      return out;
    } catch (Exception e) {
      return List.of();
    }
  }

  @Override
  public String domainOf(String taskId) {
    return "jb";
  }

  @Override
  public String taskPrompt(String taskId) {
    Path instr = taskFiles(taskId).resolve("task_folder").resolve("TASK_INSTRUCTIONS.txt");
    try {
      if (Files.isRegularFile(instr)) {
        return Files.readString(instr);
      }
    } catch (Exception ignored) {
      // fall through
    }
    return "Complete JobBench task " + taskId;
  }

  @Override
  public Path taskFiles(String taskId) {
    return root.resolve("tasks").resolve(taskId);
  }

  @Override
  public Path execute(String taskId, Path harnessDir, String runId, Integer timeout) {
    throw new UnsupportedOperationException(
        "JobBenchAdapter.execute requires bun/opencode; isolation helpers are available offline");
  }

  @Override
  public boolean delivered(Path runDir) {
    Path out = runDir.resolve("output");
    try {
      return Files.isDirectory(out) && Files.list(out).findAny().isPresent();
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public String trajectory(Path runDir, int obsTrunc) {
    Path stream = runDir.resolve("opencode_session.jsonl");
    try {
      if (!Files.isRegularFile(stream)) {
        return "(no trajectory recorded for " + runDir.getFileName() + ")";
      }
      String text = Files.readString(stream);
      return obsTrunc > 0 && text.length() > obsTrunc ? text.substring(0, obsTrunc) : text;
    } catch (Exception e) {
      return "(trajectory unreadable: " + e.getMessage() + ")";
    }
  }

  @Override
  public Map<String, String> deliverableFiles(Path runDir) {
    Path out = runDir.resolve("output");
    Map<String, String> files = new LinkedHashMap<>();
    if (!Files.isDirectory(out)) {
      return files;
    }
    try (var walk = Files.walk(out)) {
      for (Path p : walk.filter(Files::isRegularFile).sorted().toList()) {
        String rel = out.relativize(p).toString().replace('\\', '/');
        files.put(rel, Files.readString(p));
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return files;
  }

  @Override
  public String deliverableText(Path runDir) {
    Map<String, String> files = deliverableFiles(runDir);
    if (files.isEmpty()) {
      return "(this run produced NO deliverables)";
    }
    StringBuilder sb = new StringBuilder();
    for (var e : files.entrySet()) {
      if (!sb.isEmpty()) {
        sb.append("\n\n");
      }
      sb.append("----- FILE: ").append(e.getKey()).append(" -----\n").append(e.getValue());
    }
    return sb.toString();
  }

  @Override
  public Path baseHarness() {
    return root.resolve("_base_harness_jb");
  }

  @Override
  public void validateTree(Map<String, String> tree) {
    String body = tree.getOrDefault("systemprompt.md", "");
    if (!body.toLowerCase().contains("output")) {
      throw new IllegalArgumentException("JobBench harness must direct deliverables to output/");
    }
  }

  @Override
  public Map<String, Object> score(Path runDir, String taskId) {
    try {
      return Judge.scoreFromCache(runDir);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
