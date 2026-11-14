package com.metamorph.core.judge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JudgeMergeTest {
  private static final ObjectMapper JSON = new ObjectMapper();

  @Test
  void gradedRowSurvivesFailedRejudgeOfSameSelection() {
    Scored graded = scored("t1", "agg", 1, "rid", "t0", 1, 2, true, "");
    Scored failed = scored("t1", "agg", 1, "rid", "t0", null, null, false, "TimeoutException: x");
    List<Scored> merged = Judge.merge(List.of(graded), List.of(failed));
    assertEquals(1, merged.size());
    assertTrue(merged.get(0).graded());
    assertEquals(1, merged.get(0).nPassed);
  }

  @Test
  void newSelectedAtReplacesEvenWhenRejudgeFails() {
    Scored old = scored("t1", "agg", 1, "rid", "t0", 2, 2, true, "");
    Scored neu = scored("t1", "agg", 1, "rid", "t1", null, null, false, "judge returned nothing");
    List<Scored> merged = Judge.merge(List.of(old), List.of(neu));
    assertEquals(1, merged.size());
    assertFalse(merged.get(0).graded());
    assertEquals("t1", merged.get(0).selectedAt);
  }

  @Test
  void writeLoadRoundTripPreservesGrade(@TempDir Path root) throws Exception {
    Scored a = scored("contracts/task-1", "agg", 1, "run-a", "2026", 1, 2, false, "");
    Path out = Judge.write(List.of(a), root.resolve("judged.json"));
    List<Scored> loaded = Judge.loadRows(out);
    assertEquals(1, loaded.size());
    assertTrue(loaded.get(0).graded());
    assertEquals(1, loaded.get(0).nPassed);
    assertEquals(2, loaded.get(0).nCriteria);
    assertEquals(0.5, loaded.get(0).frac(), 1e-9);
  }

  @Test
  void collectSelectionsReadsArmAsMode(@TempDir Path root) throws Exception {
    Path task = root.resolve("task-1");
    Files.createDirectories(task);
    Map<String, Object> sel = new LinkedHashMap<>();
    sel.put("task_id", "task-1");
    sel.put("arm", "agg");
    sel.put("k", 1);
    sel.put("chosen_label", "agg1");
    sel.put("ok", true);
    Map<String, Object> summary = new LinkedHashMap<>();
    summary.put("task_id", "task-1");
    summary.put("k", 1);
    summary.put("selections", Map.of("agg", sel));
    JSON.writeValue(task.resolve("summary.json").toFile(), summary);
    List<Map<String, Object>> got = Judge.collectSelections(root, List.of("agg"));
    assertEquals(1, got.size());
    assertEquals("agg", got.get(0).get("mode"));
  }

  private static Scored scored(
      String taskId,
      String mode,
      int k,
      String runId,
      String selectedAt,
      Integer passed,
      Integer criteria,
      boolean allPass,
      String error) {
    Scored s = new Scored(taskId, mode, k, "agg1", runId, false, selectedAt, error);
    s.nPassed = passed;
    s.nCriteria = criteria;
    s.allPass = allPass;
    return s;
  }
}
