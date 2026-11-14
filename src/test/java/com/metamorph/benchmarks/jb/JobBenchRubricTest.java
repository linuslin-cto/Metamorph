package com.metamorph.benchmarks.jb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.metamorph.core.judge.Judge;
import com.metamorph.core.judge.Scored;
import com.metamorph.core.report.Report;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JobBenchRubricTest {
  @TempDir Path root;

  @Test
  void workerExceptionIsUngradedAndQuarantined() throws Exception {
    Map<String, Object> fail =
        JobBenchRubric.failedRubricResult(0, Map.of("rubric", "Read the local output"), "boom");
    Path details = root.resolve("details.json");
    JobBenchRubric.writeDetails(details, "agg1", "fixture-judge", List.of(fail));
    Map<String, Object> score = JobBenchRubric.scoreFromResults(List.of(fail));
    assertNull(score);
    Path quarantined = JobBenchRubric.quarantineDetails(details);
    assertTrue(Files.isRegularFile(quarantined));
    assertFalse(Files.exists(details));

    Scored scored = new Scored("accounting/task1", "agg", 1, "agg1", "run-1", false, "");
    scored.error = "worker failure quarantined";
    Judge.write(List.of(scored), root.resolve("judged.json"));
    List<Scored> loaded = Judge.loadRows(root.resolve("judged.json"));
    Report.Summary summary = Report.summarise(loaded, null, null).get(0);
    assertFalse(loaded.get(0).graded());
    assertEquals(0, summary.n);
    assertEquals(1, summary.nUngraded);
  }

  @Test
  void partialWorkerFailureDoesNotLowerMixedScore() {
    Map<String, Object> score =
        JobBenchRubric.scoreFromResults(
            List.of(JobBenchRubric.modelResult(true), JobBenchRubric.failedRubricResult(1, Map.of(), "x")));
    assertNull(score);
  }

  @Test
  void validZeroAndFullCreditRemainGraded() {
    Map<String, Object> zero = JobBenchRubric.scoreFromResults(List.of(JobBenchRubric.modelResult(false)));
    assertEquals(0, zero.get("n_passed"));
    assertEquals(1, zero.get("n_criteria"));
    assertFalse(Boolean.TRUE.equals(zero.get("all_pass")));
    assertTrue(Judge.hasGrade((Integer) zero.get("n_passed"), (Integer) zero.get("n_criteria")));

    Map<String, Object> full = JobBenchRubric.scoreFromResults(List.of(JobBenchRubric.modelResult(true)));
    assertEquals(1, full.get("n_passed"));
    assertTrue(Boolean.TRUE.equals(full.get("all_pass")));
  }

  @Test
  void transportFailureNullPassedStaysUngraded() {
    Map<String, Object> transport = new java.util.LinkedHashMap<>();
    transport.put("rubric_passed", null);
    transport.put("failed", false);
    assertNull(JobBenchRubric.scoreFromResults(List.of(transport)));
  }
}
