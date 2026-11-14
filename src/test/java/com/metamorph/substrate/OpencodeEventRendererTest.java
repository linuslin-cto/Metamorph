package com.metamorph.substrate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.metamorph.benchmarks.jb.JobBenchExitNote;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OpencodeEventRendererTest {
  @TempDir Path root;

  @Test
  void toolUseAndAssistantAndError() throws Exception {
    Path traj = root.resolve("mix.jsonl");
    Files.writeString(
        traj,
        """
        {"type":"text","part":{"type":"text","text":"draft memo"}}
        not-json
        {"type":"tool_use","part":{"type":"tool","tool":"write","state":{"status":"completed","input":{"filePath":"output/memo.docx"},"output":"written"}}}
        {"type":"error","error":{"name":"RunnerExit","data":{"message":"stopped at the 1 s time limit (exit 124); kept as the deliverable"}}}
        """);
    String rendered = OpencodeEventRenderer.renderEvents(traj, 1000, 10_000);
    assertTrue(rendered.contains("[ASSISTANT] draft memo"), rendered);
    assertTrue(rendered.contains("[ACTION] write("), rendered);
    assertTrue(rendered.contains("[OBSERVATION] written"), rendered);
    assertTrue(rendered.endsWith("kept as the deliverable") || rendered.contains("[ERROR] "), rendered);
    assertTrue(rendered.contains("stopped at the 1 s time limit (exit 124)"), rendered);
  }

  @Test
  void emptyAndJunkYieldPlaceholder() throws Exception {
    Path empty = root.resolve("empty.jsonl");
    Files.writeString(empty, "\n# comment\n{not json}\n");
    assertEquals(
        "(no parseable events in trajectory)",
        OpencodeEventRenderer.renderEvents(empty, 100, 1000));
  }

  @Test
  void clipAndMaxCharsTruncate() throws Exception {
    assertEquals("ab…(+2 chars — render truncation)", OpencodeEventRenderer.clip("abcd", 2));
    Path traj = root.resolve("long.jsonl");
    Files.writeString(
        traj, "{\"type\":\"text\",\"part\":{\"type\":\"text\",\"text\":\"" + "x".repeat(80) + "\"}}\n");
    String rendered = OpencodeEventRenderer.renderEvents(traj, 1000, 40);
    assertTrue(rendered.length() > 40);
    assertTrue(rendered.contains("chars — render truncation"), rendered);
  }

  @Test
  void exitNoteRendersThroughSharedGrammar() throws Exception {
    Path traj = root.resolve("note.jsonl");
    Files.writeString(traj, "");
    JobBenchExitNote.noteExit(traj, root.resolve("x.log"), 124, "1", true);
    String rendered = OpencodeEventRenderer.renderEvents(traj, 1000, 10_000);
    assertTrue(rendered.startsWith("[ERROR] "), rendered);
    assertTrue(rendered.contains("stopped at the 1 s time limit (exit 124)"), rendered);
    assertTrue(rendered.contains("kept as the deliverable"), rendered);
    assertFalse(rendered.contains("(no parseable"));
  }

  @Test
  void toolErrorStatusEmitsObservationError() throws Exception {
    Path traj = root.resolve("terr.jsonl");
    Files.writeString(
        traj,
        "{\"type\":\"tool_call\",\"part\":{\"type\":\"tool_call\",\"name\":\"bash\",\"state\":{\"status\":\"error\",\"input\":{\"cmd\":\"ls\"},\"error\":\"boom\"}}}\n");
    String rendered = OpencodeEventRenderer.renderEvents(traj, 200, 5000);
    assertTrue(rendered.contains("[ACTION] bash("), rendered);
    assertTrue(rendered.contains("[OBSERVATION ERROR] boom"), rendered);
  }
}
