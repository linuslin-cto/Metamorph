package com.metamorph.benchmarks.lab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
// Map used for validateTree cue
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LabDeliverablesTest {
  @TempDir Path root;

  @Test
  void extractsNestedTextAndCapsLongFiles() throws Exception {
    Path out = root.resolve("output").resolve("contracts");
    Files.createDirectories(out);
    Files.writeString(out.resolve("memo.txt"), "short");
    Files.writeString(out.resolve("long.md"), "x".repeat(100));
    Map<String, String> files = LabDeliverables.extract(root, 20);
    assertEquals("short", files.get("contracts/memo.txt"));
    assertTrue(files.get("contracts/long.md").contains("[truncated"));
    assertTrue(LabDeliverables.asContents(files).contains("----- FILE: contracts/memo.txt -----"));
  }

  @Test
  void labAdapterCachesDeliverablesAndValidatesOutputCue() throws Exception {
    LabAdapter adapter = new LabAdapter(root);
    Path run = root.resolve("run-1");
    Files.createDirectories(run.resolve("workspace").resolve("output"));
    Files.writeString(run.resolve("workspace").resolve("output").resolve("a.txt"), "A");
    Map<String, String> first = adapter.deliverableFiles(run);
    Map<String, String> second = adapter.deliverableFiles(run);
    assertEquals(first, second);
    assertEquals("A", first.get("a.txt"));
    adapter.validateTree(Map.of("systemprompt.md", "write to output/report.docx"));
  }

  @Test
  void nestedBinaryGetsStubAndMissingScoreIsNull() throws Exception {
    LabAdapter adapter = new LabAdapter(root);
    Path run = root.resolve("run-nested");
    Path nested = run.resolve("output").resolve("deep").resolve("bin");
    Files.createDirectories(nested);
    Files.write(nested.resolve("slide.pptx"), new byte[] {0x00, 0x01, 0x02, 0x03, 0x50, 0x4b});
    Files.writeString(run.resolve("output").resolve("readme.txt"), "hi");
    Map<String, String> files = adapter.deliverableFiles(run);
    assertEquals("hi", files.get("readme.txt"));
    assertTrue(files.get("deep/bin/slide.pptx").contains("binary file"));
    assertTrue(adapter.deliverableText(run).contains("----- FILE: deep/bin/slide.pptx -----"));
    org.junit.jupiter.api.Assertions.assertNull(adapter.score(run, "contracts/t1"));
  }
}
