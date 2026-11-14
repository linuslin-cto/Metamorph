package com.metamorph.core.proposer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Pinned OpenCode JSONL protocol stand-ins; no executor or model is contacted. */
class ProposerSessionErrorsTest {
  private static final ObjectMapper JSON = new ObjectMapper();

  private String events(boolean fail) throws Exception {
    List<Map<String, Object>> events = new ArrayList<>();
    Map<String, Object> text = new LinkedHashMap<>();
    text.put("type", "text");
    text.put("part", Map.of("type", "text", "text", "partial or final answer"));
    events.add(text);
    Map<String, Object> step = new LinkedHashMap<>();
    step.put("type", "step_finish");
    step.put("part", Map.of("type", "step-finish", "cost", 0.25));
    events.add(step);
    if (fail) {
      Map<String, Object> err = new LinkedHashMap<>();
      err.put("type", "error");
      err.put(
          "error",
          Map.of("name", "APIError", "data", Map.of("message", "provider unavailable")));
      events.add(err);
    }
    StringBuilder sb = new StringBuilder();
    for (Map<String, Object> e : events) {
      sb.append(JSON.writeValueAsString(e)).append('\n');
    }
    return sb.toString();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void sessionErrorRejectsPartialOutputAndPreservesCost(boolean answerFile) throws Exception {
    String stdout = events(true);
    String answer = answerFile ? "partial or final answer" : "";
    var parsed = OpencodeEventParser.parse(stdout, answer);
    assertTrue(parsed.failed());
    assertEquals(0.25, parsed.costUsd(), 1e-9);
    assertEquals("", parsed.text());
    ProposerError err =
        assertThrows(
            ProposerError.class, () -> OpencodeEventParser.toReply("fixture", parsed, 1));
    assertTrue(err.getMessage().contains("provider unavailable"));
    assertEquals(0.25, err.costUsd(), 1e-9);
    assertFalse(err.failedReply().ok());
    assertEquals("", err.failedReply().text());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void successfulFileAndStdoutAnswersRemainUsable(boolean answerFile) throws Exception {
    String stdout = events(false);
    String answer = answerFile ? "partial or final answer" : "";
    var parsed = OpencodeEventParser.parse(stdout, answer);
    Reply reply = OpencodeEventParser.toReply("fixture", parsed, 1);
    assertTrue(reply.ok());
    assertEquals("partial or final answer", reply.text());
    assertEquals(0.25, reply.costUsd(), 1e-9);
  }
}
