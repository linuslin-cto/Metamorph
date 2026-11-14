package com.metamorph.core.proposer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Parses Claude Code CLI {@code --output-format json} stdout. Ports the reply extraction in {@code
 * Proposer._claude_cli}.
 */
public final class ClaudeCliParser {
  private static final ObjectMapper JSON = new ObjectMapper();
  public static final int SYSTEM_ARG_MAX = 120_000;

  private ClaudeCliParser() {}

  public record Parsed(String text, double costUsd, String error) {
    public boolean ok() {
      return error == null || error.isBlank();
    }
  }

  public static Parsed parse(String stdout, int returnCode, String stderr) {
    JsonNode payload = parseJson(stdout);
    String text = "";
    if (payload != null) {
      text = firstText(payload, "result", "text");
    }
    String err = "";
    if (payload != null) {
      boolean isError = payload.path("is_error").asBoolean(false);
      String subtype = payload.has("subtype") ? payload.path("subtype").asText(null) : null;
      if (isError || (subtype != null && !"success".equals(subtype))) {
        String snippet = text.length() > 200 ? text.substring(0, 200) : text;
        err = "CLI error: " + subtype + " " + snippet;
        text = "";
      }
    }
    if (err.isBlank() && returnCode != 0 && text.isBlank()) {
      String tail = stderr == null ? "" : stderr;
      if (tail.length() > 300) {
        tail = tail.substring(tail.length() - 300);
      }
      err = "rc=" + returnCode + ": " + tail;
    }
    double cost = payload == null ? 0.0 : payload.path("total_cost_usd").asDouble(0.0);
    return new Parsed(text, cost, err);
  }

  /** How system instructions are attached when over/under {@link #SYSTEM_ARG_MAX}. */
  public record SystemAttach(String stdinPrefix, boolean useAppendFlag, String appendPrompt) {}

  public static SystemAttach attachSystem(String system, String mission) {
    if (system == null || system.isBlank()) {
      return new SystemAttach(mission == null ? "" : mission, false, "");
    }
    if (system.length() > SYSTEM_ARG_MAX) {
      String stdin =
          "# SYSTEM INSTRUCTIONS\n\n"
              + system
              + "\n\n# END\n\n"
              + (mission == null ? "" : mission);
      return new SystemAttach(
          stdin, true, "Follow the SYSTEM INSTRUCTIONS block at the top of the message.");
    }
    return new SystemAttach(mission == null ? "" : mission, true, system);
  }

  private static JsonNode parseJson(String stdout) {
    if (stdout == null || stdout.isBlank()) {
      return null;
    }
    try {
      return JSON.readTree(stdout.strip());
    } catch (Exception e) {
      // Some CLI builds emit trailing logs; try the last JSON object line.
      String last = "";
      for (String line : stdout.split("\\R")) {
        String s = line.strip();
        if (s.startsWith("{")) {
          last = s;
        }
      }
      if (last.isBlank()) {
        return null;
      }
      try {
        return JSON.readTree(last);
      } catch (Exception ignored) {
        return null;
      }
    }
  }

  private static String firstText(JsonNode n, String... keys) {
    for (String k : keys) {
      String v = n.path(k).asText("");
      if (!v.isBlank()) {
        return v;
      }
    }
    return "";
  }
}
