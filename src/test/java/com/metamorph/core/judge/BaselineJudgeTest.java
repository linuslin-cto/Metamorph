package com.metamorph.core.judge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.metamorph.core.schedule.Namespace;
import com.metamorph.core.schedule.TaskResult;
import com.metamorph.core.spec.BenchmarkAdapter;
import com.metamorph.core.spec.BenchPrompts;
import com.metamorph.core.spec.BenchSpec;
import com.metamorph.core.spec.ChannelSpec;
import com.metamorph.core.spec.SubstrateLike;
import com.metamorph.substrate.OpencodeSubstrate;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Mirrors {@code tests/test_baseline_judge.py} grading contract without the Python LAB CLI —
 * uses cached {@code scores.json} + {@link Judge#hasGrade}.
 */
class BaselineJudgeTest {
  private static final ObjectMapper JSON = new ObjectMapper();

  static final class CachedScoreAdapter implements BenchmarkAdapter {
    private final BenchSpec spec;
    private final OpencodeSubstrate substrate = new OpencodeSubstrate();

    CachedScoreAdapter(Path runsRoot) {
      ChannelSpec ch = new ChannelSpec("opencode", "fixture/executor");
      this.spec =
          BenchSpec.builder("lab", runsRoot, new BenchPrompts("review contracts"))
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
      return List.of("contracts/task-1");
    }

    @Override
    public String domainOf(String taskId) {
      return "contracts";
    }

    @Override
    public String taskPrompt(String taskId) {
      return "review";
    }

    @Override
    public Path taskFiles(String taskId) {
      return null;
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
      return "";
    }

    @Override
    public Path baseHarness() {
      return Path.of(".");
    }

    @Override
    public void validateTree(Map<String, String> tree) {}

    @Override
    public Map<String, Object> score(Path runDir, String taskId) {
      try {
        return Judge.scoreFromCache(runDir);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }

  private record JudgeOutcome(int exitHint, List<Map<String, Object>> rows) {}

  private JudgeOutcome judgeCached(Path root, Map<String, Object> payload, boolean bases)
      throws Exception {
    Path run = root.resolve("local-run");
    Files.createDirectories(run);
    JSON.writeValue(run.resolve("scores.json").toFile(), payload);

    Namespace namespace = new Namespace("lab", "oc", "agg", 1);
    Path adaptRoot = root.resolve("lab").resolve("adapt");
    Path taskDir = namespace.taskDir(adaptRoot, "contracts/task-1");
    Files.createDirectories(taskDir);

    Map<String, Object> selected = new LinkedHashMap<>();
    selected.put("task_id", "contracts/task-1");
    selected.put("mode", "agg");
    selected.put("k", 1);
    selected.put("chosen_label", "agg1");
    selected.put("chosen_run_id", "adapted-1");
    selected.put("chosen_run_dir", run.toString());
    selected.put("ok", true);

    TaskResult result = new TaskResult();
    result.taskId = "contracts/task-1";
    result.domain = "contracts";
    result.k = 1;
    result.status = "ok";
    result.modes.put("agg", Map.of("ok", true));
    result.base.add("base1");
    result.selections.put("agg", selected);
    // Write via package-visible path using Schedule reflection — use public runTask summary by
    // writing JSON directly matching _write_summary shape:
    JSON.writerWithDefaultPrettyPrinter()
        .writeValue(taskDir.resolve("summary.json").toFile(), result.toMap());

    CachedScoreAdapter adapter = new CachedScoreAdapter(root);
    List<Map<String, Object>> selections = new ArrayList<>();
    if (bases) {
      selections.add(
          Map.of(
              "task_id",
              "contracts/task-1",
              "mode",
              "agg",
              "k",
              1,
              "chosen_label",
              "base1",
              "chosen_run_id",
              "reference-1",
              "chosen_run_dir",
              run.toString(),
              "chosen_is_base",
              true,
              "ok",
              true));
    } else {
      selections.add(selected);
    }

    List<Scored> scored = Judge.judgeSelections(adapter, selections, 2);
    List<Map<String, Object>> rows = scored.stream().map(Scored::toMap).toList();
    long graded = scored.stream().filter(Scored::graded).count();
    int exitHint = graded == 0 ? 2 : 0;
    Path cache =
        adaptRoot
            .resolve(namespace.toString())
            .resolve(bases ? "base_judged.json" : "judged.json");
    Files.createDirectories(cache.getParent());
    JSON.writerWithDefaultPrettyPrinter().writeValue(cache.toFile(), rows);
    return new JudgeOutcome(exitHint, rows);
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(ints = {0, -1})
  void missingOrNonpositiveCriteriaAreUngraded(Integer criteria, @TempDir Path root)
      throws Exception {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("n_passed", 0);
    payload.put("n_criteria", criteria);
    payload.put("all_pass", false);
    JudgeOutcome out = judgeCached(root, payload, true);
    assertEquals(2, out.exitHint());
    assertNull(out.rows().get(0).get("score"));
  }

  @ParameterizedTest
  @CsvSource({"0", "1", "2"})
  void positiveCriteriaPreserveZeroPartialAndFullScores(int passed, @TempDir Path root)
      throws Exception {
    Map<String, Object> payload =
        Map.of("n_passed", passed, "n_criteria", 2, "all_pass", passed == 2);
    JudgeOutcome out = judgeCached(root, payload, true);
    assertEquals(0, out.exitHint());
    assertEquals(payload.get("n_passed"), ((Map<?, ?>) out.rows().get(0).get("score")).get("n_passed"));
    assertTrue(Boolean.TRUE.equals(out.rows().get(0).get("graded")));
  }

  @Test
  void selectedRunUsesTheSameCriteriaContract(@TempDir Path root) throws Exception {
    for (Integer criteria : new Integer[] {null, 0, 2}) {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("n_passed", 0);
      payload.put("n_criteria", criteria);
      payload.put("all_pass", false);
      JudgeOutcome out = judgeCached(root.resolve("c" + criteria), payload, false);
      assertEquals(criteria != null && criteria == 2 ? 0 : 2, out.exitHint());
      assertEquals(criteria != null && criteria == 2, out.rows().get(0).get("graded"));
    }
  }

  @Test
  void absentGradeFieldsDoNotCountAsCompletedGrade(@TempDir Path root) throws Exception {
    JudgeOutcome out = judgeCached(root, Map.of(), true);
    assertEquals(2, out.exitHint());
    assertNull(out.rows().get(0).get("score"));
  }

  @Test
  void missingNumeratorIsUngradedForBaselineAndSelectedRuns(@TempDir Path root) throws Exception {
    for (boolean bases : new boolean[] {false, true}) {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("n_criteria", 2);
      payload.put("all_pass", false);
      JudgeOutcome out = judgeCached(root.resolve(bases ? "b" : "s"), payload, bases);
      assertEquals(2, out.exitHint());
      if (bases) {
        assertNull(out.rows().get(0).get("score"));
      } else {
        assertFalse(Boolean.TRUE.equals(out.rows().get(0).get("graded")));
        assertNull(out.rows().get(0).get("frac"));
      }
    }
  }

  @Test
  void hasGradeContract() {
    assertFalse(Judge.hasGrade(null, 2));
    assertFalse(Judge.hasGrade(0, null));
    assertFalse(Judge.hasGrade(0, 0));
    assertFalse(Judge.hasGrade(0, -1));
    assertTrue(Judge.hasGrade(0, 2));
    assertTrue(Judge.hasGrade(2, 2));
  }
}
