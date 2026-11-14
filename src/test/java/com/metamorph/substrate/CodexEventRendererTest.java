package com.metamorph.substrate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CodexEventRendererTest {
  @TempDir Path root;

  @Test
  void parseItemsDedupesByItemIdKeepingLastState() throws Exception {
    Path traj = root.resolve("dedupe.jsonl");
    Files.writeString(
        traj,
        """
        junk
        {"type":"item","item":{"id":"a1","type":"command_execution","command":"echo 1","status":"in_progress","aggregated_output":""}}
        {"type":"item","item":{"id":"a1","type":"command_execution","command":"echo 1","status":"completed","exit_code":0,"aggregated_output":"1"}}
        {"type":"item","item":{"id":"b2","type":"agent_message","text":"done"}}
        """);
    List<JsonNode> events = CodexEventRenderer.parseItems(traj);
    assertEquals(2, events.size());
    assertEquals("completed", events.get(0).path("item").path("status").asText());
    assertEquals("done", events.get(1).path("item").path("text").asText());
  }

  @Test
  void renderCommandAgentAndTurn() throws Exception {
    Path traj = root.resolve("cx.jsonl");
    Files.writeString(
        traj,
        """
        {"type":"item","item":{"id":"1","type":"command_execution","command":"ls","status":"failed","exit_code":1,"aggregated_output":"nope"}}
        {"type":"item","item":{"id":"2","type":"agent_message","text":"retry"}}
        {"type":"item","item":{"id":"3","type":"user_message","text":"# AGENTS.md instructions\\nbe careful"}}
        {"type":"turn.completed","usage":{"input_tokens":3,"cached_input_tokens":1,"output_tokens":2,"reasoning_output_tokens":0}}
        """);
    String rendered = CodexEventRenderer.renderEvents(traj, 1000, 10_000);
    assertTrue(rendered.contains("[ACTION] shell: ls"), rendered);
    assertTrue(rendered.contains("[OBSERVATION ERROR] nope | exit_code=1"), rendered);
    assertTrue(rendered.contains("[ASSISTANT] retry"), rendered);
    assertTrue(rendered.contains("[HARNESS AGENTS.md]"), rendered);
    assertTrue(rendered.contains("turn completed | tokens in=3"), rendered);
  }

  @Test
  void rolloutPayloadFunctionCall() throws Exception {
    Path traj = root.resolve("roll.jsonl");
    Files.writeString(
        traj,
        """
        {"timestamp":"t","payload":{"type":"function_call","name":"shell","arguments":"{\\"cmd\\":\\"pwd\\"}"}}
        {"timestamp":"t2","payload":{"type":"function_call_output","output":"/tmp"}}
        {"timestamp":"t3","payload":{"type":"message","role":"assistant","text":"ok"}}
        """);
    String rendered = CodexEventRenderer.renderEvents(traj, 500, 5000);
    assertTrue(rendered.contains("[ACTION] shell: pwd"), rendered);
    assertTrue(rendered.contains("[OBSERVATION] /tmp"), rendered);
    assertTrue(rendered.contains("[ASSISTANT] ok"), rendered);
  }

  @Test
  void emptyStreamPlaceholder() throws Exception {
    Path traj = root.resolve("empty.jsonl");
    Files.writeString(traj, "not-json\n");
    assertEquals(
        "(no parseable events in trajectory)", CodexEventRenderer.renderEvents(traj, 100, 1000));
    assertFalse(CodexEventRenderer.clip("ab", 10).contains("truncation"));
  }
}
