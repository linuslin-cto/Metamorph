package com.metamorph.benchmarks.lab;

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
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal LAB adapter skeleton: deliverable extraction + cache scoring + base harness materialize.
 * Full harvey-labs execute/judge wiring is intentionally out of scope until the upstream pin is
 * available on this machine.
 */
public final class LabAdapter implements BenchmarkAdapter {
  private final Path root;
  private final OpencodeSubstrate substrate = new OpencodeSubstrate();
  private final BenchSpec spec;
  private final Map<String, Map<String, String>> deliverableCache = new ConcurrentHashMap<>();

  public LabAdapter(Path root) throws Exception {
    this.root = root;
    ChannelSpec ch = new ChannelSpec("opencode", "lab/stub");
    this.spec =
        BenchSpec.builder(
                "lab",
                root.resolve("runs"),
                new BenchPrompts("review contracts and produce the named legal deliverable"))
            .installMode("workspace_config")
            .channels(Map.of("proposer", ch, "selector", ch))
            .domainWord("practice area")
            .proposerDeliverables("contents")
            .build();
    materializeBase();
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
        "Produce exactly the deliverable files the assignment names under output/<filename>.\n");
    HarnessTree.writeTree(substrate.surface(), tree, base, false);
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
          .sorted()
          .forEach(out::add);
      return out;
    } catch (Exception e) {
      return List.of();
    }
  }

  @Override
  public String domainOf(String taskId) {
    int slash = taskId.indexOf('/');
    return slash < 0 ? "general" : taskId.substring(0, slash);
  }

  @Override
  public String taskPrompt(String taskId) {
    Path prompt = taskFiles(taskId).resolve("prompt.md");
    try {
      if (Files.isRegularFile(prompt)) {
        return Files.readString(prompt);
      }
    } catch (Exception ignored) {
      // fall through
    }
    return "Complete the LAB assignment for " + taskId;
  }

  @Override
  public Path taskFiles(String taskId) {
    return root.resolve("tasks").resolve(taskId);
  }

  @Override
  public Path execute(String taskId, Path harnessDir, String runId, Integer timeout) {
    throw new UnsupportedOperationException(
        "LabAdapter.execute requires the pinned harvey-labs runtime; use DemoAdapter offline");
  }

  @Override
  public boolean delivered(Path runDir) {
    Path out = LabDeliverables.outputDir(runDir);
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
      if (obsTrunc > 0 && text.length() > obsTrunc) {
        return text.substring(0, obsTrunc);
      }
      return text;
    } catch (Exception e) {
      return "(trajectory unreadable: " + e.getMessage() + ")";
    }
  }

  @Override
  public Map<String, String> deliverableFiles(Path runDir) {
    String key = runDir.toAbsolutePath().normalize().toString();
    return new LinkedHashMap<>(
        deliverableCache.computeIfAbsent(
            key,
            k -> {
              try {
                return LabDeliverables.extract(runDir);
              } catch (Exception e) {
                throw new RuntimeException("deliverable extraction failed for " + runDir + ": " + e, e);
              }
            }));
  }

  @Override
  public String deliverableText(Path runDir) {
    return LabDeliverables.asContents(deliverableFiles(runDir));
  }

  @Override
  public Path baseHarness() {
    return root.resolve("_base_harness_default");
  }

  @Override
  public void validateTree(Map<String, String> tree) {
    if ("oc".equals(substrate.name())) {
      validateOc(tree);
    } else {
      validateCx(tree);
    }
  }

  private void validateOc(Map<String, String> tree) {
    String body = tree.getOrDefault("systemprompt.md", "");
    if (!body.contains("output/")) {
      throw new IllegalArgumentException(
          "the rendered prompt must say deliverables go to `output/<filename>`");
    }
  }

  private void validateCx(Map<String, String> tree) {
    String body = tree.getOrDefault("AGENTS.md", "");
    if (!body.contains("output/")) {
      throw new IllegalArgumentException(
          "AGENTS.md must say deliverables go to `output/<filename>`");
    }
  }

  /**
   * Prefer cached {@code scores.json}; otherwise return null (ungraded) rather than inventing a
   * zero — matching the LAB baseline judge contract.
   */
  @Override
  public Map<String, Object> score(Path runDir, String taskId) {
    try {
      Map<String, Object> cached = Judge.scoreFromCache(runDir);
      if (cached == null || cached.isEmpty()) {
        return null;
      }
      if (!Judge.hasGrade(asInt(cached.get("n_passed")), asInt(cached.get("n_criteria")))) {
        return null;
      }
      return cached;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static Integer asInt(Object v) {
    if (v == null) {
      return null;
    }
    if (v instanceof Number n) {
      return n.intValue();
    }
    try {
      return Integer.parseInt(String.valueOf(v));
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
