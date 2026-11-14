package com.metamorph.core.proposer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OpencodeEventParserTest {
  @Test
  void ignoresMalformedLinesAndAggregatesTextParts() {
    String stdout =
        "not-json\n"
            + "{\"type\":\"text\",\"part\":{\"text\":\"hello \"}}\n"
            + "{broken\n"
            + "{\"type\":\"text\",\"part\":{\"text\":\"world\"}}\n"
            + "{\"type\":\"step-finish\",\"part\":{\"cost\":1.5}}\n";
    var parsed = OpencodeEventParser.parse(stdout, "");
    assertFalse(parsed.failed());
    assertEquals("hello world", parsed.text());
    assertEquals(1.5, parsed.costUsd(), 1e-9);
  }

  @Test
  void answerFileOverridesStdoutText() {
    String stdout = "{\"type\":\"text\",\"part\":{\"text\":\"from-stream\"}}\n";
    var parsed = OpencodeEventParser.parse(stdout, " from-file \n");
    assertEquals("from-file", parsed.text());
  }

  @Test
  void errorNameFallbackAndEmptyReply() {
    String errOnly = "{\"type\":\"error\",\"error\":{\"name\":\"TimeoutError\"}}\n";
    var parsed = OpencodeEventParser.parse(errOnly, "should-be-cleared");
    assertTrue(parsed.failed());
    assertEquals("", parsed.text());
    assertEquals("TimeoutError", parsed.error());

    var empty = OpencodeEventParser.parse("{\"type\":\"step_finish\",\"part\":{}}\n", "");
    ProposerError pe =
        assertThrows(ProposerError.class, () -> OpencodeEventParser.toReply("lbl", empty, 2));
    assertTrue(pe.getMessage().contains("empty reply"));
    assertEquals(2, pe.attempts());
  }
}
