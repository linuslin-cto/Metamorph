package com.metamorph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.metamorph.core.proposer.Proposer;
import com.metamorph.core.proposer.Reply;
import com.metamorph.core.recursion.Recursion;
import com.metamorph.core.recursion.Stage;
import com.metamorph.core.tree.HarnessTree;
import com.metamorph.demo.DemoAdapter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class StageRetryTest {
  @TempDir Path root;

  static final class RetryProposer extends Proposer {
    private final String defect;
    private final String extra;
    final List<String> seen = new ArrayList<>();
    final List<String> missions = new ArrayList<>();

    RetryProposer(DemoAdapter adapter, String defect, String extra) {
      super(adapter.spec().channels().get("proposer"));
      this.defect = defect;
      this.extra = extra;
    }

    @Override
    public Reply call(String mission, Path contextDir, String label, String harvest, int maxAttempts) {
      try {
        Path harness = contextDir.resolve("harness");
        Path script = harness.resolve("scripts/summarize.py");
        seen.add(Files.readString(script));
        missions.add(mission);
        if (!mission.contains("PREVIOUS ATTEMPT WAS REJECTED")) {
          Path defectPath = harness.resolve(defect);
          Files.createDirectories(defectPath.getParent());
          Files.writeString(defectPath, extra);
          Files.writeString(script, Files.readString(script).replace(" if amount > 0", ""));
        } else if (Files.isRegularFile(harness.resolve(defect))) {
          Files.delete(harness.resolve(defect));
        }
        Reply reply = new Reply(label, "<notes>Keep signed amounts.</notes>", true, 1, harness);
        calls.add(reply);
        return reply;
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }

  @ParameterizedTest
  @CsvSource({
    "scripts/check.py, def broken(:",
    "scripts/summarize.py.bak, # an editor's backup, off the editable surface"
  })
  void retryKeepsTheRejectedAttemptsEdits(String defect, String extra) throws Exception {
    DemoAdapter adapter = new DemoAdapter(root.resolve(defect.replace('/', '_')));
    RetryProposer prop = new RetryProposer(adapter, defect, extra);
    Path work = adapter.spec().runsRoot().getParent().resolve("retry");
    Map<String, String> fallback =
        HarnessTree.readTree(adapter.substrate().surface(), adapter.baseHarness());
    Stage stage =
        Recursion.runStage(
            "inv1",
            "INV",
            "Repair the script",
            fallback,
            adapter,
            prop,
            work.resolve("context"),
            "ledger-1",
            List.of(),
            adapter.baseHarness(),
            null,
            List.of(),
            null);
    assertTrue(prop.missions.get(1).contains(defect));
    assertFalse(prop.seen.get(1).contains(" if amount > 0"));
    assertEquals(false, stage.fellBack);
    assertEquals(false, stage.keptBase);
    assertEquals(2, stage.attempts);
    assertFalse(stage.tree.get("scripts/summarize.py").contains(" if amount > 0"));
    assertFalse(stage.tree.containsKey(defect));
  }
}
