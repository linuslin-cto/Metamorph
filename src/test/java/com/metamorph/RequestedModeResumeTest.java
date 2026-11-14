package com.metamorph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.metamorph.core.proposer.ProposerError;
import com.metamorph.core.proposer.Reply;
import com.metamorph.core.schedule.Schedule;
import com.metamorph.demo.DemoAdapter;
import com.metamorph.demo.DemoProposer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RequestedModeResumeTest {
  private static final ObjectMapper JSON = new ObjectMapper();

  static final class SequentialFailure extends DemoProposer {
    SequentialFailure(DemoAdapter adapter) {
      super(adapter.spec().channels().get("proposer"));
    }

    @Override
    public Reply call(String mission, Path contextDir, String label, String harvest, int maxAttempts) {
      if ("select.seq2".equals(label)) {
        throw new ProposerError("temporary sequential selector failure", 1, 0.0);
      }
      return super.call(mission, contextDir, label, harvest, maxAttempts);
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void validParallelModeResumesWhenSequentialSelectionFailed(@TempDir Path root) throws Exception {
    DemoAdapter adapter = new DemoAdapter(root);
    SequentialFailure proposer = new SequentialFailure(adapter);

    java.util.function.Function<List<String>, Map<String, Object>> run =
        modes -> {
          try {
            return Schedule.runDomain(
                "accounting",
                List.of("ledger-1"),
                2,
                adapter,
                proposer,
                null,
                adapter.spec().runsRoot(),
                root.resolve("library"),
                modes);
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        };

    assertEquals("partial", ((List<Map<String, Object>>) run.apply(List.of("agg", "seq")).get("tasks")).get(0).get("status"));
    Path summary = adapter.spec().runsRoot().resolve("demo-oc-agg2/ledger-1/summary.json");
    Map<String, Object> record = JSON.readValue(Files.readString(summary), Map.class);
    assertTrue((Boolean) ((Map<?, ?>) ((Map<?, ?>) record.get("selections")).get("agg")).get("ok"));
    assertFalse((Boolean) ((Map<?, ?>) ((Map<?, ?>) record.get("selections")).get("seq")).get("ok"));

    int exec = adapter.executionCount.get();
    int calls = proposer.calls().size();
    for (int i = 0; i < 2; i++) {
      assertEquals(
          "skipped",
          ((List<Map<String, Object>>) run.apply(List.of("agg")).get("tasks")).get(0).get("status"));
      assertEquals(exec, adapter.executionCount.get());
      assertEquals(calls, proposer.calls().size());
    }
    assertEquals(
        "partial",
        ((List<Map<String, Object>>) run.apply(List.of("seq")).get("tasks")).get(0).get("status"));
    assertTrue(adapter.executionCount.get() > exec);
  }
}
