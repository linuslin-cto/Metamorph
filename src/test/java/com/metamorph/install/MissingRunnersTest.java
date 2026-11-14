package com.metamorph.install;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MissingRunnersTest {
  @TempDir Path root;

  @Test
  void requiredPathsCountAndChecklistKeys() throws Exception {
    Path tp = root.resolve("third_party");
    Files.createDirectories(tp.resolve("patches"));
    var need = MissingRunners.requiredPaths(tp);
    assertEquals(MissingRunners.PATCHES.size() + MissingRunners.RUNNERS.size(), need.size());
    assertEquals(need.size(), need.stream().filter(p -> !Files.exists(p)).count());

    var checklist = MissingRunners.checklist();
    assertTrue(checklist.containsKey("patches/opencode-compat.diff"));
    assertTrue(checklist.containsKey("wb_runner/agents/oc_agent.py"));

    // Prefer sibling reference third_party when present; otherwise empty tree → non-empty report.
    String msg = MissingRunners.report();
    if (Files.isDirectory(MissingRunners.thirdPartyRoot())
        && MissingRunners.requiredPaths(MissingRunners.thirdPartyRoot()).stream()
            .allMatch(Files::exists)) {
      assertEquals("", msg);
    } else {
      assertTrue(msg.contains("missing under third_party"), msg);
    }
  }
}
