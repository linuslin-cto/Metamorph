package com.metamorph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.metamorph.cli.DemoCommand;
import com.metamorph.core.judge.Judge;
import com.metamorph.core.judge.Scored;
import com.metamorph.core.report.Report;
import com.metamorph.core.schedule.Schedule;
import com.metamorph.demo.DemoAdapter;
import com.metamorph.demo.DemoProposer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DemoEndToEndJudgeTest {
  @TempDir Path root;

  @Test
  void demoSweepJudgesSelectedRunsAndRendersReport() throws Exception {
    DemoAdapter adapter = new DemoAdapter(root);
    DemoProposer prop = new DemoProposer(adapter.spec().channels().get("proposer"));
    Schedule.runDomain(
        "accounting",
        adapter.tasks(),
        1,
        adapter,
        prop,
        adapter.spec().runsRoot(),
        root.resolve("library").resolve("1"));

    DemoCommand.judgeAndReport(adapter, 1);

    Path judged = adapter.spec().runsRoot().resolve("demo-oc-agg1").resolve("judged.json");
    assertTrue(Files.isRegularFile(judged));
    List<Scored> rows = Judge.loadRows(judged);
    assertEquals(4, rows.size()); // 2 tasks × 2 modes
    assertTrue(rows.stream().allMatch(Scored::graded));
    assertTrue(rows.stream().allMatch(r -> r.allPass));
    String text = Report.render(Report.summarise(rows, null, null), "demo", "demo");
    assertTrue(text.contains("score"));
    assertTrue(text.contains("100.00%") || text.contains("NOISE FLOOR"));
  }
}
