package com.metamorph.substrate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.metamorph.config.ConfigError;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class CodexPreflightTest {
  @AfterEach
  void clearProps() {
    System.clearProperty("METAMORPH_CODEX_BASE_URL");
    System.clearProperty("METAMORPH_CODEX_API_KEY");
    System.clearProperty("METAMORPH_CODEX_CONTAINER_URL");
  }

  @Test
  void validateHttpUrlAcceptsHttpAndRejectsJunk() {
    CodexPreflight.validateHttpUrl("METAMORPH_CODEX_BASE_URL", "http://127.0.0.1:9/v1");
    CodexPreflight.validateHttpUrl("METAMORPH_CODEX_BASE_URL", "https://bridge.example/v1");
    assertThrows(
        ConfigError.class, () -> CodexPreflight.validateHttpUrl("METAMORPH_CODEX_BASE_URL", "ftp://x"));
    assertThrows(
        ConfigError.class, () -> CodexPreflight.validateHttpUrl("METAMORPH_CODEX_BASE_URL", "not-a-url"));
    assertThrows(
        ConfigError.class, () -> CodexPreflight.validateHttpUrl("METAMORPH_CODEX_BASE_URL", ""));
  }

  @Test
  void checkReportsMissingAndBridgeRequirement() {
    System.clearProperty("METAMORPH_CODEX_BASE_URL");
    System.clearProperty("METAMORPH_CODEX_API_KEY");
    var missing = CodexPreflight.check(false, false);
    assertFalse(missing.ok());
    assertTrue(missing.errors().stream().anyMatch(e -> e.contains("METAMORPH_CODEX_BASE_URL")));

    System.setProperty("METAMORPH_CODEX_BASE_URL", "http://127.0.0.1:4000/v1");
    System.setProperty("METAMORPH_CODEX_API_KEY", "k");
    var ok = CodexPreflight.check(false, false);
    assertTrue(ok.ok(), ok.errors().toString());

    var needBridge = CodexPreflight.check(false, true);
    assertFalse(needBridge.ok());
    assertTrue(
        needBridge.errors().stream().anyMatch(e -> e.contains("METAMORPH_CODEX_CONTAINER_URL")));

    System.setProperty("METAMORPH_CODEX_CONTAINER_URL", "http://host.docker.internal:4000/v1");
    var withBridge = new CodexSubstrate().preflight(false, true);
    assertTrue(withBridge.ok(), withBridge.errors().toString());
  }
}
