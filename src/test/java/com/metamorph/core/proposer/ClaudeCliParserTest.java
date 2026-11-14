package com.metamorph.core.proposer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.metamorph.core.spec.ChannelSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ClaudeCliParserTest {
  @AfterEach
  void clear() {
    System.clearProperty("GOOGLE_CLOUD_PROJECT");
    System.clearProperty("CLOUD_ML_REGION");
  }

  @Test
  void parsesSuccessErrorAndCost() {
    var ok =
        ClaudeCliParser.parse(
            "{\"result\":\"edited\",\"subtype\":\"success\",\"total_cost_usd\":0.12}", 0, "");
    assertTrue(ok.ok());
    assertEquals("edited", ok.text());
    assertEquals(0.12, ok.costUsd(), 1e-9);

    var fail =
        ClaudeCliParser.parse(
            "{\"result\":\"nope\",\"subtype\":\"error_during_execution\",\"is_error\":true}", 0, "");
    assertFalse(fail.ok());
    assertTrue(fail.text().isBlank());
    assertTrue(fail.error().contains("CLI error"));

    var rc = ClaudeCliParser.parse("", 1, "boom");
    assertFalse(rc.ok());
    assertTrue(rc.error().contains("rc=1"));
  }

  @Test
  void systemAttachSplitsOnArgMax() {
    var small = ClaudeCliParser.attachSystem("be careful", "do work");
    assertTrue(small.useAppendFlag());
    assertEquals("be careful", small.appendPrompt());
    assertEquals("do work", small.stdinPrefix());

    String huge = "x".repeat(ClaudeCliParser.SYSTEM_ARG_MAX + 1);
    var big = ClaudeCliParser.attachSystem(huge, "do work");
    assertTrue(big.stdinPrefix().contains("SYSTEM INSTRUCTIONS"));
    assertTrue(big.stdinPrefix().contains("do work"));
    assertTrue(big.appendPrompt().contains("SYSTEM INSTRUCTIONS block"));
  }

  @Test
  void proposerEnvSetsClaudeVertexKeys() {
    System.setProperty("GOOGLE_CLOUD_PROJECT", "proj-1");
    System.setProperty("CLOUD_ML_REGION", "us-east1");
    Proposer p =
        new Proposer(new ChannelSpec("claude_cli", "claude-sonnet-4")) {
          @Override
          public Reply call(
              String mission, java.nio.file.Path contextDir, String label, String harvest, int maxAttempts) {
            throw new UnsupportedOperationException();
          }
        };
    var env = p.env();
    assertEquals("1", env.get("CLAUDE_CODE_USE_VERTEX"));
    assertEquals("proj-1", env.get("ANTHROPIC_VERTEX_PROJECT_ID"));
    assertEquals("us-east1", env.get("CLOUD_ML_REGION"));
    assertEquals("claude-sonnet-4", env.get("ANTHROPIC_MODEL"));
  }
}
