package com.metamorph.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Deployment settings. Shell / process environment values override {@code .env}.
 *
 * <p>Mirrors the upstream config module from the upstream reference.
 */
public final class Config {
  public static final int METHOD_VERSION = 2;
  public static final int TRANSPORT_ATTEMPTS = 2;
  public static final int PHASE_WIDTH = Integer.getInteger("metamorph.phaseWidth", 2);
  public static final int PROPOSER_CONCURRENCY =
      envInt("METAMORPH_PROPOSER_CONCURRENCY", PHASE_WIDTH);

  public static final String DEFAULT_PROPOSER_CHANNEL = "opencode";
  public static final String CODEX_DIST = env("METAMORPH_CODEX_DIST", "dependencies/codex");

  public static final Map<String, String> PROPOSER_MODEL_BY_CHANNEL =
      new LinkedHashMap<>(
          Map.of(
              "opencode", env("METAMORPH_IMPROVER_MODEL", env("METAMORPH_SOLVER_MODEL", "")),
              "claude_cli", env("METAMORPH_CLAUDE_MODEL", "")));

  public static final Set<String> SCRUB_ENV =
      Set.of(
          "METAMORPH_JUDGE_API_KEY",
          "METAMORPH_BRIDGE_KEY",
          "METAMORPH_CODEX_API_KEY",
          "ANTHROPIC_API_KEY",
          "ANTHROPIC_AUTH_TOKEN",
          "ANTHROPIC_BASE_URL",
          "ANTHROPIC_BEDROCK_BASE_URL",
          "CLAUDE_CODE_USE_BEDROCK",
          "OPENAI_API_KEY",
          "GEMINI_API_KEY",
          "GOOGLE_API_KEY",
          "GOOGLE_GENERATIVE_AI_API_KEY",
          "GITHUB_TOKEN",
          "GH_TOKEN",
          "SLACK_BOT_TOKEN",
          "SLACK_WEBHOOK_URL",
          "JIRA_API_TOKEN",
          "AWS_ACCESS_KEY_ID",
          "AWS_SECRET_ACCESS_KEY",
          "AWS_SESSION_TOKEN",
          "AWS_BEARER_TOKEN_BEDROCK",
          "AZURE_OPENAI_API_KEY");

  private Config() {}

  /** Load KEY=VALUE lines from path into the process environment with setdefault semantics. */
  public static void loadDotenv(Path path) {
    if (path == null || !Files.isRegularFile(path)) {
      return;
    }
    try {
      for (String raw : Files.readAllLines(path)) {
        String line = raw.strip();
        if (line.isEmpty() || line.startsWith("#") || !line.contains("=")) {
          continue;
        }
        if (line.startsWith("export ")) {
          line = line.substring("export ".length()).stripLeading();
        }
        int eq = line.indexOf('=');
        String key = line.substring(0, eq).strip();
        if (key.isEmpty() || !key.chars().allMatch(c -> Character.isLetterOrDigit(c) || c == '_')) {
          continue;
        }
        String value = line.substring(eq + 1).strip();
        if (value.length() >= 2
            && value.charAt(0) == value.charAt(value.length() - 1)
            && (value.charAt(0) == '\'' || value.charAt(0) == '"')) {
          value = value.substring(1, value.length() - 1);
        } else if (value.contains(" #")) {
          value = value.split(" #", 2)[0].stripTrailing();
        }
        if (System.getenv(key) == null && System.getProperty(key) == null) {
          System.setProperty(key, value);
        }
      }
    } catch (IOException ignored) {
      // A missing or unreadable file is not an error.
    }
  }

  public static String env(String name) {
    return env(name, null);
  }

  public static String env(String name, String defaultValue) {
    String v = System.getenv(name);
    if (v == null || v.isEmpty()) {
      v = System.getProperty(name);
    }
    if (v == null || v.isEmpty()) {
      return defaultValue;
    }
    return v;
  }

  public static int envInt(String name, int defaultValue) {
    String v = env(name);
    if (v == null) {
      return defaultValue;
    }
    try {
      return Integer.parseInt(v);
    } catch (NumberFormatException e) {
      throw new ConfigError(name + " must be an integer, got '" + v + "'");
    }
  }

  public static String require(String name, String neededBy) {
    String v = env(name);
    if (v == null || v.isEmpty()) {
      throw new ConfigError(
          name
              + " is required for "
              + neededBy
              + "; set it in .env or the shell (see .env.example)");
    }
    return v;
  }

  /** Copy of the process environment with secret keys removed for child processes. */
  public static Map<String, String> scrubEnv() {
    return scrubEnv(System.getenv(), Set.of());
  }

  public static Map<String, String> scrubEnv(Map<String, String> baseEnv) {
    return scrubEnv(baseEnv, Set.of());
  }

  public static Map<String, String> scrubEnv(Map<String, String> baseEnv, Collection<String> keep) {
    Set<String> keepSet = keep == null ? Set.of() : Set.copyOf(keep);
    Set<String> drop =
        SCRUB_ENV.stream().filter(k -> !keepSet.contains(k)).collect(Collectors.toSet());
    Map<String, String> out = new LinkedHashMap<>();
    for (var e : baseEnv.entrySet()) {
      if (!drop.contains(e.getKey())) {
        out.put(e.getKey(), e.getValue());
      }
    }
    return out;
  }

  public static Path projectRoot() {
    Path cwd = Path.of("").toAbsolutePath().normalize();
    if (Files.isRegularFile(cwd.resolve("pom.xml"))) {
      return cwd;
    }
    Path parent = cwd.getParent();
    if (parent != null && Files.isRegularFile(parent.resolve("pom.xml"))) {
      return parent;
    }
    return cwd;
  }

  public static Path runsDir() {
    String override = env("METAMORPH_RUNS_DIR");
    if (override != null && !override.isEmpty()) {
      return Path.of(override).toAbsolutePath().normalize();
    }
    return projectRoot().resolve("runs");
  }

  static {
    loadDotenv(projectRoot().resolve(".env"));
    Objects.requireNonNull(PROPOSER_MODEL_BY_CHANNEL);
  }
}
