package com.metamorph.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class BridgeCommandTest {
  @TempDir Path root;

  @Test
  void writesEndpointFilesAndPrintsEnvHints() throws Exception {
    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    PrintStream prev = System.out;
    System.setOut(new PrintStream(buf, true, StandardCharsets.UTF_8));
    int code;
    try {
      code =
          new CommandLine(new BridgeCommand())
              .execute(
                  "--model",
                  "gemini-fixture",
                  "--host",
                  "0.0.0.0",
                  "--root",
                  root.toString());
    } finally {
      System.setOut(prev);
    }
    assertEquals(0, code, buf.toString(StandardCharsets.UTF_8));
    assertTrue(Files.isRegularFile(root.resolve("bridge.json")));
    assertTrue(Files.isRegularFile(root.resolve("bridge.yaml")));
    String yaml = Files.readString(root.resolve("bridge.yaml"));
    assertTrue(yaml.contains("gemini-fixture"));
    String out = buf.toString(StandardCharsets.UTF_8);
    assertTrue(out.contains("METAMORPH_CODEX_BASE_URL="));
    assertTrue(out.contains("METAMORPH_CODEX_CONTAINER_URL="));
    assertTrue(out.contains("LiteLLM not launched"));
  }
}
