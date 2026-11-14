package com.metamorph.core.runmark;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RunMarkTest {
  @Test
  void fingerprintBaseAndTree(@TempDir Path root) throws Exception {
    assertEquals("base", RunMark.fingerprint(null));
    assertEquals("absent", RunMark.fingerprint(root.resolve("missing")));
    Path harness = root.resolve("h");
    Files.createDirectories(harness);
    Files.writeString(harness.resolve("AGENTS.md"), "hello");
    String a = RunMark.fingerprint(harness);
    Files.writeString(harness.resolve("AGENTS.md"), "hello!");
    String b = RunMark.fingerprint(harness);
    assertEquals(16, a.length());
    assertNotEquals(a, b);
  }

  @Test
  void inputsFingerprintStable(@TempDir Path root) throws Exception {
    Path src = root.resolve("src");
    Files.createDirectories(src);
    Files.writeString(src.resolve("a.txt"), "1");
    String first = RunMark.inputsFingerprint("task", src);
    String second = RunMark.inputsFingerprint("task", src);
    assertEquals(first, second);
    assertNotEquals(first, RunMark.inputsFingerprint("other", src));
  }

  @Test
  void markCompleteWritesMarker(@TempDir Path root) throws Exception {
    Path run = root.resolve("run");
    RunMark.markComplete(run, "rid-1", null, "abcd", null);
    assertTrue(RunMark.isComplete(run));
    assertTrue(Files.readString(run.resolve(RunMark.MARK)).contains("rid-1"));
  }

  @Test
  void callerWithoutInputsKeepsPreviousBehaviour(@TempDir Path root) throws Exception {
    Path run = root.resolve("run");
    RunMark.markComplete(run, "r1", null, "aaaa", null);
    assertTrue(RunMark.isComplete(run));
    assertEquals("reusable", RunMark.whyNot(run));
  }

  @Test
  void fingerprintIgnoresMarkPycacheAndDsStore(@TempDir Path root) throws Exception {
    Path harness = root.resolve("h");
    Files.createDirectories(harness.resolve("__pycache__"));
    Files.writeString(harness.resolve("AGENTS.md"), "body");
    String before = RunMark.fingerprint(harness);
    Files.writeString(harness.resolve(RunMark.MARK), "{\"version\":1}");
    Files.writeString(harness.resolve("__pycache__/x.pyc"), "junk");
    Files.writeString(harness.resolve(".DS_Store"), "mac");
    assertEquals(before, RunMark.fingerprint(harness));
  }

  @Test
  void whyNotReportsInputAndHarnessMismatch(@TempDir Path root) throws Exception {
    Path harness = root.resolve("h");
    Files.createDirectories(harness);
    Files.writeString(harness.resolve("a.md"), "v1");
    Path run = root.resolve("run");
    RunMark.markComplete(run, "r1", harness, "inputs-a", null);
    assertTrue(RunMark.isComplete(run, harness, true, "inputs-a"));
    assertTrue(RunMark.isComplete(run, harness, false, "inputs-a"));

    String inputsWhy = RunMark.whyNot(run, harness, "inputs-b");
    assertTrue(inputsWhy.contains("task inputs"));
    assertTrue(inputsWhy.contains("changed since this rollout finished"));
    assertFalse(RunMark.isComplete(run, harness, true, "inputs-b"));

    Files.writeString(harness.resolve("a.md"), "v2");
    String harnessWhy = RunMark.whyNot(run, harness, "inputs-a");
    assertTrue(harnessWhy.contains("harness fingerprint"));
    assertTrue(harnessWhy.contains("different configuration"));
  }

  @Test
  void missingMarkExplainsNeverFinished(@TempDir Path root) {
    String why = RunMark.whyNot(root.resolve("empty"));
    assertTrue(why.contains("no " + RunMark.MARK));
    assertTrue(why.contains("never finished"));
  }
}
