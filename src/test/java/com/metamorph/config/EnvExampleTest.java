package com.metamorph.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Replay documented .env loading: shell/process values win over file. */
class EnvExampleTest {
  @TempDir Path root;

  @Test
  void existingFileLoadingPreservesShellPrecedence() throws Exception {
    Path envFile = root.resolve(".env");
    Files.writeString(envFile, "GOOGLE_CLOUD_LOCATION=from-file\nMETAMORPH_DEMO_KEY=from-file\n");

    // Simulate shell already set (process property wins via Config.env check order: getenv then
    // getProperty). Set property after loadDotenv setdefault semantics.
    System.clearProperty("METAMORPH_DEMO_KEY");
    Config.loadDotenv(envFile);
    assertEquals("from-file", System.getProperty("METAMORPH_DEMO_KEY"));

    // Shell precedence: if already present, loadDotenv must not overwrite.
    System.setProperty("METAMORPH_DEMO_KEY", "from-shell");
    Files.writeString(envFile, "METAMORPH_DEMO_KEY=from-file-again\n");
    Config.loadDotenv(envFile);
    assertEquals("from-shell", System.getProperty("METAMORPH_DEMO_KEY"));
    System.clearProperty("METAMORPH_DEMO_KEY");
  }

  @Test
  void missingFileIsNotAnError() {
    Config.loadDotenv(root.resolve("missing.env"));
  }

  @Test
  void commentsAndExportPrefix() throws Exception {
    Path envFile = root.resolve(".env");
    Files.writeString(
        envFile,
        """
        # comment
        export METAMORPH_EXPORT_KEY=exported
        METAMORPH_QUOTED='single'
        METAMORPH_INLINE=value # trailing
        """);
    System.clearProperty("METAMORPH_EXPORT_KEY");
    System.clearProperty("METAMORPH_QUOTED");
    System.clearProperty("METAMORPH_INLINE");
    Config.loadDotenv(envFile);
    assertEquals("exported", System.getProperty("METAMORPH_EXPORT_KEY"));
    assertEquals("single", System.getProperty("METAMORPH_QUOTED"));
    assertEquals("value", System.getProperty("METAMORPH_INLINE"));
    System.clearProperty("METAMORPH_EXPORT_KEY");
    System.clearProperty("METAMORPH_QUOTED");
    System.clearProperty("METAMORPH_INLINE");
  }

  @Test
  void envReturnsNullWhenUnset() {
    assertNull(Config.env("METAMORPH_DEFINITELY_UNSET_XYZ"));
  }
}
