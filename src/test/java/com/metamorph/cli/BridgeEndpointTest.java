package com.metamorph.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BridgeEndpointTest {
  @TempDir Path root;

  @Test
  void reusesSavedPortAndKey() throws Exception {
    Path bridge = root.resolve("codex").resolve("bridge.json");
    BridgeEndpoint.Endpoint first = BridgeEndpoint.loadOrCreate(bridge, "127.0.0.1", 0);
    assertTrue(first.port() > 0);
    assertFalse(first.key().isBlank());
    assertTrue(Files.isRegularFile(bridge));

    BridgeEndpoint.Endpoint again = BridgeEndpoint.loadOrCreate(bridge, "127.0.0.1", 0);
    assertEquals(first.port(), again.port());
    assertEquals(first.key(), again.key());

    BridgeEndpoint.Endpoint forced = BridgeEndpoint.loadOrCreate(bridge, "127.0.0.1", 18080);
    assertEquals(18080, forced.port());
    assertEquals(first.key(), forced.key());
  }

  @Test
  void envHintsAndLitellmTemplate() {
    BridgeEndpoint.Endpoint ep = new BridgeEndpoint.Endpoint(9, "secret-key");
    Map<String, String> local = BridgeEndpoint.envHints(ep, "gemini-x", "127.0.0.1");
    assertEquals("http://127.0.0.1:9/v1", local.get("METAMORPH_CODEX_BASE_URL"));
    assertEquals("secret-key", local.get("METAMORPH_CODEX_API_KEY"));
    assertFalse(local.containsKey("METAMORPH_CODEX_CONTAINER_URL"));

    Map<String, String> docker = BridgeEndpoint.envHints(ep, "gemini-x", "0.0.0.0");
    assertEquals(
        "http://host.docker.internal:9/v1", docker.get("METAMORPH_CODEX_CONTAINER_URL"));

    String yaml = BridgeEndpoint.litellmYaml("gemini-x");
    assertTrue(yaml.contains("model_name: gemini-x"));
    assertTrue(yaml.contains("vertex_ai/gemini-x"));
    assertTrue(yaml.contains("METAMORPH_BRIDGE_KEY"));
  }
}
