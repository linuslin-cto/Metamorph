package com.metamorph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.metamorph.core.proposer.ProposerError;
import com.metamorph.core.proposer.Reply;
import com.metamorph.core.schedule.Schedule;
import com.metamorph.core.spec.ChannelSpec;
import com.metamorph.demo.DemoAdapter;
import com.metamorph.demo.DemoProposer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProposerFailureTest {
  private static final ObjectMapper JSON = new ObjectMapper();

  @TempDir Path root;
  DemoAdapter adapter;
  ChannelSpec spec;

  static final class FirstCandidateSelector extends DemoProposer {
    FirstCandidateSelector(ChannelSpec spec) {
      super(spec);
    }

    @Override
    public Reply call(String mission, Path contextDir, String label, String harvest, int maxAttempts) {
      if (label.startsWith("select.")) {
        try (Stream<Path> stream = Files.list(contextDir.resolve("candidates"))) {
          String chosen =
              stream
                  .filter(p -> p.getFileName().toString().endsWith(".md"))
                  .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                  .map(p -> p.getFileName().toString().replaceFirst("\\.md$", ""))
                  .findFirst()
                  .orElseThrow();
          Reply reply = new Reply(label, "```choice\n" + chosen + "\n```", true, 1, null);
          calls.add(reply);
          return reply;
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
      return super.call(mission, contextDir, label, harvest, maxAttempts);
    }
  }

  static final class OutageProposer extends DemoProposer {
    OutageProposer(ChannelSpec spec) {
      super(spec);
    }

    @Override
    public Reply call(String mission, Path contextDir, String label, String harvest, int maxAttempts) {
      throw new ProposerError("offline outage", 2, 0.25);
    }
  }

  @BeforeEach
  void setUp() throws Exception {
    adapter = new DemoAdapter(root);
    spec = adapter.spec().channels().get("proposer");
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> runStream(int k, DemoProposer proposer, DemoProposer selector)
      throws Exception {
    return Schedule.runDomain(
        "accounting",
        adapter.tasks(),
        k,
        adapter,
        proposer,
        selector,
        adapter.spec().runsRoot(),
        root.resolve("library").resolve(String.valueOf(k)));
  }

  private Path summaryPath(int k, String taskId) {
    return adapter.spec().runsRoot().resolve("demo-oc-agg" + k).resolve(taskId).resolve("summary.json");
  }

  @Test
  @SuppressWarnings("unchecked")
  void outageIsPartialThenHealthyRerunSucceedsAndSkips() throws Exception {
    for (int k : new int[] {1, 2}) {
      FirstCandidateSelector selector = new FirstCandidateSelector(spec);
      int executions = adapter.executionCount.get();
      Map<String, Object> failed = runStream(k, new OutageProposer(spec), selector);
      assertEquals(0, ((Number) failed.get("done")).intValue());
      assertEquals(2, ((Number) failed.get("failed")).intValue());
      List<Map<String, Object>> failedTasks = (List<Map<String, Object>>) failed.get("tasks");
      assertEquals(List.of("partial", "partial"), failedTasks.stream().map(t -> t.get("status")).toList());

      for (String taskId : adapter.tasks()) {
        Map<String, Object> summary = JSON.readValue(Files.readString(summaryPath(k, taskId)), Map.class);
        assertEquals("partial", summary.get("status"));
        assertEquals(6 * k, ((List<?>) summary.get("errors")).size());
        Map<String, Object> modes = (Map<String, Object>) summary.get("modes");
        Map<String, Object> selections = (Map<String, Object>) summary.get("selections");
        for (String mode : List.of("agg", "seq")) {
          Map<String, Object> record = (Map<String, Object>) modes.get(mode);
          assertFalse((Boolean) record.get("ok"));
          assertTrue((Boolean) ((Map<?, ?>) selections.get(mode)).get("ok"));
          for (Map<String, Object> stage : (List<Map<String, Object>>) record.get("stages")) {
            assertTrue((Boolean) stage.get("execution_failed"));
            assertTrue((Boolean) stage.get("fell_back"));
            assertFalse((Boolean) stage.get("cached"));
            assertEquals(2, ((Number) stage.get("attempts")).intValue());
            assertEquals(0.25, ((Number) stage.get("usd")).doubleValue(), 1e-9);
            assertTrue(
                ((List<String>) summary.get("errors"))
                    .contains(
                        mode
                            + ": "
                            + stage.get("label")
                            + ": proposer execution failed: offline outage"));
          }
          Map<String, Object> ledger = (Map<String, Object>) record.get("ledger");
          assertEquals(4 * k, ((Number) ledger.get("R")).intValue());
          assertEquals(3 * k, ((Number) ledger.get("P")).intValue());
          assertEquals(6 * k, ((Number) ledger.get("session_attempts")).intValue());
          assertEquals(0.75 * k, ((Number) ledger.get("session_usd")).doubleValue(), 1e-9);
          Path chosen = Path.of(String.valueOf(((Map<?, ?>) selections.get(mode)).get("chosen_run_dir")));
          assertTrue(adapter.delivered(chosen));
        }
      }
      assertEquals(14 * k, adapter.executionCount.get() - executions);

      DemoProposer proposer = new DemoProposer(spec);
      executions = adapter.executionCount.get();
      Map<String, Object> healthy = runStream(k, proposer, proposer);
      assertEquals(List.of("ok", "ok"), ((List<Map<String, Object>>) healthy.get("tasks")).stream().map(t -> t.get("status")).toList());
      assertEquals(14 * k, adapter.executionCount.get() - executions);
      for (String taskId : adapter.tasks()) {
        Map<String, Object> summary = JSON.readValue(Files.readString(summaryPath(k, taskId)), Map.class);
        assertEquals("ok", summary.get("status"));
        assertEquals(List.of(), summary.get("errors"));
        for (String mode : List.of("agg", "seq")) {
          Map<String, Object> record =
              (Map<String, Object>) ((Map<String, Object>) summary.get("modes")).get(mode);
          assertTrue((Boolean) record.get("ok"));
          assertTrue(
              ((List<Map<String, Object>>) record.get("stages"))
                  .stream().noneMatch(s -> Boolean.TRUE.equals(s.get("execution_failed"))));
          Map<String, Object> ledger = (Map<String, Object>) record.get("ledger");
          assertEquals(3 * k, ((Number) ledger.get("session_attempts")).intValue());
          assertEquals(0.0, ((Number) ledger.get("session_usd")).doubleValue(), 1e-9);
        }
      }
      executions = adapter.executionCount.get();
      int calls = proposer.calls().size();
      Map<String, Object> resumed = runStream(k, proposer, proposer);
      assertEquals(
          List.of("skipped", "skipped"),
          ((List<Map<String, Object>>) resumed.get("tasks")).stream().map(t -> t.get("status")).toList());
      assertEquals(executions, adapter.executionCount.get());
      assertEquals(calls, proposer.calls().size());
    }
  }
}
