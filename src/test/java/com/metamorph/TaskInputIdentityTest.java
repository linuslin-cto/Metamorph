package com.metamorph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.metamorph.core.runmark.RunMark;
import com.metamorph.core.schedule.Schedule;
import com.metamorph.core.schedule.TaskResult;
import com.metamorph.core.spec.BenchPrompts;
import com.metamorph.core.spec.BenchSpec;
import com.metamorph.core.spec.BenchmarkAdapter;
import com.metamorph.core.spec.ChannelSpec;
import com.metamorph.core.spec.SubstrateLike;
import com.metamorph.substrate.OpencodeSubstrate;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Task inputs are part of rollout identity at fingerprint and summary resume layers. */
class TaskInputIdentityTest {
  private static final ObjectMapper JSON = new ObjectMapper();

  @TempDir Path root;
  Path files;

  static final class FakeAdapter implements BenchmarkAdapter {
    private final String prompt;
    private final Path files;
    private final BenchSpec spec;
    private final OpencodeSubstrate substrate = new OpencodeSubstrate();

    FakeAdapter(String prompt, Path files, Path runsRoot) {
      this.prompt = prompt;
      this.files = files;
      ChannelSpec ch = new ChannelSpec("opencode", "local/deterministic");
      this.spec =
          BenchSpec.builder("demo", runsRoot, new BenchPrompts("reconcile amounts"))
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
      return List.of("task-1");
    }

    @Override
    public String domainOf(String taskId) {
      return "d";
    }

    @Override
    public String taskPrompt(String taskId) {
      return prompt;
    }

    @Override
    public Path taskFiles(String taskId) {
      return files;
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
      return "";
    }

    @Override
    public Map<String, String> deliverableFiles(Path runDir) {
      return Map.of();
    }

    @Override
    public String deliverableText(Path runDir) {
      return "ok";
    }

    @Override
    public Path baseHarness() {
      return rootBase;
    }

    @Override
    public void validateTree(Map<String, String> tree) {}

    Path rootBase;
  }

  @BeforeEach
  void setUp() throws Exception {
    files = root.resolve("documents");
    Files.createDirectories(files);
    Files.writeString(files.resolve("source.txt"), "version-one");
  }

  @Test
  void digestIsStableForUnchangedInputs() throws Exception {
    String first = RunMark.inputsFingerprint("Use version one.", files);
    String second = RunMark.inputsFingerprint("Use version one.", files);
    assertEquals(first, second);
  }

  @Test
  void digestFollowsThePrompt() throws Exception {
    assertNotEquals(
        RunMark.inputsFingerprint("Use version one.", files),
        RunMark.inputsFingerprint("Use version two.", files));
  }

  @Test
  void digestFollowsFileContentsAndMembership() throws Exception {
    String before = RunMark.inputsFingerprint("Use version one.", files);
    Files.writeString(files.resolve("source.txt"), "version-two");
    String changed = RunMark.inputsFingerprint("Use version one.", files);
    assertNotEquals(before, changed);
    Files.writeString(files.resolve("extra.txt"), "more");
    assertNotEquals(changed, RunMark.inputsFingerprint("Use version one.", files));
  }

  @Test
  void digestIgnoresByproductsAndMissingMaterial() throws Exception {
    String before = RunMark.inputsFingerprint("Use version one.", files);
    Files.createDirectories(files.resolve("__pycache__"));
    Files.write(files.resolve("__pycache__/stale.pyc"), new byte[] {0});
    assertEquals(before, RunMark.inputsFingerprint("Use version one.", files));
    assertEquals(
        RunMark.inputsFingerprint("Use version one.", null),
        RunMark.inputsFingerprint("Use version one.", root.resolve("absent")));
  }

  @Test
  void markerRefusesReuseUnderChangedInputs() throws Exception {
    Path runDir = root.resolve("run");
    RunMark.markComplete(runDir, "r1", null, "aaaa", null);
    assertTrue(RunMark.isComplete(runDir, "aaaa"));
    assertFalse(RunMark.isComplete(runDir, "bbbb"));
    assertEquals("reusable", RunMark.whyNot(runDir, null, "aaaa"));
    assertTrue(RunMark.whyNot(runDir, null, "bbbb").contains("task inputs aaaa != bbbb"));
  }

  @Test
  void markerWithoutInputsStaysReusable() throws Exception {
    Path runDir = root.resolve("run2");
    RunMark.markComplete(runDir, "r1");
    Map<?, ?> marker = RunMark.readMark(runDir);
    assertFalse(marker.containsKey("inputs"));
    assertTrue(RunMark.isComplete(runDir, "aaaa"));
    assertEquals("reusable", RunMark.whyNot(runDir, null, "aaaa"));
  }

  @Test
  void writeSummaryDropsModesFromOlderInputIdentity() throws Exception {
    Path summary = root.resolve("summary.json");
    FakeAdapter adapter = new FakeAdapter("Sum ledger.csv.", files, root.resolve("runs"));
    adapter.rootBase = root.resolve("base");
    Files.createDirectories(adapter.rootBase);
    String oldInputs = Schedule.inputsFingerprint(adapter.taskPrompt("task-1"), files);

    Map<String, Object> old = new LinkedHashMap<>();
    old.put("task_id", "task-1");
    old.put("domain", "d");
    old.put("k", 1);
    old.put("status", "ok");
    old.put("inputs", oldInputs);
    old.put("modes", Map.of("agg", Map.of("ok", true), "seq", Map.of("ok", true)));
    old.put(
        "selections",
        Map.of(
            "agg", Map.of("ok", true, "chosen_label", "c1"),
            "seq", Map.of("ok", true, "chosen_label", "c1")));
    JSON.writerWithDefaultPrettyPrinter().writeValue(summary.toFile(), old);

    Files.writeString(files.resolve("source.txt"), "version-two");
    TaskResult res = new TaskResult();
    res.taskId = "task-1";
    res.domain = "d";
    res.k = 1;
    res.inputs = Schedule.inputsFingerprint(adapter.taskPrompt("task-1"), files);
    res.status = "ok";
    res.modes.put("agg", Map.of("ok", true));
    res.selections.put("agg", Map.of("ok", true, "chosen_label", "c2"));

    Schedule.writeSummary(summary, res);

    @SuppressWarnings("unchecked")
    Map<String, Object> merged = JSON.readValue(Files.readString(summary), Map.class);
    assertEquals(res.inputs, merged.get("inputs"));
    assertTrue(((Map<?, ?>) merged.get("modes")).containsKey("agg"));
    assertFalse(((Map<?, ?>) merged.get("modes")).containsKey("seq"));
    assertFalse(((Map<?, ?>) merged.get("selections")).containsKey("seq"));
  }
}
