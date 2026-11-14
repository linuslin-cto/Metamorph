package com.metamorph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.metamorph.core.schedule.Schedule;
import com.metamorph.demo.DemoAdapter;
import com.metamorph.demo.DemoProposer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkflowTest {
  private static final ObjectMapper JSON = new ObjectMapper();

  @TempDir Path root;

  @Test
  @SuppressWarnings("unchecked")
  void recursionBudgetAndResume() throws Exception {
    DemoAdapter adapter = new DemoAdapter(root);
    DemoProposer proposer = new DemoProposer(adapter.spec().channels().get("proposer"));

    for (int k : new int[] {1, 2}) {
      Map<String, Object> result =
          Schedule.runDomain(
              "accounting",
              adapter.tasks(),
              k,
              adapter,
              proposer,
              adapter.spec().runsRoot(),
              root.resolve("library").resolve(String.valueOf(k)));
      assertEquals(2, ((Number) result.get("done")).intValue());
      assertEquals(0, ((Number) result.get("failed")).intValue());

      for (String taskId : adapter.tasks()) {
        Path summary =
            adapter.spec().runsRoot().resolve("demo-oc-agg" + k).resolve(taskId).resolve("summary.json");
        Map<String, Object> doc = JSON.readValue(Files.readString(summary), Map.class);
        Map<String, Object> modes = (Map<String, Object>) doc.get("modes");
        Map<String, Object> selections = (Map<String, Object>) doc.get("selections");
        for (String mode : List.of("agg", "seq")) {
          Map<String, Object> ledger =
              (Map<String, Object>) ((Map<String, Object>) modes.get(mode)).get("ledger");
          assertEquals(4 * k, ((Number) ledger.get("R")).intValue());
          assertEquals(3 * k, ((Number) ledger.get("P")).intValue());
          Map<String, Object> selection = (Map<String, Object>) selections.get(mode);
          String expected = taskId.equals("ledger-1") ? "Total: 85.15\n" : "Total: 20.25\n";
          String got =
              adapter
                  .deliverableText(Path.of(String.valueOf(selection.get("chosen_run_dir"))))
                  .replace("\r\n", "\n");
          assertEquals(expected, got);
        }
      }

      int executions = adapter.executionCount.get();
      int calls = proposer.calls().size();
      Map<String, Object> resumed =
          Schedule.runDomain(
              "accounting",
              adapter.tasks(),
              k,
              adapter,
              proposer,
              adapter.spec().runsRoot(),
              root.resolve("library").resolve(String.valueOf(k)));
      assertEquals(executions, adapter.executionCount.get());
      assertEquals(calls, proposer.calls().size());
      List<Map<String, Object>> tasks = (List<Map<String, Object>>) resumed.get("tasks");
      assertEquals(List.of("skipped", "skipped"), tasks.stream().map(t -> t.get("status")).toList());
    }
    assertTrue(proposer.calls().stream().noneMatch(r -> r.label().startsWith("select.") && (r.label().endsWith(".agg1") || r.label().endsWith(".seq1"))));
  }
}
