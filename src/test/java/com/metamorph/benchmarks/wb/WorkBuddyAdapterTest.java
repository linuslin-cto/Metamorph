package com.metamorph.benchmarks.wb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkBuddyAdapterTest {
  @TempDir Path root;

  @Test
  void scoreFromPayloadPrecedenceAndRejectsOutOfRange() {
    assertEquals(0.75, WorkBuddyScore.scoreFromPayload(Map.of("reward", 0.75)));
    assertEquals(0.5, WorkBuddyScore.scoreFromPayload(Map.of("overall", 0.5, "reward", "bad")));
    assertEquals(
        0.5, WorkBuddyScore.scoreFromPayload(Map.of("tests_passed", 1, "tests_total", 2)));
    assertNull(WorkBuddyScore.scoreFromPayload(Map.of("reward", 1.5)));
    assertNull(WorkBuddyScore.scoreFromPayload(Map.of()));
  }

  @Test
  void deliveredNeedsBodyCharsAndScoreHandlesKilled() throws Exception {
    WorkBuddyAdapter adapter = new WorkBuddyAdapter(root);
    assertTrue(Files.isDirectory(adapter.baseHarness()));
    assertTrue(Files.isRegularFile(adapter.baseHarness().resolve("scripts/README.md")));

    Path shortRun = root.resolve("short");
    Files.createDirectories(shortRun.resolve("output"));
    Files.writeString(shortRun.resolve("output").resolve("a.txt"), "tiny");
    assertFalse(adapter.delivered(shortRun));

    Path ok = root.resolve("ok");
    Files.createDirectories(ok.resolve("output"));
    Files.writeString(ok.resolve("output").resolve("report.txt"), "x".repeat(200));
    assertTrue(adapter.delivered(ok));
    Files.createDirectories(ok.resolve("verifier"));
    Files.writeString(ok.resolve("verifier").resolve("score.json"), "{\"reward\":1.0}");
    Map<String, Object> scored = adapter.score(ok, "t1");
    assertEquals(100, scored.get("n_passed"));
    assertEquals(true, scored.get("all_pass"));

    Path killed = root.resolve("killed");
    Files.createDirectories(killed.resolve("agent"));
    Files.writeString(
        killed.resolve("agent").resolve("oc-output.txt"),
        "{\"type\":\"error\",\"error\":{\"data\":{\"message\":\"cut short\"}}}\n");
    assertTrue(adapter.killed(killed));
    assertNull(adapter.score(killed, "t1"));
  }

  @Test
  void validateTreeRejectsExtraJinja() throws Exception {
    WorkBuddyAdapter adapter = new WorkBuddyAdapter(root);
    Map<String, String> bad = new LinkedHashMap<>();
    bad.put("systemprompt.md", "hello {{ leak }}");
    bad.put("guardrails.md", "");
    bad.put("LongTermMEMORY.md", "");
    bad.put("tool_descriptions/tool_guidance.md", "");
    assertThrows(IllegalArgumentException.class, () -> adapter.validateTree(bad));

    Map<String, String> good = new LinkedHashMap<>(bad);
    good.put("systemprompt.md", "hello");
    adapter.validateTree(good);
  }
}
