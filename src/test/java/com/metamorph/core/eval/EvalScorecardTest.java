package com.metamorph.core.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EvalScorecardTest {
  @TempDir Path root;

  @Test
  void offlineDemoScorecardPassesAtK1WithNoiseFloor() throws Exception {
    EvalScorecard.Result result = EvalScorecard.runOfflineDemo(root.resolve("k1"), 1);
    assertTrue(result.ok(), EvalScorecard.renderMarkdown(result));
    assertTrue(result.overall() >= 99.0, "overall=" + result.overall());
    assertTrue(
        result.dimensions().stream().anyMatch(d -> "noise_floor_k1".equals(d.id()) && d.pass()),
        EvalScorecard.renderMarkdown(result));
    Path md = EvalScorecard.writeMarkdown(result, root.resolve("EVAL.md"));
    assertTrue(Files.readString(md).contains("重新生成"));
  }

  @Test
  void offlineDemoScorecardPassesAtK2ClearingNoiseFloor() throws Exception {
    EvalScorecard.Result result = EvalScorecard.runOfflineDemo(root.resolve("k2"), 2);
    assertTrue(result.ok(), EvalScorecard.renderMarkdown(result));
    assertTrue(
        result.dimensions().stream().anyMatch(d -> "modes_clear_at_k".equals(d.id()) && d.pass()),
        EvalScorecard.renderMarkdown(result));
    assertTrue(
        result.dimensions().stream()
            .filter(d -> "budget_identity".equals(d.id()))
            .anyMatch(d -> d.detail().contains("R=8") && d.detail().contains("P=6")));
    assertEquals(
        2,
        result.summaries().stream().filter(s -> s.k == 2).count());
  }
}
