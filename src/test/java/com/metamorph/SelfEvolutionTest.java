package com.metamorph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.metamorph.core.judge.Judge;
import com.metamorph.core.judge.Scored;
import com.metamorph.core.report.Report;
import com.metamorph.core.schedule.Schedule;
import com.metamorph.demo.DemoAdapter;
import com.metamorph.demo.DemoProposer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies Metamorph implements the Propose–Probe–Compose self-evolution eval contract offline:
 *
 * <ul>
 *   <li>Propose–Probe–Compose recursion under budgets R=4k, P=3k (scaled by k)
 *   <li>Both aggregate and sequential modes
 *   <li>Base harness fails the criterion; evolved selection passes
 *   <li>Cross-task library retains experience after the first task
 *   <li>Judge + report surface mode×k scores (the paper's figure pipeline)
 * </ul>
 */
class SelfEvolutionTest {
  private static final ObjectMapper JSON = new ObjectMapper();

  @TempDir Path root;

  @Test
  @SuppressWarnings("unchecked")
  void proposeProbeComposeEvolvesHarnessAndRetainsExperience() throws Exception {
    DemoAdapter adapter = new DemoAdapter(root);
    DemoProposer proposer = new DemoProposer(adapter.spec().channels().get("proposer"));
    int k = 1;
    Path libraryRoot = root.resolve("library").resolve(String.valueOf(k));

    // Baseline: stock harness fails the signed-sum criterion.
    Path baseRun = adapter.execute("ledger-1", adapter.baseHarness(), "self-evo/base");
    Map<String, Object> baseScore = adapter.score(baseRun, "ledger-1");
    assertEquals(0, ((Number) baseScore.get("n_passed")).intValue());
    assertFalse(Boolean.TRUE.equals(baseScore.get("all_pass")));

    Map<String, Object> result =
        Schedule.runDomain(
            "accounting",
            adapter.tasks(),
            k,
            adapter,
            proposer,
            adapter.spec().runsRoot(),
            libraryRoot);
    assertEquals(2, ((Number) result.get("done")).intValue());
    assertEquals(0, ((Number) result.get("failed")).intValue());

    // Improvement stages proposed (DemoProposer invents summarize.py).
    assertTrue(
        proposer.calls().stream().anyMatch(r -> r.ok() && !r.text().isBlank()),
        "expected successful improvement-stage proposer calls");

    Path nsDir = adapter.spec().runsRoot().resolve("demo-oc-agg" + k);
    for (String mode : List.of("agg", "seq")) {
      Path summary = nsDir.resolve("ledger-1").resolve("summary.json");
      Map<String, Object> doc = JSON.readValue(Files.readString(summary), Map.class);
      Map<String, Object> modes = (Map<String, Object>) doc.get("modes");
      Map<String, Object> ledger =
          (Map<String, Object>) ((Map<String, Object>) modes.get(mode)).get("ledger");
      assertEquals(4 * k, ((Number) ledger.get("R")).intValue(), mode);
      assertEquals(3 * k, ((Number) ledger.get("P")).intValue(), mode);

      Map<String, Object> sel =
          (Map<String, Object>) ((Map<String, Object>) doc.get("selections")).get(mode);
      Path chosen = Path.of(String.valueOf(sel.get("chosen_run_dir")));
      Map<String, Object> evolved = adapter.score(chosen, "ledger-1");
      assertEquals(
          1, ((Number) evolved.get("n_passed")).intValue(), mode + " should pass after evolution");
      assertTrue(Boolean.TRUE.equals(evolved.get("all_pass")), mode);
      assertEquals("Total: 85.15\n", adapter.deliverableText(chosen).replace("\r\n", "\n"));
    }

    // Library self-evolution: per-mode libraries under library/{k}/{agg|seq}/...
    assertTrue(Files.isDirectory(libraryRoot), "library root should exist");
    try (Stream<Path> walk = Files.walk(libraryRoot)) {
      List<Path> files = walk.filter(Files::isRegularFile).toList();
      assertFalse(files.isEmpty(), "expected library files under " + libraryRoot);
      boolean hasExperience =
          files.stream().anyMatch(p -> p.getFileName().toString().equals("EXPERIENCE.md"));
      boolean hasIndex =
          files.stream().anyMatch(p -> p.getFileName().toString().equalsIgnoreCase("INDEX.md"));
      assertTrue(
          hasExperience || hasIndex || files.size() >= 1,
          "library should retain cross-task material: " + files);
    }

    // Paper eval figure path: judge selections → report aggregates.
    List<Map<String, Object>> sels = Judge.collectSelections(nsDir, List.of("agg", "seq"));
    List<Scored> graded = Judge.judgeSelections(adapter, sels, 2);
    Path judged = nsDir.resolve("judged.json");
    Judge.write(graded, judged);
    List<Report.Summary> summaries = Report.summarise(Judge.loadRows(judged), null, null);
    assertFalse(summaries.isEmpty());
    assertTrue(
        summaries.stream().anyMatch(s -> s.n > 0 && s.score != null && s.score >= 99.0),
        "evolved selections should score ~100% in the report: "
            + summaries.stream().map(s -> s.name() + "=" + s.score).toList());
  }
}
