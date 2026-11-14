package com.metamorph.core.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.metamorph.core.judge.Scored;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReportTest {
  @Test
  void summariseAveragesGradedRowsAndExcludesUngraded() {
    Scored a = scored("agg", 1, "agg1", 1, 2, false);
    Scored b = scored("agg", 1, "agg1", 2, 2, true);
    Scored ug = scored("agg", 1, "agg1", null, null, false);
    List<Report.Summary> summaries = Report.summarise(List.of(a, b, ug), null, null);
    assertEquals(1, summaries.size());
    assertEquals(2, summaries.get(0).n);
    assertEquals(1, summaries.get(0).nUngraded);
    assertEquals(75.0, summaries.get(0).score, 1e-9);
    assertEquals(50.0, summaries.get(0).allPass, 1e-9);
    assertEquals(2, summaries.get(0).choseFinal);
  }

  @Test
  void renderIncludesNoiseFloorNoteAtK1() {
    Scored a = scored("agg", 1, "agg1", 1, 2, false);
    Scored s = scored("seq", 1, "c1", 2, 2, true);
    String text = Report.render(Report.summarise(List.of(a, s), null, null), "Results", "lab");
    assertTrue(text.contains("NOISE FLOOR"));
    assertTrue(text.contains("partial"));
  }

  private static Scored scored(
      String mode, int k, String label, Integer passed, Integer criteria, boolean allPass) {
    Scored sc = new Scored("t", mode, k, label, "rid", false, "");
    sc.nPassed = passed;
    sc.nCriteria = criteria;
    sc.allPass = allPass;
    return sc;
  }
}
