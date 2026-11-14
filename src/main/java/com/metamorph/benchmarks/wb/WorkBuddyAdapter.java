package com.metamorph.benchmarks.wb;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.metamorph.core.spec.BenchPrompts;
import com.metamorph.core.spec.BenchSpec;
import com.metamorph.core.spec.BenchmarkAdapter;
import com.metamorph.core.spec.ChannelSpec;
import com.metamorph.core.spec.SubstrateLike;
import com.metamorph.core.tree.HarnessTree;
import com.metamorph.substrate.OpencodeEventRenderer;
import com.metamorph.substrate.OpencodeSubstrate;
import com.metamorph.substrate.OpencodeSurfaces;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * WorkBuddy adapter skeleton: base harness with stubs, Jinja validate, deliverable body threshold,
 * verifier score.json. Full WB container execute remains unwired.
 */
public final class WorkBuddyAdapter implements BenchmarkAdapter {
  public static final int TRAJ_MAX_CHARS = 80_000;
  private static final ObjectMapper JSON = new ObjectMapper();

  private final Path root;
  private final OpencodeSubstrate substrate = new OpencodeSubstrate();
  private final BenchSpec spec;

  public WorkBuddyAdapter(Path root) throws Exception {
    this.root = root;
    ChannelSpec ch = new ChannelSpec("opencode", "wb/stub");
    this.spec =
        BenchSpec.builder("wb", root.resolve("runs"), new BenchPrompts("complete the WorkBuddy task"))
            .installMode("workspace_config")
            .channels(Map.of("proposer", ch, "selector", ch))
            .domainWord("role")
            .proposerDeliverables("contents")
            .build();
    materializeBase();
  }

  private void materializeBase() throws Exception {
    Path base = baseHarness();
    if (Files.isDirectory(base)) {
      try (var stream = Files.list(base)) {
        if (stream.findAny().isPresent()) {
          return;
        }
      }
    }
    Map<String, String> tree = new LinkedHashMap<>();
    tree.put("systemprompt.md", "");
    tree.put("guardrails.md", "");
    tree.put("LongTermMEMORY.md", "");
    tree.put("tool_descriptions/tool_guidance.md", "");
    HarnessTree.writeTree(substrate.surface(), tree, base, true);
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
    return slash < 0 ? "wb" : taskId.substring(0, slash);
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
    return "WorkBuddy task " + taskId;
  }

  @Override
  public Path taskFiles(String taskId) {
    return root.resolve("tasks").resolve(taskId);
  }

  @Override
  public Path execute(String taskId, Path harnessDir, String runId, Integer timeout) {
    throw new UnsupportedOperationException(
        "WorkBuddyAdapter.execute requires the WB runtime (uv + scripts/run.sh); use DemoAdapter offline");
  }

  @Override
  public boolean delivered(Path runDir) {
    try {
      return WorkBuddyDeliverables.bodyChars(runDir) >= WorkBuddyDeliverables.MIN_BODY_CHARS;
    } catch (Exception e) {
      return false;
    }
  }

  public Path trajectoryPath(Path runDir) {
    return runDir.resolve("agent").resolve("oc-output.txt");
  }

  @Override
  public String trajectory(Path runDir, int obsTrunc) {
    Path f = trajectoryPath(runDir);
    if (!Files.isRegularFile(f)) {
      return "(no trajectory)";
    }
    try {
      String raw = Files.readString(f, StandardCharsets.UTF_8);
      if (raw.strip().startsWith("{")) {
        return OpencodeEventRenderer.renderEvents(f, obsTrunc, TRAJ_MAX_CHARS);
      }
      if (raw.length() > TRAJ_MAX_CHARS) {
        return raw.substring(0, TRAJ_MAX_CHARS);
      }
      return raw;
    } catch (Exception e) {
      return "(no trajectory)";
    }
  }

  /** True when the capture stream ends on a provider error (cut short, not finished). */
  public boolean killed(Path runDir) {
    Path f = trajectoryPath(runDir);
    if (!Files.isRegularFile(f)) {
      return false;
    }
    try {
      List<String> lines = Files.readAllLines(f, StandardCharsets.UTF_8);
      for (int i = lines.size() - 1; i >= 0; i--) {
        String ln = lines.get(i).strip();
        if (!ln.startsWith("{")) {
          continue;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> ev = JSON.readValue(ln, Map.class);
        return "error".equals(String.valueOf(ev.get("type"))) || ev.get("error") instanceof Map<?, ?>;
      }
    } catch (Exception ignored) {
      return false;
    }
    return false;
  }

  @Override
  public Map<String, String> deliverableFiles(Path runDir) {
    try {
      return WorkBuddyDeliverables.extract(runDir);
    } catch (Exception e) {
      return Map.of();
    }
  }

  @Override
  public String deliverableText(Path runDir) {
    try {
      return WorkBuddyDeliverables.flatText(runDir);
    } catch (Exception e) {
      return "(this run produced NO deliverables)";
    }
  }

  @Override
  public Path baseHarness() {
    return root.resolve("_base_harness_wb_oc");
  }

  @Override
  public void validateTree(Map<String, String> tree) {
    try {
      Path tmp = Files.createTempDirectory("wb-validate-");
      try {
        Path staged = HarnessTree.writeTree(substrate.surface(), tree, tmp.resolve("h"), false);
        // Minimal compose stand-in: always-on files concatenated + instruction placeholder.
        StringBuilder composed = new StringBuilder();
        for (String name : substrate.surface().alwaysOn()) {
          composed.append(tree.getOrDefault(name, "")).append('\n');
        }
        composed.append("{{ instruction }}");
        OpencodeSurfaces.assertJinjaSafe(composed.toString());
        if (!Files.isDirectory(staged)) {
          throw new IllegalStateException("staged harness missing");
        }
      } finally {
        try (var walk = Files.walk(tmp)) {
          walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
            try {
              Files.deleteIfExists(p);
            } catch (Exception ignored) {
              // best-effort
            }
          });
        }
      }
    } catch (IllegalArgumentException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalArgumentException("validateTree failed: " + e.getMessage(), e);
    }
  }

  @Override
  public Map<String, Object> score(Path runDir, String taskId) {
    try {
      if (!delivered(runDir) && killed(runDir)) {
        return null;
      }
      Path scoreJson = runDir.resolve("verifier").resolve("score.json");
      if (!Files.isRegularFile(scoreJson)) {
        return null;
      }
      @SuppressWarnings("unchecked")
      Map<String, Object> payload = JSON.readValue(Files.readString(scoreJson), Map.class);
      return WorkBuddyScore.toScoreRow(payload);
    } catch (Exception e) {
      return null;
    }
  }
}
