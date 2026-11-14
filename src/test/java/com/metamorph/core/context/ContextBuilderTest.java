package com.metamorph.core.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.metamorph.core.spec.BenchPrompts;
import com.metamorph.core.spec.BenchSpec;
import com.metamorph.core.spec.BenchmarkAdapter;
import com.metamorph.core.spec.ChannelSpec;
import com.metamorph.core.spec.RunView;
import com.metamorph.core.spec.SubstrateLike;
import com.metamorph.substrate.OpencodeSubstrate;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ContextBuilderTest {
  @TempDir Path root;

  @Test
  void buildCopiesTaskSourceHarnessRolloutsAndRejectsLeaks() throws Exception {
    Path sources = root.resolve("sources/t1");
    Files.createDirectories(sources);
    Files.writeString(sources.resolve("input.txt"), "src");
    Path harness = root.resolve("harness");
    Files.createDirectories(harness);
    Files.writeString(harness.resolve("systemprompt.md"), "do work");
    Path run = root.resolve("run1");
    Files.createDirectories(run);
    Files.writeString(run.resolve("traj.md"), "[ACTION] go\n");
    Files.writeString(run.resolve("answer.txt"), "out");
    FakeAdapter adapter = new FakeAdapter(root, List.of("reference_answer.txt"));

    ContextTree tree =
        ContextBuilder.build(
            root.resolve("ctx"),
            adapter,
            "t1",
            List.of(new RunView("agg1", "rid", run, true, true)),
            harness,
            null,
            List.of(),
            Map.of("agg1", "kept"));
    assertEquals(1, tree.sourceFiles());
    assertTrue(Files.isRegularFile(root.resolve("ctx/source/input.txt")));
    assertTrue(Files.isRegularFile(root.resolve("ctx/harness/systemprompt.md")));
    assertTrue(Files.isRegularFile(root.resolve("ctx/rollouts/agg1.md")));
    assertTrue(Files.isRegularFile(root.resolve("ctx/deliverables/agg1/answer.txt")));
    assertTrue(Files.isRegularFile(root.resolve("ctx/decisions/agg1.md")));
    assertTrue(ContextBuilder.mission(tree, "improve").contains("EVIDENCE LAYOUT"));
  }

  @Test
  void leakGlobsAndGraderLanguageAreRejected() throws Exception {
    Path harness = root.resolve("h");
    Files.createDirectories(harness);
    Files.writeString(harness.resolve("reference_answer.txt"), "secret");
    FakeAdapter adapter = new FakeAdapter(root, List.of("reference_answer.txt"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            ContextBuilder.build(
                root.resolve("bad"),
                adapter,
                "t1",
                List.of(),
                harness,
                null,
                List.of(),
                null));

    assertThrows(
        IllegalArgumentException.class,
        () ->
            ContextBuilder.assertTreeHasNoGraderVerdicts(
                Map.of("systemprompt.md", "optimize the rubric score")));
  }

  static final class FakeAdapter implements BenchmarkAdapter {
    private final BenchSpec spec;
    private final OpencodeSubstrate substrate = new OpencodeSubstrate();
    private final Path root;

    FakeAdapter(Path root, List<String> leaks) {
      this.root = root;
      ChannelSpec ch = new ChannelSpec("opencode", "fixture");
      var b =
          BenchSpec.builder("demo", root.resolve("runs"), new BenchPrompts("task"))
              .installMode("workspace_config")
              .channels(Map.of("proposer", ch, "selector", ch));
      if (leaks != null && !leaks.isEmpty()) {
        b.leakGlobs(leaks.toArray(String[]::new));
      }
      this.spec = b.build();
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
      return List.of("t1");
    }

    @Override
    public String domainOf(String taskId) {
      return "d";
    }

    @Override
    public String taskPrompt(String taskId) {
      return "do the task";
    }

    @Override
    public Path taskFiles(String taskId) {
      return root.resolve("sources").resolve(taskId);
    }

    @Override
    public Path execute(String taskId, Path harnessDir, String runId, Integer timeout) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean delivered(Path runDir) {
      return true;
    }

    @Override
    public String trajectory(Path runDir, int obsTrunc) {
      try {
        return Files.readString(runDir.resolve("traj.md"));
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public Map<String, String> deliverableFiles(Path runDir) {
      try {
        return Map.of("answer.txt", Files.readString(runDir.resolve("answer.txt")));
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public String deliverableText(Path runDir) {
      return deliverableFiles(runDir).get("answer.txt");
    }

    @Override
    public Path baseHarness() {
      return Path.of(".");
    }

    @Override
    public void validateTree(Map<String, String> tree) {}
  }
}
