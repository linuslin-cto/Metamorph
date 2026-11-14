package com.metamorph.core.selection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.metamorph.core.proposer.Proposer;
import com.metamorph.core.proposer.Reply;
import com.metamorph.core.spec.BenchPrompts;
import com.metamorph.core.spec.BenchSpec;
import com.metamorph.core.spec.BenchmarkAdapter;
import com.metamorph.core.spec.ChannelSpec;
import com.metamorph.core.spec.RunView;
import com.metamorph.core.spec.SubstrateLike;
import com.metamorph.substrate.OpencodeSubstrate;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SelectionRunnerTest {
  @TempDir Path root;

  @Test
  void selectionPoolKeepsModeLabelsOnly() {
    Map<String, RunView> rollouts = new LinkedHashMap<>();
    rollouts.put("agg1", view("agg1"));
    rollouts.put("agg2", view("agg2"));
    rollouts.put("c1", view("c1"));
    assertEquals(
        List.of("agg1", "agg2"),
        List.copyOf(SelectionRunner.selectionPool("agg", 2, rollouts, List.of()).keySet()));
    assertEquals(
        List.of("c1"),
        List.copyOf(SelectionRunner.selectionPool("seq", 1, rollouts, List.of()).keySet()));
  }

  @Test
  void assignIdsIsDeterministicAndBijective() throws Exception {
    Map<String, String> a = SelectionRunner.assignIds(List.of("agg1", "agg2", "agg3"), "t", 3, "agg");
    Map<String, String> b = SelectionRunner.assignIds(List.of("agg1", "agg2", "agg3"), "t", 3, "agg");
    assertEquals(a, b);
    assertEquals(3, a.size());
    assertEquals(3, a.values().stream().distinct().count());
    assertTrue(a.containsKey("A"));
    assertTrue(a.containsKey("B"));
    assertTrue(a.containsKey("C"));
  }

  @Test
  void trivialSingleCandidateAndMissingChoiceFallback() throws Exception {
    Path work = root.resolve("work");
    Files.createDirectories(work);
    Path run = root.resolve("run");
    Files.createDirectories(run);
    Files.writeString(run.resolve("answer.txt"), "ok");
    FakeAdapter adapter = new FakeAdapter(root);
    Map<String, RunView> one =
        Map.of("agg1", new RunView("agg1", "rid-1", run, true, true));
    Selection trivial =
        SelectionRunner.runSelection(
            "t1", "agg", 1, one, List.of(), adapter, new FixedProposer(""), work, work.resolve("lib"));
    assertTrue(trivial.ok);
    assertTrue(trivial.trivial);
    assertEquals("agg1", trivial.chosenLabel);

    Path run2 = root.resolve("run2");
    Files.createDirectories(run2);
    Files.writeString(run2.resolve("answer.txt"), "two");
    Map<String, RunView> two = new LinkedHashMap<>();
    two.put("agg1", new RunView("agg1", "rid-1", run, true, true));
    two.put("agg2", new RunView("agg2", "rid-2", run2, true, true));
    Selection fallback =
        SelectionRunner.runSelection(
            "t1",
            "agg",
            2,
            two,
            List.of(),
            adapter,
            new FixedProposer("no fence here"),
            work.resolve("fb"),
            work.resolve("lib"));
    assertTrue(fallback.ok);
    assertTrue(fallback.fallback);
    assertEquals("no ```choice``` fence", fallback.error);
    assertFalse(fallback.chosenLabel.isBlank());
  }

  @Test
  void unknownChoiceFallsBack() throws Exception {
    Path work = root.resolve("unk");
    Files.createDirectories(work);
    Path r1 = root.resolve("u1");
    Path r2 = root.resolve("u2");
    Files.createDirectories(r1);
    Files.createDirectories(r2);
    Files.writeString(r1.resolve("answer.txt"), "a");
    Files.writeString(r2.resolve("answer.txt"), "b");
    Map<String, RunView> two = new LinkedHashMap<>();
    two.put("agg1", new RunView("agg1", "1", r1, true, true));
    two.put("agg2", new RunView("agg2", "2", r2, true, true));
    Selection sel =
        SelectionRunner.runSelection(
            "t1",
            "agg",
            2,
            two,
            List.of(),
            new FakeAdapter(root),
            new FixedProposer("```choice\nZ\n```\n<why>nope</why>"),
            work,
            work.resolve("lib"));
    assertTrue(sel.ok);
    assertTrue(sel.fallback);
    assertTrue(sel.error.startsWith("unknown choice"));
  }

  private static RunView view(String label) {
    return new RunView(label, label, Path.of("."), true, true);
  }

  static final class FixedProposer extends Proposer {
    private final String text;

    FixedProposer(String text) {
      super(new ChannelSpec("opencode", "fixture/selector"));
      this.text = text;
    }

    @Override
    public Reply call(String mission, Path contextDir, String label, String harvest, int maxAttempts) {
      return new Reply(label, text, true, 1, contextDir);
    }
  }

  static final class FakeAdapter implements BenchmarkAdapter {
    private final BenchSpec spec;
    private final OpencodeSubstrate substrate = new OpencodeSubstrate();

    FakeAdapter(Path root) {
      ChannelSpec ch = new ChannelSpec("opencode", "fixture");
      this.spec =
          BenchSpec.builder("demo", root.resolve("runs"), new BenchPrompts("pick"))
              .installMode("workspace_config")
              .channels(Map.of("proposer", ch, "selector", ch))
              .build();
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
      return "pick one";
    }

    @Override
    public Path taskFiles(String taskId) {
      return rootTasks();
    }

    private Path rootTasks() {
      try {
        Path p = spec.runsRoot().getParent().resolve("sources/t1");
        Files.createDirectories(p);
        return p;
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public Path execute(String taskId, Path harnessDir, String runId, Integer timeout) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean delivered(Path runDir) {
      return Files.isRegularFile(runDir.resolve("answer.txt"));
    }

    @Override
    public String trajectory(Path runDir, int obsTrunc) {
      return "";
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
